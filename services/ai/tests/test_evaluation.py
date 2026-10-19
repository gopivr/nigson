"""Actual HTTP/native PostgreSQL acceptance for AI evaluation APIs."""

from __future__ import annotations

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
from psycopg_pool import ConnectionPool
from pydantic import ValidationError

from nigson_ai.evaluation.api import CreateSuite
from nigson_ai.evaluation.mock import LocalEvaluationModel
from nigson_ai.platform.problem import Failure


class TokenMap:
    def __init__(self):
        self.values = {
            "admin": {
                "sub": "ai-test-admin",
                "azp": "nigson-web",
                "realm_access": {"roles": ["ADMIN"]},
            },
            "admin-b": {
                "sub": "ai-test-admin-b",
                "azp": "nigson-web",
                "realm_access": {"roles": ["ADMIN"]},
            },
            "analyst": {
                "sub": "ai-test-analyst",
                "azp": "nigson-web",
                "realm_access": {"roles": ["ANALYST"]},
            },
            "org-analyst": {
                "sub": "ai-test-org-analyst",
                "azp": "nigson-web",
                "organization_id": str(uuid.uuid4()),
                "realm_access": {"roles": ["ANALYST"]},
            },
            "workload": {
                "sub": "commerce-client",
                "azp": "nigson-commerce",
                "scope": "ai.manage_WITH_SIGNED_ADMIN",
            },
            "other-workload": {
                "sub": "other-client",
                "azp": "nigson-integration",
                "scope": "ai.manage_WITH_SIGNED_ADMIN",
            },
        }

    def decode(self, token: str):
        if token not in self.values:
            raise Failure("AUTH_REQUIRED")
        return self.values[token]


ACTOR_ISSUER = "https://identity.example.test/realms/nigson"
ACTOR_SUBJECTS = {
    "ai-test-admin": "10000000-0000-4000-8000-000000000001",
    "ai-test-admin-b": "10000000-0000-4000-8000-000000000002",
}


def signed_run(
    client,
    app,
    body,
    *,
    actor="ai-test-admin",
    if_match='"1"',
    key="run",
    signed_body=None,
    mfa_age=0,
):
    wire = json.dumps(body, separators=(",", ":"), ensure_ascii=False).encode()
    signed_wire = json.dumps(
        signed_body if signed_body is not None else body,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode()
    correlation_id = str(uuid.uuid4())
    now = int(time.time())
    assertion = jwt.encode(
        {
            "iss": "nigson-commerce-bff",
            "aud": ["ai"],
            "sub": ACTOR_SUBJECTS[actor],
            "identity_issuer": ACTOR_ISSUER,
            "identity_subject": actor,
            "roles": ["ADMIN"],
            "scopes": ["ai.manage_WITH_SIGNED_ADMIN"],
            "operation": "ai.manage_WITH_SIGNED_ADMIN",
            "http_method": "POST",
            "resource": "/internal/v1/evaluations",
            "body_sha256": hashlib.sha256(signed_wire).hexdigest(),
            "correlation_id": correlation_id,
            "iat": now,
            "exp": now + 60,
            "jti": str(uuid.uuid4()),
            "mfa_at": now - mfa_age,
        },
        app.state.test_actor_private_key,
        algorithm="ES256",
        headers={"kid": "test-actor-key", "typ": "nigson-actor+jwt"},
    )
    return client.post(
        "/internal/v1/evaluations",
        content=wire,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer workload",
            "X-Nigson-Actor": assertion,
            "X-Correlation-ID": correlation_id,
            "If-Match": if_match,
            "Idempotency-Key": key,
        },
    )


def suite_body(code: str, source_ids=None):
    source_ids = source_ids or []
    return {
        "code": code,
        "cases": [
            {
                "id": f"case-{i}",
                "input": f"Synthetic test input {i}",
                "market": "NG",
                "locale": "en-NG",
                "expectedIntent": "catalogue",
                "requiredSourceIds": source_ids,
                "mustEscalate": False,
                "forbiddenActions": [],
            }
            for i in range(200)
        ],
        "thresholds": {
            "groundednessMinimum": 0.8,
            "scopeLeaksMaximum": 0,
            "injectionMutationMaximum": 0,
            "latencyP95MaximumMs": 20000,
        },
    }


@pytest.fixture
def evaluation_client(monkeypatch):
    dsn = os.environ.get("NIGSON_TEST_AI_DSN")
    if not dsn:
        pytest.skip("Run make platform-check for native PostgreSQL and HTTP acceptance")

    values = conninfo_to_dict(dsn)
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("NIGSON_DB_HOST", values["host"])
    monkeypatch.setenv("NIGSON_DB_NAME", values["dbname"])
    monkeypatch.setenv("NIGSON_DB_PASSWORD", values["password"])
    monkeypatch.setenv("NIGSON_DB_PORT", values.get("port", "5432"))
    from nigson_ai.api.health import app

    with TestClient(
        app, raise_server_exceptions=False, client=("127.0.0.1", 12345)
    ) as client:
        app.state.ai_authenticator = TokenMap()
        private_key = ec.generate_private_key(ec.SECP256R1())
        app.state.test_actor_private_key = private_key
        app.state.ai_actor_public_keys = {"test-actor-key": private_key.public_key()}
        app.state.ai_actor_identity_issuers = frozenset({ACTOR_ISSUER})
        app.state.ai_private_peer_cidrs = (ipaddress.ip_network("127.0.0.1/32"),)
        yield client, app


def test_suite_validation_and_local_mock_are_bounded_and_deterministic():
    body = suite_body("suite")
    valid = CreateSuite.model_validate(body)
    mock = LocalEvaluationModel()
    first = mock.run([case.model_dump(mode="json") for case in valid.cases], "policy:1")
    second = mock.run(
        [case.model_dump(mode="json") for case in valid.cases], "policy:1"
    )
    assert first == second and len(first) == 200 and mock.calls == 400
    private = suite_body("private")
    private["cases"][0]["input"] = "Contact user@example.com"
    with pytest.raises(ValidationError):
        CreateSuite.model_validate(private)


def test_suite_creation_policy_grounding_owner_replay_race_and_report_http(
    evaluation_client,
):
    client, app = evaluation_client
    code = "eval-" + uuid.uuid4().hex[:20]
    body = suite_body(code)
    denied = client.post(
        "/api/v1/staff/ai/evaluation-suites",
        json=body,
        headers={"Authorization": "Bearer analyst", "Idempotency-Key": "denied"},
    )
    assert denied.status_code == 403
    created = client.post(
        "/api/v1/staff/ai/evaluation-suites",
        json=body,
        headers={"Authorization": "Bearer admin", "Idempotency-Key": "suite-key"},
    )
    assert created.status_code == 201
    replay = client.post(
        "/api/v1/staff/ai/evaluation-suites",
        json=body,
        headers={"Authorization": "Bearer admin", "Idempotency-Key": "suite-key"},
    )
    assert replay.status_code == 201 and replay.json() == created.json()
    changed_request = suite_body(code)
    changed_request["thresholds"]["groundednessMinimum"] = 0.9
    conflicting_replay = client.post(
        "/api/v1/staff/ai/evaluation-suites",
        json=changed_request,
        headers={"Authorization": "Bearer admin", "Idempotency-Key": "suite-key"},
    )
    assert conflicting_replay.status_code == 409
    race_body = suite_body("race-" + uuid.uuid4().hex[:20])

    def create_race(_):
        return client.post(
            "/api/v1/staff/ai/evaluation-suites",
            json=race_body,
            headers={"Authorization": "Bearer admin", "Idempotency-Key": "race-key"},
        )

    with ThreadPoolExecutor(max_workers=8) as executor:
        raced = list(executor.map(create_race, range(8)))
    assert all(
        response.status_code == 201 and response.json() == raced[0].json()
        for response in raced
    )
    run_body = {
        "suiteId": created.json()["id"],
        "modelPolicyRevision": "missing-policy:1",
        "promptRevision": "prompt:1",
        "retrievalRevision": "retrieval-1",
    }
    mismatch = signed_run(client, app, run_body, if_match='"9"', key="run-stale")
    assert mismatch.status_code == 412, mismatch.text
    altered_body = dict(run_body, retrievalRevision="different-retrieval")
    tampered = signed_run(
        client, app, altered_body, signed_body=run_body, key="tampered-run"
    )
    assert tampered.status_code == 403
    stale_mfa = signed_run(client, app, run_body, key="stale-mfa", mfa_age=901)
    assert stale_mfa.status_code == 403
    foreign = signed_run(
        client,
        app,
        run_body,
        actor="ai-test-admin-b",
        if_match='"1"',
        key="foreign-run",
    )
    assert foreign.status_code == 403
    denied_client = client.post(
        "/internal/v1/evaluations",
        json={
            "suiteId": created.json()["id"],
            "modelPolicyRevision": "missing-policy:1",
            "promptRevision": "prompt:1",
            "retrievalRevision": "retrieval-1",
        },
        headers={
            "Authorization": "Bearer other-workload",
            "If-Match": '"1"',
            "Idempotency-Key": "wrong-client",
        },
    )
    assert denied_client.status_code == 403
    dsn = os.environ["NIGSON_TEST_AI_DSN"]
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        assert (
            connection.execute(
                "SELECT count(*) FROM ai.idempotency_record WHERE principal_key='ai-test-admin-b' AND operation='AI-EVALU-RUN' AND request_key='foreign-run'"
            ).fetchone()[0]
            == 0
        )
    app.state.evaluation_model.fail_next = "MOCK_FAILURE"
    failed = signed_run(client, app, run_body, key="run-fail")
    assert failed.status_code == 500
    run = signed_run(client, app, run_body, key="run-key")
    assert run.status_code == 202 and run.json()["state"] == "QUEUED"
    replay_run = signed_run(client, app, run_body, key="run-key")
    assert replay_run.status_code == 202 and replay_run.json() == run.json()

    def run_race(_):
        return signed_run(client, app, run_body, key="run-race")

    with ThreadPoolExecutor(max_workers=8) as executor:
        run_races = list(executor.map(run_race, range(8)))
    assert all(
        response.status_code == 202 and response.json() == run_races[0].json()
        for response in run_races
    )
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        row = connection.execute(
            "SELECT passed,scores FROM ai.evaluation_result WHERE id=%s",
            (run.json()["evaluationId"],),
        ).fetchone()
        assert (
            row
            and row[0] is False
            and row[1]["policyMatched"] is False
            and row[1]["activationEligible"] is False
            and row[1]["groundedness"] == 0
            and {"policy", "groundedness"} <= set(row[1]["insightDraft"]["failedGates"])
        )
        assert (
            connection.execute(
                "SELECT count(*) FROM ai.evaluation_result WHERE suite_id=%s",
                (created.json()["id"],),
            ).fetchone()[0]
            == 2
        )
        assert (
            connection.execute(
                "SELECT count(*) FROM ai.idempotency_record WHERE principal_key='ai-test-admin' AND operation='AI-EVALU-RUN' AND request_key='run-fail'"
            ).fetchone()[0]
            == 0
        )
    report = client.get(
        "/api/v1/staff/reports/ai",
        params={"from": "2026-10-01T00:00:00Z", "to": "2026-10-19T00:00:00Z"},
        headers={"Authorization": "Bearer analyst"},
    )
    assert report.status_code == 200 and set(report.json()) == {
        "requests",
        "escalations",
        "topIntents",
        "emptySearches",
        "tokenUsage",
        "costEstimate",
        "latencyMs",
    }
    too_wide = client.get(
        "/api/v1/staff/reports/ai",
        params={"from": "2026-01-01T00:00:00Z", "to": "2026-10-19T00:00:00Z"},
        headers={"Authorization": "Bearer analyst"},
    )
    assert too_wide.status_code == 400
    org_report = client.get(
        "/api/v1/staff/reports/ai",
        params={"from": "2026-10-01T00:00:00Z", "to": "2026-10-19T00:00:00Z"},
        headers={"Authorization": "Bearer org-analyst"},
    )
    assert org_report.status_code == 403
