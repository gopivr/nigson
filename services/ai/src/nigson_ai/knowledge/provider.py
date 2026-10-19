"""Bounded deterministic test provider for source and embedding acceptance."""

from __future__ import annotations

import hashlib
import math
import threading
from dataclasses import dataclass
from typing import Literal


@dataclass(frozen=True)
class PublicSource:
    source_service: str
    resource_id: str
    source_revision: int
    source_type: str
    locale: str
    market_code: str
    title: str
    body: str
    source_url: str
    checksum: str
    approved: bool = True
    current: bool = True
    visibility: str = "PUBLIC"


class LocalKnowledgeProvider:
    """Stateful source/embedding mock. It is never registered outside local/test."""

    def __init__(self, max_entries: int = 256) -> None:
        if not 1 <= max_entries <= 4096:
            raise ValueError("MOCK_BOUND_INVALID")
        self.max_entries = max_entries
        self._lock = threading.Lock()
        self._sources: dict[tuple[str, str], PublicSource] = {}
        self._vectors: dict[str, tuple[str, tuple[float, ...]]] = {}
        self._vector_order: list[str] = []
        self._next_failure: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"] | None = None
        self.calls = 0

    def publish(self, source: PublicSource) -> None:
        key = (source.source_service, source.resource_id)
        with self._lock:
            self._sources[key] = source

    def withdraw(self, source_service: str, resource_id: str) -> None:
        key = (source_service, resource_id)
        with self._lock:
            source = self._sources.get(key)
            if source is not None:
                self._sources[key] = PublicSource(
                    **{**source.__dict__, "approved": False, "current": False}
                )

    def source(self, source_service: str, resource_id: str) -> PublicSource | None:
        with self._lock:
            return self._sources.get((source_service, resource_id))

    def fetch(self, source_service: str, resource_id: object) -> PublicSource | None:
        return self.source(source_service, str(resource_id))

    def is_current(self, source: PublicSource) -> bool:
        current = self.source(source.source_service, source.resource_id)
        return bool(
            current
            and current.approved
            and current.current
            and current.visibility == "PUBLIC"
            and current.source_revision == source.source_revision
            and current.checksum == source.checksum
        )

    def fail_next(
        self, kind: Literal["UNAVAILABLE", "UNKNOWN_AFTER_ACCEPTANCE"]
    ) -> None:
        with self._lock:
            self._next_failure = kind

    def embed(
        self,
        request_id: str,
        text: str,
        *,
        dimensions: int = 1024,
        deadline_seconds: int = 20,
    ) -> tuple[float, ...]:
        if (
            not request_id
            or len(request_id) > 180
            or not text
            or len(text) > 12000
            or dimensions != 1024
            or not 1 <= deadline_seconds <= 20
        ):
            raise ValueError("EMBEDDING_BOUND_INVALID")
        digest = hashlib.sha256(text.encode()).hexdigest()
        with self._lock:
            previous = self._vectors.get(request_id)
            if previous:
                if previous[0] != digest:
                    raise ValueError("PROVIDER_IDEMPOTENCY_MISMATCH")
                return previous[1]
            failure = self._next_failure
            self._next_failure = None
            if failure == "UNAVAILABLE":
                raise ConnectionError("PROVIDER_UNAVAILABLE") from None
            raw = bytearray()
            block = digest.encode()
            counter = 0
            while len(raw) < dimensions * 4:
                raw.extend(hashlib.sha512(block + counter.to_bytes(4, "big")).digest())
                counter += 1
            values = [((int.from_bytes(raw[i : i + 4], "big") / 2**32) * 2) - 1 for i in range(0, dimensions * 4, 4)]
            norm = math.sqrt(sum(value * value for value in values))
            vector = tuple(value / norm for value in values)
            self._vectors[request_id] = (digest, vector)
            self._vector_order.append(request_id)
            self.calls += 1
            while len(self._vector_order) > self.max_entries:
                old = self._vector_order.pop(0)
                self._vectors.pop(old, None)
            if failure == "UNKNOWN_AFTER_ACCEPTANCE":
                raise TimeoutError("PROVIDER_OUTCOME_UNKNOWN") from None
            return vector

    def reconcile_embedding(self, request_id: str) -> tuple[float, ...] | None:
        with self._lock:
            result = self._vectors.get(request_id)
            return result[1] if result else None
