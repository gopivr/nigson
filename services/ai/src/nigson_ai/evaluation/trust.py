"""Verification of the shared ES256 private actor assertion contract."""

from __future__ import annotations

import base64
import hashlib
import json
import re
import time
import uuid
from typing import Any

import jwt
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_der_public_key
from fastapi import Request
from jwt.utils import base64url_decode

from nigson_ai.platform.problem import Failure
from nigson_ai.platform.telemetry import correlation


def _strict_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON member")
        result[key] = value
    return result


def parse_public_keys(raw: str) -> dict[str, ec.EllipticCurvePublicKey]:
    try:
        if not raw or len(raw) > 8192:
            raise ValueError
        values = json.loads(raw, object_pairs_hook=_strict_pairs)
        if not isinstance(values, dict) or not 1 <= len(values) <= 4:
            raise ValueError
        result: dict[str, ec.EllipticCurvePublicKey] = {}
        for kid, encoded in values.items():
            if not re.fullmatch(r"[A-Za-z0-9_-]{1,40}", kid) or not isinstance(
                encoded, str
            ):
                raise ValueError
            key = load_der_public_key(base64.b64decode(encoded, validate=True))
            if (
                not isinstance(key, ec.EllipticCurvePublicKey)
                or key.curve.name != "secp256r1"
            ):
                raise ValueError
            result[kid] = key
        return result
    except (ValueError, TypeError, json.JSONDecodeError):
        raise ValueError("ACTOR_PUBLIC_KEYS_REQUIRED") from None


def verify_admin_actor(request: Request, operation: str) -> dict[str, Any]:
    try:
        headers = [
            value.decode("latin1")
            for name, value in request.scope.get("headers", [])
            if name.lower() == b"x-nigson-actor"
        ]
        if len(headers) != 1 or len(headers[0]) > 8192:
            raise ValueError
        token = headers[0]
        parts = token.split(".")
        if len(parts) != 3 or any(not part for part in parts):
            raise ValueError
        header = json.loads(
            base64url_decode(parts[0].encode()), object_pairs_hook=_strict_pairs
        )
        payload = json.loads(
            base64url_decode(parts[1].encode()), object_pairs_hook=_strict_pairs
        )
        if (
            not isinstance(header, dict)
            or set(header) != {"alg", "kid", "typ"}
            or not isinstance(payload, dict)
            or len(payload) > 18
        ):
            raise ValueError
        if (
            header.get("alg") != "ES256"
            or header.get("typ") != "nigson-actor+jwt"
            or not isinstance(header.get("kid"), str)
        ):
            raise ValueError
        keys = getattr(request.app.state, "ai_actor_public_keys", {})
        key = keys.get(header["kid"]) if isinstance(keys, dict) else None
        if key is None:
            raise ValueError
        claims = jwt.decode(
            token,
            key,
            algorithms=["ES256"],
            audience="ai",
            issuer="nigson-commerce-bff",
            options={"require": ["iss", "aud", "sub", "iat", "exp", "jti"]},
            leeway=0,
        )
        now = int(time.time())
        required = {
            "iss",
            "aud",
            "sub",
            "identity_issuer",
            "identity_subject",
            "roles",
            "scopes",
            "operation",
            "http_method",
            "resource",
            "body_sha256",
            "correlation_id",
            "iat",
            "exp",
            "jti",
        }
        if (
            not required <= set(claims)
            or set(claims) - (required | {"organization_id", "mfa_at"})
            or set(payload) != set(claims)
        ):
            raise ValueError
        if claims["aud"] not in ("ai", ["ai"]):
            raise ValueError
        if (
            type(claims["iat"]) is not int
            or type(claims["exp"]) is not int
            or claims["iat"] > now + 5
            or claims["exp"] <= now
            or claims["exp"] > claims["iat"] + 60
        ):
            raise ValueError
        if (
            claims["operation"] != operation
            or claims["http_method"] != request.method
            or claims["resource"] != request.url.path
        ):
            raise ValueError
        body_hash = hashlib.sha256(
            request.scope.get("nigson.raw_body", b"")
        ).hexdigest()
        if claims["body_sha256"] != body_hash:
            raise ValueError
        request_correlation = correlation.get()
        if request_correlation is None or claims["correlation_id"] != str(
            request_correlation
        ):
            raise ValueError
        actor_id = uuid.UUID(claims["sub"])
        if actor_id.int == 0 or uuid.UUID(claims["jti"]).int == 0:
            raise ValueError
        allowed_issuers: frozenset[str] = getattr(
            request.app.state, "ai_actor_identity_issuers", frozenset()
        )
        if (
            claims["identity_issuer"] not in allowed_issuers
            or not isinstance(claims["identity_subject"], str)
            or not 1 <= len(claims["identity_subject"]) <= 200
        ):
            raise ValueError
        roles, scopes = claims["roles"], claims["scopes"]
        allowed_roles = {
            "CUSTOMER",
            "ADMIN",
            "CONTENT_EDITOR",
            "CONTENT_PUBLISHER",
            "SALES",
            "OPERATIONS",
            "FINANCE",
            "ANALYST",
        }
        if (
            not isinstance(roles, list)
            or not 1 <= len(roles) <= 10
            or any(
                not isinstance(role, str) or role not in allowed_roles for role in roles
            )
        ):
            raise ValueError
        if (
            not isinstance(scopes, list)
            or len(scopes) > 64
            or any(
                not isinstance(scope, str)
                or not re.fullmatch(r"[A-Za-z0-9:._-]{1,100}", scope)
                for scope in scopes
            )
        ):
            raise ValueError
        if (
            "ADMIN" not in roles
            or operation not in scopes
            or claims.get("organization_id") is not None
        ):
            raise ValueError
        mfa_at = claims.get("mfa_at")
        if type(mfa_at) is not int or mfa_at > now + 5 or now - mfa_at >= 900:
            raise ValueError
        return {
            "sub": str(actor_id),
            "identity_subject": claims["identity_subject"],
            "roles": roles,
            "scopes": scopes,
        }
    except (
        ValueError,
        TypeError,
        KeyError,
        jwt.PyJWTError,
        json.JSONDecodeError,
        RecursionError,
    ):
        raise Failure("ACCESS_DENIED") from None
