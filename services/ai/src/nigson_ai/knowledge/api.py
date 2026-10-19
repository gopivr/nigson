"""AI knowledge control-plane APIs. Jobs are durable intent, never fake completion."""

from __future__ import annotations

import hashlib
import uuid
from datetime import UTC, datetime
from typing import Any, Literal, cast

from fastapi import APIRouter, Header, Request
from psycopg.types.json import Jsonb
from pydantic import Field, StrictStr, field_validator

from nigson_ai.evaluation.api import _principal as _evaluation_principal
from nigson_ai.evaluation.trust import verify_admin_actor
from nigson_ai.gateway.api import (
    _audit,
    _lock_idempotency,
    _pool,
    _principal,
    _reject_query,
    _save_idempotency,
)
from nigson_ai.platform.events import uuid7
from nigson_ai.platform.problem import Failure, StrictModel
from nigson_ai.platform.store import Conflict
from nigson_ai.platform.telemetry import correlation

router = APIRouter()
class CreateEmbeddingRevision(StrictModel):
    provider: Literal["BEDROCK"]
    modelId: Literal["amazon.titan-embed-text-v2:0"]
    dimensions: Literal[1024]
    chunkingRevision: StrictStr = Field(min_length=1, max_length=40)
    approvalReference: StrictStr = Field(min_length=1, max_length=120)


class ReindexKnowledge(StrictModel):
    sourceService: Literal["COMMERCE", "EXPERIENCE"]
    resourceId: StrictStr | None = Field(default=None, min_length=36, max_length=36)
    embeddingRevisionId: StrictStr = Field(min_length=36, max_length=36)

    @field_validator("resourceId", "embeddingRevisionId")
    @classmethod
    def valid_uuid(cls, value: str | None) -> str | None:
        if value is None:
            return None
        try:
            parsed = uuid.UUID(value)
        except ValueError:
            raise ValueError("invalid UUID") from None
        if parsed.int == 0:
            raise ValueError("invalid UUID")
        return str(parsed)


def _record(request: Request, operation: str, outcome: str) -> None:
    telemetry = getattr(request.app.state, "telemetry", None)
    counter = getattr(telemetry, "ai_knowledge_operations", None)
    if counter is not None:
        counter.add(1, {"owner": "ai", "operation": operation, "outcome": outcome})


@router.post("/api/v1/staff/ai/embedding-revisions", status_code=201)
async def create_embedding_revision(
    payload: CreateEmbeddingRevision,
    request: Request,
    idempotency_key: str = Header(alias="Idempotency-Key", min_length=1, max_length=100),
) -> dict[str, Any]:
    _reject_query(request)
    if len(request.headers.getlist("Idempotency-Key")) != 1:
        raise Failure("VALIDATION_ERROR")
    actor, _claims = _principal(request, recent_mfa=True)
    data = payload.model_dump(mode="json")
    pool = _pool(request)
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-EMBEDDING-REVISION", idempotency_key, data
        )
        if replay:
            _record(request, "knowledge.embedding_revision", "replayed")
            return cast(dict[str, Any], replay[1])
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
            (f"embedding-revision:{payload.provider}:{payload.modelId}:1024:{payload.chunkingRevision}",),
        )
        existing = await (
            await connection.execute(
                "SELECT id FROM ai.embedding_revision WHERE provider=%s AND model_id=%s AND dimensions=1024 AND chunker_version=%s",
                (payload.provider, payload.modelId, payload.chunkingRevision),
            )
        ).fetchone()
        if existing:
            raise Conflict("CONFLICT")
        identifier = uuid7()
        await connection.execute(
            "INSERT INTO ai.embedding_revision(id,created_by,updated_by,provider,model_id,dimensions,normalization,chunker_version,enabled) VALUES (%s,%s,%s,%s,%s,1024,true,%s,false)",
            (identifier, actor, actor, payload.provider, payload.modelId, payload.chunkingRevision),
        )
        result = {"id": str(identifier), "enabled": False}
        await _save_idempotency(
            connection, actor, "AI-EMBEDDING-REVISION", idempotency_key,
            idem, 201, result, identifier,
        )
        await _audit(
            connection, actor, "AI_EMBEDDING_REVISION_CREATE", "AI_EMBEDDING_REVISION",
            identifier,
            {
                "provider": payload.provider,
                "modelId": payload.modelId,
                "dimensions": 1024,
                "chunkingRevision": payload.chunkingRevision,
                "enabled": False,
                "checksum": hashlib.sha256(payload.approvalReference.encode()).hexdigest(),
            },
            reason="CONFIGURATION_CHANGE",
        )
    _record(request, "knowledge.embedding_revision", "created")
    return result


@router.post("/internal/v1/knowledge/reindex", status_code=202)
async def reindex_knowledge(
    payload: ReindexKnowledge,
    request: Request,
    idempotency_key: str = Header(alias="Idempotency-Key", min_length=1, max_length=100),
) -> dict[str, Any]:
    _reject_query(request)
    if len(request.headers.getlist("Idempotency-Key")) != 1:
        raise Failure("VALIDATION_ERROR")
    _evaluation_principal(request, "ADMIN", workload=True)
    assertion = verify_admin_actor(request, "ai.manage_WITH_SIGNED_ADMIN")
    actor = str(assertion["identity_subject"])
    actor_kind = "USER"
    resource_id = uuid.UUID(payload.resourceId) if payload.resourceId else None
    embedding_id = uuid.UUID(payload.embeddingRevisionId)
    data = payload.model_dump(mode="json")
    pool = _pool(request)
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        idem, replay = await _lock_idempotency(
            connection, actor, "AI-KNOWLEDGE-REINDEX", idempotency_key, data
        )
        if replay:
            _record(request, "knowledge.reindex", "replayed")
            return cast(dict[str, Any], replay[1])
        revision = await (
            await connection.execute(
                "SELECT provider,model_id,dimensions,enabled FROM ai.embedding_revision WHERE id=%s",
                (embedding_id,),
            )
        ).fetchone()
        if not revision:
            raise Failure("NOT_FOUND")
        if revision[2] != 1024:
            raise Failure("KNOWLEDGE_RULE_VIOLATION")
        scope_key = f"{payload.sourceService}:{payload.resourceId or '*'}:{embedding_id}"
        await connection.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", ("knowledge-job:" + scope_key,)
        )
        active_job = await (
            await connection.execute(
                "SELECT id FROM ai.ingestion_job WHERE source_service=%s AND source_resource_id IS NOT DISTINCT FROM %s AND embedding_revision_id=%s AND status IN ('PENDING','CLAIMED') ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
                (payload.sourceService, resource_id, embedding_id),
            )
        ).fetchone()
        if active_job:
            job_id = active_job[0]
        else:
            job_id = uuid7()
            business_key = "knowledge:" + hashlib.sha256(
                f"{scope_key}:{job_id}".encode()
            ).hexdigest()
            await connection.execute(
                "INSERT INTO ai.ingestion_job(id,created_by,updated_by,business_key,source_service,source_resource_id,embedding_revision_id,status,next_attempt_at,attempt_count) VALUES (%s,%s,%s,%s,%s,%s,%s,'PENDING',now(),0)",
                (job_id, actor, actor, business_key, payload.sourceService, resource_id, embedding_id),
            )
        result = {"jobId": str(job_id), "state": "QUEUED"}
        await _save_idempotency(
            connection, actor, "AI-KNOWLEDGE-REINDEX", idempotency_key,
            idem, 202, result, job_id,
        )
        correlation_id = correlation.get() or uuid7()
        await connection.execute(
            "INSERT INTO ai.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason,new_value) VALUES (%s,%s,%s,%s,'AI_KNOWLEDGE_REINDEX','AI_INGESTION_JOB',%s,%s,%s,'OPERATOR_REQUEST',%s)",
            (
                uuid7(), actor, actor, actor_kind, job_id, datetime.now(UTC),
                correlation_id,
                Jsonb({"sourceService": payload.sourceService, "resourceId": payload.resourceId, "embeddingRevisionId": str(embedding_id), "state": "QUEUED"}),
            ),
        )
    _record(request, "knowledge.reindex", "queued")
    return result
