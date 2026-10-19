"""Static read-only tool allowlist and stateful owner-checked local adapter."""

from __future__ import annotations

import hashlib
import threading
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ToolResult:
    value: dict[str, Any]
    argument_hash: str


class LocalOwnerTools:
    """Local/test fixture adapter; production must inject signed owner HTTP ports."""

    ALLOWED = frozenset({"search_catalog", "get_product_details", "get_available_price", "get_order_status"})

    def __init__(self, max_entries: int = 512) -> None:
        self.max_entries = max_entries
        self._lock = threading.Lock()
        self._orders: dict[tuple[str, str], dict[str, Any]] = {}
        self._catalog: dict[str, dict[str, Any]] = {}
        self.calls = 0

    def seed_order(self, party_id: str, order_id: str, status: str, tracking: str | None = None) -> None:
        if status not in {"PENDING", "PAID", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "HOLD"}:
            raise ValueError("TOOL_FIXTURE_INVALID")
        with self._lock:
            self._orders[(party_id, order_id)] = {"status": status, "tracking": tracking}

    def seed_product(self, product_id: str, market: str, title: str, revision: int) -> None:
        with self._lock:
            self._catalog[product_id] = {"id": product_id, "market": market, "title": title, "revision": revision}

    def invoke(self, tool_name: str, arguments: dict[str, Any], *, party_id: str | None, market: str) -> ToolResult | None:
        if tool_name not in self.ALLOWED or len(arguments) > 4:
            raise ValueError("TOOL_NOT_ALLOWED")
        digest = hashlib.sha256(__import__("json").dumps(arguments, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        with self._lock:
            self.calls += 1
            if tool_name == "get_order_status":
                order_id = arguments.get("orderId")
                if not party_id or not isinstance(order_id, str):
                    return None
                row = self._orders.get((party_id, order_id))
                return ToolResult({"status": row["status"], "tracking": row["tracking"]}, digest) if row else None
            if tool_name == "get_product_details":
                product = self._catalog.get(str(arguments.get("productId")))
                if not product or product["market"] != market:
                    return None
                return ToolResult({"id": product["id"], "title": product["title"], "revision": product["revision"]}, digest)
            # Search and live price results require Commerce-owned fixtures and
            # never fabricate an eligibility or price result.
            return None
