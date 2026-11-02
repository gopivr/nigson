"""SDK protocol tests; no credentials or paid model invocation."""

import io
import json

import boto3
import pytest
from botocore.exceptions import ReadTimeoutError
from botocore.response import StreamingBody
from botocore.stub import Stubber

from nigson_ai.providers.bedrock import BedrockProvider, TitanEmbeddingProvider

MODEL = "approved-test-chat"


def client():
    return boto3.client(
        "bedrock-runtime",
        region_name="us-east-1",
        aws_access_key_id="test",
        aws_secret_access_key="test",
    )


def invoke(provider, **changes):
    args = {
        "request_id": "run-1",
        "model_id": MODEL,
        "prompt": "Approved question",
        "max_input_tokens": 100,
        "max_output_tokens": 128,
        "deadline_seconds": 12,
    }
    args.update(changes)
    return provider.invoke(**args)


def count(stub, value=3):
    stub.add_response(
        "count_tokens",
        {"inputTokens": value},
        {
            "modelId": MODEL,
            "input": {
                "converse": {
                    "messages": [
                        {"role": "user", "content": [{"text": "Approved question"}]}
                    ]
                }
            },
        },
    )


def test_converse_protocol_and_real_usage():
    sdk = client()
    with Stubber(sdk) as stub:
        count(stub)
        stub.add_response(
            "converse",
            {
                "output": {
                    "message": {
                        "role": "assistant",
                        "content": [{"text": "Verified answer"}],
                    }
                },
                "stopReason": "end_turn",
                "usage": {"inputTokens": 3, "outputTokens": 4, "totalTokens": 7},
                "metrics": {"latencyMs": 10},
            },
            {
                "modelId": MODEL,
                "messages": [
                    {"role": "user", "content": [{"text": "Approved question"}]}
                ],
                "inferenceConfig": {"maxTokens": 128, "temperature": 0.0},
            },
        )
        reply = invoke(BedrockProvider("us-east-1", frozenset({MODEL}), client=sdk))
        assert (reply.text, reply.input_tokens, reply.output_tokens) == (
            "Verified answer",
            3,
            4,
        )
        stub.assert_no_pending_responses()


def test_allowlist_and_deadline_reject_before_network():
    provider = BedrockProvider("us-east-1", frozenset({MODEL}), client=client())
    with Stubber(provider.client):
        with pytest.raises(ValueError, match="NOT_APPROVED"):
            invoke(provider, model_id="unapproved")
        with pytest.raises(ValueError, match="BOUND_INVALID"):
            invoke(provider, deadline_seconds=5)


def test_exact_token_limit_prevents_paid_invocation():
    sdk = client()
    with Stubber(sdk) as stub:
        count(stub, 101)
        with pytest.raises(ValueError, match="INPUT_LIMIT"):
            invoke(BedrockProvider("us-east-1", frozenset({MODEL}), client=sdk))
        stub.assert_no_pending_responses()


def test_uncertain_invocation_is_not_retried_or_falsely_reconciled():
    class TimeoutClient:
        calls = 0

        def count_tokens(self, **args):
            return {"inputTokens": 3}

        def converse(self, **args):
            self.calls += 1
            raise ReadTimeoutError(
                endpoint_url="https://bedrock-runtime.us-east-1.amazonaws.com"
            )

    sdk = TimeoutClient()
    provider = BedrockProvider("us-east-1", frozenset({MODEL}), client=sdk)
    with pytest.raises(TimeoutError, match="OUTCOME_UNKNOWN"):
        invoke(provider)
    assert sdk.calls == 1 and provider.reconcile("run-1") is None


def test_known_denial_is_not_an_unknown_paid_outcome():
    sdk = client()
    with Stubber(sdk) as stub:
        count(stub)
        stub.add_client_error(
            "converse",
            service_error_code="AccessDeniedException",
            service_message="denied",
            http_status_code=403,
        )
        with pytest.raises(ConnectionError, match="UNAVAILABLE"):
            invoke(BedrockProvider("us-east-1", frozenset({MODEL}), client=sdk))


@pytest.mark.parametrize(
    "values", [[0.03125] * 1024, [0.1] * 12, [float("nan")] * 1024]
)
def test_titan_protocol_and_vector_validation(values):
    sdk = client()
    raw = json.dumps({"embedding": values}).encode()
    with Stubber(sdk) as stub:
        stub.add_response(
            "invoke_model",
            {
                "body": StreamingBody(io.BytesIO(raw), len(raw)),
                "contentType": "application/json",
            },
            {
                "modelId": TitanEmbeddingProvider.MODEL,
                "contentType": "application/json",
                "accept": "application/json",
                "body": json.dumps(
                    {
                        "inputText": "public content",
                        "dimensions": 1024,
                        "normalize": True,
                        "embeddingTypes": ["float"],
                    }
                ),
            },
        )
        provider = TitanEmbeddingProvider(
            "us-east-1", frozenset({TitanEmbeddingProvider.MODEL}), client=sdk
        )
        if len(values) == 1024 and values[0] == 0.03125:
            assert len(provider.embed("job-1", "public content")) == 1024
        else:
            with pytest.raises(TimeoutError, match="OUTCOME_UNKNOWN"):
                provider.embed("job-1", "public content")
        assert provider.reconcile_embedding("job-1") is None
