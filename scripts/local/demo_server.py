#!/usr/bin/env python3
"""Serve the static API demo and proxy only to local Nigson owner services."""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


ROOT = Path(__file__).resolve().parents[2]
WEB_ROOT = ROOT / "docs" / "demo"
HOST = os.environ.get("DEMO_HOST", "127.0.0.1")
PORT = int(os.environ.get("DEMO_PORT", "8090"))
if HOST not in {"127.0.0.1", "localhost"} or not 1024 <= PORT <= 65535:
    raise SystemExit("The demo server must bind to localhost on an unprivileged port.")

PORTS = {
    "commerce": int(os.environ.get("COMMERCE_PORT", "8081")),
    "experience": int(os.environ.get("EXPERIENCE_PORT", "8082")),
    "ai": int(os.environ.get("AI_PORT", "8084")),
}
if any(not 1024 <= port <= 65535 for port in PORTS.values()):
    raise SystemExit("Owner service ports must be unprivileged local ports.")

ALLOWED_HEADERS = {
    "accept",
    "content-type",
    "if-match",
    "idempotency-key",
    "x-correlation-id",
    "x-csrf-token",
    "x-conversation-token",
}
ALLOWED_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"}
PREFIX = re.compile(r"^/proxy/(commerce|experience|integration|ai)(/.*)?$")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, _request, _response, _code, _message, _headers, _newurl):
        # OAuth and application redirects must be followed by the browser.
        return None


UPSTREAM = build_opener(NoRedirect)


class DemoHandler(BaseHTTPRequestHandler):
    server_version = "NigsonLocalDemo/1.0"
    sys_version = ""

    def log_message(self, _format: str, *_args: object) -> None:
        # Avoid logging query values, identifiers, or request payloads.
        return

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler name
        self._dispatch()

    def do_POST(self) -> None:  # noqa: N802
        self._dispatch()

    def do_PUT(self) -> None:  # noqa: N802
        self._dispatch()

    def do_PATCH(self) -> None:  # noqa: N802
        self._dispatch()

    def do_DELETE(self) -> None:  # noqa: N802
        self._dispatch()

    def _dispatch(self) -> None:
        if self.command not in ALLOWED_METHODS:
            self._json(405, {"error": "METHOD_NOT_ALLOWED"})
            return
        host = self.headers.get("Host", "").split(":", 1)[0].lower()
        if host not in {"localhost", "127.0.0.1"}:
            self._json(403, {"error": "LOCAL_HOST_REQUIRED"})
            return
        origin = self.headers.get("Origin")
        if origin and origin not in {
            f"http://localhost:{PORT}",
            f"http://127.0.0.1:{PORT}",
        }:
            self._json(403, {"error": "LOCAL_ORIGIN_REQUIRED"})
            return

        parsed = urlsplit(self.path)
        route = PREFIX.fullmatch(parsed.path)
        if route:
            forwarded = route.group(2) or "/"
            if parsed.query:
                forwarded += "?" + parsed.query
            self._proxy(route.group(1), forwarded)
            return
        if self.command != "GET":
            self._json(404, {"error": "NOT_FOUND"})
            return
        self._static(urlsplit(self.path).path)

    def _proxy(self, service: str, path: str) -> None:
        # Integration is workload-only; the browser workbench must never become
        # a route into the private provider service.
        if service == "integration":
            self._json(403, {"error": "PRIVATE_SERVICE"})
            return
        length = self.headers.get("Content-Length", "0")
        if not length.isascii() or not length.isdigit() or int(length) > 1_048_576:
            self._json(413, {"error": "BODY_TOO_LARGE"})
            return
        body = self.rfile.read(int(length)) if int(length) else None
        port = PORTS[service]
        target = f"http://127.0.0.1:{port}{path}"
        headers = {
            key: value
            for key, value in self.headers.items()
            if key.lower() in ALLOWED_HEADERS
        }
        if service == "commerce":
            cookie = self.headers.get("Cookie")
            if cookie and len(cookie) <= 8192:
                headers["Cookie"] = cookie
        try:
            upstream = Request(target, data=body, headers=headers, method=self.command)
            with UPSTREAM.open(upstream, timeout=35) as response:
                payload = response.read(4_194_305)
                self._upstream(response.status, response.headers, payload, service)
        except HTTPError as response:
            with response:
                payload = response.read(4_194_305)
                self._upstream(response.code, response.headers, payload, service)
        except (URLError, TimeoutError, OSError) as failure:
            reason = "SERVICE_UNAVAILABLE"
            if isinstance(failure, URLError) and isinstance(failure.reason, TimeoutError):
                reason = "UPSTREAM_TIMEOUT"
            self._json(502, {"error": reason, "service": service})

    def _upstream(self, status: int, headers, payload: bytes, service: str) -> None:
        if len(payload) > 4_194_304:
            self._json(502, {"error": "RESPONSE_TOO_LARGE"})
            return
        self.send_response(status)
        for name in (
            "Content-Type",
            "Content-Disposition",
            "Cache-Control",
            "ETag",
            "X-Correlation-ID",
            "Location",
        ):
            value = headers.get(name)
            if value and "\r" not in value and "\n" not in value:
                self.send_header(name, value)
        if service == "commerce":
            for value in headers.get_all("Set-Cookie", []):
                if "\r" not in value and "\n" not in value:
                    self.send_header("Set-Cookie", value)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        if payload and self.command != "HEAD":
            self.wfile.write(payload)

    def _static(self, path: str) -> None:
        if path == "/local-guide":
            target = ROOT / "docs" / "LOCAL_APPLICATION_GUIDE.md"
        else:
            relative = "index.html" if path in {"", "/"} else path.lstrip("/")
            target = (WEB_ROOT / relative).resolve()
        if target != ROOT / "docs" / "LOCAL_APPLICATION_GUIDE.md" and WEB_ROOT.resolve() not in target.parents and target != WEB_ROOT.resolve():
            self._json(404, {"error": "NOT_FOUND"})
            return
        if not target.is_file():
            self._json(404, {"error": "NOT_FOUND"})
            return
        content_type = "text/html; charset=utf-8" if target.suffix == ".html" else ("text/markdown; charset=utf-8" if target.suffix == ".md" else "application/octet-stream")
        data = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)

    def _json(self, status: int, value: dict[str, str]) -> None:
        data = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), DemoHandler)
    print(f"Nigson API demo: http://{HOST}:{PORT}")
    print("Press Ctrl-C to stop; this server only proxies loopback Commerce, Experience, and AI.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
