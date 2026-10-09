import json
import os
import ssl
import threading
import time
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import ec, rsa

from nigson_ai.platform.identity import IdentityPolicy, KeycloakIdentity
from nigson_ai.platform.problem import Failure


@pytest.fixture
def identity():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public = json.loads(jwt.algorithms.RSAAlgorithm.to_jwk(private.public_key()))
    public.update(kid="qualification-rsa", alg="RS256", use="sig")
    state = {"keys": [public], "requests": 0, "status": 200}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            state["requests"] += 1
            body = json.dumps({"keys": state["keys"]}).encode()
            self.send_response(state["status"])
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    issuer = f"http://127.0.0.1:{server.server_port}/realms/qualification"
    policy = IdentityPolicy(
        "test",
        issuer,
        issuer + "/protocol/openid-connect/certs",
        "nigson-ai",
        frozenset({"nigson-commerce"}),
        frozenset({"ai.run"}),
    )
    trust = KeycloakIdentity(policy)
    claims = {
        "iss": issuer,
        "aud": "nigson-ai",
        "sub": "offline-qualification",
        "azp": "nigson-commerce",
        "typ": "Bearer",
        "scope": "ai.run",
        "iat": int(time.time()),
        "exp": int(time.time()) + 600,
    }
    yield trust, private, claims, state
    trust.close()
    server.shutdown()
    server.server_close()
    thread.join()


def signed(key, claims):
    return jwt.encode(
        claims, key, algorithm="RS256", headers={"kid": "qualification-rsa"}
    )


def test_signed_claims_and_cached_keys(identity):
    trust, key, claims, state = identity
    assert trust.decode(signed(key, claims))["typ"] == "Bearer"
    bads = {
        "iss": claims["iss"] + "-wrong",
        "aud": "nigson-commerce",
        "azp": "nigson-bff",
        "scope": "other.run",
        "typ": "ID",
        "client_id": "nigson-integration",
        "nbf": int(time.time()) + 120,
        "exp": int(time.time()) - 120,
        "iat": int(time.time()) + 120,
    }
    for field, value in bads.items():
        with pytest.raises(Failure) as error:
            trust.decode(signed(key, {**claims, field: value}))
        assert error.value.code == "AUTH_REQUIRED"
    for field in ("iss", "aud", "exp", "iat", "sub", "typ", "azp", "scope"):
        missing = dict(claims)
        missing.pop(field)
        with pytest.raises(Failure):
            trust.decode(signed(key, missing))
    assert state["requests"] == 1


def test_refresh_is_bounded_and_expired_keys_fail_closed(identity, monkeypatch):
    trust, key, claims, state = identity
    now = [100.0]
    monkeypatch.setattr("nigson_ai.platform.identity.time.monotonic", lambda: now[0])
    token = signed(key, claims)
    trust.decode(token)
    unknown = jwt.encode(claims, key, algorithm="RS256", headers={"kid": "unknown"})
    with pytest.raises(Failure):
        trust.decode(unknown)
    assert state["requests"] == 1
    now[0] += 6
    with pytest.raises(Failure):
        trust.decode(unknown)
    assert state["requests"] == 2
    now[0] += 301
    state["status"] = 503
    with pytest.raises(Failure):
        trust.decode(token)
    assert state["requests"] == 3


def test_elliptic_signature_and_unsupported_algorithm(identity):
    trust, _key, claims, state = identity
    elliptic = ec.generate_private_key(ec.SECP256R1())
    public = json.loads(jwt.algorithms.ECAlgorithm.to_jwk(elliptic.public_key()))
    public.update(kid="qualification-ec", alg="ES256", use="sig")
    state["keys"].append(public)
    assert trust.decode(
        jwt.encode(
            claims, elliptic, algorithm="ES256", headers={"kid": "qualification-ec"}
        )
    )
    with pytest.raises(Failure):
        trust.decode(
            jwt.encode(
                claims,
                "test-only-hmac-key-must-never-be-trusted",
                algorithm="HS256",
                headers={"kid": "qualification-rsa"},
            )
        )
    for token in ("invalid", "a.b.c", "a" * 16385):
        with pytest.raises(Failure):
            trust.decode(token)


def test_production_policy_has_no_local_fallback(identity):
    trust, _, _, _ = identity
    with pytest.raises(ValueError):
        replace(trust.policy, environment="prod")
    with pytest.raises(ValueError):
        KeycloakIdentity(trust.policy, False)
    with pytest.raises(ValueError):
        replace(trust.policy, clients=frozenset())


@pytest.mark.skipif(
    not os.environ.get("NIGSON_IDENTITY_FIXTURE"),
    reason="Real Keycloak runs in PROFILE=ai identity-check",
)
def test_actual_keycloak_access_and_id_token():
    data = json.loads(Path(os.environ["NIGSON_IDENTITY_FIXTURE"]).read_text())
    policy = IdentityPolicy(
        "test",
        data["issuer"],
        data["jwks"],
        "nigson-commerce",
        frozenset({"nigson-bff"}),
        frozenset({"openid"}),
    )
    tls = ssl.create_default_context(cafile=data["ca"])
    trust = KeycloakIdentity(policy, tls)
    try:
        workload = KeycloakIdentity(
            replace(
                policy,
                clients=frozenset({"nigson-integration"}),
                scopes=frozenset({"payment.reconcile"}),
            ),
            tls,
        )
        try:
            assert workload.decode(data["workload"]["access_token"])
        finally:
            workload.close()
        assert trust.decode(data["retail"]["access_token"])
        with pytest.raises(Failure):
            trust.decode(data["retail"]["id_token"])
    finally:
        trust.close()
