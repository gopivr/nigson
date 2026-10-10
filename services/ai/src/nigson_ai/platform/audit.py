"""AI-owned append-only evidence; no raw forms, prompts, tokens or free-text reasons."""

import hashlib
import hmac
import ipaddress
import json
import re
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from psycopg import Connection
from psycopg.pq import TransactionStatus
from psycopg.types.json import Jsonb

from .events import uuid7
from .problem import Failure

STATES = frozenset(
    json.loads(Path(__file__).with_name("audit_states.json").read_text())
)
CURRENCIES = frozenset(
    json.loads(Path(__file__).with_name("currencies.json").read_text())
)
KINDS = {"USER", "SERVICE", "SYSTEM"}
ACTIONS = {
    "NOTIFICATION_DELIVERY", "NOTIFICATION_TEMPLATE",
    "FILE_UPLOAD", "FILE_COMPLETION", "FILE_SCAN_RESULT", "FILE_PROMOTION_REQUEST", "FILE_TRANSFER_UNKNOWN",
    "PRODUCT_CREATE", "PRODUCT_UPDATE", "SKU_CREATE", "SKU_UPDATE", "TAXONOMY_CREATE", "TAXONOMY_UPDATE",
    "SESSION_LOGIN", "SESSION_LOGOUT", "PROFILE_UPDATE",
    "ADDRESS_CREATE",
    "ADDRESS_UPDATE",
    "ADDRESS_DELETE",
    "PARTY_PROVISION",
    "STAFF_ROLE_GRANT",
    "STAFF_ROLE_REVOKE",
    "ENTITLEMENT_APPROVAL",
    "ENTITLEMENT_SUSPENSION",
    "PRICE_ACTIVATION",
    "PROMOTION_ACTIVATION",
    "STOCK_LOCATION_CREATE",
    "STOCK_TRANSITION",
    "MARKET_CREATE",
    "MARKET_BOOK",
    "MARKET_ELIGIBILITY",
    "STOCK_ADJUSTMENT",
    "REFUND_REQUEST",
    "REFUND_APPROVAL",
    "REFUND_VERIFICATION",
    "QUOTE_OVERRIDE",
    "CONTENT_PUBLICATION",
    "CONTENT_WITHDRAWAL",
    "MODERATION",
    "PRIVATE_FILE_DOWNLOAD",
    "APPLICANT_EXPORT",
    "FEATURE_ACTIVATION",
    "QUEUE_REPLAY",
    "AI_POLICY_ACTIVATION",
}
RESOURCES = {
    "NOTIFICATION",
    "PRODUCT", "SKU", "BRAND", "CATEGORY",
    "SESSION", "PARTY",
    "PROFILE",
    "ADDRESS",
    "STAFF_ROLE",
    "ENTITLEMENT",
    "PRICE_BOOK",
    "PROMOTION",
    "INVENTORY",
    "MARKET",
    "REFUND",
    "QUOTE",
    "CONTENT",
    "REVIEW",
    "FILE",
    "CAREER_APPLICATION",
    "FEATURE",
    "QUEUE",
    "AI_POLICY",
}
REASONS = {
    "OPERATOR_REQUEST",
    "VERIFIED_PROVIDER",
    "RECONCILIATION",
    "PUBLICATION_REVIEW",
    "SECURITY_POLICY",
    "CONFIGURATION_CHANGE",
}
ROLES = {
    "CUSTOMER",
    "CONTENT_EDITOR",
    "CONTENT_PUBLISHER",
    "SALES",
    "OPERATIONS",
    "FINANCE",
    "ANALYST",
    "ADMIN",
    "SERVICE_EXPERIENCE",
    "SERVICE_INTEGRATION",
    "SERVICE_AI",
    "SERVICE_COMMERCE",
    "OWNER",
    "BUYER",
}


def redact(values: dict[str, Any] | None) -> dict[str, Any] | None:
    if values is None:
        return None
    if len(values) > 100:
        raise Failure("VALIDATION_ERROR")
    output: dict[str, Any] = {}
    omitted = False
    for key, value in values.items():
        if key in {"version", "revision"}:
            valid = type(value) is int and 0 <= value <= 9223372036854775807
        elif key == "enabled":
            valid = type(value) is bool
        elif key in {"amount", "quantity", "currency"}:
            pattern = {
                "amount": r"-?[0-9]{1,15}(\.[0-9]{1,4})?",
                "quantity": r"[0-9]{1,17}(\.[0-9]{1,3})?",
                "currency": r"[A-Z]{3}",
            }[key]
            valid = isinstance(value, str) and bool(re.fullmatch(pattern, value))
            if valid and key == "currency":
                valid = value in CURRENCIES
            if valid and key == "quantity":
                from decimal import Decimal

                valid = Decimal(value) > 0
        elif key in {"state", "role", "channel", "providerMode"}:
            allowed = {
                "state": STATES,
                "role": ROLES,
                "channel": {"EMAIL", "SMS", "WHATSAPP"},
                "providerMode": {"MOCK", "TEST", "LIVE"},
            }[key]
            valid = isinstance(value, str) and value in allowed
        else:
            omitted = True
            continue
        if not valid:
            raise Failure("VALIDATION_ERROR")
        output[key] = value
    if omitted:
        output["redacted"] = True
    return output


def ip_fingerprint(key: bytes, address: str) -> str:
    if (
        len(key) < 32
        or len(address) > 45
        or not re.fullmatch(r"[0-9a-fA-F:.]+", address)
    ):
        raise Failure("VALIDATION_ERROR")
    try:
        packed = ipaddress.ip_address(address).packed
    except ValueError as failure:
        raise Failure("VALIDATION_ERROR") from failure
    return hmac.new(key, packed, hashlib.sha256).hexdigest()


class AuditWriter:
    def append(
        self,
        connection: Connection[Any],
        *,
        subject: str,
        kind: str,
        action: str,
        resource_type: str,
        resource_id: uuid.UUID,
        correlation_id: uuid.UUID,
        reason: str | None = None,
        old_values: dict[str, Any] | None = None,
        new_values: dict[str, Any] | None = None,
        ip_hash: str | None = None,
    ) -> uuid.UUID:
        if connection.info.transaction_status != TransactionStatus.INTRANS:
            raise RuntimeError("LOCAL_TRANSACTION_REQUIRED")
        if (
            not subject
            or len(subject) > 200
            or not subject.isascii()
            or any(ord(c) < 32 or ord(c) > 126 for c in subject)
            or kind not in KINDS
            or action not in ACTIONS
            or resource_type not in RESOURCES
            or not resource_id.int
            or not correlation_id.int
            or (reason is not None and reason not in REASONS)
            or (ip_hash is not None and not re.fullmatch(r"[a-f0-9]{64}", ip_hash))
        ):
            raise Failure("VALIDATION_ERROR")
        identifier = uuid7()
        old, new = redact(old_values), redact(new_values)
        connection.execute(
            "INSERT INTO ai.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,ip_hash,reason,old_value,new_value) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (
                identifier,
                subject,
                subject,
                kind,
                action,
                resource_type,
                resource_id,
                datetime.now(UTC),
                correlation_id,
                ip_hash,
                reason,
                Jsonb(old) if old is not None else None,
                Jsonb(new) if new is not None else None,
            ),
        )
        return identifier
