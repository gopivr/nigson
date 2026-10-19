"""PostgreSQL-backed, policy-scoped run reservations for concurrent gateway calls."""

from __future__ import annotations

import hashlib
import re
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, time, timedelta
from decimal import ROUND_CEILING, Decimal, DecimalException
from typing import Any

from psycopg import AsyncConnection
from psycopg.types.json import Jsonb

from nigson_ai.platform.events import uuid7
from nigson_ai.platform.problem import Failure


@dataclass(frozen=True)
class BudgetReservation:
    run_id: uuid.UUID
    reserved_cost: Decimal
    replayed: bool


def _decimal(value: Any) -> Decimal:
    try:
        result = Decimal(str(value))
    except (DecimalException, ValueError, TypeError):
        raise Failure("GATEWAY_RULE_VIOLATION") from None
    if not result.is_finite() or result < 0:
        raise Failure("GATEWAY_RULE_VIOLATION")
    return result


def _ceil_cost(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.000001"), rounding=ROUND_CEILING)


def _utc_day_start(value: datetime) -> datetime:
    return datetime.combine(value.astimezone(UTC).date(), time.min, UTC)


async def reserve_run(
    connection: AsyncConnection[Any],
    *,
    actor: str,
    conversation_id: uuid.UUID,
    request_key: str,
    workflow_version: str,
    prompt_version: str,
    policy_id: uuid.UUID,
    input_text: str,
    now: datetime | None = None,
) -> BudgetReservation:
    """Commit a pessimistic token-cost reservation before any provider call.

    Call inside a short local transaction. The policy/advisory locks serialize
    requests sharing a model/rate even across replicas. Existing RUNNING and
    completed run estimates consume today's configured budget; an uncertain
    provider result keeps its full reservation until reconciliation.
    """
    if (
        not actor
        or len(actor) > 200
        or not re.fullmatch(r"[A-Za-z0-9._:@-]{1,100}", request_key)
        or len(workflow_version) > 40
        or len(prompt_version) > 40
        or not input_text
        or len(input_text) > 48_000
        or not conversation_id.int
    ):
        raise Failure("VALIDATION_ERROR")
    instant = (now or datetime.now(UTC)).astimezone(UTC)
    policy = await (
        await connection.execute(
            "SELECT provider,chat_model_id,parameters,approved FROM ai.model_policy WHERE id=%s FOR UPDATE",
            (policy_id,),
        )
    ).fetchone()
    if not policy or policy[3] is not True:
        raise Failure("FEATURE_DISABLED")
    provider, model_id, parameters, _ = policy
    required = {
        "schemaVersion",
        "temperature",
        "maxOutputTokens",
        "requestDeadlineSeconds",
        "maximumSteps",
        "maximumTools",
        "maximumInputTokens",
        "concurrency",
        "dailyBudget",
        "rateRevision",
    }
    if not isinstance(parameters, dict) or set(parameters) != required:
        raise Failure("GATEWAY_RULE_VIOLATION")
    max_input = parameters["maximumInputTokens"]
    max_output = parameters["maxOutputTokens"]
    concurrency = parameters["concurrency"]
    rate_revision = parameters["rateRevision"]
    if (
        type(max_input) is not int
        or type(max_output) is not int
        or type(concurrency) is not int
        or type(rate_revision) is not int
        or not 1 <= max_input <= 12_000
        or not 128 <= max_output <= 2_000
        or not 1 <= concurrency <= 10
        or rate_revision < 1
        or (len(input_text) + 2) // 3 > max_input
    ):
        raise Failure("GATEWAY_RULE_VIOLATION")
    rate = await (
        await connection.execute(
            "SELECT input_per_million,output_per_million,currency_code FROM ai.model_rate WHERE provider=%s AND model_id=%s AND revision=%s AND effective_from<=%s",
            (provider, model_id, rate_revision, instant),
        )
    ).fetchone()
    if not rate:
        raise Failure("GATEWAY_RULE_VIOLATION")
    daily_budget = _decimal(parameters["dailyBudget"])
    input_rate, output_rate = _decimal(rate[0]), _decimal(rate[1])
    reservation = _ceil_cost(
        (Decimal(max_input) * input_rate + Decimal(max_output) * output_rate)
        / Decimal(1_000_000)
    )
    if reservation < 0:
        raise Failure("GATEWAY_RULE_VIOLATION")
    currency = rate[2]
    key = hashlib.sha256(f"{provider}:{model_id}:{currency}".encode()).digest()
    lock_key = int.from_bytes(key[:8], "big", signed=True)
    await connection.execute("SELECT pg_advisory_xact_lock(%s)", (lock_key,))
    body_hash = hashlib.sha256(
        f"{policy_id}:{conversation_id}:{workflow_version}:{prompt_version}:{input_text}".encode()
    ).hexdigest()
    idem = await (
        await connection.execute(
            "SELECT request_hash,state,resource_id FROM ai.idempotency_record WHERE principal_key=%s AND operation='AI-GATEWAY-RUN' AND request_key=%s FOR UPDATE",
            (actor, request_key),
        )
    ).fetchone()
    existing = None
    retry_existing = False
    if idem:
        if idem[0] != body_hash:
            raise Failure("IDEMPOTENCY_HASH_MISMATCH")
        existing = await (
            await connection.execute(
                "SELECT id,provider,model_id,rate_revision,status,cost_estimate,failure_code,workflow_version,prompt_version FROM ai.ai_run WHERE id=%s FOR UPDATE",
                (idem[2],),
            )
        ).fetchone()
        if (
            not existing
            or existing[1] != provider
            or existing[2] != model_id
            or existing[3] != rate_revision
            or existing[7] != workflow_version
            or existing[8] != prompt_version
        ):
            raise Failure("IDEMPOTENCY_HASH_MISMATCH")
        if existing[6] == "PROVIDER_OUTCOME_UNKNOWN":
            raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        if existing[4] in {"SUCCEEDED", "ESCALATED"}:
            return BudgetReservation(existing[0], existing[5], True)
        if existing[4] == "RUNNING":
            raise Failure("IDEMPOTENCY_IN_PROGRESS")
        if existing[4] == "FAILED" and existing[6] == "PROVIDER_UNAVAILABLE":
            retry_existing = True
        else:
            raise Failure("INVALID_TRANSITION")
    else:
        legacy = await (
            await connection.execute(
                "SELECT id FROM ai.ai_run WHERE conversation_id=%s AND request_key=%s",
                (conversation_id, request_key),
            )
        ).fetchone()
        if legacy:
            raise Failure("IDEMPOTENCY_HASH_MISMATCH")
    active = await (
        await connection.execute(
            "SELECT count(*)::int FROM ai.ai_run WHERE provider=%s AND model_id=%s AND status='RUNNING' AND created_at >= %s",
            (provider, model_id, _utc_day_start(instant)),
        )
    ).fetchone()
    if active is None:
        raise Failure("PROVIDER_UNAVAILABLE")
    if active[0] >= concurrency:
        raise Failure("RATE_LIMITED")
    spent = await (
        await connection.execute(
            "SELECT coalesce(sum(r.cost_estimate),0) FROM ai.ai_run r JOIN ai.model_rate m ON m.provider=r.provider AND m.model_id=r.model_id AND m.revision=r.rate_revision WHERE r.provider=%s AND r.model_id=%s AND m.currency_code=%s AND r.status<>'CANCELLED' AND r.created_at >= %s",
            (provider, model_id, currency, _utc_day_start(instant)),
        )
    ).fetchone()
    if spent is None:
        raise Failure("PROVIDER_UNAVAILABLE")
    if _decimal(spent[0]) + reservation > daily_budget:
        raise Failure("RATE_LIMITED")
    if retry_existing and existing:
        run_id = existing[0]
        await connection.execute(
            "UPDATE ai.ai_run SET status='RUNNING',cost_estimate=%s,failure_code=NULL,updated_at=now(),updated_by=created_by,version=version+1 WHERE id=%s AND status='FAILED' AND failure_code='PROVIDER_UNAVAILABLE'",
            (reservation, run_id),
        )
        await connection.execute(
            "UPDATE ai.idempotency_record SET state='RUNNING',updated_at=now(),updated_by=%s WHERE principal_key=%s AND operation='AI-GATEWAY-RUN' AND request_key=%s",
            (actor, actor, request_key),
        )
    else:
        run_id = uuid7()
        await connection.execute(
            "INSERT INTO ai.ai_run(id,created_by,updated_by,conversation_id,request_key,workflow_version,prompt_version,provider,model_id,status,cost_estimate,rate_revision) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'RUNNING',%s,%s)",
            (
                run_id,
                actor,
                actor,
                conversation_id,
                request_key,
                workflow_version,
                prompt_version,
                provider,
                model_id,
                reservation,
                rate_revision,
            ),
        )
        await connection.execute(
            "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,response_status,response_body,resource_id,expires_at) VALUES (%s,%s,%s,%s,'AI-GATEWAY-RUN',%s,%s,'RUNNING',202,%s,%s,%s)",
            (
                uuid7(),
                actor,
                actor,
                actor,
                request_key,
                body_hash,
                Jsonb({"runId": str(run_id), "state": "RUNNING"}),
                run_id,
                instant + timedelta(days=1),
            ),
        )
    return BudgetReservation(run_id, reservation, False)


async def finish_run(
    connection: AsyncConnection[Any],
    *,
    run_id: uuid.UUID,
    input_tokens: int,
    output_tokens: int,
    status: str,
    failure_code: str | None = None,
    uncertain: bool = False,
) -> Decimal:
    if (
        type(input_tokens) is not int
        or type(output_tokens) is not int
        or input_tokens < 0
        or output_tokens < 0
        or status not in {"SUCCEEDED", "ESCALATED", "FAILED"}
        or (
            failure_code is not None and not re.fullmatch(r"[A-Z_]{1,60}", failure_code)
        )
    ):
        raise Failure("VALIDATION_ERROR")
    async with connection.transaction():
        row = await (
            await connection.execute(
                "SELECT r.input_tokens,r.output_tokens,r.cost_estimate,r.provider,r.model_id,r.rate_revision,m.input_per_million,m.output_per_million FROM ai.ai_run r JOIN ai.model_rate m ON m.provider=r.provider AND m.model_id=r.model_id AND m.revision=r.rate_revision WHERE r.id=%s FOR UPDATE OF r",
                (run_id,),
            )
        ).fetchone()
        if not row:
            raise Failure("NOT_FOUND")
        if row[0] != 0 or row[1] != 0:
            raise Failure("INVALID_TRANSITION")
        if uncertain:
            cost = row[2]
        else:
            cost = _ceil_cost(
                (
                    Decimal(input_tokens) * _decimal(row[6])
                    + Decimal(output_tokens) * _decimal(row[7])
                )
                / Decimal(1_000_000)
            )
        await connection.execute(
            "UPDATE ai.ai_run SET status=%s,input_tokens=%s,output_tokens=%s,cost_estimate=%s,failure_code=%s,updated_at=now(),updated_by=created_by,version=version+1 WHERE id=%s AND status='RUNNING'",
            (status, input_tokens, output_tokens, cost, failure_code, run_id),
        )
        idem_state = (
            "RUNNING"
            if uncertain
            else "FAILED_RETRYABLE"
            if failure_code == "PROVIDER_UNAVAILABLE"
            else "SUCCEEDED"
        )
        await connection.execute(
            "UPDATE ai.idempotency_record SET state=%s,updated_at=now(),updated_by=(SELECT created_by FROM ai.ai_run WHERE id=%s),response_body=%s WHERE operation='AI-GATEWAY-RUN' AND resource_id=%s",
            (
                idem_state,
                run_id,
                Jsonb(
                    {"runId": str(run_id), "state": "UNKNOWN" if uncertain else status}
                ),
                run_id,
            ),
        )
        return Decimal(cost)
