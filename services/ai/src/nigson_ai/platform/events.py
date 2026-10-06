"""Closed wire contracts derived from the authoritative event registry."""

import hashlib
import json
import re
import secrets
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

REGISTRY = json.loads(Path(__file__).with_name("registry.json").read_text())


def uuid7() -> uuid.UUID:
    millis = int(datetime.now(UTC).timestamp() * 1000)
    return uuid.UUID(
        int=(millis << 80)
        | (0x7 << 76)
        | (secrets.randbits(12) << 64)
        | (0b10 << 62)
        | secrets.randbits(62)
    )


def validate(value: Any, schema: dict[str, Any]) -> None:
    if "anyOf" in schema:
        for branch in schema["anyOf"]:
            try:
                validate(value, branch)
                return
            except ValueError:
                pass
        raise ValueError("EVENT_FIELD_INVALID")
    kind = schema.get("type")
    types = {
        "object": dict,
        "array": list,
        "string": str,
        "integer": int,
        "boolean": bool,
        "null": type(None),
    }
    if kind in types and type(value) is not types[kind]:
        raise ValueError("EVENT_FIELD_TYPE_INVALID")
    if "const" in schema and value != schema["const"]:
        raise ValueError("EVENT_CONSTANT_INVALID")
    if "enum" in schema and value not in schema["enum"]:
        raise ValueError("EVENT_ENUM_INVALID")
    if kind == "object":
        properties = schema["properties"]
        if not set(schema["required"]).issubset(value) or set(value) - set(properties):
            raise ValueError("EVENT_FIELDS_INVALID")
        for key, child in value.items():
            validate(child, properties[key])
    if kind == "array":
        if len(value) > schema.get("maxItems", 100):
            raise ValueError("EVENT_ARRAY_TOO_LARGE")
        for child in value:
            validate(child, schema["items"])
    if kind == "string":
        if (
            not schema.get("minLength", 0)
            <= len(value)
            <= schema.get("maxLength", 65536)
        ):
            raise ValueError("EVENT_STRING_INVALID")
        if "pattern" in schema and re.fullmatch(schema["pattern"], value) is None:
            raise ValueError("EVENT_STRING_INVALID")
        if schema.get("format") == "date-time":
            try:
                parsed = datetime.fromisoformat(value)
                if parsed.tzinfo is None:
                    raise ValueError("timezone required")
            except ValueError:
                raise ValueError("EVENT_TIME_INVALID") from None
    if kind == "integer" and not schema.get("minimum", -(2**63)) <= value <= schema.get(
        "maximum", 2**63 - 1
    ):
        raise ValueError("EVENT_INTEGER_INVALID")


def decode(body: str) -> dict[str, Any]:
    if len(body.encode()) > 65536:
        raise ValueError("EVENT_SIZE_INVALID")

    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("EVENT_DUPLICATE_FIELD")
            result[key] = value
        return result

    value = json.loads(body, object_pairs_hook=unique)
    if not isinstance(value, dict) or value.get("event_type") not in REGISTRY:
        raise ValueError("EVENT_TYPE_UNKNOWN")
    validate(value, REGISTRY[value["event_type"]]["schema"])
    if (
        value["event_type"] == "file.scan.requested.v1"
        and value["producer"] != value["payload"]["owner_service"]
    ):
        raise ValueError("EVENT_OWNER_MISMATCH")
    return value


def subscribed(event: dict[str, Any], release: int) -> bool:
    contract = REGISTRY[event["event_type"]]
    return release == 5 and "ai" in contract["consumers"]


def request_hash(request: Any) -> str:
    def finite(value: Any) -> Any:
        if isinstance(value, dict):
            return {k: finite(v) for k, v in value.items()}
        if isinstance(value, list):
            return [finite(v) for v in value]
        if isinstance(value, float) and value.is_integer():
            return int(value)
        return value

    body = json.dumps(
        finite(request),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
        allow_nan=False,
    ).encode()
    if len(body) > 65536:
        raise ValueError("REQUEST_SIZE_INVALID")
    return hashlib.sha256(body).hexdigest()
