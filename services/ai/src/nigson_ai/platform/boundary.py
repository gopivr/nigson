"""Bounded ASGI requests and identical safe errors. No business/authentication bypass."""

import logging
import time
import uuid
from collections.abc import Callable
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from opentelemetry import trace
from opentelemetry.trace import SpanKind, Status, StatusCode
from psycopg import IntegrityError
from pydantic import BaseModel
from starlette.exceptions import HTTPException
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from .problem import Failure, ProblemWriter, nonnil, safe_field
from .store import Conflict
from .telemetry import METHODS, Telemetry, correlation, parent, route_name
from .values import json_shape

LOG = logging.getLogger("nigson-ai.boundary")


def classify(failure: Exception) -> str:
    if isinstance(failure, Failure):
        return failure.code
    if isinstance(failure, RequestValidationError):
        return "VALIDATION_ERROR"
    if isinstance(failure, IntegrityError):
        return "CONFLICT"
    if isinstance(failure, Conflict):
        return (
            str(failure)
            if str(failure) in {"IDEMPOTENCY_HASH_MISMATCH", "IDEMPOTENCY_IN_PROGRESS"}
            else "CONFLICT"
        )
    if isinstance(failure, HTTPException):
        return {
            400: "VALIDATION_ERROR",
            401: "AUTH_REQUIRED",
            403: "ACCESS_DENIED",
            404: "NOT_FOUND",
            405: "METHOD_NOT_ALLOWED",
            412: "VERSION_MISMATCH",
            415: "UNSUPPORTED_MEDIA_TYPE",
            422: "BUSINESS_RULE_VIOLATION",
            429: "RATE_LIMITED",
            503: "PROVIDER_UNAVAILABLE",
        }.get(failure.status_code, "INTERNAL_ERROR")
    return "INTERNAL_ERROR"


class RequestBoundary:
    def __init__(
        self,
        app: ASGIApp,
        telemetry: Callable[[], Telemetry],
        problems: Callable[[], ProblemWriter],
    ):
        self.app, self.telemetry, self.problems = app, telemetry, problems

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = scope.get("headers", [])

        def single(key: bytes) -> str | None:
            values = [v.decode("latin1") for k, v in headers if k.lower() == key]
            return values[0] if len(values) == 1 else None

        try:
            identifier = nonnil(single(b"x-correlation-id") or "")
        except Failure:
            identifier = uuid.uuid4()
        sdk = self.telemetry()
        route = route_name(scope["path"])
        method = scope["method"] if scope["method"] in METHODS else "OTHER"
        token = correlation.set(identifier)
        started = time.monotonic()
        with sdk.tracer.start_as_current_span(
            "http.request",
            context=parent(single(b"traceparent")),
            kind=SpanKind.SERVER,
            attributes={
                "http.route": route,
                "http.request.method": method,
                "nigson.priority": route.startswith("/api/v1/checkout/"),
            },
            record_exception=False,
            set_status_on_exception=False,
        ) as span:
            status = 500
            sent = False

            async def safe_send(message: Message) -> None:
                nonlocal status, sent
                if message["type"] == "http.response.start":
                    status = (
                        message["status"] if 100 <= message["status"] <= 599 else 500
                    )
                    output = [
                        (k, v)
                        for k, v in message.get("headers", [])
                        if k.lower() not in {b"x-correlation-id"}
                    ]
                    output.append((b"x-correlation-id", str(identifier).encode()))
                    if not any(k.lower() == b"cache-control" for k, _ in output):
                        output.append((b"cache-control", b"no-store"))
                    message = {**message, "headers": output}
                    sent = True
                await send(message)

            try:
                limit = (
                    262144 if scope["path"].startswith("/api/v1/webhooks/") else 1048576
                )
                data = bytearray()
                while True:
                    message = await receive()
                    if message["type"] == "http.disconnect":
                        status = 499
                        return
                    chunk = message.get("body", b"")
                    if len(data) + len(chunk) > limit:
                        raise Failure("BODY_TOO_LARGE")
                    data.extend(chunk)
                    if not message.get("more_body", False):
                        break
                if (
                    data
                    and (single(b"content-type") or "").split(";", 1)[0].lower()
                    == "application/json"
                    and not scope["path"].startswith("/api/v1/webhooks/")
                ):
                    json_shape(
                        bytes(data),
                        500
                        if scope["path"] == "/api/v1/staff/ai/evaluation-suites"
                        else 100,
                    )
                consumed = False

                async def replay() -> Message:
                    nonlocal consumed
                    if consumed:
                        return await receive()
                    consumed = True
                    return {
                        "type": "http.request",
                        "body": bytes(data),
                        "more_body": False,
                    }

                await self.app(scope, replay, safe_send)
            except Exception as failure:  # noqa: BLE001 - safe final HTTP boundary never exposes exception text
                code = classify(failure)
                if not sent:
                    await self.problems().response(Request(scope), code)(
                        scope, receive, safe_send
                    )
                LOG.warning("REQUEST_FAILED", extra={"error_code": code})
            finally:
                span.set_attribute("http.response.status_code", status)
                if status >= 400:
                    span.set_status(Status(StatusCode.ERROR))
                sdk.http_timer.record(
                    time.monotonic() - started,
                    {
                        "owner": "ai",
                        "route": route,
                        "method": method,
                        "status": str(status),
                    },
                )
                if scope["path"].startswith(("/api/", "/internal/", "/bff/")):
                    sdk.http_count.add(1, {"owner": "ai"})
                    if status >= 500:
                        sdk.http_errors.add(1, {"owner": "ai"})
                LOG.info("REQUEST_COMPLETE")
                correlation.reset(token)


def install(
    application: FastAPI,
    sdk: Callable[[], Telemetry],
    writer: Callable[[], ProblemWriter],
) -> None:
    async def failed(request: Request, failure: Exception) -> Any:
        violations: list[dict[str, str]] = []
        if isinstance(failure, RequestValidationError):
            route = request.scope.get("route")
            field = getattr(route, "body_field", None)
            annotation = getattr(getattr(field, "field_info", None), "annotation", None)
            model = (
                annotation
                if isinstance(annotation, type) and issubclass(annotation, BaseModel)
                else None
            )
            violations = [
                {
                    "field": safe_field(model, tuple(e["loc"])),
                    "code": "INVALID_VALUE",
                    "message": "Supply a valid value.",
                }
                for e in failure.errors()[:100]
            ]
        code = classify(failure)
        trace.get_current_span().set_status(Status(StatusCode.ERROR))
        LOG.warning("REQUEST_FAILED", extra={"error_code": code})
        return writer().response(request, code, violations)

    for exception in (Failure, RequestValidationError, HTTPException, Exception):
        application.add_exception_handler(exception, failed)
    application.add_middleware(RequestBoundary, telemetry=sdk, problems=writer)
