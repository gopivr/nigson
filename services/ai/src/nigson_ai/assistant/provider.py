"""Deterministic local assistant provider; never calls a remote model."""

from __future__ import annotations

import hashlib
import threading
from dataclasses import dataclass
from typing import Literal


@dataclass(frozen=True)
class AssistantReply:
    text: str
    input_tokens: int
    output_tokens: int
    request_hash: str


class LocalAssistantProvider:
    """Replayable stateful test double with known and uncertain failure modes."""

    def __init__(self, max_entries: int = 256) -> None:
        if not 1 <= max_entries <= 4096:
            raise ValueError("MOCK_BOUND_INVALID")
        self.max_entries = max_entries
        self._lock = threading.Lock()
        self._replies: dict[str, AssistantReply] = {}
        self._order: list[str] = []
        self._next_failure: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"] | None = None
        self.calls = 0

    def fail_next(self, kind: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"]) -> None:
        with self._lock:
            self._next_failure = kind

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
            not request_id or len(request_id) > 100 or not model_id or len(model_id) > 200
            or not prompt_revision or len(prompt_revision) > 200 or not 1 <= len(question) <= 2000
            or len(evidence) > 20 or len(tool_facts) > 4 or not 1 <= max_input_tokens <= 12000
            or not 128 <= max_output_tokens <= 2000 or not 1 <= deadline_seconds <= 20
        ):
            raise ValueError("ASSISTANT_PROVIDER_BOUND_INVALID")
        material = "\n".join([model_id, prompt_revision, question, *(f"{sid}:{body}" for sid, body in evidence), *(f"tool:{name}:{fact}" for name, fact in tool_facts)])
        digest = hashlib.sha256(material.encode("utf-8")).hexdigest()
        input_tokens = (len(material) + 2) // 3
        if input_tokens > max_input_tokens:
            raise ValueError("ASSISTANT_INPUT_LIMIT")
        with self._lock:
            previous = self._replies.get(request_id)
            if previous:
                if previous.request_hash != digest:
                    raise ValueError("ASSISTANT_IDEMPOTENCY_MISMATCH")
                return previous
            failure = self._next_failure
            self._next_failure = None
            if failure == "UNAVAILABLE":
                raise ConnectionError("PROVIDER_UNAVAILABLE") from None
            # The mock only quotes vetted evidence. It cannot obey instructions in
            # user text or invent facts when retrieval returns no sources.
            if tool_facts:
                answer = " ".join(fact for _, fact in tool_facts)[:700]
            elif not evidence:
                answer = "I could not verify this from approved information. Please use the site search or contact the team for help."
            else:
                excerpt = " ".join(evidence[0][1].split())[:700]
                answer = "Based on the approved information: " + excerpt
            output_tokens = (len(answer) + 2) // 3
            if output_tokens > max_output_tokens:
                raise ValueError("ASSISTANT_OUTPUT_LIMIT")
            reply = AssistantReply(answer, input_tokens, output_tokens, digest)
            self._replies[request_id] = reply
            self._order.append(request_id)
            self.calls += 1
            while len(self._order) > self.max_entries:
                self._replies.pop(self._order.pop(0), None)
            if failure == "UNKNOWN_AFTER_ACCEPTANCE":
                raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
            return reply

    def reconcile(self, request_id: str) -> AssistantReply | None:
        with self._lock:
            return self._replies.get(request_id)
