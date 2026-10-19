"""Owner-scoped conversation, bounded SSE run and feedback APIs."""

from __future__ import annotations

import hashlib
import json
import re
import uuid
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from typing import Any, cast

from fastapi import APIRouter, Header, Request, Response
from fastapi.responses import StreamingResponse
from psycopg.types.json import Jsonb
from pydantic import Field, StrictInt, StrictStr, field_validator

from nigson_ai.gateway.api import (
    _audit,
    _lock_idempotency,
    _pool,
    _save_idempotency,
)
from nigson_ai.gateway.budget import finish_run, reserve_run
from nigson_ai.knowledge.service import retrieve_hybrid
from nigson_ai.platform.events import uuid7
from nigson_ai.platform.problem import Failure, StrictModel

from .security import CheckpointCipher, party_uuid

router = APIRouter()
LOCALE_RE = re.compile(r"^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,4}$")
MARKET_RE = re.compile(r"^[A-Z0-9_-]{1,32}$")
UUID_RE = re.compile(r"(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b")
INJECTION_RE = re.compile(
    r"(ignore|disregard|override|forget).{0,60}(instruction|policy|system|safety)|"
    r"(reveal|print|show).{0,40}(system prompt|secret|credential)|"
    r"(execute|run).{0,30}(sql|shell|url|code)",
    re.IGNORECASE,
)
PII_RE = re.compile(
    r"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|\+?\d[\d ()-]{8,}\d|"
    r"\b(?:bearer\s+)[A-Za-z0-9._~-]{16,}\b"
)


def _reject_query(request: Request) -> None:
    if request.query_params:
        raise Failure("VALIDATION_ERROR")


class CreateConversation(StrictModel):
    market: StrictStr = Field(min_length=1, max_length=32)
    locale: StrictStr | None = Field(default=None, max_length=16)

    @field_validator("market")
    @classmethod
    def market_code(cls, value: str) -> str:
        if not MARKET_RE.fullmatch(value):
            raise ValueError("invalid market")
        return value

    @field_validator("locale")
    @classmethod
    def locale_code(cls, value: str | None) -> str | None:
        if value is not None and not LOCALE_RE.fullmatch(value):
            raise ValueError("invalid locale")
        return value


class SendMessage(StrictModel):
    message: StrictStr = Field(min_length=1, max_length=2000)
    clientRequestId: StrictStr = Field(min_length=36, max_length=36)

    @field_validator("clientRequestId")
    @classmethod
    def request_id(cls, value: str) -> str:
        try:
            parsed = uuid.UUID(value)
        except ValueError:
            raise ValueError("invalid request id") from None
        if not parsed.int or str(parsed) != value.lower():
            raise ValueError("invalid request id")
        return str(parsed)


class SubmitFeedback(StrictModel):
    rating: StrictInt
    category: StrictStr | None = Field(default=None, max_length=30)
    comment: StrictStr | None = Field(default=None, max_length=1000)

    @field_validator("rating")
    @classmethod
    def rating_value(cls, value: int) -> int:
        if value not in {-1, 1}:
            raise ValueError("invalid rating")
        return value

    @field_validator("category")
    @classmethod
    def category_value(cls, value: str | None) -> str | None:
        if value is not None and not re.fullmatch(r"[A-Za-z0-9_-]{1,30}", value):
            raise ValueError("invalid category")
        return value


def _principal(request: Request) -> tuple[str, uuid.UUID | None]:
    values = [v.decode("latin1") for k, v in request.scope.get("headers", []) if k.lower() == b"authorization"]
    if len(values) != 1 or not values[0].startswith("Bearer ") or len(values[0]) > 16400:
        raise Failure("AUTH_REQUIRED")
    authenticator = getattr(request.app.state, "ai_authenticator", None)
    if authenticator is None:
        raise Failure("AUTH_REQUIRED")
    claims = authenticator.decode(values[0][7:])
    subject = claims.get("sub")
    roles = claims.get("realm_access", {}).get("roles", [])
    if not isinstance(subject, str) or not subject or len(subject) > 200 or not isinstance(roles, list):
        raise Failure("AUTH_REQUIRED")
    if "CUSTOMER" not in roles or claims.get("organization_id") is not None:
        raise Failure("ACCESS_DENIED")
    party = party_uuid(claims.get("party_id"))
    if party is None:
        raise Failure("ACCESS_DENIED")
    return subject, party


def _optional_principal(request: Request) -> tuple[str, uuid.UUID | None] | None:
    values = [v for k, v in request.scope.get("headers", []) if k.lower() == b"authorization"]
    return _principal(request) if values else None


def _owner_token(request: Request, conversation_id: uuid.UUID) -> str:
    values = [v.decode("latin1") for k, v in request.scope.get("headers", []) if k.lower() == b"x-conversation-token"]
    capability = getattr(request.app.state, "ai_conversation_capability", None)
    if len(values) != 1 or capability is None:
        raise Failure("NOT_FOUND")
    return cast(str, capability.verify(values[0], conversation_id))


async def _owner(request: Request, connection: Any, conversation_id: uuid.UUID) -> tuple[str, uuid.UUID | None, str | None, dict[str, Any]]:
    row = await (await connection.execute(
        "SELECT party_id,anonymous_session_hash,market_code,locale,state,expires_at,version FROM ai.conversation WHERE id=%s",
        (conversation_id,),
    )).fetchone()
    if not row or row[4] != "ACTIVE" or row[5] <= datetime.now(UTC):
        raise Failure("NOT_FOUND")
    party, anonymous_hash, market, locale, _, _, version = row
    if party is not None:
        actor, caller_party = _principal(request)
        if caller_party != party:
            raise Failure("NOT_FOUND")
        return actor, party, None, {"market": market, "locale": locale, "version": version}
    token_hash = _owner_token(request, conversation_id)
    if token_hash != anonymous_hash:
        raise Failure("NOT_FOUND")
    return "anonymous:" + token_hash[:48], None, token_hash, {"market": market, "locale": locale, "version": version}


def _cipher(request: Request) -> CheckpointCipher:
    cipher = getattr(request.app.state, "ai_checkpoint_cipher", None)
    if cipher is None:
        raise Failure("FEATURE_DISABLED")
    return cast(CheckpointCipher, cipher)


def _metric(request: Request, operation: str, outcome: str) -> None:
    counter = getattr(getattr(request.app.state, "telemetry", None), "ai_assistant_operations", None)
    if counter is not None:
        counter.add(1, {"owner": "ai", "operation": operation, "outcome": outcome})


def _version(value: str | None) -> int:
    match = re.fullmatch(r'"([0-9]{1,19})"', value or "")
    if not match:
        raise Failure("VALIDATION_ERROR")
    return int(match.group(1))


def _sse(name: str, value: dict[str, Any]) -> bytes:
    return b"event: " + name.encode() + b"\ndata: " + json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode() + b"\n\n"


async def _response(run_id: uuid.UUID, answer: dict[str, Any]) -> StreamingResponse:
    async def body() -> AsyncIterator[bytes]:
        yield _sse("metadata", {"runId": str(run_id)})
        yield _sse("delta", {"text": answer["answer"]})
        yield _sse("sources", {"items": answer["sources"]})
        yield _sse("completed", {"answer": answer})

    return StreamingResponse(body(), media_type="text/event-stream", headers={"Cache-Control": "no-store", "X-Accel-Buffering": "no"})


async def _checkpoint(connection: Any, cipher: CheckpointCipher, actor: str, conversation_id: uuid.UUID, run_id: uuid.UUID, number: int, parent: uuid.UUID | None, expires: datetime, state: dict[str, Any]) -> uuid.UUID:
    identifier = uuid7()
    encrypted = cipher.encrypt(conversation_id, run_id, state)
    await connection.execute(
        "INSERT INTO ai.ai_checkpoint(id,created_by,conversation_id,run_id,checkpoint_number,state_ciphertext,parent_checkpoint_id,expires_at) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
        (identifier, actor, conversation_id, run_id, number, encrypted, parent, expires),
    )
    return identifier


async def _sources(request: Request, connection: Any, query: str, market: str, locale: str) -> list[dict[str, Any]]:
    provider = getattr(request.app.state, "ai_knowledge_mock", None)
    if provider is None:
        return []

    vector = provider.embed("assistant-query:" + hashlib.sha256(query.encode()).hexdigest(), query[:1000])

    async def validate(items: list[dict[str, Any]]) -> set[uuid.UUID]:
        approved: set[uuid.UUID] = set()
        for item in items:
            source = provider.fetch(str(item["sourceService"]), str(item["sourceResourceId"]))
            if source and provider.is_current(source):
                approved.add(uuid.UUID(str(item["sourceResourceId"])))
        return approved

    results = await retrieve_hybrid(
        connection, query=query[:1000], query_vector=vector, locale=locale,
        market_codes=(market,), validate_sources=validate, limit=5,
    )
    return [
        {
            "id": str(item["sourceResourceId"]),
            "url": str(item["sourceUrl"]),
            "title": str(item["sectionTitle"])[:200],
            "revision": int(item["sourceRevision"]),
            "text": str(item["body"])[:4000],
            "sourceService": str(item["sourceService"]),
            "sourceResourceId": str(item["sourceResourceId"]),
        }
        for item in results
    ]


def _answer(text: str, sources: list[dict[str, Any]], run_id: uuid.UUID, *, escalated: bool = False) -> dict[str, Any]:
    citations = [{k: source[k] for k in ("id", "url", "title", "revision")} for source in sources]
    return {
        "status": "ESCALATED" if escalated else "ANSWERED",
        "answer": text[:2000],
        "sources": citations,
        "products": [],
        "handoffUrl": None,
        "runId": str(run_id),
    }


@router.post("/api/v1/ai/conversations", status_code=201)
async def create_conversation(
    payload: CreateConversation,
    request: Request,
    response: Response,
    idempotency_key: str = Header(alias="Idempotency-Key", min_length=1, max_length=100),
) -> dict[str, Any]:
    _reject_query(request)
    if len(request.headers.getlist("Idempotency-Key")) != 1:
        raise Failure("VALIDATION_ERROR")
    principal = _optional_principal(request)
    capability = getattr(request.app.state, "ai_conversation_capability", None)
    if principal is None:
        if capability is None:
            raise Failure("FEATURE_DISABLED")
        remote = request.client.host if request.client else "unknown"
        actor = "anonymous:" + hashlib.sha256(remote.encode()).hexdigest()[:48]
        party = None
    else:
        actor, party = principal
    ttl = getattr(request.app.state, "ai_conversation_ttl_seconds", None)
    locale = payload.locale or getattr(request.app.state, "ai_default_locale", None)
    markets: frozenset[str] = getattr(request.app.state, "ai_allowed_markets", frozenset())
    if ttl is None or not 60 <= ttl <= 86400 or not locale or not LOCALE_RE.fullmatch(locale) or payload.market not in markets:
        raise Failure("FEATURE_DISABLED")
    pool = _pool(request)
    data = payload.model_dump(mode="json") | {"resolvedLocale": locale}
    conversation_id = uuid7()
    expires = datetime.now(UTC) + timedelta(seconds=ttl)
    result = {"id": str(conversation_id), "expiresAt": expires.isoformat().replace("+00:00", "Z"), "anonymousToken": None}
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        if party is None:
            await connection.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", (actor,))
            rate = await (await connection.execute("SELECT count(*) FROM ai.idempotency_record WHERE principal_key=%s AND operation='AI-CONVERSATION-CREATE' AND created_at>=now()-interval '1 hour'", (actor,))).fetchone()
            if rate and rate[0] >= 10:
                raise Failure("RATE_LIMITED")
        idem, replay = await _lock_idempotency(connection, actor, "AI-CONVERSATION-CREATE", idempotency_key, data)
        if replay:
            response.status_code = int(replay[0])
            body = cast(dict[str, Any], replay[1])
            if party is None:
                expiry = datetime.fromisoformat(str(body["expiresAt"]))
                body["anonymousToken"] = cast(Any, capability).issue(uuid.UUID(str(body["id"])), int(expiry.timestamp()))
            _metric(request, "conversation.create", "replayed")
            return body
        anonymous_token = cast(Any, capability).issue(conversation_id, int(expires.timestamp())) if party is None else None
        token_hash = hashlib.sha256(anonymous_token.encode()).hexdigest() if anonymous_token else None
        await connection.execute(
            "INSERT INTO ai.conversation(id,created_by,updated_by,party_id,anonymous_session_hash,market_code,locale,state,expires_at) VALUES (%s,%s,%s,%s,%s,%s,%s,'ACTIVE',%s)",
            (conversation_id, actor, actor, party, token_hash, payload.market, locale, expires),
        )
        await _save_idempotency(connection, actor, "AI-CONVERSATION-CREATE", idempotency_key, idem, 201, result, conversation_id)
        await _audit(connection, actor, "AI_CONVERSATION_CREATE", "AI_CONVERSATION", conversation_id, {"state": "ACTIVE", "market": payload.market, "version": 0})
    response.headers["ETag"] = '"0"'
    result["anonymousToken"] = anonymous_token
    _metric(request, "conversation.create", "created")
    return result


async def _replay(request: Request, conversation_id: uuid.UUID, key: str, message_hash: str) -> tuple[uuid.UUID, dict[str, Any], str] | None:
    cipher = _cipher(request)
    async with _pool(request).connection() as connection:
        row = await (await connection.execute(
            "SELECT id,status,failure_code FROM ai.ai_run WHERE conversation_id=%s AND request_key=%s",
            (conversation_id, key),
        )).fetchone()
        if not row:
            return None
        run_id, status, failure = row
        checkpoints = await (await connection.execute(
            "SELECT checkpoint_number,state_ciphertext FROM ai.ai_checkpoint WHERE run_id=%s ORDER BY checkpoint_number",
            (run_id,),
        )).fetchall()
        if not checkpoints:
            raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        initial = cipher.decrypt(conversation_id, run_id, bytes(checkpoints[0][1]))
        if initial.get("messageHash") != message_hash:
            raise Failure("IDEMPOTENCY_HASH_MISMATCH")
        if status in {"SUCCEEDED", "ESCALATED"} and checkpoints[-1][0] > 0:
            final = cipher.decrypt(conversation_id, run_id, bytes(checkpoints[-1][1]))
            if isinstance(final.get("answer"), dict):
                return run_id, cast(dict[str, Any], final["answer"]), "replay"
        if failure == "PROVIDER_UNAVAILABLE":
            return run_id, initial, "retry"
        provider = getattr(request.app.state, "assistant_provider", None)
        reply = provider.reconcile(str(run_id)) if provider else None
        if reply is None:
            raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        return run_id, {"initial": initial, "reply": reply}, "reconcile"


@router.post("/api/v1/ai/conversations/{id}/messages")
async def send_message(
    id: uuid.UUID,
    payload: SendMessage,
    request: Request,
    response: Response,
    if_match: str | None = Header(default=None, alias="If-Match"),
) -> StreamingResponse:
    _reject_query(request)
    expected_version = _version(if_match)
    conversation_id = uuid.UUID(str(id))
    cipher = _cipher(request)
    provider = getattr(request.app.state, "assistant_provider", None)
    if provider is None:
        raise Failure("FEATURE_DISABLED")
    message_hash = hashlib.sha256(payload.message.encode()).hexdigest()
    async with _pool(request).connection() as connection:
        actor, party, anonymous_hash, context = await _owner(request, connection, conversation_id)
    existing = await _replay(request, conversation_id, payload.clientRequestId, message_hash)
    if existing and existing[2] == "replay":
        _metric(request, "message", "replayed")
        return await _response(existing[0], existing[1])
    if existing and existing[2] == "reconcile":
        run_id, data, _ = existing
        initial = data["initial"]
        reply = data["reply"]
        facts = tuple((str(x[0]), str(x[1])) for x in initial.get("toolFacts", []))
        answer = _answer(reply.text, cast(list[dict[str, Any]], initial.get("sources", [])), run_id, escalated=bool(initial.get("injection")))
        if facts and not answer["sources"]:
            answer["status"] = "ANSWERED"
            answer["answer"] = reply.text[:2000]
        async with _pool(request).connection() as connection, connection.transaction():
            await finish_run(connection, run_id=run_id, input_tokens=reply.input_tokens, output_tokens=reply.output_tokens, status="SUCCEEDED" if answer["status"] == "ANSWERED" else "ESCALATED")
            initial_row = await (await connection.execute("SELECT id,expires_at FROM ai.ai_checkpoint WHERE run_id=%s AND checkpoint_number=0", (run_id,))).fetchone()
            await _checkpoint(connection, cipher, actor, conversation_id, run_id, 1, initial_row[0], initial_row[1], {"answer": answer})
        return await _response(run_id, answer)
    retry_state = existing[1] if existing and existing[2] == "retry" else None
    if context["version"] != expected_version:
        raise Failure("VERSION_MISMATCH")

    injection = bool(retry_state.get("injection")) if retry_state else bool(INJECTION_RE.search(payload.message))
    market, locale = str(context["market"]), str(context["locale"])
    evidence: list[dict[str, Any]] = cast(list[dict[str, Any]], retry_state.get("sources", [])) if retry_state else []
    tool_facts: tuple[tuple[str, str], ...] = tuple((str(x[0]), str(x[1])) for x in retry_state.get("toolFacts", [])) if retry_state else ()
    tool_name: str | None = None
    tool_scope: str | None = None
    tool_hash: str | None = None
    tool_result = None
    private_order_request = not injection and bool(re.search(r"(?i)\b(order|shipment|tracking)\b", payload.message))
    if retry_state is None and private_order_request:
        ids = UUID_RE.findall(payload.message)
        tools = getattr(request.app.state, "assistant_owner_tools", None)
        if party and len(ids) == 1 and tools is not None:
            tool_name, tool_scope = "get_order_status", "ORDER_STATUS_OWNER"
            args = {"orderId": ids[0].lower()}
            tool_hash = hashlib.sha256(json.dumps(args, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
            tool_result = tools.invoke(tool_name, args, party_id=str(party), market=market)
            if tool_result:
                value = tool_result.value
                statement = "Your order status is " + str(value["status"]) + "."
                if value.get("tracking"):
                    statement += " Tracking reference: " + str(value["tracking"]) + "."
                tool_facts = ((tool_name, statement),)
    if retry_state is None and not injection and not private_order_request and not tool_facts:
        async with _pool(request).connection() as connection:
            evidence = await _sources(request, connection, payload.message, market, locale)

    safe_fallback = injection or (private_order_request and not tool_facts) or (not evidence and not tool_facts)
    async with _pool(request).connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        current = await (await connection.execute(
            "SELECT version,state,expires_at FROM ai.conversation WHERE id=%s FOR UPDATE",
            (conversation_id,),
        )).fetchone()
        if not current or current[1] != "ACTIVE" or current[2] <= datetime.now(UTC):
            raise Failure("NOT_FOUND")
        raced = await (await connection.execute(
            "SELECT id,status FROM ai.ai_run WHERE conversation_id=%s AND request_key=%s FOR UPDATE",
            (conversation_id, payload.clientRequestId),
        )).fetchone()
        if raced and raced[1] != "FAILED":
            first = await (await connection.execute(
                "SELECT state_ciphertext FROM ai.ai_checkpoint WHERE run_id=%s AND checkpoint_number=0",
                (raced[0],),
            )).fetchone()
            if not first:
                raise Failure("PROVIDER_OUTCOME_UNKNOWN")
            first_state = cipher.decrypt(conversation_id, raced[0], bytes(first[0]))
            if first_state.get("messageHash") != message_hash:
                raise Failure("IDEMPOTENCY_HASH_MISMATCH")
            if raced[1] == "RUNNING":
                raise Failure("IDEMPOTENCY_IN_PROGRESS")
            final = await (await connection.execute(
                "SELECT state_ciphertext FROM ai.ai_checkpoint WHERE run_id=%s ORDER BY checkpoint_number DESC LIMIT 1",
                (raced[0],),
            )).fetchone()
            if not final:
                raise Failure("PROVIDER_OUTCOME_UNKNOWN")
            final_state = cipher.decrypt(conversation_id, raced[0], bytes(final[0]))
            if not isinstance(final_state.get("answer"), dict):
                raise Failure("PROVIDER_OUTCOME_UNKNOWN")
            _metric(request, "message", "replayed")
            return await _response(raced[0], cast(dict[str, Any], final_state["answer"]))
        if raced and raced[1] == "FAILED":
            failure = await (await connection.execute("SELECT failure_code FROM ai.ai_run WHERE id=%s", (raced[0],))).fetchone()
            if not failure or failure[0] != "PROVIDER_UNAVAILABLE":
                raise Failure("INVALID_TRANSITION")
        if current[0] != expected_version:
            raise Failure("VERSION_MISMATCH")
        rate_lock = "party:" + str(party) if party is not None else "anonymous:" + str(anonymous_hash)
        await connection.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", (rate_lock,))
        rate = await (await connection.execute(
            "SELECT count(*) FROM ai.ai_run r JOIN ai.conversation c ON c.id=r.conversation_id WHERE ((%s::uuid IS NOT NULL AND c.party_id=%s::uuid) OR (%s::text IS NOT NULL AND c.anonymous_session_hash=%s)) AND r.created_at>=now()-interval '1 hour'",
            (party, party, anonymous_hash, anonymous_hash),
        )).fetchone()
        if rate and rate[0] >= (30 if party is not None else 10):
            raise Failure("RATE_LIMITED")
        policy = await (await connection.execute(
            "SELECT p.id,p.provider,p.chat_model_id,p.parameters,pr.code,pr.revision,pr.body FROM ai.model_policy p JOIN ai.prompt_revision pr ON pr.id=p.prompt_revision_id WHERE p.approved=true AND p.code='nigson-assistant' ORDER BY p.revision DESC LIMIT 1",
        )).fetchone()
        if not policy:
            raise Failure("FEATURE_DISABLED")
        policy_id, provider_name, model_id, parameters, prompt_code, prompt_revision, prompt_template = policy
        if provider_name != "BEDROCK" or not isinstance(parameters, dict) or type(parameters.get("rateRevision")) is not int:
            raise Failure("GATEWAY_RULE_VIOLATION")
        await connection.execute("UPDATE ai.conversation SET version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND version=%s", (actor, conversation_id, expected_version))
        if safe_fallback:
            run_id = uuid7()
            await connection.execute(
                "INSERT INTO ai.ai_run(id,created_by,updated_by,conversation_id,request_key,workflow_version,prompt_version,provider,model_id,status,failure_code,source_revision_ids,step_count,rate_revision) VALUES (%s,%s,%s,%s,%s,'assistant-1',%s,%s,%s,'ESCALATED',%s,%s,4,%s)",
                (run_id, actor, actor, conversation_id, payload.clientRequestId, f"{prompt_code}:{prompt_revision}", provider_name, model_id, "ASSISTANT_RULE_VIOLATION" if injection else "NO_GROUNDED_EVIDENCE", Jsonb([]), parameters["rateRevision"]),
            )
            expires = min(current[2], datetime.now(UTC) + timedelta(seconds=int(getattr(request.app.state, "ai_checkpoint_ttl_seconds", 0))))
            if expires <= datetime.now(UTC):
                raise Failure("FEATURE_DISABLED")
            fallback = "I can help with approved company information, but I cannot follow instructions to bypass safeguards or disclose internal instructions." if injection else "I could not verify this from approved information. Please use the site search or contact the team for help."
            answer = _answer(fallback, [], run_id, escalated=True)
            cp = await _checkpoint(connection, cipher, actor, conversation_id, run_id, 0, None, expires, {"messageHash": message_hash, "answer": answer, "sources": [], "injection": injection})
            await _checkpoint(connection, cipher, actor, conversation_id, run_id, 1, cp, expires, {"answer": answer})
            if tool_name and tool_scope and tool_hash:
                await connection.execute(
                    "INSERT INTO ai.tool_invocation(id,created_by,run_id,tool_name,invocation_number,argument_hash,authorization_scope,outcome,duration_ms) VALUES (%s,%s,%s,%s,1,%s,%s,'DENIED',0)",
                    (uuid7(), actor, run_id, tool_name, tool_hash, tool_scope),
                )
            await _audit(connection, actor, "AI_RUN_ESCALATE", "AI_RUN", run_id, {"state": "ESCALATED", "reason": "ASSISTANT_RULE_VIOLATION" if injection else "NO_GROUNDED_EVIDENCE"})
            response.headers["ETag"] = f'"{expected_version + 1}"'
            _metric(request, "message", "escalated")
            return await _response(run_id, answer)
        caps: frozenset[tuple[str, str]] = getattr(request.app.state, "ai_model_capabilities", frozenset())
        if (provider_name, model_id) not in caps and not getattr(request.app.state, "assistant_test_model_enabled", False):
            raise Failure("FEATURE_DISABLED")
        if parameters.get("maximumSteps", 0) < 4 or (tool_facts and parameters.get("maximumTools", 0) < 1):
            raise Failure("GATEWAY_RULE_VIOLATION")
        prompt_version = f"{prompt_code}:{prompt_revision}"
        input_text = "\n".join((str(prompt_template), str(model_id), payload.message, *(item["text"] for item in evidence), *(fact for _, fact in tool_facts)))
        reservation = await reserve_run(connection, actor=actor, conversation_id=conversation_id, request_key=payload.clientRequestId, workflow_version="assistant-1", prompt_version=prompt_version, policy_id=policy_id, input_text=input_text)
        run_id = reservation.run_id
        revision_ids = [{"id": x["id"], "revision": x["revision"]} for x in evidence]
        await connection.execute("UPDATE ai.ai_run SET source_revision_ids=%s,step_count=4 WHERE id=%s", (Jsonb(revision_ids), run_id))
        expires = min(current[2], datetime.now(UTC) + timedelta(seconds=int(getattr(request.app.state, "ai_checkpoint_ttl_seconds", 0))))
        if expires <= datetime.now(UTC):
            raise Failure("FEATURE_DISABLED")
        state = {"messageHash": message_hash, "message": payload.message, "sources": evidence, "toolFacts": [list(x) for x in tool_facts], "injection": injection, "checkpointExpiresAt": expires.isoformat()}
        if retry_state is not None:
            initial = await (await connection.execute("SELECT id,expires_at FROM ai.ai_checkpoint WHERE run_id=%s AND checkpoint_number=0", (run_id,))).fetchone()
            if not initial or initial[1] <= datetime.now(UTC):
                raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        else:
            await _checkpoint(connection, cipher, actor, conversation_id, run_id, 0, None, expires, state)
        if tool_name and tool_scope and tool_hash:
            await connection.execute(
                "INSERT INTO ai.tool_invocation(id,created_by,run_id,tool_name,invocation_number,argument_hash,authorization_scope,outcome,duration_ms) VALUES (%s,%s,%s,%s,1,%s,%s,%s,0)",
                (uuid7(), actor, run_id, tool_name, tool_result.argument_hash if tool_result else tool_hash, tool_scope, "SUCCEEDED" if tool_result else "DENIED"),
            )
        await _audit(connection, actor, "AI_RUN_START", "AI_RUN", run_id, {"state": "RUNNING", "policyRevision": int(policy[5]), "promptRevision": prompt_version[:80], "sourceCount": len(evidence), "toolCount": len(tool_facts)})

    try:
        reply = provider.generate(
            str(run_id), str(model_id), str(prompt_template), payload.message,
            tuple((x["id"], x["text"]) for x in evidence), tool_facts=tool_facts,
            max_input_tokens=int(parameters["maximumInputTokens"]),
            max_output_tokens=min(1500, int(parameters["maxOutputTokens"])),
            deadline_seconds=min(20, int(parameters["requestDeadlineSeconds"])),
        )
    except ConnectionError:
        async with _pool(request).connection() as connection:
            await finish_run(connection, run_id=run_id, input_tokens=0, output_tokens=0, status="FAILED", failure_code="PROVIDER_UNAVAILABLE")
        raise Failure("PROVIDER_UNAVAILABLE") from None
    except TimeoutError:
        _metric(request, "message", "unknown")
        raise Failure("PROVIDER_OUTCOME_UNKNOWN") from None
    except ValueError:
        async with _pool(request).connection() as connection:
            await finish_run(connection, run_id=run_id, input_tokens=0, output_tokens=0, status="FAILED", failure_code="PROVIDER_UNAVAILABLE")
        raise Failure("PROVIDER_UNAVAILABLE") from None

    verified: list[dict[str, Any]] = []
    knowledge = getattr(request.app.state, "ai_knowledge_mock", None)
    if knowledge is not None:
        for item in evidence:
            source = knowledge.fetch(item["sourceService"], item["sourceResourceId"])
            if source and knowledge.is_current(source):
                verified.append(item)
    facts = tool_facts
    status = "ANSWERED" if verified or facts else "ESCALATED"
    text = reply.text if status == "ANSWERED" else "The source is no longer current. Please search the site or contact the team for verified information."
    answer = _answer(text, verified, run_id, escalated=status != "ANSWERED")
    async with _pool(request).connection() as connection, connection.transaction():
        await finish_run(connection, run_id=run_id, input_tokens=reply.input_tokens, output_tokens=reply.output_tokens, status="SUCCEEDED" if status == "ANSWERED" else "ESCALATED")
        await connection.execute("UPDATE ai.ai_run SET step_count=4 WHERE id=%s", (run_id,))
        initial = await (await connection.execute("SELECT id,expires_at FROM ai.ai_checkpoint WHERE run_id=%s AND checkpoint_number=0", (run_id,))).fetchone()
        await _checkpoint(connection, cipher, actor, conversation_id, run_id, 1, initial[0], initial[1], {"answer": answer})
        await _audit(connection, actor, "AI_RUN_COMPLETE", "AI_RUN", run_id, {"state": status, "sourceCount": len(verified), "inputTokens": reply.input_tokens, "outputTokens": reply.output_tokens})
    response.headers["ETag"] = f'"{expected_version + 1}"'
    _metric(request, "message", "completed")
    return await _response(run_id, answer)


@router.post("/api/v1/ai/runs/{id}/feedback")
async def submit_feedback(
    id: uuid.UUID,
    payload: SubmitFeedback,
    request: Request,
    idempotency_key: str = Header(alias="Idempotency-Key", min_length=1, max_length=100),
    if_match: str | None = Header(default=None, alias="If-Match"),
) -> dict[str, bool]:
    _reject_query(request)
    run_id = id
    if len(request.headers.getlist("Idempotency-Key")) != 1:
        raise Failure("VALIDATION_ERROR")
    expected = _version(if_match)
    data = payload.model_dump(mode="json") | {"runId": str(run_id)}
    async with _pool(request).connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        row = await (await connection.execute(
            "SELECT r.conversation_id,r.version,r.status,c.party_id,c.anonymous_session_hash FROM ai.ai_run r JOIN ai.conversation c ON c.id=r.conversation_id WHERE r.id=%s FOR UPDATE OF r",
            (run_id,),
        )).fetchone()
        if not row:
            raise Failure("NOT_FOUND")
        conversation_id, version, status, owner_party, owner_hash = row
        if owner_party is not None:
            actor, caller_party = _principal(request)
            if caller_party != owner_party:
                raise Failure("NOT_FOUND")
            anonymous_hash = None
        else:
            anonymous_hash = _owner_token(request, conversation_id)
            if anonymous_hash != owner_hash:
                raise Failure("NOT_FOUND")
            actor = "anonymous:" + anonymous_hash[:48]
        idem, replay = await _lock_idempotency(connection, actor, "AI-FEEDBACK-CREATE", idempotency_key, data)
        if replay:
            _metric(request, "feedback", "replayed")
            return cast(dict[str, bool], replay[1])
        if status == "RUNNING":
            raise Failure("INVALID_TRANSITION")
        if version != expected:
            raise Failure("VERSION_MISMATCH")
        comment = PII_RE.sub("[redacted]", payload.comment or "")[:1000]
        feedback_id = uuid7()
        await connection.execute(
            "INSERT INTO ai.ai_feedback(id,created_by,updated_by,run_id,party_id,anonymous_session_hash,rating,category,comment) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (feedback_id, actor, actor, run_id, owner_party, anonymous_hash, payload.rating, payload.category, comment or None),
        )
        await connection.execute("UPDATE ai.ai_run SET version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND version=%s", (actor, run_id, expected))
        await _save_idempotency(connection, actor, "AI-FEEDBACK-CREATE", idempotency_key, idem, 200, {"received": True}, feedback_id)
        await _audit(connection, actor, "AI_FEEDBACK_CREATE", "AI_FEEDBACK", feedback_id, {"state": "RECEIVED", "rating": payload.rating, "category": payload.category, "comment": "REDACTED" if comment else None})
    _metric(request, "feedback", "received")
    return {"received": True}
