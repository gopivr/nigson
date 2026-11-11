#!/usr/bin/env python3
"""Start/stop and verify the explicitly local Nigson dependency environment."""

import argparse
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
BASE_SERVICES = ("postgres", "identity-db", "keycloak", "localstack", "mailpit")
OWNERS = ("commerce", "experience", "integration")


def profiles() -> list[str]:
    selected = os.environ.get("PROFILE", "core")
    if selected not in {"core", "app", "ai"}:
        raise ValueError("Supported local profiles: core, app, ai")
    return ["--profile", "provider-mocks"] + ([] if selected == "core" else ["--profile", selected])


def require_build_space() -> None:
    """Stop before Docker writes layers when the host filesystem is nearly full."""
    free = shutil.disk_usage(ROOT).free
    minimum = 5 * 1024**3
    if free < minimum:
        raise ValueError(
            f"Insufficient host disk space for Docker build: {free / 1024**3:.1f} GiB free; "
            "free at least 5 GiB before retrying. If Docker reports metadata_v2.db "
            "input/output errors, restart Docker Desktop after freeing space. "
            "Preserve database volumes; do not factory-reset Docker"
        )


def command(*arguments: str, capture: bool = False) -> str:
    if "build" in arguments:
        require_build_space()
    envfile = ROOT / ".env"
    if not envfile.is_file():
        envfile = ROOT / ".env.example"
    project = os.environ.get("COMPOSE_PROJECT_NAME", "nigson-local")
    if project != "nigson-local" and not re.fullmatch(r"nigson-test-[a-z0-9-]+", project):
        raise ValueError("Refusing nonlocal Compose project name")
    # Persist the explicitly enabled sandbox quote overlay across run-core restarts.
    settings = dict(line.strip().split("=", 1) for line in envfile.read_text().splitlines()
                    if line.strip() and not line.lstrip().startswith("#") and "=" in line)
    pandago = os.environ.get("NIGSON_PANDAGO_ENABLED", settings.get("NIGSON_PANDAGO_ENABLED", "false")) == "true"
    overlays = ["--file", str(ROOT / "docker-compose.pandago.yml")] if pandago else []
    result = subprocess.run(
        ["docker", "compose", "--project-name", project, "--env-file", str(envfile),
         "--file", str(ROOT / "docker-compose.yml"), *overlays, *profiles(), *arguments],
        cwd=ROOT, check=True, text=True, capture_output=capture, timeout=900 if "build" in arguments else 240,
    )
    return result.stdout if capture else ""


def validate_config(config: dict) -> dict:
    for name, service in config["services"].items():
        if "build" in service:
            kind = "migration" if name.startswith("migrate-") else name
            build = service["build"]
            expected = ROOT / "services/ai" if kind == "ai" else ROOT
            dockerfile = "Dockerfile" if kind == "ai" else f"services/{'commerce' if kind == 'migration' else kind}/Dockerfile"
            if kind not in {*OWNERS, "ai", "migration"} or service["image"] != f"nigson/{kind}:local" or Path(build["context"]).resolve() != expected or build["dockerfile"] != dockerfile:
                raise ValueError(f"Untrusted local application build: {name}")
            if kind != "ai" and build.get("target") != ("migration" if kind == "migration" else "application"):
                raise ValueError(f"Unexpected Docker build target: {name}")
        elif not re.fullmatch(r"[a-z0-9./:_-]+@sha256:[a-f0-9]{64}", service["image"]):
            raise ValueError(f"Unpinned image: {name}")
        if service.get("environment", {}).get("APP_ENV") not in {"local", "test"}:
            raise ValueError(f"Refusing nonlocal configuration: {name}")
        environment = service.get("environment", {})
        if name.startswith("migrate-"):
            if environment.get("MIGRATION_RELEASE") not in {"BP1", "BP2", "BP3", "BP4", "BP5"}:
                raise ValueError("Schema deployment gate must be BP1..BP5")
            owner = name.removeprefix("migrate-")
            expected_url = "jdbc:postgresql://vector-db:5432/nigson_vector_test" if owner == "ai" else "jdbc:postgresql://postgres:5432/nigson"
            if environment.get("MIGRATION_OWNER") != owner or environment.get("MIGRATION_JDBC_URL") != expected_url:
                raise ValueError(f"Refusing nonlocal/mismatched owner migration target: {name}")
            if not environment.get("MIGRATION_PASSWORD"):
                raise ValueError(f"Missing local migration credential: {name}")
        if name in OWNERS:
            if environment.get("NIGSON_JDBC_URL") != f"jdbc:postgresql://postgres:5432/nigson?currentSchema={name}" or environment.get("NIGSON_DB_USER") != f"nigson_{name}_app":
                raise ValueError(f"Refusing nonlocal/mismatched owner runtime target: {name}")
        for port in service.get("ports", []):
            if port.get("host_ip") != "127.0.0.1":
                raise ValueError(f"Refusing externally published dependency port: {name}")
    return config


def provision(database: str = "postgres") -> None:
    if database not in {"postgres", "vector-db"}:
        raise ValueError("Invalid local provisioning target")
    db = "nigson" if database == "postgres" else "nigson_vector_test"
    # Mounted, checked-in provisioning SQL upgrades legacy role names in place.
    command("exec", "-T", database, "psql", "-U", "nigson_bootstrap", "-d", db,
            "-v", "ON_ERROR_STOP=1", "-f", "/docker-entrypoint-initdb.d/10-local-bootstrap.sql", capture=True)
    if database == "vector-db":
        command("exec", "-T", database, "psql", "-U", "nigson_bootstrap", "-d", db,
                "-v", "ON_ERROR_STOP=1", "-c", "CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA ai", capture=True)


def migrate() -> None:
    validate()
    provision()
    # Build only the one-shot tool, never start domain services as a side effect.
    command("--profile", "app", "build", "migrate-commerce")
    for owner in OWNERS:
        command("--profile", "app", "run", "--rm", "--no-deps", "migrate-" + owner)
    if os.environ.get("PROFILE") == "ai":
        provision("vector-db")
        command("run", "--rm", "--no-deps", "migrate-ai")


def validate() -> dict:
    if os.environ.get("ENV", "local") not in {"local", "test"}:
        raise ValueError("Local commands require ENV=local|test")
    if os.environ.get("DOCKER_HOST") and not os.environ["DOCKER_HOST"].startswith("unix://"):
        raise ValueError("Local commands refuse remote Docker hosts")
    if os.environ.get("DOCKER_CONTEXT", "default") not in {"default", "desktop-linux"}:
        raise ValueError("Local commands refuse remote Docker contexts")
    values = settings()
    for owner in (*OWNERS, "ai"):
        for suffix in ("DB_PASSWORD", "MIGRATION_PASSWORD"):
            setting = owner.upper() + "_" + suffix
            if not values.get(setting):
                raise ValueError("Missing local credential setting: " + setting)
    if not os.environ.get("DOCKER_HOST"):
        endpoint = subprocess.run(["docker", "context", "inspect", "--format", "{{json .Endpoints.docker.Host}}"],
                                  check=True, text=True, capture_output=True, timeout=15).stdout.strip()
        if not json.loads(endpoint).startswith("unix://"):
            raise ValueError("Local commands refuse the active remote Docker endpoint")
    if os.environ.get("COMPOSE_PROFILES") or os.environ.get("PROFILES", "") not in {"", "provider-mocks"}:
        raise ValueError("Use PROFILE=core|app|ai and optional PROFILES=provider-mocks")
    config = json.loads(command("--profile", "*", "config", "--format", "json", capture=True))
    return validate_config(config)


def bootstrap() -> None:
    import importlib.util
    spec=importlib.util.spec_from_file_location("topology", ROOT/"scripts/platform/topology.py")
    topology=importlib.util.module_from_spec(spec); spec.loader.exec_module(topology)
    # Explicit names, dummy local AWS credentials, emulator-only executable.
    topic = json.loads(command("exec", "-T", "localstack", "awslocal", "sns",
                               "create-topic", "--name", "nigson-local-domain-events", capture=True))["TopicArn"]
    command("exec", "-T", "localstack", "awslocal", "sns", "set-topic-attributes",
            "--topic-arn", topic, "--attribute-name", "KmsMasterKeyId", "--attribute-value", "alias/aws/sns")
    for owner,name in topology.QUEUES.items():
        if owner=="ai" and os.environ.get("SCHEMA_RELEASE","BP1")!="BP5":
            continue
        dead = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "create-queue",
                                  "--queue-name", name + "-dlq", "--attributes",
                                  "MessageRetentionPeriod=1209600,SqsManagedSseEnabled=true", capture=True))["QueueUrl"]
        arn = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "get-queue-attributes",
                                 "--queue-url", dead, "--attribute-names", "QueueArn", capture=True))["Attributes"]["QueueArn"]
        attributes = json.dumps({"VisibilityTimeout": "120", "MessageRetentionPeriod": "345600", "ReceiveMessageWaitTimeSeconds":"20", "SqsManagedSseEnabled":"true",
                                 "RedrivePolicy": json.dumps({"deadLetterTargetArn": arn, "maxReceiveCount": "5"})})
        queue = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "create-queue",
                                   "--queue-name", name, "--attributes", attributes, capture=True))["QueueUrl"]
        queue_arn = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "get-queue-attributes",
                                       "--queue-url", queue, "--attribute-names", "QueueArn", capture=True))["Attributes"]["QueueArn"]
        policy = json.dumps({"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Principal": {"Service": "sns.amazonaws.com"},
                            "Action": "sqs:SendMessage", "Resource": queue_arn,
                            "Condition": {"ArnEquals": {"aws:SourceArn": topic}}}]})
        command("exec", "-T", "localstack", "awslocal", "sqs", "set-queue-attributes", "--queue-url", queue,
                "--attributes", json.dumps({"Policy": policy}))
        command("exec", "-T", "localstack", "awslocal", "sns", "subscribe", "--topic-arn", topic,
                "--protocol", "sqs", "--notification-endpoint", queue_arn,
                "--attributes", json.dumps({"RawMessageDelivery":"true","FilterPolicyScope":"MessageBody","FilterPolicy":json.dumps(topology.filters(owner,int(os.environ.get("SCHEMA_RELEASE","BP1")[2:])))}))
    command("exec", "-T", "localstack", "awslocal", "s3api", "create-bucket", "--bucket", "nigson-local-quarantine")
    command("exec", "-T", "localstack", "awslocal", "s3api", "create-bucket", "--bucket", "nigson-local-private")
    print("Local infrastructure bootstrap applied; production/provider activation remains disabled")


def wait_identity(config: dict) -> None:
    port = config["services"]["keycloak"]["ports"][0]["published"]
    url = f"http://127.0.0.1:{int(port)}/realms/nigson-local/.well-known/openid-configuration"
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open(url, timeout=2) as response:
                document = json.load(response)
            if document["issuer"] != f"http://localhost:{int(port)}/realms/nigson-local":
                raise ValueError("Unexpected local identity issuer")
            print("Real local Keycloak discovery ready; trust/session qualification is SEC-001")
            return
        except (urllib.error.URLError, TimeoutError, ConnectionResetError, http.client.RemoteDisconnected):
            time.sleep(1)
    raise ValueError("Local Keycloak did not become ready within 180 seconds")


def configure_registration(config: dict) -> None:
    """Ensure the local realm has the separate, local-only user-registration client."""
    values = settings()
    if values.get("NIGSON_REGISTRATION_ENABLED", "true").lower() not in {"1", "true", "yes"}:
        return
    client_id = values.get("NIGSON_REGISTRATION_CLIENT_ID", "nigson-registration")
    secret = values.get("NIGSON_REGISTRATION_CLIENT_SECRET", "mock-registration-local-secret-32-bytes")
    if client_id != "nigson-registration" or len(secret) < 32 or len(secret) > 512:
        raise ValueError("Invalid local registration client settings")
    port = int(config["services"]["keycloak"]["ports"][0]["published"])
    base = f"http://127.0.0.1:{port}"

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise ValueError("Local identity redirect refused")

    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(path: str, method: str = "GET", payload=None, token=None, form=False):
        headers = {}; data = None
        if payload is not None:
            data = (urllib.parse.urlencode(payload) if form else json.dumps(payload)).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        if token: headers["Authorization"] = "Bearer " + token
        try:
            with opener.open(urllib.request.Request(base + path, data=data, headers=headers, method=method), timeout=10) as response:
                body = response.read(65537)
                if len(body) > 65536: raise ValueError("Local identity response exceeded its bound")
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            # The operation path contains no credentials and pinpoints a local
            # bootstrap contract mismatch without echoing Keycloak's response.
            raise ValueError(f"Local Keycloak registration setup refused (HTTP {error.code}, {method} {path})") from None

    # Once installed, the dedicated least-privilege client can verify that it
    # still has user-management access. This keeps ordinary local restarts from
    # depending on the temporary/bootstrap administrator password.
    try:
        registration_token = request("/realms/nigson-local/protocol/openid-connect/token", "POST", {
            "grant_type": "client_credentials", "client_id": client_id, "client_secret": secret}, form=True).get("access_token")
        if registration_token:
            managed_users = request("/admin/realms/nigson-local/users?max=1", token=registration_token)
            if isinstance(managed_users, list):
                print("PASS: existing local Keycloak registration client has user-management access")
                return
    except ValueError as error:
        if not any(f"HTTP {status}" in str(error) for status in (400, 401, 403, 404)):
            raise

    admin_username, admin_password = keycloak_bootstrap_credentials()
    admin_token = request("/realms/master/protocol/openid-connect/token", "POST", {
        "grant_type": "password", "client_id": "admin-cli", "username": admin_username,
        "password": admin_password}, form=True).get("access_token")
    if not admin_token:
        raise ValueError("Local Keycloak registration setup could not authenticate")
    admin = "/admin/realms/nigson-local"
    clients = request(admin + "/clients?" + urllib.parse.urlencode({"clientId": client_id}), token=admin_token)
    desired = {"clientId": client_id, "enabled": True, "protocol": "openid-connect", "publicClient": False,
               "secret": secret, "serviceAccountsEnabled": True, "standardFlowEnabled": False,
               "directAccessGrantsEnabled": False, "fullScopeAllowed": False}
    if not clients:
        request(admin + "/clients", "POST", desired, admin_token)
        clients = request(admin + "/clients?" + urllib.parse.urlencode({"clientId": client_id}), token=admin_token)
    if len(clients) != 1 or any(clients[0].get(key) != value for key, value in desired.items() if key != "secret"):
        raise ValueError("Local Keycloak registration client drift")
    client_uuid = clients[0]["id"]
    current_secret = request(admin + f"/clients/{client_uuid}/client-secret", token=admin_token).get("value")
    if current_secret != secret:
        raise ValueError("Local Keycloak registration secret differs from Compose configuration")
    service_user = request(admin + f"/clients/{client_uuid}/service-account-user", token=admin_token)
    if not isinstance(service_user, dict) or not service_user.get("id"):
        raise ValueError("Local Keycloak registration service account is unavailable")
    management = request(admin + "/clients?" + urllib.parse.urlencode({"clientId": "realm-management"}), token=admin_token)
    if len(management) != 1:
        raise ValueError("Local Keycloak registration role owner is unavailable")
    management_id = management[0]["id"]
    role = request(admin + f"/clients/{management_id}/roles/manage-users", token=admin_token)
    mappings_path = f"{admin}/users/{service_user['id']}/role-mappings/clients/{management_id}"
    mappings = request(mappings_path, token=admin_token)
    roles = {item.get("name") for item in mappings if item.get("containerId") == management_id}
    if roles - {"manage-users"}:
        raise ValueError("Local Keycloak registration service-account role drift")
    if "manage-users" not in roles:
        request(mappings_path, "POST", [role], admin_token)
    scope_path = f"{admin}/clients/{client_uuid}/scope-mappings/clients/{management_id}"
    scoped = request(scope_path, token=admin_token)
    scoped_roles = {item.get("name") for item in scoped}
    if scoped_roles - {"manage-users"}:
        raise ValueError("Local Keycloak registration token-scope drift")
    if "manage-users" not in scoped_roles:
        request(scope_path, "POST", [role], admin_token)
    print("PASS: local Keycloak registration service account configured; credentials were not emitted")


def settings() -> dict[str, str]:
    path = ROOT / ".env"
    if not path.is_file():
        path = ROOT / ".env.example"
    values = {}
    for line in path.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    values.update(os.environ)
    return values


def keycloak_bootstrap_credentials() -> tuple[str, str]:
    values = settings()
    username = values.get("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME", "local-bootstrap")
    password = values.get("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD", "mock-local-identity-admin")
    if (not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", username)
        or len(password) < 12 or len(password) > 512
        or any(ord(char) < 32 for char in password)):
        raise ValueError("Local Keycloak bootstrap credentials are invalid")
    return username, password


def seed(config: dict) -> None:
    values = settings()
    if values.get("FIXTURE_PROFILE") != "foundation":
        raise ValueError("Seed requires explicit FIXTURE_PROFILE=foundation; domain fixtures await DATA-001")
    password = values.get("KEYCLOAK_FIXTURE_PASSWORD")
    if not password:
        raise ValueError("Missing local KEYCLOAK_FIXTURE_PASSWORD")
    port = int(config["services"]["keycloak"]["ports"][0]["published"])
    base = f"http://127.0.0.1:{port}"
    # No redirects/proxies: local credentials must never reach a remote host.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise ValueError("Local identity redirect refused")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(path: str, method: str = "GET", payload=None, token=None, form=False):
        headers = {}
        data = None
        if payload is not None:
            data = (urllib.parse.urlencode(payload) if form else json.dumps(payload)).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        try:
            with opener.open(urllib.request.Request(base + path, data=data, headers=headers, method=method), timeout=10) as response:
                body = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            # Classify safely; do not echo provider response bodies, tokens or credentials.
            operation = "fixture login" if path.endswith("/token") else "fixture administration"
            raise ValueError(f"Local Keycloak {operation} refused (HTTP {error.code}); check configured credentials and fixture profile") from None

    admin_username, admin_password = keycloak_bootstrap_credentials()
    token = request("/realms/master/protocol/openid-connect/token", "POST", {
        "grant_type": "password", "client_id": "admin-cli", "username": admin_username,
        "password": admin_password}, form=True)["access_token"]
    admin = "/admin/realms/nigson-local"
    realm = json.loads((ROOT / "infrastructure/docker/keycloak/nigson-local-realm.json").read_text())
    for fixture in realm["users"]:
        matches = request(admin + "/users?" + urllib.parse.urlencode({"username": fixture["username"], "exact": "true"}), token=token)
        if not matches:
            fixture["credentials"][0]["value"] = password
            request(admin + "/users", "POST", fixture, token)
            matches = request(admin + "/users?" + urllib.parse.urlencode({"username": fixture["username"], "exact": "true"}), token=token)
        user = matches[0]
        if len(matches) != 1 or user["id"] != fixture["id"] or user.get("email") != fixture["email"] or not user.get("enabled"):
            raise ValueError("Local fixture identity drift; seed refused to overwrite existing identity")
        missing_names = {key: fixture[key] for key in ("firstName", "lastName") if not user.get(key)}
        if missing_names:
            request(admin + "/users/" + user["id"], "PUT", {**user, **missing_names}, token)
        roles = request(admin + "/users/" + user["id"] + "/role-mappings/realm", token=token)
        declared = {role["name"] for role in realm["roles"]["realm"]}
        assigned = {role["name"] for role in roles}
        if (assigned & declared) - set(fixture["realmRoles"]):
            raise ValueError("Local fixture business-role drift; seed refused elevated permissions")
        if not set(fixture["realmRoles"]).issubset(assigned):
            additions = [request(admin + "/roles/" + name, token=token) for name in fixture["realmRoles"]]
            request(admin + "/users/" + user["id"] + "/role-mappings/realm", "POST", additions, token)
    # Test-only client for real signed fixture tokens, never enabled on BFF.
    clients = request(admin + "/clients?clientId=nigson-local-fixtures", token=token)
    desired = {"clientId": "nigson-local-fixtures", "enabled": True, "publicClient": True,
               "protocol": "openid-connect", "standardFlowEnabled": False, "directAccessGrantsEnabled": True}
    if not clients:
        request(admin + "/clients", "POST", desired, token)
    elif any(clients[0].get(key) != value for key, value in desired.items()):
        raise ValueError("Fixture client drift")
    # Bounded fixture-only audiences allow foundation denial tests for all three owners.
    fixture_client = request(admin + "/clients?clientId=nigson-local-fixtures", token=token)[0]
    mappers = request(admin + "/clients/" + fixture_client["id"] + "/protocol-mappers/models", token=token)
    for owner in OWNERS:
        name = "foundation-audience-" + owner
        wanted = {"name": name, "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                  "config": {"included.custom.audience": "nigson-" + owner, "access.token.claim": "true", "id.token.claim": "false", "introspection.token.claim": "true", "userinfo.token.claim": "false"}}
        found = [mapper for mapper in mappers if mapper["name"] == name]
        if not found:
            request(admin + "/clients/" + fixture_client["id"] + "/protocol-mappers/models", "POST", wanted, token)
        elif any(found[0].get(k) != v for k, v in wanted.items()):
            raise ValueError("Fixture audience mapper drift")
    for fixture in realm["users"]:
        signed = request("/realms/nigson-local/protocol/openid-connect/token", "POST", {
            "grant_type": "password", "client_id": "nigson-local-fixtures",
            "username": fixture["username"], "password": password, "scope": "openid"}, form=True)
        if not signed.get("access_token"):
            raise ValueError("Real local identity fixture login failed")
    print("PASS: 13 deterministic Keycloak fixtures and real signed logins; domain seeds pending DATA-001")


def ensure_actor_key() -> None:
    """Recover only explicitly enabled local signing trust before recreating apps."""
    envfile = ROOT / ".env"
    if not envfile.is_file():
        return
    settings = dict(line.split("=", 1) for line in envfile.read_text().splitlines()
                    if line and not line.startswith("#") and "=" in line)
    if settings.get("NIGSON_SECURITY_ENABLED", "false") == "true":
        subprocess.run([sys.executable, str(ROOT / "scripts/local/restore_actor_key.py")],
                       cwd=ROOT, check=True, timeout=120)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["validate", "up", "down", "bootstrap", "status", "migrate", "seed", "run-core", "run-ai"])
    args = parser.parse_args()
    try:
        config = validate()
        if args.action == "up":
            command("up", "--detach", "--no-recreate", "--wait", "--wait-timeout", "180", *BASE_SERVICES)
            if os.environ.get("PROFILE") == "ai":
                command("up", "--detach", "--no-recreate", "--wait", "--wait-timeout", "180", "vector-db")
                provision("vector-db")
            wait_identity(config)
            configure_registration(config)
            bootstrap()
            subprocess.run([sys.executable, str(ROOT/"scripts/platform/build_mocks.py")],check=True)
            command("up", "--detach", "--no-recreate", "--wait", "--wait-timeout", "60", "paystack-mock", "fedex-mock", "twilio-mock")
        elif args.action == "migrate":
            migrate()
        elif args.action == "seed":
            seed(config)
        elif args.action == "run-core":
            migrate()
            command("--profile", "app", "build", *OWNERS)
            wait_identity(config)
            configure_registration(config)
            ensure_actor_key()
            # `build` may have produced new local images. Let Compose recreate only
            # services whose image/config changed so run-core actually runs the build.
            command("--profile", "app", "up", "--detach", "--wait", "--wait-timeout", "180", *OWNERS)
        elif args.action == "run-ai":
            if os.environ.get("PROFILE") != "ai":
                raise ValueError("run-ai requires PROFILE=ai")
            migrate()
            command("build", "ai")
            command("up", "--detach", "--wait", "--wait-timeout", "180", "ai")
        elif args.action == "down":
            command("--profile", "*", "down", "--timeout", "30")  # Preserve every named volume.
        elif args.action == "bootstrap":
            bootstrap()
        elif args.action == "status":
            command("ps")
        else:
            print("PASS: local-only Compose configuration and immutable image references")
        return 0
    except (ValueError, subprocess.SubprocessError, OSError) as exc:
        # Never echo config bodies, credentials or entire failed subprocess arguments.
        detail = str(exc) if isinstance(exc, ValueError) else type(exc).__name__
        print(f"Local dependency operation failed: {detail}. Check local daemon/configuration.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
