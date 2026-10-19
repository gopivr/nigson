"""AI knowledge HTTP/PostgreSQL acceptance, deterministic only in local test mode."""

from __future__ import annotations

import asyncio
import hashlib
import ipaddress
import json
import os
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient
from psycopg.conninfo import conninfo_to_dict
from psycopg_pool import AsyncConnectionPool, ConnectionPool

from nigson_ai.knowledge.provider import LocalKnowledgeProvider, PublicSource
from nigson_ai.knowledge.service import process_job, retrieve_hybrid
from nigson_ai.platform.problem import Failure

ISSUER = "https://identity.example.test/realms/nigson"
ADMIN_ID = "20000000-0000-4000-8000-000000000001"
ADMIN_SUBJECT = "ai-knowledge-admin"
APPROVAL = "test-fixture-only-no-production-approval"


class KnowledgeTokenMap:
    def __init__(self) -> None:
        self.values = {
            "admin": {"sub": ADMIN_SUBJECT, "azp": "nigson-web", "mfa_at": int(time.time()), "realm_access": {"roles": ["ADMIN"]}},
            "stale-admin": {"sub": ADMIN_SUBJECT, "azp": "nigson-web", "mfa_at": int(time.time()) - 901, "realm_access": {"roles": ["ADMIN"]}},
            "no-mfa-admin": {"sub": ADMIN_SUBJECT, "azp": "nigson-web", "realm_access": {"roles": ["ADMIN"]}},
            "analyst": {"sub": "ai-knowledge-analyst", "azp": "nigson-web", "realm_access": {"roles": ["ANALYST"]}},
            "org-admin": {"sub": "ai-knowledge-org-admin", "azp": "nigson-web", "organization_id": str(uuid.uuid4()), "mfa_at": int(time.time()), "realm_access": {"roles": ["ADMIN"]}},
            "workload": {"sub": "commerce-workload", "azp": "nigson-commerce", "scope": "ai.manage_WITH_SIGNED_ADMIN"},
            "wrong-workload": {"sub": "other-workload", "azp": "nigson-integration", "scope": "ai.manage_WITH_SIGNED_ADMIN"},
            "wrong-scope": {"sub": "commerce-workload", "azp": "nigson-commerce", "scope": "knowledge.read"},
        }

    def decode(self, token: str):
        if token not in self.values:
            raise Failure("AUTH_REQUIRED")
        return self.values[token]


@pytest.fixture
def knowledge_client(monkeypatch):
    dsn = os.environ.get("NIGSON_TEST_AI_DSN")
    if not dsn:
        pytest.skip("Run the isolated AI PostgreSQL/HTTP harness")
    values = conninfo_to_dict(dsn)
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("NIGSON_DB_HOST", values["host"])
    monkeypatch.setenv("NIGSON_DB_NAME", values["dbname"])
    monkeypatch.setenv("NIGSON_DB_PASSWORD", values["password"])
    monkeypatch.setenv("NIGSON_DB_PORT", values.get("port", "5432"))
    from nigson_ai.api.health import app

    private_key = ec.generate_private_key(ec.SECP256R1())
    public = private_key.public_key()
    with TestClient(app, raise_server_exceptions=False, client=("127.0.0.1", 19221)) as client:
        app.state.ai_authenticator = KnowledgeTokenMap()
        app.state.ai_actor_public_keys = {"knowledge-test": public}
        app.state.ai_actor_identity_issuers = frozenset({ISSUER})
        app.state.ai_private_peer_cidrs = (ipaddress.ip_network("127.0.0.0/8"),)
        app.state.ai_knowledge_mock = LocalKnowledgeProvider()
        yield client, app, dsn, private_key


def embedding_body(chunker: str = "fixture-chunker-v1") -> dict:
    return {
        "provider": "BEDROCK",
        "modelId": "amazon.titan-embed-text-v2:0",
        "dimensions": 1024,
        "chunkingRevision": chunker,
        "approvalReference": APPROVAL,
    }


def bearer(token: str, key: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}", "Idempotency-Key": key}


def sign_reindex(client, app, private_key, body: dict, *, key="reindex", token="workload", scope="ai.manage_WITH_SIGNED_ADMIN"):
    wire = json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode()
    correlation_id = str(uuid.uuid4())
    now = int(time.time())
    assertion = jwt.encode(
        {
            "iss": "nigson-commerce-bff",
            "aud": ["ai"],
            "sub": ADMIN_ID,
            "identity_issuer": ISSUER,
            "identity_subject": ADMIN_SUBJECT,
            "roles": ["ADMIN"],
            "scopes": [scope],
            "operation": scope,
            "http_method": "POST",
            "resource": "/internal/v1/knowledge/reindex",
            "body_sha256": hashlib.sha256(wire).hexdigest(),
            "correlation_id": correlation_id,
            "iat": now,
            "exp": now + 60,
            "jti": str(uuid.uuid4()),
            "mfa_at": now,
        },
        private_key,
        algorithm="ES256",
        headers={"kid": "knowledge-test", "typ": "nigson-actor+jwt"},
    )
    return client.post(
        "/internal/v1/knowledge/reindex",
        content=wire,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Nigson-Actor": assertion,
            "X-Correlation-ID": correlation_id,
            "Idempotency-Key": key,
        },
    )


def public_source(*, resource_id: str | None = None, revision: int = 1) -> PublicSource:
    identifier = resource_id or str(uuid.uuid4())
    title = "Public product care information"
    body = "Apply the product care instructions using clean water and a soft cloth. " * 14
    return PublicSource(
        source_service="COMMERCE",
        resource_id=identifier,
        source_revision=revision,
        source_type="PRODUCT",
        locale="en-NG",
        market_code="NG",
        title=title,
        body=body,
        source_url="https://catalogue.example.test/products/public-item",
        checksum=hashlib.sha256((title + "\n" + body).encode()).hexdigest(),
    )


def test_embedding_and_reindex_http_auth_replay_races_and_owner_queue(knowledge_client):
    client, app, dsn, private_key = knowledge_client
    embedding = embedding_body()
    assert client.post("/api/v1/staff/ai/embedding-revisions", json=embedding, headers=bearer("analyst", "role-deny")).status_code == 403
    assert client.post("/api/v1/staff/ai/embedding-revisions", json=embedding, headers=bearer("stale-admin", "stale-mfa")).status_code == 403
    assert client.post("/api/v1/staff/ai/embedding-revisions", json=embedding, headers=bearer("org-admin", "org-deny")).status_code == 403
    created = client.post("/api/v1/staff/ai/embedding-revisions", json=embedding, headers=bearer("admin", "embedding-create"))
    assert created.status_code == 201, created.text
    revision_id = created.json()["id"]
    assert created.json()["enabled"] is False
    replay = client.post("/api/v1/staff/ai/embedding-revisions", json=embedding, headers=bearer("admin", "embedding-create"))
    assert replay.status_code == 201 and replay.json() == created.json()
    changed = client.post("/api/v1/staff/ai/embedding-revisions", json=embedding_body("changed-chunker"), headers=bearer("admin", "embedding-create"))
    assert changed.status_code == 409

    source_id = str(uuid.uuid4())
    body = {"sourceService": "COMMERCE", "resourceId": source_id, "embeddingRevisionId": revision_id}
    assert sign_reindex(client, app, private_key, body, key="wrong-scope", scope="knowledge.read").status_code == 403
    assert sign_reindex(client, app, private_key, body, key="wrong-client", token="wrong-workload").status_code == 403
    assert client.post("/internal/v1/knowledge/reindex", json=body, headers=bearer("workload", "no-actor")).status_code == 403
    queued = sign_reindex(client, app, private_key, body, key="reindex-key")
    assert queued.status_code == 202, queued.text
    result = queued.json()
    assert result["state"] == "QUEUED"
    same = sign_reindex(client, app, private_key, body, key="reindex-key")
    assert same.status_code == 202 and same.json() == result
    active_duplicate = sign_reindex(client, app, private_key, body, key="another-key")
    assert active_duplicate.status_code == 202 and active_duplicate.json() == result
    changed_body = dict(body, sourceService="EXPERIENCE")
    assert sign_reindex(client, app, private_key, changed_body, key="reindex-key").status_code == 409

    def repeat(_):
        return sign_reindex(client, app, private_key, body, key="race-key")

    with ThreadPoolExecutor(max_workers=6) as executor:
        responses = list(executor.map(repeat, range(6)))
    assert all(response.status_code == 202 and response.json() == result for response in responses)
    with ConnectionPool(dsn, min_size=1, max_size=2) as pool, pool.connection() as connection:
        assert connection.execute("SELECT status,source_service,source_resource_id FROM ai.ingestion_job WHERE id=%s", (result["jobId"],)).fetchone() == ("PENDING", "COMMERCE", uuid.UUID(source_id))
        assert connection.execute("SELECT enabled,dimensions,normalization FROM ai.embedding_revision WHERE id=%s", (revision_id,)).fetchone() == (False, 1024, True)
        assert connection.execute("SELECT count(*) FROM ai.audit_record WHERE resource_id=%s AND action='AI_KNOWLEDGE_REINDEX'", (result["jobId"],)).fetchone()[0] == 3
        assert connection.execute("SELECT count(*) FROM ai.idempotency_record WHERE operation='AI-KNOWLEDGE-REINDEX' AND resource_id=%s", (result["jobId"],)).fetchone()[0] == 3


def test_complete_embedding_revision_and_owner_withdrawal_fail_closed(knowledge_client):
    _client, _app, dsn, _private_key = knowledge_client
    identifier = str(uuid.uuid4())
    source = public_source(resource_id=identifier)
    mock = LocalKnowledgeProvider()
    mock.publish(source)
    # Isolated synthetic activation lets the persistence test exercise the
    # complete atomic pointer switch; API-179 itself always creates disabled.
    async def create_revision_and_job():
        async with AsyncConnectionPool(dsn, min_size=1, max_size=2) as pool:
            async with pool.connection() as connection, connection.transaction():
                await connection.execute(
                    "INSERT INTO ai.embedding_revision(id,created_by,updated_by,provider,model_id,dimensions,normalization,chunker_version,enabled) VALUES (%s,%s,%s,'BEDROCK','amazon.titan-embed-text-v2:0',1024,true,'synthetic-test-only',true)",
                    (uuid.uuid4(), ADMIN_SUBJECT, ADMIN_SUBJECT),
                )
                embedding_id = (await (await connection.execute("SELECT id FROM ai.embedding_revision WHERE chunker_version='synthetic-test-only'")).fetchone())[0]
                job_id = uuid.uuid4()
                await connection.execute(
                    "INSERT INTO ai.ingestion_job(id,created_by,updated_by,business_key,source_service,source_resource_id,embedding_revision_id,status,next_attempt_at) VALUES (%s,%s,%s,%s,'COMMERCE',%s,%s,'PENDING',now())",
                    (job_id, ADMIN_SUBJECT, ADMIN_SUBJECT, f"test-knowledge:{uuid.uuid4()}", uuid.UUID(identifier), embedding_id),
                )
            state = await process_job(pool, job_id, actor=ADMIN_SUBJECT, sources=mock, embedder=mock)
            assert state == "ACTIVE"
            async with pool.connection() as connection:
                row = await (await connection.execute("SELECT d.active_revision_id,r.status,count(c.id)::int FROM ai.knowledge_document d JOIN ai.knowledge_revision r ON r.id=d.active_revision_id JOIN ai.knowledge_chunk c ON c.revision_id=r.id WHERE d.source_resource_id=%s GROUP BY d.active_revision_id,r.status", (uuid.UUID(identifier),))).fetchone()
                assert row and row[1] == "ACTIVE" and row[2] >= 1
                vector_text = await (await connection.execute("SELECT embedding::text FROM ai.knowledge_chunk WHERE revision_id=%s ORDER BY ordinal LIMIT 1", (row[0],))).fetchone()
                import ast
                query_vector = tuple(float(value) for value in ast.literal_eval(vector_text[0]))
                async def validate_current(sources):
                    return _approved_source_ids(sources, mock)

                found = await retrieve_hybrid(connection, query="product care instructions", query_vector=query_vector, locale="en-NG", market_codes=("NG",), validate_sources=validate_current, limit=5)
                assert found and found[0]["sourceResourceId"] == uuid.UUID(identifier)
                denied = await retrieve_hybrid(connection, query="product care instructions", query_vector=query_vector, locale="en-NG", market_codes=("NG",), validate_sources=lambda _sources: asyncio.sleep(0, result=set()), limit=5)
                assert denied == []

                # Current owner withdrawal invalidates all active evidence before
                # any later retrieval can cite it.
                mock.withdraw("COMMERCE", identifier)
                assert not mock.is_current(source)
                await connection.execute("UPDATE ai.knowledge_document SET withdrawn_at=now(),active_revision_id=NULL,version=version+1 WHERE source_resource_id=%s", (uuid.UUID(identifier),))
                after = await retrieve_hybrid(connection, query="product care instructions", query_vector=query_vector, locale="en-NG", market_codes=("NG",), validate_sources=lambda _sources: asyncio.sleep(0, result=set()), limit=5)
                assert after == []

            async def queue_source_job(item: PublicSource):
                mock.publish(item)
                next_job = uuid.uuid4()
                async with pool.connection() as connection, connection.transaction():
                    await connection.execute(
                        "INSERT INTO ai.ingestion_job(id,created_by,updated_by,business_key,source_service,source_resource_id,embedding_revision_id,status,next_attempt_at) VALUES (%s,%s,%s,%s,'COMMERCE',%s,%s,'PENDING',now())",
                        (next_job, ADMIN_SUBJECT, ADMIN_SUBJECT, f"test-knowledge:{uuid.uuid4()}", uuid.UUID(item.resource_id), embedding_id),
                    )
                return next_job

            unavailable_source = public_source()
            unavailable_job = await queue_source_job(unavailable_source)
            mock.fail_next("UNAVAILABLE")
            with pytest.raises(Failure, match="PROVIDER_UNAVAILABLE"):
                await process_job(pool, unavailable_job, actor=ADMIN_SUBJECT, sources=mock, embedder=mock)
            async with pool.connection() as connection:
                assert (await (await connection.execute("SELECT status FROM ai.ingestion_job WHERE id=%s", (unavailable_job,))).fetchone())[0] == "PENDING"
            assert await process_job(pool, unavailable_job, actor=ADMIN_SUBJECT, sources=mock, embedder=mock) == "ACTIVE"

            uncertain_source = public_source()
            uncertain_job = await queue_source_job(uncertain_source)
            mock.fail_next("UNKNOWN_AFTER_ACCEPTANCE")
            assert await process_job(pool, uncertain_job, actor=ADMIN_SUBJECT, sources=mock, embedder=mock) == "ACTIVE"

    asyncio.run(create_revision_and_job())


def _approved_source_ids(values, mock):
    return {
        value["sourceResourceId"]
        for value in values
        if (source := mock.source(value["sourceService"], str(value["sourceResourceId"])))
        and source.source_revision == value["sourceRevision"]
        and mock.is_current(source)
    }


def test_public_source_validation_and_stateful_embedding_uncertainty():
    from nigson_ai.knowledge.service import split_chunks

    source = public_source()
    mock = LocalKnowledgeProvider()
    mock.publish(source)
    first = mock.embed("stable-chunk", source.title + source.body)
    assert len(first) == 1024 and abs(sum(item * item for item in first) - 1) < 0.001
    assert mock.embed("stable-chunk", source.title + source.body) == first
    with pytest.raises(ValueError):
        mock.embed("stable-chunk", "different input")
    mock.fail_next("UNKNOWN_AFTER_ACCEPTANCE")
    with pytest.raises(TimeoutError):
        mock.embed("uncertain-chunk", source.body)
    assert mock.reconcile_embedding("uncertain-chunk") is not None
    assert split_chunks(source)
    private = PublicSource(**{**source.__dict__, "body": source.body + " Contact: person@example.test"})
    with pytest.raises(Failure):
        split_chunks(private)
