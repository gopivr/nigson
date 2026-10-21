"""Local demo proxy allowlist and header boundary tests."""

from __future__ import annotations

from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
from pathlib import Path
import threading
from urllib.error import HTTPError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("nigson_demo_server", ROOT / "scripts/local/demo_server.py")
assert SPEC and SPEC.loader
demo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(demo)


class Upstream(BaseHTTPRequestHandler):
    seen: list[tuple[str, dict[str, str]]] = []

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def do_GET(self) -> None:  # noqa: N802
        type(self).seen.append((self.path, dict(self.headers.items())))
        if self.path == "/redirect":
            self.send_response(302)
            self.send_header("Location", "https://identity.example.invalid/authorize")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        payload = b'{"status":"UP"}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


import unittest


class DemoProxyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.old_port = demo.PORT
        cls.old_commerce_port = demo.PORTS["commerce"]
        cls.upstream = ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        cls.upstream_thread = threading.Thread(target=cls.upstream.serve_forever, daemon=True)
        cls.upstream_thread.start()
        cls.proxy = ThreadingHTTPServer(("127.0.0.1", 0), demo.DemoHandler)
        demo.PORT = cls.proxy.server_port
        demo.PORTS["commerce"] = cls.upstream.server_port
        cls.proxy_thread = threading.Thread(target=cls.proxy.serve_forever, daemon=True)
        cls.proxy_thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.proxy.shutdown()
        cls.upstream.shutdown()
        cls.proxy.server_close()
        cls.upstream.server_close()
        demo.PORT = cls.old_port
        demo.PORTS["commerce"] = cls.old_commerce_port

    def test_loopback_proxy_forwards_query_and_safe_headers_but_not_origin_or_identity(self):
        Upstream.seen.clear()
        request = Request(
            f"http://127.0.0.1:{self.proxy.server_port}/proxy/commerce/api/v1/markets?market=NG",
            headers={
                "Origin": f"http://127.0.0.1:{self.proxy.server_port}",
                "X-Correlation-ID": "correlation-demo",
                "Cookie": "nigson-local-session=synthetic-session",
                "Authorization": "Bearer should-not-forward",
                "X-Nigson-Actor": "should-not-forward",
            },
        )
        with urlopen(request, timeout=3) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b'{"status":"UP"}')
        path, received = Upstream.seen[-1]
        headers = {name.lower(): value for name, value in received.items()}
        self.assertEqual(path, "/api/v1/markets?market=NG")
        self.assertEqual(headers["x-correlation-id"], "correlation-demo")
        self.assertEqual(headers["cookie"], "nigson-local-session=synthetic-session")
        self.assertNotIn("authorization", headers)
        self.assertNotIn("x-nigson-actor", headers)
        self.assertNotIn("origin", headers)


    def test_private_integration_route_and_foreign_browser_origin_are_rejected(self):
        for path, origin, status in (
            ("/proxy/integration/internal/v1/jobs", None, 403),
            ("/proxy/commerce/api/v1/markets", "https://unexpected.example", 403),
        ):
            headers = {"Origin": origin} if origin else {}
            with_url = Request(f"http://127.0.0.1:{self.proxy.server_port}{path}", headers=headers)
            try:
                urlopen(with_url, timeout=3)
            except HTTPError as response:
                with response:
                    self.assertEqual(response.code, status)
            else:
                self.fail(f"Expected {status} for {path}")

    def test_upstream_redirect_is_returned_to_the_browser(self):
        connection = HTTPConnection("127.0.0.1", self.proxy.server_port, timeout=3)
        try:
            connection.request("GET", "/proxy/commerce/redirect")
            response = connection.getresponse()
            self.assertEqual(response.status, 302)
            self.assertEqual(response.getheader("Location"), "https://identity.example.invalid/authorize")
            response.read()
        finally:
            connection.close()
