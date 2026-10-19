import importlib.util
import json
from pathlib import Path
import unittest
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("section42", ROOT / "scripts/load/section42.py")
section42 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(section42)


class Section42Safety(unittest.TestCase):
    def test_target_allows_only_loopback_or_private_https(self):
        self.assertEqual(section42.target_origin("http://127.0.0.1:8080/"), "http://127.0.0.1:8080")
        self.assertEqual(section42.target_origin("https://commerce.stage.internal"), "https://commerce.stage.internal")
        for target in ("https://example.com", "http://commerce.stage.internal", "https://user:pass@commerce.stage.internal", "https://commerce.stage.internal/path"):
            with self.subTest(target=target), self.assertRaises(ValueError):
                section42.target_origin(target)

    def test_fixture_requires_exact_shape_distinct_sessions_and_private_permissions(self):
        build = ROOT / ".build/load"
        build.mkdir(parents=True, exist_ok=True)
        path = build / ("section42-test-" + str(uuid4()) + ".json")
        rows = [{"cookie": f"NIGSONSESSION=test-{n}", "csrf": "x" * 43, "marketCode": "NG", "marketId": str(uuid4()), "addressId": str(uuid4()), "skuId": str(uuid4())} for n in range(100)]
        try:
            path.write_text(json.dumps(rows))
            path.chmod(0o600)
            self.assertEqual(len(section42.load_fixture(path)), 100)
            rows[1]["cookie"] = rows[0]["cookie"]
            path.write_text(json.dumps(rows))
            path.chmod(0o600)
            with self.assertRaisesRegex(ValueError, "DISTINCT"):
                section42.load_fixture(path)
            path.chmod(0o644)
            with self.assertRaisesRegex(ValueError, "PERMISSIONS"):
                section42.load_fixture(path)
        finally:
            path.unlink(missing_ok=True)

    def test_metrics_are_aggregate_only_and_report_percentiles(self):
        metrics = section42.Metrics()
        metrics.record("/api/v1/carts/{id}/delivery-quotes", 200, 10)
        metrics.record("/api/v1/carts/{id}/delivery-quotes", 503, 20)
        result = metrics.summary()["/api/v1/carts/{id}/delivery-quotes"]
        self.assertEqual((result["requests"], result["errors"], result["p50_ms"]), (2, 1, 10))
        self.assertNotIn("cookie", json.dumps(result))

    def test_checkout_flow_uses_registered_routes_and_stops_on_unknown(self):
        seen = []
        checkout_status = {"value": 201}
        class Response:
            def __init__(self, status, value, etag=None):
                self.status, self.headers = status, {"ETag": etag} if etag else {}
                self.payload = json.dumps(value).encode()
            def __enter__(self): return self
            def __exit__(self, *_): return False
            def read(self, _): return self.payload

        class Opener:
            def open(self, req, timeout):
                path = req.full_url.split("127.0.0.1", 1)[1].split("?", 1)[0]
                method = req.get_method()
                seen.append((method, path, dict(req.header_items())))
                if path == "/api/v1/carts": return Response(201, {"id": str(uuid4())}, '"0"')
                if method == "PUT": return Response(200, {}, '"1"')
                if path.endswith("/delivery-quotes"): return Response(201, {"id": str(uuid4())})
                if path == "/api/v1/checkout/quotes": return Response(201, {"id": str(uuid4())})
                if path == "/api/v1/checkout/attempts": return Response(checkout_status["value"], {"payment": {"state": "PENDING"}})
                if method == "GET": return Response(200, {"items": []})
                return Response(404, {})

        session = {"cookie": "NIGSONSESSION=synthetic", "csrf": "x" * 43, "marketCode": "NG", "marketId": str(uuid4()), "addressId": str(uuid4()), "skuId": str(uuid4())}
        metrics = section42.Metrics()
        client = section42.Workload("http://127.0.0.1", [session] * 100, metrics)
        client.opener = Opener()
        client.checkout(session)
        client.catalogue(session)
        self.assertEqual([method for method, _, _ in seen], ["POST", "PUT", "POST", "POST", "POST", "GET"])
        line = next(headers for method, _, headers in seen if method == "PUT")
        self.assertEqual(line["If-match"], '"0"')
        self.assertEqual(line["X-csrf-token"], "x" * 43)
        self.assertNotIn(session["cookie"], json.dumps(metrics.summary()))
        checkout_status["value"] = 202
        with self.assertRaisesRegex(RuntimeError, "OUTCOME_UNKNOWN"):
            client.checkout(session)
        self.assertEqual(sum(1 for method, path, _ in seen if path == "/api/v1/checkout/attempts"), 2)


if __name__ == "__main__":
    unittest.main()
