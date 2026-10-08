"""Actual ASGI/SDK privacy and parity checks; real DB/HTTP checks use isolated fixtures."""

import json
import logging
import uuid

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from opentelemetry.sdk.metrics.export import InMemoryMetricReader
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter
from opentelemetry.trace import SpanKind, StatusCode
from pydantic import Field
from starlette.exceptions import HTTPException

from nigson_ai.platform.audit import ip_fingerprint, redact
from nigson_ai.platform.boundary import install
from nigson_ai.platform.problem import Failure, ProblemWriter, StrictModel
from nigson_ai.platform.telemetry import SafeFormatter, Telemetry, correlation


class Body(StrictModel):
    value: str = Field(min_length=1, max_length=20)


def test_actual_asgi_problems_body_bounds_correlation_and_bounded_metrics():
    exporter = InMemorySpanExporter()
    metrics = InMemoryMetricReader()
    telemetry = Telemetry("test", exporter=exporter, metric_reader=metrics)
    problems = ProblemWriter("test")
    app = FastAPI()
    install(app, lambda: telemetry, lambda: problems)

    @app.post("/internal/v1/tool-test")
    def command(body: Body) -> None:
        raise HTTPException(403, detail="private@example.invalid secret-value")

    @app.get("/internal/v1/error-test")
    def crashed() -> None:
        raise RuntimeError("SELECT private_token FROM protected_table")

    identifier = uuid.uuid4()
    parent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00"
    try:
        with TestClient(app) as client:
            result = client.post(
                "/internal/v1/tool-test",
                json={"value": "valid"},
                headers={"X-Correlation-ID": str(identifier), "traceparent": parent},
            )
            assert result.status_code == 403
            assert result.headers["content-type"] == "application/problem+json"
            assert result.headers["x-correlation-id"] == str(identifier)
            body = result.json()
            assert body["code"] == "ACCESS_DENIED"
            assert body["traceId"] == "0123456789abcdef0123456789abcdef"
            assert (
                "secret-value" not in result.text
                and "private@example" not in result.text
            )
            invalid = client.post(
                "/internal/v1/tool-test",
                json={"value": "", "private@example.invalid": "secret"},
            )
            assert invalid.status_code == 400
            assert "private@example" not in invalid.text
            assert {v["field"] for v in invalid.json()["violations"]} <= {"value", "$"}
            assert (
                client.post(
                    "/internal/v1/tool-test", content=b"x" * 1048577
                ).status_code
                == 413
            )
            error = client.get("/internal/v1/error-test")
            assert error.status_code == 500
            assert "SELECT" not in error.text and "protected_table" not in error.text
            for _ in range(30):
                unknown = client.get(
                    "/private-" + str(uuid.uuid4()),
                    headers={
                        "X-Correlation-ID": "private@example.invalid",
                        "traceparent": "bad",
                    },
                )
                assert unknown.status_code == 404
                assert uuid.UUID(unknown.headers["x-correlation-id"]).int
            telemetry.provider.force_flush()
            spans = exporter.get_finished_spans()
            assert any(
                span.parent
                and span.parent.span_id == int("0123456789abcdef", 16)
                and span.status.status_code is StatusCode.ERROR
                for span in spans
            )
            assert "private@example" not in str(spans)
            data = metrics.get_metrics_data()
            assert data is not None
            http = next(
                metric
                for resource in data.resource_metrics
                for scope in resource.scope_metrics
                for metric in scope.metrics
                if metric.name == "nigson.http.server.requests"
            )
            # All fixture paths are absent from the production contract, hence UNKNOWN.
            assert all(
                point.attributes["route"] == "UNKNOWN"
                for point in http.data.data_points
            )
            assert len(http.data.data_points) <= 5
    finally:
        telemetry.close()
    assert correlation.get() is None


def test_redacted_audit_values_and_safe_structured_logs():
    values = redact(
        {
            "version": 1,
            "enabled": True,
            "state": "PAID",
            "token": "private-token",
            "email": "private@example.invalid",
        }
    )
    assert values == {"version": 1, "enabled": True, "state": "PAID", "redacted": True}
    for invalid in (
        {"amount": 1.5},
        {"quantity": "0"},
        {"role": "PAID"},
        {"state": "private@example.invalid"},
    ):
        with pytest.raises(Failure):
            redact(invalid)
    with pytest.raises(Failure):
        ip_fingerprint(b"x" * 32, "example.com")
    assert len(ip_fingerprint(b"x" * 32, "127.0.0.1")) == 64
    record = logging.LogRecord(
        "private",
        logging.ERROR,
        "secret_path",
        1,
        "private@example.invalid token=%s",
        ("secret-token",),
        None,
    )
    log = SafeFormatter("test").format(record)
    assert "private@example" not in log and "secret-token" not in log
    assert set(json.loads(log)) == {
        "timestamp",
        "severity",
        "service",
        "environment",
        "version",
        "trace_id",
        "span_id",
        "correlation_id",
        "error_code",
        "message",
    }


def test_consumer_starts_new_linked_span_and_redacts_private_trace_attributes():
    exporter = InMemorySpanExporter()
    telemetry = Telemetry("test", exporter=exporter)
    try:
        identifier = uuid.uuid4()
        with telemetry.messaging(
            identifier,
            "00-0123456789abcdef0123456789abcdef-0123456789abcdef-00",
            "catalogue.product.changed.v1",
        ) as span:
            assert correlation.get() == identifier
            span.set_attribute(
                "db.statement", "SELECT private_token FROM protected_table"
            )
            span.set_attribute("http.url", "https://private.invalid/?token=secret")
        telemetry.provider.force_flush()
        span = exporter.get_finished_spans()[0]
        assert span.parent is None
        assert span.context.trace_id != int("0123456789abcdef0123456789abcdef", 16)
        assert span.links[0].context.trace_id == int(
            "0123456789abcdef0123456789abcdef", 16
        )
        assert (
            "db.statement" not in span.attributes and "http.url" not in span.attributes
        )
        assert correlation.get() is None
    finally:
        telemetry.close()


def test_real_httpx_transport_propagates_child_trace_without_exporting_urls():
    import threading
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    received = []

    class Receiver(BaseHTTPRequestHandler):
        def do_GET(self):
            received.append(dict(self.headers))
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Receiver)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    exporter = InMemorySpanExporter()
    sdk = Telemetry("test", exporter=exporter)
    identifier = uuid.uuid4()
    try:
        with (
            sdk.messaging(identifier, None, "catalogue.product.changed.v1") as root,
            sdk.httpx_client() as client,
        ):
            assert (
                client.get(
                    f"http://127.0.0.1:{server.server_port}/private@example.invalid?token=private-token"
                ).status_code
                == 200
            )
        sdk.provider.force_flush()
        spans = exporter.get_finished_spans()
        child = next(s for s in spans if s.kind == SpanKind.CLIENT)
        assert child.parent.span_id == root.get_span_context().span_id
        assert received[0]["X-Correlation-ID"] == str(identifier)
        assert (
            received[0]["traceparent"]
            == f"00-{child.context.trace_id:032x}-{child.context.span_id:016x}-{child.context.trace_flags:02x}"
        )
        assert all(
            "private" not in str(s.attributes) and "url" not in str(s.attributes)
            for s in spans
        )
    finally:
        sdk.close()
        server.shutdown()
        server.server_close()
        thread.join()


def test_wire_vocabulary_and_structural_validation_parity():
    from pathlib import Path

    from nigson_ai.platform.problem import CODES
    from nigson_ai.platform.values import (
        idempotency_key,
        json_shape,
        money,
        page,
        quantity,
        version,
    )

    root = Path(__file__).resolve().parents[3]
    contract = json.loads((root / "contracts/problem/codes.json").read_text())
    assert contract == {
        code: {"status": status, "title": title, "detail": detail}
        for code, (status, title, detail) in CODES.items()
    }
    assert json.loads(
        (root / "contracts/problem/routes.json").read_text()
    ) == json.loads(
        (root / "services/ai/src/nigson_ai/platform/routes.json").read_text()
    )
    for body in (
        b'{"a":1,"a":2}',
        b'{"a":NaN}',
        ("[" + "0," * 100 + "0]").encode(),
        ("[" * 34 + "0" + "]" * 34).encode(),
    ):
        with pytest.raises(Failure):
            json_shape(body)
    json_shape(("[" + "0," * 100 + "0]").encode(), 500)
    for call in (
        lambda: money("1.23456"),
        lambda: quantity("0"),
        lambda: idempotency_key("key\nprivate"),
        lambda: version('W/"2"'),
        lambda: page(0, 101),
    ):
        with pytest.raises(Failure):
            call()


def test_real_python_otlp_protobuf_transport_and_alarm_metric_names():
    import gzip
    import threading
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    from opentelemetry.proto.collector.metrics.v1.metrics_service_pb2 import (
        ExportMetricsServiceRequest,
    )
    from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import (
        ExportTraceServiceRequest,
    )

    received = {}

    class Receiver(BaseHTTPRequestHandler):
        def do_POST(self):
            data = self.rfile.read(int(self.headers["Content-Length"]))
            if self.headers.get("Content-Encoding") == "gzip":
                data = gzip.decompress(data)
            received[self.path] = data
            self.send_response(200)
            self.end_headers()

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Receiver)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    sdk = Telemetry("test", f"http://127.0.0.1:{server.server_port}")
    try:
        with sdk.messaging(uuid.uuid4(), None, "catalogue.product.changed.v1") as span:
            span.set_attribute(
                "http.url", "https://private.invalid?token=private-token"
            )
            span.set_attribute("db.statement", "SELECT private@example.invalid")
            sdk.http_count.add(2, {"owner": "ai"})
            sdk.http_errors.add(1, {"owner": "ai"})
        assert sdk.provider.force_flush()
        assert sdk.metric_provider.force_flush()
        traces = ExportTraceServiceRequest.FromString(received["/v1/traces"])
        metrics = ExportMetricsServiceRequest.FromString(received["/v1/metrics"])
        names = {
            metric.name
            for resource in metrics.resource_metrics
            for scope in resource.scope_metrics
            for metric in scope.metrics
        }
        assert {"nigson.http.requests", "nigson.http.errors"} <= names
        assert "private" not in str(traces) and "db.statement" not in str(traces)
        assert (
            sum(
                len(scope.spans)
                for resource in traces.resource_spans
                for scope in resource.scope_spans
            )
            == 1
        )
    finally:
        sdk.close()
        server.shutdown()
        server.server_close()
        thread.join()
