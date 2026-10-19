"""AI-owned policy, immutable prompt/rate revision and activation contracts."""

from __future__ import annotations

import hashlib
import re
import uuid
from datetime import UTC, datetime, timedelta
from decimal import Decimal, DecimalException
from typing import Any, Literal, cast

from fastapi import APIRouter, Header, Request
from psycopg.types.json import Jsonb
from pydantic import (
    Field,
    StrictFloat,
    StrictInt,
    StrictStr,
    field_validator,
    model_validator,
)

from nigson_ai.platform.audit import CURRENCIES
from nigson_ai.platform.events import request_hash, uuid7
from nigson_ai.platform.problem import Failure, StrictModel
from nigson_ai.platform.store import Conflict
from nigson_ai.platform.telemetry import correlation

router = APIRouter()
ID_RE = re.compile(r"^[A-Za-z0-9._:-]{1,80}$")
MODEL_RE = re.compile(r"^[A-Za-z0-9._:/-]{1,200}$")
ETAG_RE = re.compile(r'^"([0-9]{1,19})"$')
APPROVED_TEMPLATE_VALUES = frozenset({"market", "locale", "question", "evidence"})


class ModelParameters(StrictModel):
    schemaVersion: Literal[1]
    temperature: StrictFloat = Field(ge=0, le=1)
    maxOutputTokens: StrictInt = Field(ge=128, le=2000)
    requestDeadlineSeconds: StrictInt = Field(ge=1, le=20)
    maximumSteps: StrictInt = Field(ge=1, le=8)
    maximumTools: StrictInt = Field(ge=0, le=4)
    maximumInputTokens: StrictInt = Field(ge=1, le=12000)
    concurrency: StrictInt = Field(ge=1, le=10)
    dailyBudget: StrictStr = Field(min_length=1, max_length=32)
    rateRevision: StrictInt = Field(ge=1)

    @field_validator("dailyBudget")
    @classmethod
    def positive_daily_budget(cls, value: str) -> str:
        try:
            amount = Decimal(value)
        except (DecimalException, ValueError, TypeError):
            raise ValueError("invalid budget") from None
        if not amount.is_finite() or amount <= 0 or len(amount.as_tuple().digits) > 19:
            raise ValueError("invalid budget")
        return format(amount, "f")


class CreatePolicy(StrictModel):
    code: StrictStr = Field(min_length=1, max_length=80)
    provider: Literal["BEDROCK"]
    chatModelId: StrictStr = Field(min_length=1, max_length=200)
    fallbackModelId: StrictStr | None = Field(default=None, max_length=200)
    parameters: ModelParameters
    promptRevisionId: StrictStr = Field(min_length=36, max_length=36)

    @field_validator("code")
    @classmethod
    def code_identifier(cls, value: str) -> str:
        if not ID_RE.fullmatch(value):
            raise ValueError("invalid code")
        return value

    @field_validator("chatModelId", "fallbackModelId")
    @classmethod
    def model_identifier(cls, value: str | None) -> str | None:
        if value is not None and not MODEL_RE.fullmatch(value):
            raise ValueError("invalid model id")
        return value

    @field_validator("promptRevisionId")
    @classmethod
    def prompt_id(cls, value: str) -> str:
        try:
            parsed = uuid.UUID(value)
        except ValueError:
            raise ValueError("invalid prompt revision") from None
        if parsed.int == 0:
            raise ValueError("invalid prompt revision")
        return str(parsed)


class CreatePrompt(StrictModel):
    code: StrictStr = Field(min_length=1, max_length=80)
    template: StrictStr = Field(min_length=1, max_length=20000)
    toolSchemaRevision: StrictStr = Field(min_length=1, max_length=80)
    reason: StrictStr = Field(min_length=1, max_length=500)

    @field_validator("code", "toolSchemaRevision")
    @classmethod
    def safe_code(cls, value: str) -> str:
        if not ID_RE.fullmatch(value):
            raise ValueError("invalid identifier")
        return value

    @field_validator("reason")
    @classmethod
    def safe_reason(cls, value: str) -> str:
        if not value.strip() or any(ord(char) < 32 for char in value):
            raise ValueError("invalid reason")
        return value

    @model_validator(mode="after")
    def reviewed_template_shape(self) -> CreatePrompt:
        placeholders = re.findall(r"\{\{\s*([^{}\s]+)\s*\}\}", self.template)
        template_without_slots = re.sub(
            r"\{\{\s*[A-Za-z][A-Za-z0-9_]*\s*\}\}", "", self.template
        )
        if (
            "{{" in template_without_slots
            or "}}" in template_without_slots
            or any(value not in APPROVED_TEMPLATE_VALUES for value in placeholders)
            or re.search(
                r"(?i)(AKIA[0-9A-Z]{16}|-----BEGIN .*PRIVATE KEY-----|Bearer\s+[A-Za-z0-9._-]{16,})",
                self.template,
            )
        ):
            raise ValueError("invalid reviewed template")
        return self


class CreateRate(StrictModel):
    provider: Literal["BEDROCK"]
    modelId: StrictStr = Field(min_length=1, max_length=200)
    inputPerMillion: StrictStr = Field(min_length=1, max_length=32)
    outputPerMillion: StrictStr = Field(min_length=1, max_length=32)
    currency: StrictStr = Field(pattern=r"^[A-Z]{3}$")
    effectiveFrom: StrictStr = Field(min_length=20, max_length=40)
    approvalReference: StrictStr = Field(min_length=1, max_length=120)

    @field_validator("modelId")
    @classmethod
    def model_identifier(cls, value: str) -> str:
        if not MODEL_RE.fullmatch(value):
            raise ValueError("invalid model id")
        return value

    @field_validator("approvalReference")
    @classmethod
    def safe_reference(cls, value: str) -> str:
        if not re.fullmatch(r"[A-Za-z0-9._:/-]{1,120}", value):
            raise ValueError("invalid approval reference")
        return value

    @field_validator("inputPerMillion", "outputPerMillion")
    @classmethod
    def money_decimal(cls, value: str) -> str:
        if not re.fullmatch(r"(?:0|[1-9][0-9]{0,12})(?:\.[0-9]{1,6})?", value):
            raise ValueError("invalid rate")
        return format(Decimal(value), ".6f")

    @field_validator("effectiveFrom")
    @classmethod
    def instant(cls, value: str) -> str:
        try:
            parsed = datetime.fromisoformat(value)
        except ValueError:
            raise ValueError("invalid instant") from None
        if parsed.tzinfo is None:
            raise ValueError("timezone required")
        return parsed.astimezone(UTC).isoformat().replace("+00:00", "Z")


class ActivatePolicy(StrictModel):
    evaluationResultId: StrictStr = Field(min_length=36, max_length=36)
    reason: StrictStr = Field(min_length=1, max_length=500)

    @field_validator("evaluationResultId")
    @classmethod
    def result_id(cls, value: str) -> str:
        try:
            parsed = uuid.UUID(value)
        except ValueError:
            raise ValueError("invalid evaluation result") from None
        if parsed.int == 0:
            raise ValueError("invalid evaluation result")
        return str(parsed)

    @field_validator("reason")
    @classmethod
    def safe_reason(cls, value: str) -> str:
        if not value.strip() or any(ord(char) < 32 for char in value):
            raise ValueError("invalid reason")
        return value


def _reject_query(request: Request) -> None:
    if request.query_params:
        raise Failure("VALIDATION_ERROR")


def _single_header(request: Request, name: str) -> None:
    if len(request.headers.getlist(name)) != 1:
        raise Failure("VALIDATION_ERROR")


def _principal(
    request: Request, *, recent_mfa: bool = False
) -> tuple[str, dict[str, Any]]:
    values = [
        value.decode("latin1")
        for name, value in request.scope.get("headers", [])
        if name.lower() == b"authorization"
    ]
    if (
        len(values) != 1
        or not values[0].startswith("Bearer ")
        or len(values[0]) > 16400
    ):
        raise Failure("AUTH_REQUIRED")
    authenticator = getattr(request.app.state, "ai_authenticator", None)
    if authenticator is None:
        raise Failure("AUTH_REQUIRED")
    claims = authenticator.decode(values[0][7:])
    subject, realm = claims.get("sub"), claims.get("realm_access", {})
    roles = realm.get("roles", []) if isinstance(realm, dict) else None
    if not isinstance(subject, str) or not subject or len(subject) > 200:
        raise Failure("AUTH_REQUIRED")
    if not isinstance(roles, list) or "ADMIN" not in roles:
        raise Failure("ACCESS_DENIED")
    if claims.get("organization_id") is not None:
        raise Failure("ACCESS_DENIED")
    if recent_mfa:
        now = int(datetime.now(UTC).timestamp())
        mfa_at = claims.get("mfa_at")
        if type(mfa_at) is not int or mfa_at > now + 5 or now - mfa_at >= 900:
            raise Failure("ACCESS_DENIED")
    return subject, claims


def _etag_version(value: str) -> int:
    match = ETAG_RE.fullmatch(value)
    if not match:
        raise Failure("VALIDATION_ERROR")
    return int(match.group(1))


def _pool(request: Request) -> Any:
    pool = getattr(request.app.state, "pool", None)
    if pool is None:
        raise Failure("PROVIDER_UNAVAILABLE")
    return pool


async def _lock_idempotency(
    connection: Any, actor: str, operation: str, key: str, data: dict[str, Any]
) -> tuple[str, Any]:
    digest = request_hash(data)
    await connection.execute(
        "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
        (f"{operation}:{actor}:{key}",),
    )
    row = await (
        await connection.execute(
            "SELECT request_hash,response_status,response_body FROM ai.idempotency_record WHERE principal_key=%s AND operation=%s AND request_key=%s FOR UPDATE",
            (actor, operation, key),
        )
    ).fetchone()
    if row:
        if row[0] != digest:
            raise Conflict("IDEMPOTENCY_HASH_MISMATCH")
        return "REPLAY", (row[1], row[2])
    return digest, None


async def _save_idempotency(
    connection: Any,
    actor: str,
    operation: str,
    key: str,
    digest: str,
    status: int,
    body: dict[str, Any],
    resource: uuid.UUID,
) -> None:
    now = datetime.now(UTC)
    await connection.execute(
        "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,resource_id,expires_at) VALUES (%s,%s,%s,%s,%s,%s,%s,'SUCCEEDED',%s,%s,%s,%s)",
        (
            uuid7(),
            actor,
            actor,
            actor,
            operation,
            key,
            digest,
            status,
            Jsonb(body),
            resource,
            now + timedelta(days=1),
        ),
    )


async def _audit(
    connection: Any,
    actor: str,
    action: str,
    resource_type: str,
    resource_id: uuid.UUID,
    values: dict[str, Any],
    reason: str = "CONFIGURATION_CHANGE",
) -> None:
    correlation_id = correlation.get() or uuid7()
    await connection.execute(
        "INSERT INTO ai.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason,new_value) VALUES (%s,%s,%s,'USER',%s,%s,%s,%s,%s,%s,%s)",
        (
            uuid7(),
            actor,
            actor,
            action,
            resource_type,
            resource_id,
            datetime.now(UTC),
            correlation_id,
            reason,
            Jsonb(values),
        ),
    )


def _record_command(request: Request, operation: str, outcome: str) -> None:
    telemetry = getattr(request.app.state, "telemetry", None)
    counter = getattr(telemetry, "ai_gateway_operations", None)
    if counter is not None:
        counter.add(1, {"owner": "ai", "operation": operation, "outcome": outcome})


def _capability_allowed(request: Request, provider: str, model: str) -> bool:
    configured: frozenset[tuple[str, str]] = getattr(
        request.app.state, "ai_model_capabilities", frozenset()
    )
    return (provider, model) in configured


@router.post("/api/v1/staff/ai/model-policies", status_code=201)
async def create_policy(
    payload: CreatePolicy,
    request: Request,
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request)
    _single_header(request, "Idempotency-Key")
    actor, _ = _principal(request)
    if not _capability_allowed(request, payload.provider, payload.chatModelId):
        raise Failure("FEATURE_DISABLED")
    pool = _pool(request)
    data = payload.model_dump(mode="json")
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-POLICY-CREATE", idempotency_key, data
        )
        if replay:
            _record_command(request, "policy.create", "replayed")
            return cast(dict[str, Any], replay[1])
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"ai-policy-code:{payload.code}",),
        )
        prompt = await (
            await connection.execute(
                "SELECT id FROM ai.prompt_revision WHERE id=%s",
                (uuid.UUID(payload.promptRevisionId),),
            )
        ).fetchone()
        rate = await (
            await connection.execute(
                "SELECT id FROM ai.model_rate WHERE provider=%s AND model_id=%s AND revision=%s AND effective_from<=%s",
                (
                    payload.provider,
                    payload.chatModelId,
                    payload.parameters.rateRevision,
                    datetime.now(UTC),
                ),
            )
        ).fetchone()
        if not prompt or not rate:
            raise Failure("GATEWAY_RULE_VIOLATION")
        revision = await (
            await connection.execute(
                "SELECT coalesce(max(revision),0)+1 FROM ai.model_policy WHERE code=%s",
                (payload.code,),
            )
        ).fetchone()
        identifier = uuid7()
        parameters = payload.parameters.model_dump(mode="json")
        result = {
            "id": str(identifier),
            "revision": revision[0],
            "approved": False,
            "version": 0,
        }
        await connection.execute(
            "INSERT INTO ai.model_policy(id,created_by,updated_by,code,revision,provider,chat_model_id,fallback_model_id,parameters,approved,prompt_revision_id) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,false,%s)",
            (
                identifier,
                actor,
                actor,
                payload.code,
                revision[0],
                payload.provider,
                payload.chatModelId,
                payload.fallbackModelId,
                Jsonb(parameters),
                uuid.UUID(payload.promptRevisionId),
            ),
        )
        await _save_idempotency(
            connection,
            actor,
            "AI-POLICY-CREATE",
            idempotency_key,
            idem,
            201,
            result,
            identifier,
        )
        await _audit(
            connection,
            actor,
            "AI_POLICY_CREATE",
            "AI_POLICY",
            identifier,
            {"revision": revision[0], "enabled": False, "state": "CREATED"},
        )
    _record_command(request, "policy.create", "created")
    return result


@router.post("/api/v1/staff/ai/prompt-revisions", status_code=201)
async def create_prompt(
    payload: CreatePrompt,
    request: Request,
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request)
    _single_header(request, "Idempotency-Key")
    actor, _ = _principal(request)
    pool = _pool(request)
    data = payload.model_dump(mode="json")
    checksum = hashlib.sha256(payload.template.encode("utf-8")).hexdigest()
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-PROMPT-CREATE", idempotency_key, data
        )
        if replay:
            _record_command(request, "prompt.create", "replayed")
            return cast(dict[str, Any], replay[1])
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"ai-prompt-code:{payload.code}",),
        )
        revision = await (
            await connection.execute(
                "SELECT coalesce(max(revision),0)+1 FROM ai.prompt_revision WHERE code=%s",
                (payload.code,),
            )
        ).fetchone()
        identifier = uuid7()
        result = {
            "id": str(identifier),
            "code": payload.code,
            "revision": revision[0],
            "checksum": checksum,
        }
        schema = {"type": "object", "additionalProperties": False}
        await connection.execute(
            "INSERT INTO ai.prompt_revision(id,created_by,code,revision,body,output_schema,checksum_sha256,tool_schema_revision) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
            (
                identifier,
                actor,
                payload.code,
                revision[0],
                payload.template,
                Jsonb(schema),
                checksum,
                payload.toolSchemaRevision,
            ),
        )
        await _save_idempotency(
            connection,
            actor,
            "AI-PROMPT-CREATE",
            idempotency_key,
            idem,
            201,
            result,
            identifier,
        )
        await _audit(
            connection,
            actor,
            "AI_PROMPT_REVISION_CREATE",
            "AI_PROMPT",
            identifier,
            {"revision": revision[0], "checksum": checksum, "reasonChecksum": hashlib.sha256(payload.reason.encode()).hexdigest(), "state": "CREATED"},
        )
    _record_command(request, "prompt.create", "created")
    return result


@router.post("/api/v1/staff/ai/model-rates", status_code=201)
async def create_rate(
    payload: CreateRate,
    request: Request,
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request)
    _single_header(request, "Idempotency-Key")
    actor, _ = _principal(request, recent_mfa=True)
    approved_references: frozenset[str] = getattr(
        request.app.state, "ai_approved_rate_references", frozenset()
    )
    if payload.approvalReference not in approved_references:
        raise Failure("FEATURE_DISABLED")
    if payload.currency not in CURRENCIES:
        raise Failure("VALIDATION_ERROR")
    pool = _pool(request)
    data = payload.model_dump(mode="json")
    approval_hash = hashlib.sha256(payload.approvalReference.encode()).hexdigest()
    effective = datetime.fromisoformat(payload.effectiveFrom)
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-RATE-CREATE", idempotency_key, data
        )
        if replay:
            _record_command(request, "rate.create", "replayed")
            return cast(dict[str, Any], replay[1])
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"ai-rate:{payload.provider}:{payload.modelId}",),
        )
        revision = await (
            await connection.execute(
                "SELECT coalesce(max(revision),0)+1 FROM ai.model_rate WHERE provider=%s AND model_id=%s",
                (payload.provider, payload.modelId),
            )
        ).fetchone()
        identifier = uuid7()
        result = {"id": str(identifier), "revision": revision[0]}
        await connection.execute(
            "INSERT INTO ai.model_rate(id,created_by,provider,model_id,revision,input_per_million,output_per_million,currency_code,effective_from) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (
                identifier,
                actor,
                payload.provider,
                payload.modelId,
                revision[0],
                Decimal(payload.inputPerMillion),
                Decimal(payload.outputPerMillion),
                payload.currency,
                effective,
            ),
        )
        await _save_idempotency(
            connection,
            actor,
            "AI-RATE-CREATE",
            idempotency_key,
            idem,
            201,
            result,
            identifier,
        )
        await _audit(
            connection,
            actor,
            "AI_MODEL_RATE_CREATE",
            "AI_MODEL_RATE",
            identifier,
            {
                "revision": revision[0],
                "currency": payload.currency,
                "checksum": approval_hash,
                "state": "APPROVED",
            },
        )
    _record_command(request, "rate.create", "created")
    return result


@router.post("/api/v1/staff/ai/model-policies/{id}/activation")
async def activate_policy(
    id: str,
    payload: ActivatePolicy,
    request: Request,
    if_match: str = Header(alias="If-Match", min_length=3, max_length=22),
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request)
    _single_header(request, "If-Match")
    _single_header(request, "Idempotency-Key")
    actor, _ = _principal(request, recent_mfa=True)
    try:
        policy_id = uuid.UUID(id)
    except ValueError:
        raise Failure("VALIDATION_ERROR") from None
    if not policy_id.int:
        raise Failure("VALIDATION_ERROR")
    expected_version = _etag_version(if_match)
    pool = _pool(request)
    data = {
        "id": str(policy_id),
        **payload.model_dump(mode="json"),
        "ifMatch": if_match,
    }
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-POLICY-ACTIVATE", idempotency_key, data
        )
        if replay:
            _record_command(request, "policy.activate", "replayed")
            return cast(dict[str, Any], replay[1])
        row = await (
            await connection.execute(
                "SELECT code,revision,provider,chat_model_id,parameters,approved,prompt_revision_id,version FROM ai.model_policy WHERE id=%s FOR UPDATE",
                (policy_id,),
            )
        ).fetchone()
        if not row:
            raise Failure("NOT_FOUND")
        code, revision, provider, model_id, parameters, approved, prompt_id, version = (
            row
        )
        if version != expected_version:
            raise Failure("VERSION_MISMATCH")
        if approved:
            raise Failure("INVALID_TRANSITION")
        if not _capability_allowed(request, provider, model_id):
            raise Failure("FEATURE_DISABLED")
        prompt = await (
            await connection.execute(
                "SELECT code,revision FROM ai.prompt_revision WHERE id=%s", (prompt_id,)
            )
        ).fetchone()
        evaluation = await (
            await connection.execute(
                "SELECT model_policy_revision,prompt_revision,scores,passed FROM ai.evaluation_result WHERE id=%s",
                (uuid.UUID(payload.evaluationResultId),),
            )
        ).fetchone()
        expected_policy = f"{code}:{revision}"
        expected_prompt = f"{prompt[0]}:{prompt[1]}" if prompt else ""
        scores = evaluation[2] if evaluation else None
        if (
            not evaluation
            or not evaluation[3]
            or evaluation[0] != expected_policy
            or evaluation[1] != expected_prompt
            or not isinstance(scores, dict)
            or scores.get("activationEligible") is not True
            or scores.get("localMockOnly") is not False
            or scores.get("policyMatched") is not True
        ):
            raise Failure("GATEWAY_RULE_VIOLATION")
        params = parameters if isinstance(parameters, dict) else {}
        rate = await (
            await connection.execute(
                "SELECT id FROM ai.model_rate WHERE provider=%s AND model_id=%s AND revision=%s AND effective_from<=%s",
                (provider, model_id, params.get("rateRevision"), datetime.now(UTC)),
            )
        ).fetchone()
        if not rate:
            raise Failure("GATEWAY_RULE_VIOLATION")
        await connection.execute(
            "UPDATE ai.model_policy SET approved=true,evaluation_result_id=%s,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND version=%s AND approved=false",
            (uuid.UUID(payload.evaluationResultId), actor, policy_id, expected_version),
        )
        result = {"id": str(policy_id), "revision": revision, "approved": True}
        await _save_idempotency(
            connection,
            actor,
            "AI-POLICY-ACTIVATE",
            idempotency_key,
            idem,
            200,
            result,
            policy_id,
        )
        await _audit(
            connection,
            actor,
            "AI_POLICY_ACTIVATION",
            "AI_POLICY",
            policy_id,
            {"revision": revision, "enabled": True, "checksum": hashlib.sha256(payload.reason.encode()).hexdigest(), "state": "ACTIVE"},
        )
    _record_command(request, "policy.activate", "activated")
    return result
