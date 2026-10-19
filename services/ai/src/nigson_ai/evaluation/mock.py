"""Deterministic, stateful local evaluator. It never calls a model provider."""

from __future__ import annotations

import hashlib
import threading
from typing import Any


class LocalEvaluationModel:
    """A deterministic local substitute with bounded calls and failure injection."""

    def __init__(self, max_cases: int = 500):
        self.max_cases = max_cases
        self.calls = 0
        self.fail_next: str | None = None
        self._lock = threading.Lock()

    def evaluate(self, case: dict[str, Any], policy_revision: str) -> dict[str, Any]:
        with self._lock:
            if self.fail_next:
                failure, self.fail_next = self.fail_next, None
                raise RuntimeError(failure)
            self.calls += 1
            # The mock uses only declared synthetic expectations. It does not infer
            # correctness from prompts, call a remote model, or expose input text.
            fingerprint = hashlib.sha256(
                (case["id"] + "\0" + policy_revision).encode()
            ).hexdigest()
            return {
                "grounded": bool(case["requiredSourceIds"]),
                "scopeLeak": False,
                "injectionMutation": False,
                "latencyMs": 1 + int(fingerprint[:2], 16) % 3,
                "intentMatch": True,
                "escalationMatch": True,
            }

    def run(
        self, cases: list[dict[str, Any]], policy_revision: str
    ) -> list[dict[str, Any]]:
        if len(cases) > self.max_cases:
            raise ValueError("EVALUATION_BOUND")
        return [self.evaluate(case, policy_revision) for case in cases]
