"""Database-backed health for the optional, local BP5 foundation profile."""

import ipaddress
import os
import re
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from psycopg import AsyncConnection, Error
from psycopg_pool import AsyncConnectionPool, PoolTimeout


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise ValueError(f"Missing required AI foundation setting: {name}")
    return value


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    if required("APP_ENV") not in {"local", "test"}:
        raise ValueError("AI foundation runtime requires APP_ENV=local|test")
    from psycopg.conninfo import make_conninfo

    dsn = make_conninfo(
        host=required("NIGSON_DB_HOST"),
        port=os.environ.get("NIGSON_DB_PORT", "5432"),
        dbname=required("NIGSON_DB_NAME"),
        user="nigson_ai_app",
        password=required("NIGSON_DB_PASSWORD"),
        options="-csearch_path=ai",
    )
    import logging

    from nigson_ai.platform.problem import ProblemWriter
    from nigson_ai.platform.telemetry import SafeFormatter, Telemetry

    telemetry = Telemetry(
        required("APP_ENV"), os.environ.get("NIGSON_OTEL_ENDPOINT", "")
    )
    application.state.telemetry = telemetry
    application.state.problems = ProblemWriter(
        required("APP_ENV"), os.environ.get("NIGSON_PROBLEM_TYPE_BASE", "")
    )
    # Identity configuration is an explicit deployment input. Missing values keep
    # business routes closed while liveness/readiness remain useful offline.
    auth_values = (
        os.environ.get("NIGSON_IDP_ISSUER"),
        os.environ.get("NIGSON_IDP_JWKS"),
        os.environ.get("NIGSON_IDP_AUDIENCE"),
        os.environ.get("NIGSON_IDP_CLIENTS"),
    )
    application.state.ai_authenticator = None
    application.state.ai_actor_public_keys = {}
    application.state.ai_actor_identity_issuers = frozenset()
    application.state.ai_private_peer_cidrs = ()
    application.state.ai_model_capabilities = frozenset()
    application.state.ai_provider_region = None
    application.state.ai_approved_rate_references = frozenset()
    application.state.ai_checkpoint_cipher = None
    application.state.ai_conversation_capability = None
    application.state.ai_conversation_ttl_seconds = None
    application.state.ai_checkpoint_ttl_seconds = None
    application.state.ai_default_locale = os.environ.get("NIGSON_AI_DEFAULT_LOCALE")
    application.state.ai_allowed_markets = frozenset(
        value for value in os.environ.get("NIGSON_AI_ALLOWED_MARKETS", "").split(",") if value
    )
    application.state.assistant_test_model_enabled = False
    if os.environ.get("NIGSON_ACTOR_PUBLIC_KEYS"):
        from nigson_ai.evaluation.trust import parse_public_keys

        application.state.ai_actor_public_keys = parse_public_keys(
            os.environ["NIGSON_ACTOR_PUBLIC_KEYS"]
        )
    if os.environ.get("NIGSON_PRIVATE_PEER_CIDRS"):
        peer_values = os.environ["NIGSON_PRIVATE_PEER_CIDRS"].split(",")
        if not 1 <= len(peer_values) <= 32 or any(not value for value in peer_values):
            raise ValueError("PRIVATE_PEER_POLICY_REQUIRED")
        application.state.ai_private_peer_cidrs = tuple(
            ipaddress.ip_network(value, strict=True) for value in peer_values
        )
    if all(auth_values):
        from nigson_ai.platform.identity import IdentityPolicy, KeycloakIdentity

        issuer, jwks, audience, clients = auth_values
        application.state.ai_actor_identity_issuers = frozenset({str(issuer)})
        application.state.ai_authenticator = KeycloakIdentity(
            IdentityPolicy(
                required("APP_ENV"),
                str(issuer),
                str(jwks),
                str(audience),
                frozenset(str(clients).split(",")),
                frozenset({"openid"}),
            )
        )
    region = os.environ.get("NIGSON_AI_BEDROCK_REGION", "")
    model_values = os.environ.get("NIGSON_AI_APPROVED_BEDROCK_MODELS", "").split(",")
    if region and not re.fullmatch(r"[a-z]{2}(?:-gov)?-[a-z]+-[0-9]", region):
        raise ValueError("AI_PROVIDER_REGION_INVALID")
    if region and model_values and all(model_values):
        if len(model_values) > 32 or any(len(model) > 200 for model in model_values):
            raise ValueError("AI_MODEL_CAPABILITY_POLICY_INVALID")
        application.state.ai_provider_region = region
        application.state.ai_model_capabilities = frozenset(
            ("BEDROCK", model) for model in model_values
        )
    elif region or any(model_values):
        raise ValueError("AI_MODEL_CAPABILITY_POLICY_INVALID")
    approved_rate_values = os.environ.get("NIGSON_AI_RATE_APPROVAL_REFERENCES", "").split(",")
    if any(approved_rate_values):
        if (
            len(approved_rate_values) > 100
            or any(
                not re.fullmatch(r"[A-Za-z0-9._:/-]{1,120}", value)
                for value in approved_rate_values
            )
        ):
            raise ValueError("AI_RATE_APPROVAL_POLICY_INVALID")
        application.state.ai_approved_rate_references = frozenset(approved_rate_values)
    from nigson_ai.evaluation.mock import LocalEvaluationModel
    from nigson_ai.gateway.provider import LocalProviderMock

    application.state.evaluation_model = LocalEvaluationModel()
    application.state.ai_provider_mock = (
        LocalProviderMock() if required("APP_ENV") in {"local", "test"} else None
    )
    from nigson_ai.assistant.provider import LocalAssistantProvider
    from nigson_ai.assistant.security import CheckpointCipher, ConversationCapabilities
    from nigson_ai.assistant.tools import LocalOwnerTools
    from nigson_ai.knowledge.provider import LocalKnowledgeProvider

    checkpoint_key = os.environ.get("NIGSON_AI_CHECKPOINT_KEY", "")
    anonymous_key = os.environ.get("NIGSON_AI_ANON_TOKEN_KEY", "")
    if checkpoint_key:
        import base64

        try:
            application.state.ai_checkpoint_cipher = CheckpointCipher(base64.urlsafe_b64decode(checkpoint_key + "=" * (-len(checkpoint_key) % 4)))
        except Exception as failure:
            raise ValueError("AI_CHECKPOINT_KEY_INVALID") from failure
    if anonymous_key:
        import base64

        try:
            application.state.ai_conversation_capability = ConversationCapabilities(base64.urlsafe_b64decode(anonymous_key + "=" * (-len(anonymous_key) % 4)))
        except Exception as failure:
            raise ValueError("AI_ANONYMOUS_TOKEN_KEY_INVALID") from failure
    for env_name, attr in (
        ("NIGSON_AI_CONVERSATION_TTL_SECONDS", "ai_conversation_ttl_seconds"),
        ("NIGSON_AI_CHECKPOINT_TTL_SECONDS", "ai_checkpoint_ttl_seconds"),
    ):
        raw = os.environ.get(env_name)
        if raw:
            if not raw.isascii() or not raw.isdigit():
                raise ValueError("AI_RETENTION_CONFIGURATION_INVALID")
            setattr(application.state, attr, int(raw))

    application.state.ai_knowledge_mock = (
        LocalKnowledgeProvider() if required("APP_ENV") in {"local", "test"} else None
    )
    application.state.assistant_provider = (
        LocalAssistantProvider() if required("APP_ENV") in {"local", "test"} else None
    )
    application.state.assistant_owner_tools = (
        LocalOwnerTools() if required("APP_ENV") in {"local", "test"} else None
    )
    application.state.assistant_test_model_enabled = required("APP_ENV") in {"local", "test"}
    handler = logging.StreamHandler()
    handler.setFormatter(SafeFormatter(required("APP_ENV")))
    logging.getLogger().handlers = [handler]
    logging.getLogger().setLevel(logging.INFO)
    logging.getLogger("uvicorn.access").disabled = True

    async def observe_connection(connection: AsyncConnection[Any]) -> None:
        telemetry.instrument_connection(connection)

    pool = AsyncConnectionPool(
        dsn, min_size=1, max_size=2, timeout=2, open=False, configure=observe_connection
    )
    runtime = None
    try:
        await pool.open()
        application.state.pool = pool
        if os.environ.get("NIGSON_EVENTS_ENABLED") == "true":
            from nigson_ai.platform.runtime import EventRuntime

            runtime = EventRuntime(dsn, telemetry)
            application.state.events = runtime.pump
            runtime.start()
        telemetry.observe_pools([pool] + ([runtime.pool] if runtime else []))
        yield
    finally:
        if runtime is not None:
            import asyncio

            await asyncio.to_thread(runtime.close)
        await pool.close()
        if application.state.ai_authenticator is not None and hasattr(
            application.state.ai_authenticator, "close"
        ):
            application.state.ai_authenticator.close()
        telemetry.close()


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
from nigson_ai.platform.boundary import install

install(app, lambda: app.state.telemetry, lambda: app.state.problems)
from nigson_ai.assistant.api import router as assistant_router
from nigson_ai.evaluation.api import router as evaluation_router
from nigson_ai.gateway.api import router as gateway_router
from nigson_ai.knowledge.api import router as knowledge_router

app.include_router(evaluation_router)
app.include_router(gateway_router)
app.include_router(knowledge_router)
app.include_router(assistant_router)


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready")
async def ready() -> JSONResponse:
    try:
        async with (
            app.state.pool.connection(timeout=2) as connection,
            connection.cursor() as cursor,
        ):
            await cursor.execute(
                "SELECT current_user = 'nigson_ai_app' AND current_schema() = 'ai' "
                "AND EXISTS (SELECT 1 FROM ai.databasechangelog WHERE id = 'ai-20261005-001') "
                "AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector' AND extversion = '0.8.2')"
            )
            result = await cursor.fetchone()
        healthy = bool(result and result[0])
    except (Error, PoolTimeout, TimeoutError):
        # Probe fails safely without disclosing DB errors/credentials. No business command is swallowed.
        healthy = False
    return JSONResponse(
        {"status": "UP" if healthy else "DOWN"}, status_code=200 if healthy else 503
    )
