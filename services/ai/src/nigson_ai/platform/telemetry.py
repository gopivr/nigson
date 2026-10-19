"""One owned SDK, safe spans before batching, bounded metrics and wire context."""

import contextvars
import json
import logging
import os
import re
import uuid
from collections.abc import Iterator, Sequence
from contextlib import contextmanager
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

import httpx
from opentelemetry import trace
from opentelemetry.context import Context
from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import MetricReader, PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import (
    ReadableSpan,
    SpanLimits,
    SpanProcessor,
    TracerProvider,
)
from opentelemetry.sdk.trace.export import (
    BatchSpanProcessor,
    SpanExporter,
)
from opentelemetry.sdk.trace.sampling import ALWAYS_ON
from opentelemetry.trace import Link, SpanKind, Status, StatusCode
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from .problem import CODES

correlation: contextvars.ContextVar[uuid.UUID | None] = contextvars.ContextVar(
    "correlation", default=None
)
TRACE_PATTERN = re.compile(
    r"00-(?!0{32}-)[a-f0-9]{32}-(?!0{16}-)[a-f0-9]{16}-[a-f0-9]{2}"
)
ROUTES = tuple(json.loads(Path(__file__).with_name("routes.json").read_text()))
PATTERNS = tuple(
    (
        route,
        re.compile(
            "^" + re.sub(r"\\\{[^}]+\\\}", "[^/]{1,200}", re.escape(route)) + "$"
        ),
    )
    for route in ROUTES
)
METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
DB_OPERATIONS = {"SELECT", "INSERT", "UPDATE", "DELETE", "OTHER"}


def route_name(path: str) -> str:
    if len(path) > 2048:
        return "UNKNOWN"
    return next(
        (route for route, pattern in PATTERNS if pattern.fullmatch(path)), "UNKNOWN"
    )


def parent(value: str | None) -> Context:
    if value is None or not TRACE_PATTERN.fullmatch(value):
        return Context()
    return TraceContextTextMapPropagator().extract(
        {"traceparent": value}, context=Context()
    )


class SafeFormatter(logging.Formatter):
    def __init__(self, environment: str):
        super().__init__()
        self.environment = environment

    def format(self, record: logging.LogRecord) -> str:
        context = trace.get_current_span().get_span_context()
        identifier = correlation.get()
        safe = (
            record.msg
            if isinstance(record.msg, str)
            and record.msg
            in {
                "REQUEST_COMPLETE",
                "REQUEST_FAILED",
                "OUTBOX_RELAY_UNAVAILABLE",
                "EVENT_POLL_UNAVAILABLE",
                "AUDIT_PERSISTED",
            }
            else "OPERATOR_EVENT"
        )
        code = getattr(record, "error_code", None)
        return json.dumps(
            {
                "timestamp": datetime.fromtimestamp(record.created, UTC)
                .isoformat()
                .replace("+00:00", "Z"),
                "severity": record.levelname,
                "service": "nigson-ai",
                "environment": self.environment,
                "version": "0.1.0",
                "trace_id": f"{context.trace_id:032x}" if context.is_valid else None,
                "span_id": f"{context.span_id:016x}" if context.is_valid else None,
                "correlation_id": str(identifier) if identifier else None,
                "error_code": code if isinstance(code, str) and code in CODES else None,
                "message": safe,
            }
        )


def safe_span(span: ReadableSpan) -> ReadableSpan:
    source = span.attributes or {}
    attributes: dict[str, Any] = {}
    method = source.get("http.request.method", source.get("http.method"))
    route = source.get("http.route")
    status = source.get("http.response.status_code", source.get("http.status_code"))
    if isinstance(method, str) and method in METHODS:
        attributes["http.request.method"] = method
    if isinstance(route, str) and route in ROUTES:
        attributes["http.route"] = route
    if type(status) is int and 100 <= status <= 599:
        attributes["http.response.status_code"] = status
    database = source.get("db.system.name", source.get("db.system"))
    if database == "postgresql":
        attributes["db.system.name"] = database
        operation = span.name.split(" ", 1)[0].upper()
        attributes["db.operation.name"] = (
            operation if operation in DB_OPERATIONS else "OTHER"
        )
    for key, allowed in {
        "messaging.system": {"aws_sqs", "aws_sns"},
        "messaging.operation.name": {"process", "publish"},
    }.items():
        if isinstance(source.get(key), str) and source.get(key) in allowed:
            attributes[key] = source[key]
    event = source.get("nigson.event_type")
    from .events import REGISTRY

    if isinstance(event, str) and event in REGISTRY:
        attributes["nigson.event_type"] = event
    if source.get("nigson.priority") is True:
        attributes["nigson.priority"] = True
    name = (
        "db.execute"
        if database == "postgresql"
        else {
            SpanKind.SERVER: "http.request",
            SpanKind.CLIENT: "http.client",
            SpanKind.CONSUMER: "messaging.process",
            SpanKind.PRODUCER: "messaging.publish",
        }.get(span.kind, "operation")
    )
    return ReadableSpan(
        name=name,
        context=span.context,
        parent=span.parent,
        resource=span.resource,
        attributes=attributes,
        events=(),
        links=tuple(Link(link.context) for link in span.links),
        kind=span.kind,
        status=Status(span.status.status_code),
        start_time=span.start_time,
        end_time=span.end_time,
        instrumentation_scope=span.instrumentation_scope,
    )


class PrivacyProcessor(BatchSpanProcessor):
    def on_end(self, span: ReadableSpan) -> None:
        super().on_end(safe_span(span))


class QueryMetricsProcessor(SpanProcessor):
    def __init__(self, histogram: Any):
        self.histogram = histogram

    def on_end(self, span: ReadableSpan) -> None:
        cleaned = safe_span(span)
        if (
            cleaned.name == "db.execute"
            and cleaned.end_time is not None
            and cleaned.start_time is not None
        ):
            self.histogram.record(
                (cleaned.end_time - cleaned.start_time) / 1e9,
                {
                    "owner": "ai",
                    "operation": (cleaned.attributes or {}).get(
                        "db.operation.name", "OTHER"
                    ),
                },
            )


class Telemetry:
    def __init__(
        self,
        environment: str,
        endpoint: str = "",
        exporter: SpanExporter | None = None,
        metric_reader: MetricReader | None = None,
    ):
        if endpoint:
            target = urlsplit(endpoint)
            if (
                not target.hostname
                or target.username
                or target.query
                or target.fragment
                or target.path not in {"", "/"}
            ):
                raise ValueError("TELEMETRY_TARGET_INVALID")
            if environment in {"local", "test"}:
                if target.scheme != "http" or target.hostname not in {
                    "127.0.0.1",
                    "otel-collector",
                }:
                    raise ValueError("LOCAL_TELEMETRY_TARGET_INVALID")
            elif not (
                (
                    target.scheme == "http"
                    and target.hostname == "127.0.0.1"
                    and target.port == 4318
                )
                or (
                    target.scheme == "https"
                    and target.hostname not in {"127.0.0.1", "localhost"}
                    and not target.hostname.endswith(".example")
                )
            ) or not os.environ.get("NIGSON_TRACE_POLICY_ID"):
                raise ValueError("TELEMETRY_POLICY_REQUIRED")
            exporter = OTLPSpanExporter(
                endpoint=endpoint.rstrip("/") + "/v1/traces", timeout=3
            )
        resource = Resource(
            {
                "service.name": "nigson-ai",
                "deployment.environment.name": environment,
                "service.version": "0.1.0",
            }
        )
        readers: list[MetricReader] = []
        if metric_reader is not None:
            readers.append(metric_reader)
        if endpoint:
            readers.append(
                PeriodicExportingMetricReader(
                    OTLPMetricExporter(
                        endpoint=endpoint.rstrip("/") + "/v1/metrics", timeout=3
                    ),
                    export_interval_millis=10000,
                    export_timeout_millis=3000,
                )
            )
        self.metric_provider = MeterProvider(resource=resource, metric_readers=readers)
        self.meter = self.metric_provider.get_meter("nigson-ai", "0.1.0")
        self.http_count = self.meter.create_counter("nigson.http.requests")
        self.http_errors = self.meter.create_counter("nigson.http.errors")
        self.http_timer = self.meter.create_histogram(
            "nigson.http.server.requests", unit="s"
        )
        self.query_timer = self.meter.create_histogram("nigson.db.query", unit="s")
        self.ai_gateway_operations = self.meter.create_counter("nigson.ai.gateway.commands")
        self.ai_budget_reservations = self.meter.create_counter("nigson.ai.budget.reservations")
        self.ai_provider_outcomes = self.meter.create_counter("nigson.ai.provider.outcomes")
        self.ai_knowledge_operations = self.meter.create_counter("nigson.ai.knowledge.operations")
        self.ai_assistant_operations = self.meter.create_counter("nigson.ai.assistant.operations")
        self.provider = TracerProvider(
            sampler=ALWAYS_ON,
            resource=Resource(
                {
                    "service.name": "nigson-ai",
                    "deployment.environment.name": environment,
                    "service.version": "0.1.0",
                }
            ),
            span_limits=SpanLimits(
                max_attributes=16, max_events=0, max_links=8, max_attribute_length=128
            ),
        )
        self.provider.add_span_processor(QueryMetricsProcessor(self.query_timer))
        if exporter is not None:
            self.provider.add_span_processor(
                PrivacyProcessor(
                    exporter,
                    max_queue_size=2048,
                    max_export_batch_size=256,
                    schedule_delay_millis=1000,
                    export_timeout_millis=3000,
                )
            )
        self.tracer = self.provider.get_tracer("nigson-ai", "0.1.0")
        self.http_errors.add(0, {"owner": "ai"})

    @contextmanager
    def messaging(
        self,
        identifier: uuid.UUID,
        upstream: str | None,
        event: str,
        publish: bool = False,
    ) -> Iterator[trace.Span]:
        from .events import REGISTRY

        if event not in REGISTRY:
            raise ValueError("EVENT_TYPE_UNREGISTERED")
        token = correlation.set(identifier)
        remote = trace.get_current_span(parent(upstream)).get_span_context()
        try:
            with self.tracer.start_as_current_span(
                "messaging.publish" if publish else "messaging.process",
                context=Context(),
                kind=SpanKind.PRODUCER if publish else SpanKind.CONSUMER,
                links=[Link(remote)] if remote.is_valid else [],
                attributes={
                    "messaging.system": "aws_sns" if publish else "aws_sqs",
                    "messaging.operation.name": "publish" if publish else "process",
                    "nigson.event_type": event,
                },
                record_exception=False,
                set_status_on_exception=False,
            ) as span:
                try:
                    yield span
                except Exception:
                    span.set_status(Status(StatusCode.ERROR))
                    raise
        finally:
            correlation.reset(token)

    def instrument_connection(self, connection: Any) -> None:
        from opentelemetry.instrumentation.psycopg import PsycopgInstrumentor

        PsycopgInstrumentor.instrument_connection(
            connection,
            tracer_provider=self.provider,
            enable_commenter=False,
            enable_attribute_commenter=False,
        )

    def observe_pools(self, pools: Sequence[Any]) -> None:
        from opentelemetry.metrics import Observation

        def stats() -> tuple[int, int, int, int]:
            snapshots = [pool.get_stats() for pool in pools]
            maximum = sum(row.get("pool_max", 0) for row in snapshots)
            active = sum(
                row.get("pool_size", 0) - row.get("pool_available", 0)
                for row in snapshots
            )
            waiting = sum(row.get("requests_waiting", 0) for row in snapshots)
            errors = sum(row.get("requests_errors", 0) for row in snapshots)
            return maximum, active, waiting, errors

        def utilization(_: Any) -> list[Observation]:
            maximum, active, _, _ = stats()
            return [
                Observation(
                    active / maximum if maximum else float("nan"), {"owner": "ai"}
                )
            ]

        def active(_: Any) -> list[Observation]:
            return [Observation(stats()[1], {"owner": "ai"})]

        def waiting(_: Any) -> list[Observation]:
            return [Observation(stats()[2], {"owner": "ai"})]

        def errors(_: Any) -> list[Observation]:
            return [Observation(stats()[3], {"owner": "ai"})]

        for name, callback in (
            ("utilization", utilization),
            ("active", active),
            ("waiting", waiting),
            ("acquisition.errors", errors),
        ):
            self.meter.create_observable_gauge(
                "nigson.db.pool." + name, callbacks=[callback]
            )

    def httpx_client(self) -> httpx.Client:
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor

        def headers(request: httpx.Request) -> None:
            identifier = correlation.get()
            if identifier:
                request.headers["X-Correlation-ID"] = str(identifier)

        client = httpx.Client(
            timeout=httpx.Timeout(8, connect=2, pool=2),
            limits=httpx.Limits(max_connections=10, max_keepalive_connections=5),
            follow_redirects=False,
            event_hooks={"request": [headers]},
        )
        HTTPXClientInstrumentor.instrument_client(client, tracer_provider=self.provider)
        return client

    def close(self) -> None:
        self.provider.shutdown()
        self.metric_provider.shutdown()
