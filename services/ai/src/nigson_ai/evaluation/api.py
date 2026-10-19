"""Evaluation HTTP contracts. The only evaluator target here is the local mock."""

from __future__ import annotations

import hashlib
import json
import re
import uuid
from datetime import UTC, datetime, timedelta
from typing import Annotated, Any, cast

from fastapi import APIRouter, Header, HTTPException, Query, Request, Response
from psycopg.types.json import Jsonb
from pydantic import (
    Field,
    StrictBool,
    StrictInt,
    StrictStr,
    field_validator,
    model_validator,
)

from nigson_ai.evaluation.trust import verify_admin_actor
from nigson_ai.platform.events import request_hash, uuid7
from nigson_ai.platform.problem import Failure, StrictModel
from nigson_ai.platform.store import Conflict
from nigson_ai.platform.telemetry import correlation

router = APIRouter()
UUID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
ID_RE = re.compile(r"^[A-Za-z0-9._:-]{1,80}$")
REV_RE = re.compile(r"^[A-Za-z0-9._:-]{1,80}$")


class Case(StrictModel):
    id: StrictStr = Field(min_length=1, max_length=80)
    input: StrictStr = Field(min_length=1, max_length=2000)
    market: StrictStr = Field(min_length=1, max_length=32)
    locale: StrictStr = Field(
        min_length=2, max_length=35, pattern=r"^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$"
    )
    expectedIntent: StrictStr = Field(min_length=1, max_length=80)
    requiredSourceIds: list[StrictStr] = Field(max_length=100)
    mustEscalate: StrictBool
    forbiddenActions: list[Annotated[StrictStr, Field(max_length=80)]] = Field(
        max_length=100
    )

    @field_validator("id", "market", "expectedIntent")
    @classmethod
    def safe_identifier(cls, value: str) -> str:
        if not ID_RE.fullmatch(value):
            raise ValueError("invalid identifier")
        return value

    @field_validator("requiredSourceIds")
    @classmethod
    def source_ids_are_uuids(cls, values: list[str]) -> list[str]:
        if any(
            not UUID_PATTERN.fullmatch(value) or uuid.UUID(value).int == 0
            for value in values
        ):
            raise ValueError("invalid source identifier")
        return values


class Thresholds(StrictModel):
    groundednessMinimum: float = Field(ge=0, le=1)
    scopeLeaksMaximum: StrictInt = Field(ge=0, le=0)
    injectionMutationMaximum: StrictInt = Field(ge=0, le=0)
    latencyP95MaximumMs: StrictInt = Field(ge=1, le=20000)


class CreateSuite(StrictModel):
    code: StrictStr = Field(min_length=1, max_length=80)
    cases: list[Case] = Field(min_length=200, max_length=500)
    thresholds: Thresholds

    @field_validator("code")
    @classmethod
    def safe_code(cls, value: str) -> str:
        if not ID_RE.fullmatch(value):
            raise ValueError("invalid code")
        return value

    @model_validator(mode="after")
    def unique_case_ids(self) -> CreateSuite:
        if len({c.id for c in self.cases}) != len(self.cases):
            raise ValueError("duplicate case")
        # Evaluation suites must be synthetic or already approved/sanitized. The
        # DTO has no customer identifier field; reject common direct identifiers.
        if any(
            re.search(
                r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|\+?\d[\d ()-]{8,}\d",
                c.input,
                re.IGNORECASE,
            )
            for c in self.cases
        ):
            raise ValueError("private identifier in evaluation case")
        return self


class RunEvaluation(StrictModel):
    suiteId: StrictStr = Field(pattern=UUID_PATTERN.pattern)
    modelPolicyRevision: StrictStr = Field(min_length=1, max_length=80)
    promptRevision: StrictStr = Field(min_length=1, max_length=40)
    retrievalRevision: StrictStr = Field(min_length=1, max_length=40)

    @field_validator("modelPolicyRevision", "promptRevision", "retrievalRevision")
    @classmethod
    def revision(cls, value: str) -> str:
        if not REV_RE.fullmatch(value):
            raise ValueError("invalid revision")
        return value

    @field_validator("suiteId")
    @classmethod
    def suite_id_is_uuid(cls, value: str) -> str:
        if not UUID_PATTERN.fullmatch(value) or uuid.UUID(value).int == 0:
            raise ValueError("invalid suite id")
        return value


def _principal(
    request: Request, required_role: str, workload: bool = False
) -> tuple[str, dict[str, Any]]:
    auth_headers = [
        value.decode("latin1")
        for name, value in request.scope.get("headers", [])
        if name.lower() == b"authorization"
    ]
    if len(auth_headers) != 1:
        raise Failure("AUTH_REQUIRED")
    authorization = auth_headers[0]
    if not authorization.startswith("Bearer ") or len(authorization) > 16400:
        raise Failure("AUTH_REQUIRED")
    authenticator = getattr(request.app.state, "ai_authenticator", None)
    if authenticator is None:
        raise Failure("AUTH_REQUIRED")
    claims = authenticator.decode(authorization[7:])
    client = claims.get("azp")
    if workload:
        scopes = set(str(claims.get("scope", "")).split())
        if client != "nigson-commerce" or "ai.manage_WITH_SIGNED_ADMIN" not in scopes:
            raise Failure("ACCESS_DENIED")
        allowed_peers = getattr(request.app.state, "ai_private_peer_cidrs", ())
        remote = request.client.host if request.client else None
        import ipaddress

        try:
            address = ipaddress.ip_address(remote or "")
        except ValueError:
            raise Failure("ACCESS_DENIED")
        if not allowed_peers or not any(
            address in network for network in allowed_peers
        ):
            raise Failure("ACCESS_DENIED")
        return "", claims
    realm = claims.get("realm_access", {})
    roles = realm.get("roles", []) if isinstance(realm, dict) else None
    if not isinstance(roles, list) or required_role not in roles:
        raise Failure("ACCESS_DENIED")
    if claims.get("organization_id") is not None:
        # These contracts are global AI administration/report queries and have no
        # organization filter. Do not silently broaden a scoped actor's access.
        raise Failure("ACCESS_DENIED")
    return str(claims.get("sub", "")), claims


def _pool(request: Request) -> Any:
    pool = getattr(request.app.state, "pool", None)
    if pool is None:
        raise HTTPException(503)
    return pool


def _reject_query(request: Request, allowed: set[str]) -> None:
    pairs = list(request.query_params.multi_items())
    names = [key for key, _ in pairs]
    if set(names) != allowed or len(names) != len(allowed):
        raise Failure("VALIDATION_ERROR")


def _single_header(request: Request, name: str) -> None:
    if len(request.headers.getlist(name)) != 1:
        raise Failure("VALIDATION_ERROR")


def _safe_digest(value: Any) -> str:
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":"), default=str).encode()
    ).hexdigest()


async def _audit(
    connection: Any,
    actor: str,
    action: str,
    resource: uuid.UUID,
    values: dict[str, Any],
) -> None:
    correlation_id = correlation.get() or uuid7()
    await connection.execute(
        "INSERT INTO ai.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,new_value) VALUES (%s,%s,%s,'USER',%s,'AI_EVALUATION',%s,%s,%s,%s)",
        (
            uuid7(),
            actor,
            actor,
            action,
            resource,
            datetime.now(UTC),
            correlation_id,
            Jsonb(values),
        ),
    )


@router.post("/api/v1/staff/ai/evaluation-suites", status_code=201)
async def create_suite(
    payload: CreateSuite,
    request: Request,
    response: Response,
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request, set())
    _single_header(request, "Idempotency-Key")
    actor, _ = _principal(request, "ADMIN")
    if not actor:
        raise Failure("AUTH_REQUIRED")
    pool = _pool(request)
    data = payload.model_dump(mode="json")
    digest = request_hash(data)
    now = datetime.now(UTC)
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            ("evaluation-code:" + payload.code,),
        )
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"evaluation-suite:{actor}:{idempotency_key}",),
        )
        saved = await (
            await connection.execute(
                "SELECT request_hash,response_status,response_body FROM ai.idempotency_record WHERE principal_key=%s AND operation='AI-EVALU-SUITE' AND request_key=%s FOR UPDATE",
                (actor, idempotency_key),
            )
        ).fetchone()
        if saved:
            if saved[0] != digest:
                raise Conflict("IDEMPOTENCY_HASH_MISMATCH")
            response.status_code = saved[1]
            return cast(dict[str, Any], saved[2])
        revision = await (
            await connection.execute(
                "SELECT coalesce(max(revision),0)+1 FROM ai.evaluation_suite WHERE code=%s",
                (payload.code,),
            )
        ).fetchone()
        identifier = uuid7()
        canonical_cases = [case.model_dump(mode="json") for case in payload.cases]
        result = {"id": str(identifier), "revision": revision[0]}
        await connection.execute(
            "INSERT INTO ai.evaluation_suite(id,created_by,code,revision,dataset_checksum,cases,scoring_policy) VALUES (%s,%s,%s,%s,%s,%s,%s)",
            (
                identifier,
                actor,
                payload.code,
                revision[0],
                _safe_digest(canonical_cases),
                Jsonb(canonical_cases),
                Jsonb(payload.thresholds.model_dump(mode="json")),
            ),
        )
        await connection.execute(
            "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,resource_id,expires_at) VALUES (%s,%s,%s,%s,'AI-EVALU-SUITE',%s,%s,'SUCCEEDED',201,%s,%s,%s)",
            (
                uuid7(),
                actor,
                actor,
                actor,
                idempotency_key,
                digest,
                Jsonb(result),
                identifier,
                now + timedelta(days=1),
            ),
        )
        await _audit(
            connection,
            actor,
            "AI_EVALUATION_SUITE_CREATE",
            identifier,
            {"revision": revision[0], "state": "CREATED"},
        )
    return result


@router.post("/internal/v1/evaluations", status_code=202)
async def run_evaluation(
    payload: RunEvaluation,
    request: Request,
    if_match: str = Header(alias="If-Match", min_length=3, max_length=21),
    idempotency_key: str = Header(
        alias="Idempotency-Key", min_length=1, max_length=100
    ),
) -> dict[str, Any]:
    _reject_query(request, set())
    _single_header(request, "Idempotency-Key")
    _single_header(request, "If-Match")
    _, _ = _principal(request, "ADMIN", workload=True)
    assertion = verify_admin_actor(request, "ai.manage_WITH_SIGNED_ADMIN")
    actor = assertion["identity_subject"]
    pool = _pool(request)
    mock = request.app.state.evaluation_model
    data = payload.model_dump(mode="json")
    digest = request_hash(data)
    now = datetime.now(UTC)
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='10s'")
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"evaluation-run:{actor}:{idempotency_key}",),
        )
        saved = await (
            await connection.execute(
                "SELECT request_hash,response_body FROM ai.idempotency_record WHERE principal_key=%s AND operation='AI-EVALU-RUN' AND request_key=%s FOR UPDATE",
                (actor, idempotency_key),
            )
        ).fetchone()
        if saved:
            if saved[0] != digest:
                raise Conflict("IDEMPOTENCY_HASH_MISMATCH")
            return cast(dict[str, Any], saved[1])
        suite = await (
            await connection.execute(
                "SELECT code,revision,cases,scoring_policy,created_by FROM ai.evaluation_suite WHERE id=%s",
                (uuid.UUID(payload.suiteId),),
            )
        ).fetchone()
        if not suite:
            raise Failure("NOT_FOUND")
        _code, revision, cases, thresholds, suite_owner = suite
        if if_match != f'"{revision}"':
            raise Failure("VERSION_MISMATCH")
        if suite_owner != actor:
            raise Failure("ACCESS_DENIED")
        if not isinstance(cases, list) or not 200 <= len(cases) <= mock.max_cases:
            raise Failure("BUSINESS_RULE_VIOLATION")
        # Candidate policy must exist as an immutable revision. Only an explicitly
        # approved policy can pass the activation gate; this API never activates it.
        policy_code, separator, policy_revision = (
            payload.modelPolicyRevision.rpartition(":")
        )
        policy = None
        if (
            separator
            and policy_code
            and policy_revision.isdecimal()
            and int(policy_revision) > 0
        ):
            policy = await (
                await connection.execute(
                    "SELECT p.approved,pr.code,pr.revision FROM ai.model_policy p JOIN ai.prompt_revision pr ON pr.id=p.prompt_revision_id WHERE p.code=%s AND p.revision=%s ORDER BY p.revision DESC LIMIT 1",
                    (policy_code, int(policy_revision)),
                )
            ).fetchone()
        outputs = mock.run(cases, payload.modelPolicyRevision)
        count = len(outputs)
        grounded = sum(int(item["grounded"]) for item in outputs) / count
        leaks = sum(int(item["scopeLeak"]) for item in outputs)
        mutations = sum(int(item["injectionMutation"]) for item in outputs)
        latencies = sorted(item["latencyMs"] for item in outputs)
        p95 = latencies[min(count - 1, (count * 95 + 99) // 100 - 1)]
        policy_matches_prompt = bool(
            policy and f"{policy[1]}:{policy[2]}" == payload.promptRevision
        )
        passed = (
            bool(policy and policy[0] and policy_matches_prompt)
            and grounded >= float(thresholds["groundednessMinimum"])
            and leaks <= int(thresholds["scopeLeaksMaximum"])
            and mutations <= int(thresholds["injectionMutationMaximum"])
            and p95 <= int(thresholds["latencyP95MaximumMs"])
        )
        evaluation_id = uuid7()
        scores = {
            "caseCount": count,
            "groundedness": grounded,
            "scopeLeaks": leaks,
            "injectionMutations": mutations,
            "latencyP95Ms": p95,
            "policyMatched": bool(policy and policy[0] and policy_matches_prompt),
            "activationEligible": False,
            "localMockOnly": True,
            "insightDraft": {
                "summary": "Evaluation gates passed."
                if passed
                else "Evaluation gates did not pass.",
                "failedGates": [
                    name
                    for name, failed in (
                        (
                            "policy",
                            not bool(policy and policy[0] and policy_matches_prompt),
                        ),
                        (
                            "groundedness",
                            grounded < float(thresholds["groundednessMinimum"]),
                        ),
                        ("scopeLeaks", leaks > int(thresholds["scopeLeaksMaximum"])),
                        (
                            "injectionMutation",
                            mutations > int(thresholds["injectionMutationMaximum"]),
                        ),
                        ("latencyP95", p95 > int(thresholds["latencyP95MaximumMs"])),
                    )
                    if failed
                ],
                "activationBlocked": "LOCAL_MODEL_MOCK",
            },
        }
        await connection.execute(
            "INSERT INTO ai.evaluation_result(id,created_by,suite_id,model_policy_revision,prompt_revision,retrieval_revision,scores,passed,report_object_key) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (
                evaluation_id,
                actor,
                uuid.UUID(payload.suiteId),
                payload.modelPolicyRevision,
                payload.promptRevision,
                payload.retrievalRevision,
                Jsonb(scores),
                passed,
                f"ai-evaluations/{evaluation_id}.json",
            ),
        )
        result = {"evaluationId": str(evaluation_id), "state": "QUEUED"}
        await connection.execute(
            "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,resource_id,expires_at) VALUES (%s,%s,%s,%s,'AI-EVALU-RUN',%s,%s,'SUCCEEDED',202,%s,%s,%s)",
            (
                uuid7(),
                actor,
                actor,
                actor,
                idempotency_key,
                digest,
                Jsonb(result),
                evaluation_id,
                now + timedelta(days=1),
            ),
        )
        await _audit(
            connection,
            actor,
            "AI_EVALUATION_RUN",
            evaluation_id,
            {"state": "COMPLETED", "revision": revision},
        )
    return result


@router.get("/api/v1/staff/reports/ai")
async def ai_report(
    request: Request,
    from_: str = Query(alias="from", min_length=20, max_length=40),
    to: str = Query(min_length=20, max_length=40),
) -> dict[str, Any]:
    _reject_query(request, {"from", "to"})
    actor, _ = _principal(request, "ANALYST")
    if not actor:
        raise Failure("AUTH_REQUIRED")
    try:
        start, end = datetime.fromisoformat(from_), datetime.fromisoformat(to)
    except ValueError:
        raise Failure("VALIDATION_ERROR") from None
    if (
        start.tzinfo is None
        or end.tzinfo is None
        or end <= start
        or end - start > timedelta(days=90)
    ):
        raise Failure("VALIDATION_ERROR")
    async with _pool(request).connection() as connection:
        row = await (
            await connection.execute(
                "SELECT count(*)::int, count(*) FILTER (WHERE status='ESCALATED')::int, coalesce(sum(input_tokens+output_tokens),0)::int, coalesce(sum(cost_estimate),0)::text, coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM (updated_at-created_at))*1000),0)::int, coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM (updated_at-created_at))*1000),0)::int FROM ai.ai_run WHERE created_at >= %s AND created_at < %s",
                (start.astimezone(UTC), end.astimezone(UTC)),
            )
        ).fetchone()
    # This schema does not persist intent or empty-search dimensions; return empty
    # aggregates instead of deriving those labels from prompts or private results.
    return {
        "requests": row[0],
        "escalations": row[1],
        "topIntents": [],
        "emptySearches": 0,
        "tokenUsage": row[2],
        "costEstimate": row[3],
        "latencyMs": {"p50": row[4], "p95": row[5]},
    }
