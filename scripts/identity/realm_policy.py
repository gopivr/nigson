"""Versioned Keycloak realm contract. No secrets, users, domains or approvals are invented."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
BINDINGS = json.loads((ROOT / "contracts/security/workload-scopes.json").read_text())


def realm_policy(name: str, callback: str, phase: int, revision: str, environment: str, audit_retention_seconds: int) -> dict:
    if type(audit_retention_seconds) is not int or not 1 <= audit_retention_seconds <= 2147483647:
        raise ValueError("APPROVED_IDENTITY_AUDIT_RETENTION_REQUIRED")
    parsed = urlsplit(callback)
    if not re.fullmatch(r"[a-z][a-z0-9-]{1,62}", name) or name == "master" or phase not in range(1, 6) or not revision:
        raise ValueError("IDENTITY_POLICY_REQUIRED")
    if environment not in {"test", "dev", "qa", "stage", "prod"} or parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.query or parsed.fragment or parsed.path != "/bff/callback":
        raise ValueError("EXACT_BFF_HTTPS_CALLBACK_REQUIRED")
    if environment != "test" and (parsed.hostname in {"localhost", "127.0.0.1"} or parsed.hostname.endswith((".test", ".example", ".invalid"))):
        raise ValueError("APPROVED_BFF_HOST_REQUIRED")
    clients = [{"clientId": "nigson-bff", "enabled": True, "protocol": "openid-connect", "publicClient": False,
                "clientAuthenticatorType": "client-secret", "standardFlowEnabled": True, "implicitFlowEnabled": False,
                "directAccessGrantsEnabled": False, "serviceAccountsEnabled": False, "fullScopeAllowed": False,
                "redirectUris": [callback], "webOrigins": [],
                "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": callback.rsplit("/bff/", 1)[0] + "/"},
                "protocolMappers": [subject_mapper(), audience_mapper("nigson-commerce"), {"name": "auth_time", "protocol": "openid-connect", "protocolMapper": "oidc-usersessionmodel-note-mapper", "config": {"user.session.note": "AUTH_TIME", "claim.name": "auth_time", "jsonType.label": "long", "id.token.claim": "true", "access.token.claim": "true"}}, {"name": "amr", "protocol": "openid-connect", "protocolMapper": "oidc-amr-mapper", "config": {"access.token.claim": "true", "id.token.claim": "true"}}]}]
    active = [row for row in BINDINGS if row["phase"] <= phase and ((row["client"] != "nigson-ai" and row["owner"] != "ai") or phase == 5)]
    scopes = []
    for scope in sorted({row["scope"] for row in active}):
        owners = sorted({row["owner"] for row in active if row["scope"] == scope})
        scopes.append({"name": scope, "protocol": "openid-connect", "attributes": {"include.in.token.scope": "true"},
                       "protocolMappers": [audience_mapper("nigson-" + owner) for owner in owners]})
    for client in ("nigson-commerce", "nigson-experience", "nigson-integration", "nigson-ai"):
        if client == "nigson-ai" and phase < 5:
            continue
        names = sorted({row["scope"] for row in active if row["client"] == client})
        clients.append({"clientId": client, "enabled": True, "protocol": "openid-connect", "publicClient": False,
                        "clientAuthenticatorType": "client-secret", "serviceAccountsEnabled": True,
                        "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
                        "fullScopeAllowed": False, "protocolMappers": [subject_mapper()], "defaultClientScopes": names, "optionalClientScopes": []})
    return {"eventsEnabled": True, "eventsExpiration": audit_retention_seconds, "adminEventsEnabled": True, "adminEventsDetailsEnabled": False, "eventsListeners": [], "realm": name, "enabled": True, "sslRequired": "all", "registrationAllowed": False,
            "resetPasswordAllowed": False, "rememberMe": False, "bruteForceProtected": True,
            "permanentLockout": False, "failureFactor": 5, "waitIncrementSeconds": 60,
            "accessTokenLifespan": 300, "ssoSessionIdleTimeout": 1800, "ssoSessionMaxLifespan": 43200,
            "revokeRefreshToken": True, "refreshTokenMaxReuse": 0,
            "otpPolicyType": "totp", "otpPolicyAlgorithm": "HmacSHA1", "otpPolicyDigits": 6,
            "otpPolicyPeriod": 30, "otpPolicyInitialCounter": 0, "otpPolicyLookAheadWindow": 1,
            "roles": {"realm": [{"name": "nigson-staff-identity", "description": "Authentication MFA marker; grants no business permission."}]},
            "attributes": {"nigson.identity.policy.revision": revision, "adminEventsExpiration": str(audit_retention_seconds)}, "clients": clients, "clientScopes": scopes,
            "browserSecurityHeaders": {"contentSecurityPolicy": "frame-src 'self'; frame-ancestors 'self'; object-src 'none';",
                                      "xContentTypeOptions": "nosniff", "strictTransportSecurity": "max-age=31536000; includeSubDomains",
                                      "xFrameOptions": "SAMEORIGIN", "referrerPolicy": "no-referrer"}}


def subject_mapper() -> dict:
    return {"name": "sub", "protocol": "openid-connect", "protocolMapper": "oidc-sub-mapper", "config": {"access.token.claim": "true", "introspection.token.claim": "true"}}


def audience_mapper(audience: str) -> dict:
    return {"name": "audience-" + audience, "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
            "config": {"included.custom.audience": audience, "access.token.claim": "true", "id.token.claim": "false"}}


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    for name in ("realm", "callback", "revision", "environment"):
        p.add_argument("--" + name, required=True)
    p.add_argument("--phase", type=int, required=True)
    p.add_argument("--audit-retention-seconds", type=int, required=True)
    a = p.parse_args()
    print(json.dumps(realm_policy(a.realm, a.callback, a.phase, a.revision, a.environment, a.audit_retention_seconds), indent=2))
