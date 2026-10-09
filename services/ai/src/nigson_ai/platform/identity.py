"""BP5 Keycloak wire trust. Business permissions and signed actor trust remain owned."""

import json
import re
import ssl
import threading
import time
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlsplit

import httpx
import jwt

from .problem import Failure


@dataclass(frozen=True)
class IdentityPolicy:
    environment: str
    issuer: str
    jwks: str
    audience: str
    clients: frozenset[str]
    scopes: frozenset[str]

    def __post_init__(self) -> None:
        local = self.environment in {"local", "test"}
        issuer, keys = urlsplit(self.issuer), urlsplit(self.jwks)
        if (
            self.environment not in {"local", "test", "dev", "qa", "stage", "prod"}
            or not self.clients
            or not self.scopes
        ):
            raise ValueError("IDENTITY_POLICY_REQUIRED")
        for endpoint in (issuer, keys):
            if (
                not endpoint.hostname
                or endpoint.username
                or endpoint.password
                or endpoint.query
                or endpoint.fragment
                or not (
                    endpoint.scheme == "https"
                    or local
                    and endpoint.scheme == "http"
                    and endpoint.hostname in {"localhost", "127.0.0.1", "keycloak"}
                )
            ):
                raise ValueError("IDENTITY_TARGET_INVALID")
        if (
            not re.fullmatch(r"/realms/[a-zA-Z0-9-]{1,63}", issuer.path)
            or issuer.path == "/realms/master"
            or keys.path != issuer.path + "/protocol/openid-connect/certs"
        ):
            raise ValueError("IDENTITY_TARGET_INVALID")
        if not local and (
            issuer.hostname in {"localhost", "127.0.0.1", "keycloak"}
            or issuer.netloc != keys.netloc
            or (issuer.hostname or "").endswith((".example", ".test", ".invalid"))
        ):
            raise ValueError("APPROVED_IDENTITY_HTTPS_REQUIRED")
        if any(
            not re.fullmatch(r"[A-Za-z0-9:._-]{1,100}", x)
            for x in [self.audience, *self.clients, *self.scopes]
        ):
            raise ValueError("IDENTITY_POLICY_REQUIRED")


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    output: dict[str, Any] = {}
    for key, value in pairs:
        if key in output:
            raise ValueError("DUPLICATE_TOKEN_FIELD")
        output[key] = value
    return output


class KeycloakIdentity:
    def __init__(
        self, policy: IdentityPolicy, tls: ssl.SSLContext | bool = True
    ) -> None:
        if tls is False:
            raise ValueError("TLS_VERIFICATION_REQUIRED")
        self.policy = policy
        self.http = httpx.Client(
            verify=tls,
            trust_env=False,
            follow_redirects=False,
            timeout=httpx.Timeout(3, connect=2),
            limits=httpx.Limits(max_connections=2),
        )
        self.keys: dict[str, jwt.PyJWK] = {}
        self.expires, self.last_fetch = 0.0, float("-inf")
        self.lock = threading.Lock()

    def decode(self, token: str) -> dict[str, Any]:
        try:
            if len(token) > 16384 or not re.fullmatch(
                r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", token
            ):
                raise ValueError("TOKEN_SHAPE")
            for part in token.split(".")[:2]:
                obj = json.loads(
                    jwt.utils.base64url_decode(part.encode()),
                    object_pairs_hook=unique_object,
                )
                if not isinstance(obj, dict) or len(obj) > 100:
                    raise ValueError("TOKEN_SHAPE")
            if any(type(obj.get(field)) is not int for field in ("iat", "exp")) or (
                "nbf" in obj and type(obj["nbf"]) is not int
            ):
                raise ValueError("TOKEN_DATE_SHAPE")
            header = jwt.get_unverified_header(token)
            if "crit" in header or "b64" in header:
                raise ValueError("TOKEN_HEADER")
            kid, algorithm = header.get("kid"), header.get("alg")
            if (
                algorithm not in {"RS256", "ES256"}
                or not isinstance(kid, str)
                or not re.fullmatch(r"[A-Za-z0-9:._-]{1,100}", kid)
            ):
                raise ValueError("TOKEN_HEADER")
            with self.lock:
                now = time.monotonic()
                if now >= self.expires or kid not in self.keys:
                    if now - self.last_fetch < 5:
                        raise ValueError("KEY_REFRESH_BOUNDED")
                    self.last_fetch = now
                    with self.http.stream("GET", self.policy.jwks) as result:
                        result.raise_for_status()
                        payload = bytearray()
                        for chunk in result.iter_bytes():
                            payload.extend(chunk)
                            if len(payload) > 65536:
                                raise ValueError("KEY_RESPONSE_BOUND")
                    values = json.loads(payload, object_pairs_hook=unique_object)[
                        "keys"
                    ]
                    if not isinstance(values, list) or not 1 <= len(values) <= 10:
                        raise ValueError("KEY_SET_BOUND")
                    seen: set[str] = set()
                    keys: dict[str, jwt.PyJWK] = {}
                    for item in values:
                        if not isinstance(item, dict) or any(
                            name in item
                            for name in ("d", "p", "q", "dp", "dq", "qi", "k")
                        ):
                            raise ValueError("PUBLIC_KEYS_REQUIRED")
                        identity = item.get("kid")
                        if (
                            not isinstance(identity, str)
                            or not re.fullmatch(r"[A-Za-z0-9:._-]{1,100}", identity)
                            or identity in seen
                        ):
                            raise ValueError("KEY_ID_REJECTED")
                        seen.add(identity)
                        if item.get("use", "sig") != "sig":
                            continue  # Keycloak also publishes encryption keys; never use them for JWT trust.
                        if item.get("alg") not in {"RS256", "ES256"}:
                            continue
                        keys[identity] = jwt.PyJWK.from_dict(item)
                    if not keys:
                        raise ValueError("KEY_SET_REJECTED")
                    self.keys, self.expires = keys, now + 300
                key = self.keys[kid]
                if now >= self.expires or key.algorithm_name != algorithm:
                    raise ValueError("KEY_NOT_VALID")
            claims: dict[str, Any] = jwt.decode(
                token,
                key.key,
                algorithms=[algorithm],
                audience=self.policy.audience,
                issuer=self.policy.issuer,
                leeway=60,
                options={
                    "require": [
                        "iss",
                        "aud",
                        "exp",
                        "iat",
                        "sub",
                        "typ",
                        "azp",
                        "scope",
                    ]
                },
            )
            scopes = claims["scope"]
            if (
                claims["typ"] != "Bearer"
                or claims["azp"] not in self.policy.clients
                or claims.get("client_id", claims["azp"]) != claims["azp"]
                or claims["exp"] <= claims["iat"]
                or not isinstance(claims["sub"], str)
                or not 1 <= len(claims["sub"]) <= 200
                or not isinstance(scopes, str)
                or len(scopes) > 4096
                or any(
                    not re.fullmatch(r"[A-Za-z0-9:._-]{1,100}", x)
                    for x in scopes.split(" ")
                )
                or not self.policy.scopes <= set(scopes.split(" "))
            ):
                raise ValueError("TOKEN_POLICY")
            return claims
        except (
            jwt.PyJWTError,
            httpx.HTTPError,
            ValueError,
            KeyError,
            TypeError,
            RecursionError,
        ):
            # No JWT claims, network URLs, submitted tokens or exception text leave this boundary.
            raise Failure("AUTH_REQUIRED") from None

    def close(self) -> None:
        self.http.close()
