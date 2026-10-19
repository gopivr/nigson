"""Approved-public source shaping, complete revision writes and scoped hybrid search."""

from __future__ import annotations

import hashlib
import re
import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Protocol
from urllib.parse import urlsplit

from psycopg import AsyncConnection
from psycopg.types.json import Jsonb

from nigson_ai.platform.events import uuid7
from nigson_ai.platform.problem import Failure

from .provider import PublicSource

MAX_SOURCE_CHARS = 200_000
MAX_CHUNKS = 500
CHUNK_WORDS = 900
OVERLAP_WORDS = 80
EMAIL_OR_PHONE = re.compile(
    r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|\+?\d[\d ()-]{8,}\d", re.IGNORECASE
)
PRIVATE_FIELD = re.compile(
    r"(?i)\b(email|phone|telephone|address|customer|applicant|order number|account number)\s*[:=]"
)


class SourceReader(Protocol):
    def fetch(self, source_service: str, resource_id: uuid.UUID) -> PublicSource | None: ...

    def is_current(self, source: PublicSource) -> bool: ...


class Embedder(Protocol):
    def embed(self, request_id: str, text: str, *, dimensions: int = 1024, deadline_seconds: int = 20) -> tuple[float, ...]: ...

    def reconcile_embedding(self, request_id: str) -> tuple[float, ...] | None: ...


@dataclass(frozen=True)
class Chunk:
    ordinal: int
    title: str
    body: str
    token_count: int
    source_hash: str


def _source_text(source: PublicSource) -> str:
    if (
        source.source_service not in {"COMMERCE", "EXPERIENCE"}
        or source.source_type not in {"CONTENT", "PRODUCT", "FAQ", "BROCHURE"}
        or (source.source_service == "COMMERCE" and source.source_type != "PRODUCT")
        or (source.source_service == "EXPERIENCE" and source.source_type != "CONTENT")
        or not source.approved
        or not source.current
        or source.visibility != "PUBLIC"
        or not source.resource_id
        or not 1 <= source.source_revision <= 2**63 - 1
        or not re.fullmatch(r"[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,4}", source.locale)
        or not re.fullmatch(r"[A-Z0-9_-]{1,32}", source.market_code)
        or len(source.title) > 500
        or len(source.body) > MAX_SOURCE_CHARS
        or not source.title.strip()
        or not source.body.strip()
        or EMAIL_OR_PHONE.search(source.title + "\n" + source.body)
        or PRIVATE_FIELD.search(source.title + "\n" + source.body)
    ):
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    parsed = urlsplit(source.source_url)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.fragment
        or len(source.source_url) > 1000
    ):
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    digest = hashlib.sha256((source.title + "\n" + source.body).encode()).hexdigest()
    if not re.fullmatch(r"[0-9a-f]{64}", source.checksum) or digest != source.checksum:
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    return source.title.strip() + "\n" + source.body.strip()


def split_chunks(source: PublicSource) -> list[Chunk]:
    text = _source_text(source)
    words = text.split()
    if not words:
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    chunks: list[Chunk] = []
    offset = 0
    while offset < len(words):
        end = min(len(words), offset + CHUNK_WORDS)
        while end > offset + 1 and len(" ".join(words[offset:end])) > 3598:
            end -= 1
        part = words[offset:end]
        body = " ".join(part)
        token_count = (len(body) + 2) // 3
        if len(body) > 3598 or not 1 <= token_count <= 1200:
            raise Failure("KNOWLEDGE_RULE_VIOLATION")
        chunks.append(
            Chunk(
                len(chunks),
                source.title[:200],
                body,
                token_count,
                hashlib.sha256(body.encode()).hexdigest(),
            )
        )
        if len(chunks) > MAX_CHUNKS:
            raise Failure("KNOWLEDGE_RULE_VIOLATION")
        if end >= len(words):
            break
        offset = max(offset + 1, end - OVERLAP_WORDS)
    return chunks


def _vector_literal(vector: tuple[float, ...]) -> str:
    if len(vector) != 1024 or any(not (-1 <= item <= 1) for item in vector):
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    norm = sum(item * item for item in vector) ** 0.5
    if not 0.999 <= norm <= 1.001:
        raise Failure("KNOWLEDGE_RULE_VIOLATION")
    return "[" + ",".join(format(item, ".9g") for item in vector) + "]"


async def process_job(
    pool: Any,
    job_id: uuid.UUID,
    *,
    actor: str,
    sources: SourceReader,
    embedder: Embedder,
) -> str:
    """Build a complete revision outside external-call transactions, then switch atomically.

    The supplied adapters are explicitly selected by the runtime. Production has
    no default adapter until provider and owner API credentials are configured.
    """
    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='3s'")
        job = await (
            await connection.execute(
                "SELECT source_service,source_resource_id,embedding_revision_id,status,lease_until FROM ai.ingestion_job WHERE id=%s FOR UPDATE",
                (job_id,),
            )
        ).fetchone()
        if not job:
            raise Failure("NOT_FOUND")
        if job[3] == "SUCCEEDED":
            return "SUCCEEDED"
        if job[3] == "CLAIMED":
            if job[4] is None:
                raise Failure("PROVIDER_OUTCOME_UNKNOWN")
            if job[4] is not None and job[4] > datetime.now(UTC):
                raise Failure("IDEMPOTENCY_IN_PROGRESS")
            await connection.execute(
                "UPDATE ai.ingestion_job SET lease_until=now()+interval '2 minutes',attempt_count=attempt_count+1,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
                (actor, job_id),
            )
        elif job[3] == "PENDING":
            await connection.execute(
                "UPDATE ai.ingestion_job SET status='CLAIMED',lease_until=now()+interval '2 minutes',attempt_count=attempt_count+1,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s",
                (actor, job_id),
            )
        else:
            raise Failure("INVALID_TRANSITION")
        if job[1] is None:
            raise Failure("FEATURE_DISABLED")

    try:
        source = sources.fetch(job[0], job[1])
    except ConnectionError:
        await _release_job(pool, job_id, actor)
        raise Failure("PROVIDER_UNAVAILABLE") from None
    except TimeoutError:
        await _release_job(pool, job_id, actor)
        raise Failure("PROVIDER_UNAVAILABLE") from None
    if source is None:
        await _fail_job(pool, job_id, actor)
        raise Failure("NOT_FOUND")
    try:
        chunks = split_chunks(source)
    except Failure:
        if source.source_service in {"COMMERCE", "EXPERIENCE"} and (
            not source.approved or not source.current or source.visibility != "PUBLIC"
        ):
            await _withdraw_job_source(pool, job_id, actor, source)
        else:
            await _fail_job(pool, job_id, actor)
        raise
    vectors: list[tuple[float, ...]] = []
    for chunk in chunks:
        request_id = f"{job_id}:{source.source_revision}:{chunk.ordinal}"
        try:
            vectors.append(
                embedder.embed(
                    request_id,
                    chunk.title + "\n" + chunk.body,
                    dimensions=1024,
                    deadline_seconds=20,
                )
            )
        except TimeoutError:
            vector = embedder.reconcile_embedding(request_id)
            if vector is None:
                await _hold_unknown_job(pool, job_id, actor)
                raise Failure("PROVIDER_OUTCOME_UNKNOWN") from None
            vectors.append(vector)
        except ConnectionError:
            await _release_job(pool, job_id, actor)
            raise Failure("PROVIDER_UNAVAILABLE") from None
        except (ValueError, Failure):
            await _fail_job(pool, job_id, actor)
            raise Failure("KNOWLEDGE_RULE_VIOLATION") from None

    # Check current owner state again after the potentially slow embedding calls.
    if not sources.is_current(source):
        await _withdraw_job_source(pool, job_id, actor, source)
        raise Failure("KNOWLEDGE_RULE_VIOLATION")

    async with pool.connection() as connection, connection.transaction():
        await connection.execute("SET LOCAL lock_timeout='350ms'")
        await connection.execute("SET LOCAL statement_timeout='10s'")
        job_state = await (
            await connection.execute(
                "SELECT status,embedding_revision_id FROM ai.ingestion_job WHERE id=%s FOR UPDATE",
                (job_id,),
            )
        ).fetchone()
        if not job_state or job_state[0] != "CLAIMED":
            raise Failure("INVALID_TRANSITION")
        embedding_revision = await (
            await connection.execute(
                "SELECT enabled,dimensions FROM ai.embedding_revision WHERE id=%s FOR UPDATE",
                (job_state[1],),
            )
        ).fetchone()
        if not embedding_revision or embedding_revision[1] != 1024:
            raise Failure("KNOWLEDGE_RULE_VIOLATION")
        doc = await (
            await connection.execute(
                "SELECT id,active_revision_id FROM ai.knowledge_document WHERE source_service=%s AND source_resource_id=%s AND locale=%s AND market_code=%s FOR UPDATE",
                (source.source_service, uuid.UUID(source.resource_id), source.locale, source.market_code),
            )
        ).fetchone()
        if doc:
            document_id, old_revision_id = doc
            await connection.execute(
                "UPDATE ai.knowledge_document SET withdrawn_at=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s",
                (actor, document_id),
            )
        else:
            document_id, old_revision_id = uuid7(), None
            await connection.execute(
                "INSERT INTO ai.knowledge_document(id,created_by,updated_by,source_service,source_resource_id,source_type,locale,market_code,visibility,active_revision_id) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,'PUBLIC',NULL)",
                (document_id, actor, actor, source.source_service, uuid.UUID(source.resource_id), source.source_type, source.locale, source.market_code),
            )
        existing = await (
            await connection.execute(
                "SELECT id FROM ai.knowledge_revision WHERE document_id=%s AND source_revision=%s AND embedding_revision_id=%s",
                (document_id, source.source_revision, job_state[1]),
            )
        ).fetchone()
        if existing:
            revision_id = existing[0]
            await connection.execute(
                "DELETE FROM ai.knowledge_chunk WHERE revision_id=%s", (revision_id,)
            )
            await connection.execute(
                "UPDATE ai.knowledge_revision SET status='BUILDING',checksum_sha256=%s,source_url=%s,version=version+1,updated_at=now(),updated_by=%s,activated_at=NULL WHERE id=%s",
                (source.checksum, source.source_url, actor, revision_id),
            )
        else:
            revision_id = uuid7()
            await connection.execute(
                "INSERT INTO ai.knowledge_revision(id,created_by,updated_by,document_id,source_revision,checksum_sha256,source_url,embedding_revision_id,status) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,'BUILDING')",
                (revision_id, actor, actor, document_id, source.source_revision, source.checksum, source.source_url, job_state[1]),
            )
        for chunk, vector in zip(chunks, vectors, strict=True):
            await connection.execute(
                "INSERT INTO ai.knowledge_chunk(id,created_by,updated_by,revision_id,ordinal,section_title,body,token_count,embedding,metadata,source_hash) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s::ai.vector(1024),%s,%s)",
                (
                    uuid7(), actor, actor, revision_id, chunk.ordinal, chunk.title,
                    chunk.body, chunk.token_count, _vector_literal(vector),
                    Jsonb({"sourceType": source.source_type}), chunk.source_hash,
                ),
            )
        count = await (
            await connection.execute(
                "SELECT count(*)::int,min(ordinal),max(ordinal),sum(token_count) FROM ai.knowledge_chunk WHERE revision_id=%s",
                (revision_id,),
            )
        ).fetchone()
        if not count or count[0] != len(chunks) or count[1] != 0 or count[2] != len(chunks) - 1:
            raise Failure("KNOWLEDGE_RULE_VIOLATION")
        await connection.execute(
            "UPDATE ai.knowledge_revision SET status='VALIDATED',version=version+1,updated_at=now(),updated_by=%s WHERE id=%s",
            (actor, revision_id),
        )
        active_revision_id = old_revision_id
        if embedding_revision[0] is True:
            if old_revision_id and old_revision_id != revision_id:
                await connection.execute(
                    "UPDATE ai.knowledge_revision SET status='SUPERSEDED',version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='ACTIVE'",
                    (actor, old_revision_id),
                )
            await connection.execute(
                "UPDATE ai.knowledge_revision SET status='ACTIVE',activated_at=now(),version=version+1,updated_at=now(),updated_by=%s WHERE id=%s",
                (actor, revision_id),
            )
            active_revision_id = revision_id
        await connection.execute(
            "UPDATE ai.knowledge_document SET active_revision_id=%s,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s",
            (active_revision_id, actor, document_id),
        )
        await connection.execute(
            "UPDATE ai.ingestion_job SET document_id=%s,status='SUCCEEDED',lease_until=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
            (document_id, actor, job_id),
        )
        return "ACTIVE" if active_revision_id == revision_id else "VALIDATED"


async def _fail_job(pool: Any, job_id: uuid.UUID, actor: str) -> None:
    async with pool.connection() as connection, connection.transaction():
        await connection.execute(
            "UPDATE ai.ingestion_job SET status='FAILED',lease_until=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
            (actor, job_id),
        )


async def _release_job(pool: Any, job_id: uuid.UUID, actor: str) -> None:
    async with pool.connection() as connection, connection.transaction():
        await connection.execute(
            "UPDATE ai.ingestion_job SET status='PENDING',lease_until=NULL,next_attempt_at=now()+interval '1 minute',version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
            (actor, job_id),
        )


async def _hold_unknown_job(pool: Any, job_id: uuid.UUID, actor: str) -> None:
    """A NULL lease on CLAIMED is the durable no-blind-retry UNKNOWN marker."""
    async with pool.connection() as connection, connection.transaction():
        await connection.execute(
            "UPDATE ai.ingestion_job SET lease_until=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
            (actor, job_id),
        )


async def _withdraw_job_source(pool: Any, job_id: uuid.UUID, actor: str, source: PublicSource) -> None:
    async with pool.connection() as connection, connection.transaction():
        await connection.execute(
            "UPDATE ai.knowledge_document SET withdrawn_at=now(),active_revision_id=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE source_service=%s AND source_resource_id=%s AND locale=%s AND market_code=%s",
            (actor, source.source_service, uuid.UUID(source.resource_id), source.locale, source.market_code),
        )
        await connection.execute(
            "UPDATE ai.knowledge_revision SET status='WITHDRAWN',version=version+1,updated_at=now(),updated_by=%s WHERE document_id IN (SELECT id FROM ai.knowledge_document WHERE source_service=%s AND source_resource_id=%s AND locale=%s AND market_code=%s) AND status IN ('ACTIVE','VALIDATED')",
            (actor, source.source_service, uuid.UUID(source.resource_id), source.locale, source.market_code),
        )
        await connection.execute(
            "UPDATE ai.ingestion_job SET status='FAILED',lease_until=NULL,version=version+1,updated_at=now(),updated_by=%s WHERE id=%s AND status='CLAIMED'",
            (actor, job_id),
        )


async def retrieve_hybrid(
    connection: AsyncConnection[Any],
    *,
    query: str,
    query_vector: tuple[float, ...],
    locale: str,
    market_codes: tuple[str, ...],
    validate_sources: Callable[[list[dict[str, Any]]], Awaitable[set[uuid.UUID]]],
    limit: int = 10,
) -> list[dict[str, Any]]:
    """Search only active PUBLIC rows, then revalidate every candidate at its owner."""
    if (
        not query.strip()
        or len(query) > 1000
        or len(market_codes) == 0
        or len(market_codes) > 20
        or len(set(market_codes)) != len(market_codes)
        or any(not re.fullmatch(r"[A-Z0-9_-]{1,32}", code) for code in market_codes)
        or not 1 <= limit <= 20
        or not re.fullmatch(r"[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8}){0,4}", locale)
    ):
        raise Failure("VALIDATION_ERROR")
    vector = _vector_literal(query_vector)
    await connection.execute("SET LOCAL statement_timeout='1s'")
    rows = await (
        await connection.execute(
            "WITH eligible AS (SELECT c.id,c.revision_id,c.section_title,c.body,c.source_hash,d.source_service,d.source_resource_id,d.locale,d.market_code,d.active_revision_id,r.embedding_revision_id,r.source_revision,r.source_url FROM ai.knowledge_chunk c JOIN ai.knowledge_revision r ON r.id=c.revision_id AND r.status='ACTIVE' JOIN ai.knowledge_document d ON d.id=r.document_id AND d.active_revision_id=r.id AND d.withdrawn_at IS NULL AND d.visibility='PUBLIC' JOIN ai.embedding_revision e ON e.id=r.embedding_revision_id AND e.enabled=true WHERE d.locale=%s AND d.market_code=ANY(%s)), lexical AS (SELECT id,row_number() OVER(ORDER BY ts_rank_cd(c.search_vector,plainto_tsquery('simple',%s)) DESC,id) rank FROM ai.knowledge_chunk c JOIN eligible e USING(id) WHERE c.search_vector @@ plainto_tsquery('simple',%s) LIMIT 20), semantic AS (SELECT id,row_number() OVER(ORDER BY c.embedding <=> %s::ai.vector(1024),id) rank FROM ai.knowledge_chunk c JOIN eligible e USING(id) ORDER BY c.embedding <=> %s::ai.vector(1024) LIMIT 20), fused AS (SELECT id,sum(1.0/(60+rank)) score FROM (SELECT * FROM lexical UNION ALL SELECT * FROM semantic) x GROUP BY id) SELECT e.id,e.section_title,e.body,e.source_hash,e.source_service,e.source_resource_id,e.locale,e.market_code,e.embedding_revision_id,e.source_revision,f.score,e.revision_id,e.source_url FROM fused f JOIN eligible e USING(id) ORDER BY f.score DESC,e.id LIMIT %s",
            (locale, list(market_codes), query, query, vector, vector, limit),
        )
    ).fetchall()
    candidates = [
        {
            "chunkId": row[0],
            "sectionTitle": row[1],
            "body": row[2],
            "sourceHash": row[3],
            "sourceService": row[4],
            "sourceResourceId": row[5],
            "locale": row[6],
            "marketCode": row[7],
            "embeddingRevisionId": row[8],
            "sourceRevision": row[9],
            "score": float(row[10]),
            "revisionId": row[11],
            "sourceUrl": row[12],
        }
        for row in rows
    ]
    if not candidates:
        return []
    try:
        approved = await validate_sources(
            [
                {
                    key: item[key]
                    for key in (
                        "sourceService",
                        "sourceResourceId",
                        "sourceRevision",
                        "locale",
                        "marketCode",
                    )
                }
                for item in candidates
            ]
        )
    except Exception:  # noqa: BLE001 - owner validation must fail closed
        raise Failure("PROVIDER_UNAVAILABLE") from None
    return [item for item in candidates if item["sourceResourceId"] in approved]
