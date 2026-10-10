"""Closed RFC9457 values; only trusted schemas may name validation fields."""

import re
import uuid
from datetime import UTC, datetime
from typing import Any
from urllib.parse import urlsplit

from fastapi import Request
from pydantic import BaseModel, ConfigDict
from starlette.responses import JSONResponse


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


# Identical safe vocabulary to the shared Java wire contract.
CODES: dict[str, tuple[int, str, str]] = {
    "VALIDATION_ERROR": (400, "Invalid request", "Supply valid request fields."),
    "AUTH_REQUIRED": (
        401,
        "Authentication required",
        "Authenticate before continuing.",
    ),
    "ACCESS_DENIED": (403, "Access denied", "This operation is not permitted."),
    "NOT_FOUND": (404, "Not found", "The requested resource is unavailable."),
    "CONFLICT": (409, "Conflict", "The operation conflicts with current state."),
    "IDEMPOTENCY_HASH_MISMATCH": (
        409,
        "Conflicting request",
        "Use the original request with this key.",
    ),
    "IDEMPOTENCY_IN_PROGRESS": (
        409,
        "Request in progress",
        "Retry this request after the current operation finishes.",
    ),
    "VERSION_MISMATCH": (
        412,
        "Version mismatch",
        "Refresh the resource before retrying.",
    ),
    "PROVIDER_RULE_VIOLATION": (
        422,
        "Provider rule violation",
        "The provider operation does not meet current rules.",
    ),
    "PROVIDER_MODE_MISMATCH": (
        422,
        "Provider mode mismatch",
        "The configured provider target does not match this environment.",
    ),
    "FILES_RULE_VIOLATION": (
        422,
        "File rule violation",
        "The file does not meet its purpose and safety requirements.",
    ),
    "CATALOGUE_RULE_VIOLATION": (
        422,
        "Catalogue rule violation",
        "The catalogue change does not meet current rules.",
    ),
    "PLATFORM_RULE_VIOLATION": (
        422,
        "Configuration rule violation",
        "The configuration does not meet registered owner rules.",
    ),
    "PARTY_RULE_VIOLATION": (
        422,
        "Party rule violation",
        "The profile or address does not meet current rules.",
    ),
    "AUTHORIZATION_RULE_VIOLATION": (
        422,
        "Authorization rule violation",
        "Retain an active administrator before removing this role.",
    ),
    "BUSINESS_RULE_VIOLATION": (
        422,
        "Ineligible operation",
        "The operation does not meet its requirements.",
    ),
    "INVALID_TRANSITION": (
        409,
        "Invalid transition",
        "The requested transition is unavailable.",
    ),
    "FEATURE_DISABLED": (422, "Feature unavailable", "This feature is not active."),
    "RATE_LIMITED": (429, "Rate limit exceeded", "Retry after the permitted interval."),
    "PROVIDER_UNAVAILABLE": (
        503,
        "Service unavailable",
        "Retry a safe read later or inspect the existing operation.",
    ),
    "PROVIDER_OUTCOME_UNKNOWN": (
        503,
        "Outcome uncertain",
        "Inspect the existing operation before another attempt.",
    ),
    "INTERNAL_ERROR": (500, "Internal error", "The request could not be completed."),
    "BODY_TOO_LARGE": (413, "Request too large", "Reduce the request size."),
    "UNSUPPORTED_MEDIA_TYPE": (
        415,
        "Unsupported content type",
        "Use the specified content type.",
    ),
    "METHOD_NOT_ALLOWED": (
        405,
        "Method not allowed",
        "Use a supported request method.",
    ),
}


class Failure(RuntimeError):
    def __init__(self, code: str):
        if code not in CODES:
            raise ValueError("ERROR_CODE_UNREGISTERED")
        super().__init__(code)
        self.code = code


def nonnil(value: str) -> uuid.UUID:
    if not re.fullmatch(r"[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}", value):
        raise Failure("VALIDATION_ERROR")
    result = uuid.UUID(value)
    if result.int == 0:
        raise Failure("VALIDATION_ERROR")
    return result


def safe_field(model: type[BaseModel] | None, location: tuple[Any, ...]) -> str:
    from typing import get_args, get_origin

    parts: list[str] = []
    current: Any = model
    for part in location:
        if part in ("body", "query", "path") and not parts:
            continue
        if isinstance(part, int):
            if (
                not parts
                or part < 0
                or part > 99
                or get_origin(current) not in (list, tuple)
            ):
                return "$"
            parts[-1] += f"[{part}]"
            current = get_args(current)[0]
        elif (
            isinstance(part, str)
            and isinstance(current, type)
            and issubclass(current, BaseModel)
            and part in current.model_fields
        ):
            parts.append(part)
            current = current.model_fields[part].annotation
        else:
            return "$"
    return ".".join(parts) if parts else "$"


class ProblemWriter:
    def __init__(self, environment: str, base: str = ""):
        if not base:
            if environment not in {"local", "test"}:
                raise ValueError("PROBLEM_TYPE_BASE_REQUIRED")
            base = "urn:nigson:problem:"
        parsed = urlsplit(base)
        if (
            not parsed.scheme
            or parsed.username
            or parsed.query
            or parsed.fragment
            or (
                environment not in {"local", "test"}
                and (
                    parsed.scheme != "https"
                    or not parsed.hostname
                    or parsed.hostname.endswith(".example")
                )
            )
        ):
            raise ValueError("PROBLEM_TYPE_BASE_INVALID")
        self.base = base if base.endswith(("/", ":")) else base + "/"

    def response(
        self,
        request: Request,
        code: str,
        violations: list[dict[str, str]] | None = None,
    ) -> JSONResponse:
        from opentelemetry.trace import get_current_span

        from .telemetry import correlation, route_name

        status, title, detail = CODES[code]
        span = get_current_span().get_span_context()
        resource = route_name(request.url.path)
        identifier = correlation.get() or uuid.uuid4()
        body = {
            "type": self.base + code.lower().replace("_", "-"),
            "title": title,
            "status": status,
            "detail": detail,
            "instance": re.sub(r"\{[^}]+\}", "_id", resource)
            if resource != "UNKNOWN"
            else "/",
            "code": code,
            "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
            "correlationId": str(identifier),
            "traceId": f"{span.trace_id:032x}" if span.is_valid else None,
            "violations": violations or [],
        }
        headers = {"X-Correlation-ID": str(identifier), "Cache-Control": "no-store"}
        if code == "AUTH_REQUIRED":
            headers["WWW-Authenticate"] = "Bearer"
        if code in {"IDEMPOTENCY_IN_PROGRESS", "RATE_LIMITED"}:
            headers["Retry-After"] = "1"
        return JSONResponse(
            body,
            status_code=status,
            media_type="application/problem+json",
            headers=headers,
        )
