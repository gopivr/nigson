"""Explicitly approved Bedrock ports. No SDK retries or invented reconciliation.

Durable workflow checkpoints own request deduplication and uncertain outcomes.
An invocation with an unknown outcome must never be submitted again by this port.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from typing import Any

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError, EndpointConnectionError

from nigson_ai.assistant.provider import AssistantReply
from nigson_ai.gateway.provider import ProviderReply


class BedrockProvider:
    def __init__(
        self, region: str, approved_models: frozenset[str], *, client: Any = None
    ):
        if not re.fullmatch(r"[a-z]{2}(?:-gov)?-[a-z]+-[0-9]", region):
            raise ValueError("AI_PROVIDER_REGION_INVALID")
        if (
            not approved_models
            or len(approved_models) > 32
            or any(not m or len(m) > 200 for m in approved_models)
        ):
            raise ValueError("AI_MODEL_CAPABILITY_POLICY_INVALID")
        self.approved_models = approved_models
        self.client = (
            client
            if client is not None
            else boto3.client(
                "bedrock-runtime",
                region_name=region,
                config=Config(
                    connect_timeout=2, read_timeout=4, retries={"total_max_attempts": 1}
                ),
            )
        )

    def _approved(self, model_id: str) -> None:
        if model_id not in self.approved_models:
            raise ValueError("AI_MODEL_NOT_APPROVED")

    def _call(self, operation: str, *, accepted: bool = False, **arguments: Any) -> Any:
        try:
            return getattr(self.client, operation)(**arguments)
        except EndpointConnectionError:
            raise ConnectionError("PROVIDER_UNAVAILABLE") from None
        except ClientError as failure:
            code = failure.response.get("Error", {}).get("Code")
            if (
                code
                in {
                    "AccessDeniedException",
                    "ValidationException",
                    "ResourceNotFoundException",
                    "ThrottlingException",
                    "ServiceQuotaExceededException",
                }
                or not accepted
            ):
                raise ConnectionError("PROVIDER_UNAVAILABLE") from None
            raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
        except BotoCoreError:
            if accepted:
                raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
            raise ConnectionError("PROVIDER_UNAVAILABLE") from None

    def invoke(
        self,
        request_id: str,
        model_id: str,
        prompt: str,
        *,
        max_input_tokens: int,
        max_output_tokens: int,
        deadline_seconds: int,
    ) -> ProviderReply:
        self._approved(model_id)
        # CountTokens and Converse each have a bounded 2s connection + 4s read.
        # A smaller requested deadline cannot safely accommodate both calls.
        if (
            not request_id
            or len(request_id) > 100
            or not 1 <= len(prompt) <= 48000
            or not 1 <= max_input_tokens <= 12000
            or not 128 <= max_output_tokens <= 2000
            or not 12 <= deadline_seconds <= 20
        ):
            raise ValueError("PROVIDER_BOUND_INVALID")
        messages = [{"role": "user", "content": [{"text": prompt}]}]
        count = self._call(
            "count_tokens", modelId=model_id, input={"converse": {"messages": messages}}
        ).get("inputTokens")
        if type(count) is not int or count < 1 or count > max_input_tokens:
            raise ValueError("PROVIDER_INPUT_LIMIT")
        result = self._call(
            "converse",
            accepted=True,
            modelId=model_id,
            messages=messages,
            inferenceConfig={"maxTokens": max_output_tokens, "temperature": 0.0},
        )
        try:
            content = result["output"]["message"]["content"]
            usage = result["usage"]
            text = "".join(part["text"] for part in content)
            tokens_in, tokens_out = usage["inputTokens"], usage["outputTokens"]
            if (
                result["stopReason"] not in {"end_turn", "max_tokens", "stop_sequence"}
                or not text
                or len(text) > 32000
                or type(tokens_in) is not int
                or type(tokens_out) is not int
                or not 0 <= tokens_in <= max_input_tokens
                or not 0 <= tokens_out <= max_output_tokens
            ):
                raise ValueError()
        except (KeyError, TypeError, ValueError):
            # Invocation may already have incurred cost; preserve the reservation.
            raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
        return ProviderReply(
            text,
            tokens_in,
            tokens_out,
            hashlib.sha256((model_id + "\0" + prompt).encode()).hexdigest(),
        )

    def generate(
        self,
        request_id: str,
        model_id: str,
        prompt_revision: str,
        question: str,
        evidence: tuple[tuple[str, str], ...],
        *,
        tool_facts: tuple[tuple[str, str], ...] = (),
        max_input_tokens: int,
        max_output_tokens: int,
        deadline_seconds: int,
    ) -> AssistantReply:
        if (
            not prompt_revision
            or len(prompt_revision) > 12000
            or not 1 <= len(question) <= 2000
            or len(evidence) > 20
            or len(tool_facts) > 4
        ):
            raise ValueError("ASSISTANT_PROVIDER_BOUND_INVALID")
        # Structured quoting prevents evidence and user input becoming system messages.
        prompt = json.dumps(
            {
                "instructions": prompt_revision,
                "question": question,
                "approvedEvidence": evidence,
                "readOnlyToolFacts": tool_facts,
            },
            ensure_ascii=False,
        )
        reply = self.invoke(
            request_id,
            model_id,
            prompt,
            max_input_tokens=max_input_tokens,
            max_output_tokens=max_output_tokens,
            deadline_seconds=deadline_seconds,
        )
        return AssistantReply(
            reply.text, reply.input_tokens, reply.output_tokens, reply.request_hash
        )

    def reconcile(self, request_id: str) -> None:
        # Bedrock has no lookup-by-client-request-id API for Converse.
        return None


class TitanEmbeddingProvider(BedrockProvider):
    MODEL = "amazon.titan-embed-text-v2:0"

    def embed(
        self,
        request_id: str,
        text: str,
        *,
        dimensions: int = 1024,
        deadline_seconds: int = 20,
    ) -> tuple[float, ...]:
        self._approved(self.MODEL)
        if (
            not request_id
            or len(request_id) > 180
            or not 1 <= len(text) <= 12000
            or dimensions != 1024
            or not 10 <= deadline_seconds <= 20
        ):
            raise ValueError("EMBEDDING_BOUND_INVALID")
        result = self._call(
            "invoke_model",
            accepted=True,
            modelId=self.MODEL,
            contentType="application/json",
            accept="application/json",
            body=json.dumps(
                {
                    "inputText": text,
                    "dimensions": 1024,
                    "normalize": True,
                    "embeddingTypes": ["float"],
                }
            ),
        )
        try:
            raw = result["body"].read(100001)
            if len(raw) > 100000:
                raise ValueError()
            values = json.loads(raw)["embedding"]
            if len(values) != 1024 or any(
                type(x) not in {int, float} or not math.isfinite(x) for x in values
            ):
                raise ValueError()
            vector = tuple(float(x) for x in values)
            if abs(sum(x * x for x in vector) - 1) > 0.01:
                raise ValueError()
            return vector
        except (KeyError, TypeError, ValueError, OSError, BotoCoreError):
            raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
        finally:
            if result.get("body") is not None:
                result["body"].close()

    def reconcile_embedding(self, request_id: str) -> None:
        return None
