"""Deterministic stateful local provider mock; never contacts an external model."""

from __future__ import annotations

import hashlib
import threading
from dataclasses import dataclass
from typing import Literal


@dataclass(frozen=True)
class ProviderReply:
    text: str
    input_tokens: int
    output_tokens: int
    request_hash: str


@dataclass(frozen=True)
class MockFailure:
    kind: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"]


class LocalProviderMock:
    """A bounded, replayable provider double with explicit uncertain outcomes."""

    def __init__(self, max_entries: int = 256) -> None:
        if not 1 <= max_entries <= 4096:
            raise ValueError("MOCK_BOUND_INVALID")
        self.max_entries = max_entries
        self._lock = threading.Lock()
        self._responses: dict[str, ProviderReply] = {}
        self._order: list[str] = []
        self._next_failure: MockFailure | None = None
        self.calls = 0

    def fail_next(
        self, kind: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"]
    ) -> None:
        with self._lock:
            self._next_failure = MockFailure(kind)

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
        if (
            not request_id
            or len(request_id) > 100
            or not model_id
            or len(model_id) > 200
            or len(prompt) > 48_000
            or not 1 <= max_input_tokens <= 12_000
            or not 128 <= max_output_tokens <= 2_000
            or not 1 <= deadline_seconds <= 20
        ):
            raise ValueError("PROVIDER_BOUND_INVALID")
        input_tokens = (len(prompt) + 2) // 3
        if input_tokens > max_input_tokens:
            raise ValueError("PROVIDER_INPUT_LIMIT")
        request_hash = hashlib.sha256(
            (model_id + "\0" + prompt).encode("utf-8")
        ).hexdigest()
        with self._lock:
            previous = self._responses.get(request_id)
            if previous is not None:
                if previous.request_hash != request_hash:
                    raise ValueError("PROVIDER_IDEMPOTENCY_MISMATCH")
                return previous
            failure = self._next_failure
            self._next_failure = None
            if failure and failure.kind == "UNAVAILABLE":
                raise ConnectionError("PROVIDER_UNAVAILABLE") from None
            reply_text = "Local evaluation response " + request_hash[:24]
            reply = ProviderReply(
                text=reply_text,
                input_tokens=input_tokens,
                output_tokens=(len(reply_text) + 2) // 3,
                request_hash=request_hash,
            )
            if reply.output_tokens > max_output_tokens:
                raise ValueError("PROVIDER_OUTPUT_LIMIT")
            self._responses[request_id] = reply
            self._order.append(request_id)
            self.calls += 1
            while len(self._order) > self.max_entries:
                expired = self._order.pop(0)
                self._responses.pop(expired, None)
            if failure and failure.kind == "UNKNOWN_AFTER_ACCEPTANCE":
                raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
            return reply

    def reconcile(self, request_id: str) -> ProviderReply | None:
        with self._lock:
            return self._responses.get(request_id)
