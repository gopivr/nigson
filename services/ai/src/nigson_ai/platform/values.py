"""Protocol value and structural bounds; domain policies and activation stay owned."""

import json
import re
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from typing import Any

from .problem import Failure


def invalid() -> Failure:
    return Failure("VALIDATION_ERROR")


def money(value: str, nonnegative: bool = False) -> Decimal:
    if not isinstance(value, str) or not re.fullmatch(
        ("" if nonnegative else "-?") + r"[0-9]{1,15}(\.[0-9]{1,4})?", value
    ):
        raise invalid()
    return Decimal(value)


def quantity(value: str) -> Decimal:
    if (
        not isinstance(value, str)
        or not re.fullmatch(r"[0-9]{1,17}(\.[0-9]{1,3})?", value)
        or Decimal(value) <= 0
    ):
        raise invalid()
    return Decimal(value)


def idempotency_key(value: str) -> str:
    if (
        not isinstance(value, str)
        or not value.strip()
        or not re.fullmatch(r"[\x20-\x7e]{1,100}", value)
    ):
        raise invalid()
    return value


def version(value: str) -> int:
    if (
        not isinstance(value, str)
        or not re.fullmatch(r'"(0|[1-9][0-9]{0,18})"', value)
        or int(value[1:-1]) > 9223372036854775807
    ):
        raise invalid()
    return int(value[1:-1])


def instant(value: str) -> datetime:
    try:
        if not isinstance(value, str) or len(value) > 40:
            raise invalid()
        parsed = datetime.fromisoformat(value)
        if parsed.tzinfo is None:
            raise invalid()
        return parsed.astimezone(UTC)
    except ValueError as failure:
        raise invalid() from failure


def page(number: int, size: int) -> None:
    if (
        type(number) is not int
        or type(size) is not int
        or number < 0
        or not 1 <= size <= 100
    ):
        raise invalid()


def report_window(start: datetime, end: datetime) -> None:
    if (
        start.tzinfo is None
        or end.tzinfo is None
        or start > end
        or end - start > timedelta(days=366)
    ):
        raise invalid()


def query(values: dict[str, str], allowed: set[str]) -> None:
    if (
        len(values) > 20
        or not set(values) <= allowed
        or any(not isinstance(v, str) or len(v) > 200 for v in values.values())
    ):
        raise invalid()


def json_shape(body: bytes, array_limit: int = 100) -> None:
    if not 1 <= array_limit <= 500:
        raise invalid()

    def closed_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        output: dict[str, Any] = {}
        for key, value in pairs:
            if key in output or len(output) >= 100:
                raise invalid()
            output[key] = value
        return output

    def no_constant(_: str) -> Any:
        raise invalid()

    try:
        value = json.loads(
            body,
            object_pairs_hook=closed_pairs,
            parse_float=Decimal,
            parse_constant=no_constant,
        )
    except (ValueError, RecursionError) as failure:
        raise invalid() from failure
    pending = [(value, 0)]
    while pending:
        item, depth = pending.pop()
        if depth > 32 or (isinstance(item, list) and len(item) > array_limit):
            raise invalid()
        if isinstance(item, dict):
            pending.extend((child, depth + 1) for child in item.values())
        elif isinstance(item, list):
            pending.extend((child, depth + 1) for child in item)
