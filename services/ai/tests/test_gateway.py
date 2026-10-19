"""Native PostgreSQL and actual HTTP acceptance for AI gateway controls."""

from __future__ import annotations

import asyncio
import hashlib
import os
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from psycopg import AsyncConnection
from psycopg.conninfo import conninfo_to_dict
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from nigson_ai.gateway.budget import finish_run, reserve_run
from nigson_ai.gateway.provider import LocalProviderMock
from nigson_ai.platform.problem import Failure


class GatewayTokenMap:
    def __init__(self) -> None:
        self.values = {
            "admin": {
                "sub": "ai-gateway-admin",
                "azp": "nigson-web",
                "mfa_at": int(time.time()),
                "realm_access": {"roles": ["ADMIN"]},
            },
            "admin-stale": {
                "sub": "ai-gateway-admin",
                "azp": "nigson-web",
                "mfa_at": int(time.time()) - 901,
                "realm_access": {"roles": ["ADMIN"]},
            },
            "admin-no-mfa": {
                "sub": "ai-gateway-admin",
                "azp": "nigson-web",
                "realm_access": {"roles": ["ADMIN"]},
            },
            "analyst": {
                "sub": "ai-gateway-analyst",
                "azp": "nigson-web",
                "realm_access": {"roles": ["ANALYST"]},
            },
            "org-admin": {
                "sub": "ai-gateway-org-admin",
                "azp": "nigson-web",
                "organization_id": str(uuid.uuid4()),
                "realm_access": {"roles": ["ADMIN"]},
            },
        }

    def decode(self, token: str):
        if token not in self.values:
            raise Failure("AUTH_REQUIRED")
        return self.values[token]


MODEL_ID = "amazon.synthetic-gateway-test"


@pytest.fixture
def gateway_client(monkeypatch):
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

    with TestClient(
        app, raise_server_exceptions=False, client=("127.0.0.1", 12345)
    ) as client:
        app.state.ai_authenticator = GatewayTokenMap()
        app.state.ai_model_capabilities = frozenset({("BEDROCK", MODEL_ID)})
        app.state.ai_provider_region = "test-fixture-only"
        app.state.ai_provider_mock = LocalProviderMock()
        app.state.ai_approved_rate_references = frozenset(
            {"test-fixture-approval-only"}
        )
        yield client, app, dsn


def headers(token: str = "admin", key: str = "key") -> dict[str, str]:
    return {"Authorization": f"Bearer {token}", "Idempotency-Key": key}


def prompt_payload(code: str) -> dict:
    return {
        "code": code,
        "template": "Answer {{question}} for {{market}} using only {{evidence}}.",
        "toolSchemaRevision": "tools-v1",
        "reason": "Synthetic gateway acceptance fixture",
    }


def rate_payload() -> dict:
    return {
        "provider": "BEDROCK",
        "modelId": MODEL_ID,
        "inputPerMillion": "10.000000",
        "outputPerMillion": "20.000000",
        "currency": "USD",
        "effectiveFrom": (datetime.now(UTC) - timedelta(minutes=1))
        .isoformat()
        .replace("+00:00", "Z"),
        "approvalReference": "test-fixture-approval-only",
    }


def policy_payload(code: str, prompt_id: str, *, budget: str = "0.020000") -> dict:
    return {
        "code": code,
        "provider": "BEDROCK",
        "chatModelId": MODEL_ID,
        "fallbackModelId": None,
        "parameters": {
            "schemaVersion": 1,
            "temperature": 0.2,
            "maxOutputTokens": 128,
            "requestDeadlineSeconds": 5,
            "maximumSteps": 8,
            "maximumTools": 4,
            "maximumInputTokens": 1000,
            "concurrency": 1,
            "dailyBudget": budget,
            "rateRevision": 1,
        },
        "promptRevisionId": prompt_id,
    }


def test_gateway_staff_apis_idempotency_auth_mfa_version_and_activation_gates(
    gateway_client,
):
    client, app, dsn = gateway_client
    stem = uuid.uuid4().hex[:16]
    prompt_body = prompt_payload("prompt-" + stem)
    denied = client.post(
        "/api/v1/staff/ai/prompt-revisions",
        json=prompt_body,
        headers=headers("analyst", "denied"),
    )
    assert denied.status_code == 403
    org_denied = client.post(
        "/api/v1/staff/ai/prompt-revisions",
        json=prompt_body,
        headers=headers("org-admin", "org-denied"),
    )
    assert org_denied.status_code == 403
    prompt = client.post(
        "/api/v1/staff/ai/prompt-revisions",
        json=prompt_body,
        headers=headers(key="prompt-key"),
    )
    assert prompt.status_code == 201, prompt.text
    prompt_replay = client.post(
        "/api/v1/staff/ai/prompt-revisions",
        json=prompt_body,
        headers=headers(key="prompt-key"),
    )
    assert prompt_replay.status_code == 201 and prompt_replay.json() == prompt.json()
    changed_prompt = dict(prompt_body, template="A changed {{question}}")
    assert (
        client.post(
            "/api/v1/staff/ai/prompt-revisions",
            json=changed_prompt,
            headers=headers(key="prompt-key"),
        ).status_code
        == 409
    )

    rate_body = rate_payload()
    assert (
        client.post(
            "/api/v1/staff/ai/model-rates",
            json=rate_body,
            headers=headers("admin-no-mfa", "no-mfa"),
        ).status_code
        == 403
    )
    assert (
        client.post(
            "/api/v1/staff/ai/model-rates",
            json=rate_body,
            headers=headers("admin-stale", "stale-mfa"),
        ).status_code
        == 403
    )
    app.state.ai_approved_rate_references = frozenset()
    gated_rate = client.post(
        "/api/v1/staff/ai/model-rates",
        json=rate_body,
        headers=headers(key="approval-gated"),
    )
    assert gated_rate.status_code == 422
    app.state.ai_approved_rate_references = frozenset({"test-fixture-approval-only"})
    rate = client.post(
        "/api/v1/staff/ai/model-rates", json=rate_body, headers=headers(key="rate-key")
    )
    assert rate.status_code == 201, rate.text
    rate_replay = client.post(
        "/api/v1/staff/ai/model-rates", json=rate_body, headers=headers(key="rate-key")
    )
    assert rate_replay.status_code == 201 and rate_replay.json() == rate.json()
    changed_rate = dict(rate_body, inputPerMillion="11.000000")
    assert (
        client.post(
            "/api/v1/staff/ai/model-rates",
            json=changed_rate,
            headers=headers(key="rate-key"),
        ).status_code
        == 409
    )

    policy_body = policy_payload("policy-" + stem, prompt.json()["id"])
    app.state.ai_model_capabilities = frozenset()
    gated_policy = client.post(
        "/api/v1/staff/ai/model-policies",
        json=policy_body,
        headers=headers(key="unapproved-model"),
    )
    assert gated_policy.status_code == 422
    app.state.ai_model_capabilities = frozenset({("BEDROCK", MODEL_ID)})
    no_capability = client.post(
        "/api/v1/staff/ai/model-policies",
        json=policy_body,
        headers=headers(key="no-capability"),
    )
    assert no_capability.status_code == 201, no_capability.text
    policy = no_capability.json()
    assert policy["approved"] is False and policy["version"] == 0
    replay = client.post(
        "/api/v1/staff/ai/model-policies",
        json=policy_body,
        headers=headers(key="no-capability"),
    )
    assert replay.status_code == 201 and replay.json() == policy
    changed_policy = policy_payload(
        "policy-" + stem, prompt.json()["id"], budget="0.030000"
    )
    assert (
        client.post(
            "/api/v1/staff/ai/model-policies",
            json=changed_policy,
            headers=headers(key="no-capability"),
        ).status_code
        == 409
    )

    activation_url = f"/api/v1/staff/ai/model-policies/{policy['id']}/activation"
    activation_body = {
        "evaluationResultId": str(uuid.uuid4()),
        "reason": "Synthetic test",
    }
    stale = client.post(
        activation_url,
        json=activation_body,
        headers={**headers(key="activation-stale"), "If-Match": '"9"'},
    )
    assert stale.status_code == 412
    no_mfa = client.post(
        activation_url,
        json=activation_body,
        headers={**headers("admin-no-mfa", "activation-no-mfa"), "If-Match": '"0"'},
    )
    assert no_mfa.status_code == 403
    denied_capability = client.post(
        activation_url,
        json=activation_body,
        headers={**headers(key="activation-gate"), "If-Match": '"0"'},
    )
    assert denied_capability.status_code == 422
    missing = client.post(
        f"/api/v1/staff/ai/model-policies/{uuid.uuid4()}/activation",
        json=activation_body,
        headers={**headers(key="missing-policy"), "If-Match": '"0"'},
    )
    assert missing.status_code == 404

    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        assert (
            connection.execute(
                "SELECT count(*) FROM ai.prompt_revision WHERE id=%s",
                (prompt.json()["id"],),
            ).fetchone()[0]
            == 1
        )
        assert connection.execute(
            "SELECT approved,version FROM ai.model_policy WHERE id=%s", (policy["id"],)
        ).fetchone() == (False, 0)
        audit_ref = connection.execute(
            "SELECT new_value->>'checksum' FROM ai.audit_record WHERE resource_id=%s",
            (rate.json()["id"],),
        ).fetchone()
        assert (
            audit_ref
            and audit_ref[0]
            == hashlib.sha256(rate_body["approvalReference"].encode()).hexdigest()
        )
        assert (
            connection.execute(
                "SELECT response_body FROM ai.idempotency_record WHERE principal_key='ai-gateway-admin' AND operation='AI-PROMPT-CREATE' AND request_key='prompt-key'"
            ).fetchone()[0]
            == prompt.json()
        )

    eligible_policy_body = policy_payload(
        "eligible-policy-" + stem, prompt.json()["id"]
    )
    eligible_policy = client.post(
        "/api/v1/staff/ai/model-policies",
        json=eligible_policy_body,
        headers=headers(key="eligible-policy"),
    )
    assert eligible_policy.status_code == 201, eligible_policy.text
    suite_id, result_id = uuid.uuid4(), uuid.uuid4()
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        connection.execute(
            "INSERT INTO ai.evaluation_suite(id,created_by,code,revision,dataset_checksum,cases,scoring_policy) VALUES (%s,'ai-gateway-admin',%s,1,%s,'[]'::jsonb,'{}'::jsonb)",
            (
                suite_id,
                "gateway-fixture-" + stem,
                hashlib.sha256(b"fixture").hexdigest(),
            ),
        )
        connection.execute(
            "INSERT INTO ai.evaluation_result(id,created_by,suite_id,model_policy_revision,prompt_revision,retrieval_revision,scores,passed,report_object_key) VALUES (%s,'ai-gateway-admin',%s,%s,%s,'retrieval-test',%s,true,%s)",
            (
                result_id,
                suite_id,
                f"eligible-policy-{stem}:1",
                f"{prompt_body['code']}:1",
                Jsonb(
                    {
                        "activationEligible": True,
                        "localMockOnly": False,
                        "policyMatched": True,
                    }
                ),
                f"test-only/{result_id}.json",
            ),
        )
    eligible_url = (
        f"/api/v1/staff/ai/model-policies/{eligible_policy.json()['id']}/activation"
    )
    eligible_body = {
        "evaluationResultId": str(result_id),
        "reason": "Synthetic passing gate fixture",
    }

    def activate(_index):
        return client.post(
            eligible_url,
            json=eligible_body,
            headers={**headers(key="eligible-activation"), "If-Match": '"0"'},
        )

    with ThreadPoolExecutor(max_workers=4) as executor:
        activated = list(executor.map(activate, range(4)))
    assert all(
        response.status_code == 200 and response.json()["approved"] is True
        for response in activated
    )
    assert all(response.json() == activated[0].json() for response in activated)
    assert (
        client.post(
            eligible_url,
            json=eligible_body,
            headers={**headers(key="eligible-stale"), "If-Match": '"0"'},
        ).status_code
        == 412
    )
    assert (
        client.post(
            eligible_url,
            json=dict(eligible_body, reason="changed"),
            headers={**headers(key="eligible-activation"), "If-Match": '"0"'},
        ).status_code
        == 409
    )
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        assert connection.execute(
            "SELECT approved,version,evaluation_result_id FROM ai.model_policy WHERE id=%s",
            (eligible_policy.json()["id"],),
        ).fetchone() == (True, 1, result_id)


def test_gateway_atomic_budget_reservations_and_provider_uncertainty(gateway_client):
    client, app, dsn = gateway_client
    stem = uuid.uuid4().hex[:16]
    prompt = client.post(
        "/api/v1/staff/ai/prompt-revisions",
        json=prompt_payload("budget-prompt-" + stem),
        headers=headers(key="budget-prompt"),
    )
    assert prompt.status_code == 201, prompt.text
    rate = client.post(
        "/api/v1/staff/ai/model-rates",
        json=rate_payload(),
        headers=headers(key="budget-rate"),
    )
    assert rate.status_code == 201, rate.text
    body = policy_payload(
        "budget-policy-" + stem, prompt.json()["id"], budget="0.020000"
    )
    policy = client.post(
        "/api/v1/staff/ai/model-policies",
        json=body,
        headers=headers(key="budget-policy"),
    )
    assert policy.status_code == 201, policy.text
    policy_id = uuid.UUID(policy.json()["id"])
    conversations = [uuid.uuid4(), uuid.uuid4()]
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        for conversation in conversations:
            connection.execute(
                "INSERT INTO ai.conversation(id,created_by,updated_by,anonymous_session_hash,market_code,locale,state,expires_at) VALUES (%s,'ai-gateway-admin','ai-gateway-admin',%s,'NG','en-NG','ACTIVE',%s)",
                (
                    conversation,
                    hashlib.sha256(str(conversation).encode()).hexdigest(),
                    datetime.now(UTC) + timedelta(hours=1),
                ),
            )

    async def reserve(conversation: uuid.UUID, request_key: str):
        async with (
            await AsyncConnection.connect(dsn) as connection,
            connection.transaction(),
        ):
            return await reserve_run(
                connection,
                actor="ai-gateway-admin",
                conversation_id=conversation,
                request_key=request_key,
                workflow_version="gateway-test-v1",
                prompt_version="prompt-test-v1",
                policy_id=policy_id,
                input_text="synthetic request",
            )

    def reserve_in_thread(index: int):
        try:
            return asyncio.run(reserve(conversations[index], f"budget-run-{index}"))
        except Failure as failure:
            return failure.code

    # The API always creates a disabled policy. A synthetic test fixture may mark
    # it active solely to exercise the budget reservation machinery; production
    # activation remains blocked without a non-mock passing evaluation/capability.
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        connection.execute(
            "UPDATE ai.model_policy SET approved=true WHERE id=%s", (policy_id,)
        )
    outcomes = []
    with ThreadPoolExecutor(max_workers=2) as executor:
        outcomes = list(executor.map(reserve_in_thread, range(2)))
    reservations = [item for item in outcomes if not isinstance(item, str)]
    failures = [item for item in outcomes if isinstance(item, str)]
    assert len(reservations) == 1 and failures == ["RATE_LIMITED"]
    chosen = reservations[0]
    with (
        ConnectionPool(dsn, min_size=1, max_size=2) as pool,
        pool.connection() as connection,
    ):
        row = connection.execute(
            "SELECT conversation_id,request_key FROM ai.ai_run WHERE id=%s",
            (chosen.run_id,),
        ).fetchone()
        assert row is not None
        conversation_id, request_key = row

    async def mark_unavailable():
        async with await AsyncConnection.connect(dsn) as connection:
            await finish_run(
                connection,
                run_id=chosen.run_id,
                input_tokens=0,
                output_tokens=0,
                status="FAILED",
                failure_code="PROVIDER_UNAVAILABLE",
            )

    asyncio.run(mark_unavailable())
    retried = asyncio.run(reserve(conversation_id, request_key))
    assert retried.run_id == chosen.run_id and not retried.replayed

    async def mark_unknown():
        async with await AsyncConnection.connect(dsn) as connection:
            await finish_run(
                connection,
                run_id=chosen.run_id,
                input_tokens=0,
                output_tokens=0,
                status="FAILED",
                failure_code="PROVIDER_OUTCOME_UNKNOWN",
                uncertain=True,
            )

    asyncio.run(mark_unknown())

    async def retry_unknown():
        async with (
            await AsyncConnection.connect(dsn) as connection,
            connection.transaction(),
        ):
            return await reserve_run(
                connection,
                actor="ai-gateway-admin",
                conversation_id=conversation_id,
                request_key=request_key,
                workflow_version="gateway-test-v1",
                prompt_version="prompt-test-v1",
                policy_id=policy_id,
                input_text="synthetic request",
            )

    with pytest.raises(Failure, match="PROVIDER_OUTCOME_UNKNOWN"):
        asyncio.run(retry_unknown())

    mock = app.state.ai_provider_mock
    reply = mock.invoke(
        "stable-request",
        MODEL_ID,
        "synthetic prompt",
        max_input_tokens=100,
        max_output_tokens=128,
        deadline_seconds=5,
    )
    assert (
        mock.invoke(
            "stable-request",
            MODEL_ID,
            "synthetic prompt",
            max_input_tokens=100,
            max_output_tokens=128,
            deadline_seconds=5,
        )
        == reply
    )
    assert mock.calls == 1
    mock.fail_next("UNAVAILABLE")
    with pytest.raises(ConnectionError):
        mock.invoke(
            "unavailable",
            MODEL_ID,
            "synthetic prompt",
            max_input_tokens=100,
            max_output_tokens=128,
            deadline_seconds=5,
        )
    assert mock.reconcile("unavailable") is None
    mock.fail_next("UNKNOWN_AFTER_ACCEPTANCE")
    with pytest.raises(TimeoutError):
        mock.invoke(
            "uncertain",
            MODEL_ID,
            "synthetic prompt",
            max_input_tokens=100,
            max_output_tokens=128,
            deadline_seconds=5,
        )
    accepted = mock.reconcile("uncertain")
    assert (
        accepted is not None
        and mock.invoke(
            "uncertain",
            MODEL_ID,
            "synthetic prompt",
            max_input_tokens=100,
            max_output_tokens=128,
            deadline_seconds=5,
        )
        == accepted
    )
    assert mock.calls == 2
    with pytest.raises(ValueError, match="PROVIDER_IDEMPOTENCY_MISMATCH"):
        mock.invoke(
            "stable-request",
            MODEL_ID,
            "different prompt",
            max_input_tokens=100,
            max_output_tokens=128,
            deadline_seconds=5,
        )
