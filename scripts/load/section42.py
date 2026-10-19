#!/usr/bin/env python3
"""Section 42 synthetic application workload. No IDs or credentials are emitted."""
from __future__ import annotations

import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import re
import stat
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from uuid import UUID, uuid4

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = Path(os.environ.get("NIGSON_LOAD_FIXTURE", str(ROOT / ".build/load/section42-fixture.json")))
DURATION_SECONDS = 1800
SESSIONS = 100
CATALOGUE_RPS = 20
CHECKOUT_RPS = 2
UUID_FIELDS = ("marketId", "addressId", "skuId")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Metrics:
    def __init__(self):
        self.lock = threading.Lock()
        self.rows: dict[str, list[tuple[int, float]]] = {}

    def record(self, route: str, status: int, elapsed_ms: float):
        with self.lock:
            self.rows.setdefault(route, []).append((status, elapsed_ms))

    def summary(self):
        output = {}
        for route, rows in sorted(self.rows.items()):
            values = sorted(value for _, value in rows)
            percentile = lambda p: round(values[min(len(values) - 1, int((len(values) - 1) * p))], 2)
            errors = sum(status < 200 or status >= 300 for status, _ in rows)
            uncertain = sum(status == 202 for status, _ in rows)
            output[route] = {
                "requests": len(rows), "errors": errors, "uncertain_202": uncertain,
                "p50_ms": percentile(.50), "p95_ms": percentile(.95), "p99_ms": percentile(.99),
                "max_ms": round(values[-1], 2),
            }
        return output


def target_origin(raw: str) -> str:
    parsed = urlsplit(raw)
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("LOAD_TARGET_INVALID")
    local = parsed.hostname in {"127.0.0.1", "localhost", "::1"}
    private = bool(parsed.hostname and parsed.hostname.endswith(".internal"))
    if not ((parsed.scheme == "http" and local) or (parsed.scheme == "https" and (local or private))):
        raise ValueError("LOAD_TARGET_MUST_BE_LOOPBACK_OR_PRIVATE_INTERNAL_HTTPS")
    return raw.rstrip("/")


def load_fixture(path: Path) -> list[dict[str, str]]:
    resolved = path.resolve()
    build = (ROOT / ".build/load").resolve()
    if not resolved.is_relative_to(build) or not resolved.is_file():
        raise ValueError("LOAD_FIXTURE_MUST_BE_IGNORED_BUILD_FILE")
    if stat.S_IMODE(resolved.stat().st_mode) & 0o077:
        raise ValueError("LOAD_FIXTURE_PERMISSIONS_MUST_BE_0600")
    rows = json.loads(resolved.read_text())
    if not isinstance(rows, list) or len(rows) != SESSIONS:
        raise ValueError("LOAD_FIXTURE_REQUIRES_EXACTLY_100_SESSIONS")
    cookies = set()
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"cookie", "csrf", "marketCode", *UUID_FIELDS}:
            raise ValueError("LOAD_FIXTURE_SHAPE_INVALID")
        if any(not isinstance(row[k], str) or not row[k] or len(row[k]) > 4096 or "\r" in row[k] or "\n" in row[k] for k in ("cookie", "csrf")):
            raise ValueError("LOAD_FIXTURE_CREDENTIAL_INVALID")
        if len(row["csrf"]) != 43 or not re.fullmatch(r"[A-Z][A-Z0-9_-]{1,31}", row["marketCode"]):
            raise ValueError("LOAD_FIXTURE_SESSION_INVALID")
        if row["cookie"] in cookies:
            raise ValueError("LOAD_FIXTURE_SESSIONS_MUST_BE_DISTINCT")
        cookies.add(row["cookie"])
        for field in UUID_FIELDS:
            UUID(row[field])
    return rows


class Workload:
    def __init__(self, origin: str, sessions: list[dict[str, str]], metrics: Metrics):
        self.origin, self.sessions, self.metrics = origin, sessions, metrics
        self.stop = threading.Event()
        self.opener = build_opener(ProxyHandler({}), NoRedirect())
        self.failure_lock = threading.Lock()
        self.failures = 0

    def failed(self):
        with self.failure_lock:
            self.failures += 1
        self.stop.set()

    def request(self, method: str, path: str, session: dict[str, str], body: dict | None = None, etag: str | None = None) -> tuple[dict, str | None, int]:
        headers = {"Accept": "application/json", "Cookie": session["cookie"]}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if method != "GET":
            headers.update({"X-CSRF-TOKEN": session["csrf"], "Idempotency-Key": str(uuid4())})
        if etag:
            headers["If-Match"] = etag
        data = json.dumps(body, separators=(",", ":")).encode() if body is not None else None
        req = Request(self.origin + path, data=data, headers=headers, method=method)
        started = time.perf_counter()
        status = 599
        response_headers = {}
        try:
            with self.opener.open(req, timeout=10) as response:
                status = response.status
                response_headers = response.headers
                payload = response.read(1_048_577)
                if len(payload) > 1_048_576:
                    raise ValueError("LOAD_RESPONSE_TOO_LARGE")
        except HTTPError as response:
            status = response.code
            payload = b""
        except (URLError, TimeoutError, OSError, http.client.HTTPException):
            payload = b""
        elapsed = (time.perf_counter() - started) * 1000
        route = re.sub(r"/[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}(?=/|$)", "/{id}", path.split("?", 1)[0])
        self.metrics.record(route, status, elapsed)
        if status < 200 or status >= 300:
            raise RuntimeError("LOAD_HTTP_FAILURE_" + str(status))
        try:
            parsed = json.loads(payload) if payload else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise RuntimeError("LOAD_RESPONSE_INVALID") from None
        return parsed, response_headers.get("ETag"), status

    def catalogue(self, session: dict[str, str]):
        path = "/api/v1/catalogue/products?" + urlencode({"market": session["marketCode"], "page": 0, "size": 20})
        self.request("GET", path, session)

    def checkout(self, session: dict[str, str]):
        cart, etag, _ = self.request("POST", "/api/v1/carts", session, {"marketId": session["marketId"]})
        cart_id = cart.get("id")
        if not cart_id or not etag:
            raise RuntimeError("LOAD_CART_ACK_INVALID")
        _, etag, _ = self.request("PUT", f"/api/v1/carts/{cart_id}/lines/{session['skuId']}", session, {"quantity": "1"}, etag)
        quote, etag, _ = self.request("POST", f"/api/v1/carts/{cart_id}/delivery-quotes", session, {"addressId": session["addressId"]}, etag)
        carrier_id = quote.get("id")
        if not carrier_id:
            raise RuntimeError("LOAD_CARRIER_QUOTE_ACK_INVALID")
        checkout_quote, _, _ = self.request("POST", "/api/v1/checkout/quotes", session, {"cartId": cart_id, "addressId": session["addressId"], "carrierQuoteId": carrier_id})
        if not checkout_quote.get("id"):
            raise RuntimeError("LOAD_CHECKOUT_QUOTE_ACK_INVALID")
        _, _, status = self.request("POST", "/api/v1/checkout/attempts", session)
        if status == 202:
            raise RuntimeError("LOAD_CHECKOUT_OUTCOME_UNKNOWN")

    def run(self, duration: int = DURATION_SECONDS):
        deadline = time.monotonic() + duration
        with concurrent.futures.ThreadPoolExecutor(max_workers=SESSIONS + CHECKOUT_RPS) as pool:
            catalogue = [pool.submit(self._catalogue_loop, index, deadline) for index in range(SESSIONS)]
            checkouts = [pool.submit(self._checkout_loop, worker, deadline) for worker in range(CHECKOUT_RPS)]
            for future in catalogue + checkouts:
                try:
                    future.result()
                except Exception:
                    self.failed()

    def _catalogue_loop(self, index: int, deadline: float):
        session = self.sessions[index]
        due = time.monotonic() + index / CATALOGUE_RPS
        while due < deadline and not self.stop.is_set():
            delay = due - time.monotonic()
            if delay > 0:
                self.stop.wait(delay)
            if self.stop.is_set():
                return
            try:
                self.catalogue(session)
            except RuntimeError:
                self.failed()
                return
            due += SESSIONS / CATALOGUE_RPS

    def _checkout_loop(self, worker: int, deadline: float):
        index = worker
        due = time.monotonic() + worker / CHECKOUT_RPS
        while due < deadline and not self.stop.is_set():
            delay = due - time.monotonic()
            if delay > 0:
                self.stop.wait(delay)
            if self.stop.is_set():
                return
            try:
                self.checkout(self.sessions[index % SESSIONS])
            except RuntimeError:
                self.failed()
                return
            index += CHECKOUT_RPS
            due += 1


def main():
    origin = target_origin(os.environ.get("NIGSON_LOAD_TARGET", ""))
    environment = os.environ.get("NIGSON_LOAD_ENV", "test")
    target = urlsplit(origin)
    if environment == "test" and target.hostname not in {"127.0.0.1", "localhost", "::1"}:
        raise ValueError("TEST_LOAD_REQUIRES_LOOPBACK_TARGET")
    if environment == "stage" and not (target.scheme == "https" and target.hostname and target.hostname.endswith(".internal")):
        raise ValueError("STAGE_LOAD_REQUIRES_PRIVATE_HTTPS_TARGET")
    if environment not in {"test", "stage"}:
        raise ValueError("LOAD_ENVIRONMENT_MUST_BE_TEST_OR_STAGE")
    sessions = load_fixture(FIXTURE)
    workload = Workload(origin, sessions, Metrics())
    started = time.monotonic()
    workload.run()
    elapsed = time.monotonic() - started
    measurements = workload.metrics.summary()
    report = {"environment": environment, "duration_seconds": round(elapsed, 2), "target_requests_per_second": {"catalogue": CATALOGUE_RPS, "checkout_flows": CHECKOUT_RPS}, "observed_requests_per_second": {"catalogue": round(measurements.get("/api/v1/catalogue/products", {}).get("requests", 0) / max(elapsed, 0.001), 3), "checkout_flows": round(measurements.get("/api/v1/checkout/attempts", {}).get("requests", 0) / max(elapsed, 0.001), 3)}, "workload_failures": workload.failures, "measurements": measurements}
    print(json.dumps(report, sort_keys=True))
    if workload.failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
