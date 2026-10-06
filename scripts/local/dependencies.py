#!/usr/bin/env python3
"""Start/stop and verify the explicitly local Nigson dependency environment."""

import argparse
import http.client
import json
import os
from pathlib import Path
import re
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
    return [] if selected == "core" else ["--profile", selected]


def command(*arguments: str, capture: bool = False) -> str:
    envfile = ROOT / ".env"
    if not envfile.is_file():
        envfile = ROOT / ".env.example"
    project = os.environ.get("COMPOSE_PROJECT_NAME", "nigson-local")
    if project != "nigson-local" and not re.fullmatch(r"nigson-test-[a-z0-9-]+", project):
        raise ValueError("Refusing nonlocal Compose project name")
    result = subprocess.run(
        ["docker", "compose", "--project-name", project, "--env-file", str(envfile),
         "--file", str(ROOT / "docker-compose.yml"), *profiles(), *arguments],
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
    if os.environ.get("COMPOSE_PROFILES") or os.environ.get("PROFILES"):
        raise ValueError("Use PROFILE=core|app|ai; provider-mocks belongs to MOCK-001")
    config = json.loads(command("--profile", "*", "config", "--format", "json", capture=True))
    return validate_config(config)


def bootstrap() -> None:
    # Explicit names, dummy local AWS credentials, emulator-only executable.
    topic = json.loads(command("exec", "-T", "localstack", "awslocal", "sns",
                               "create-topic", "--name", "nigson-local-domain-events", capture=True))["TopicArn"]
    for name in ("integration-work", "experience-projections", "commerce-domain"):
        dead = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "create-queue",
                                  "--queue-name", name + "-dlq", "--attributes",
                                  "MessageRetentionPeriod=1209600", capture=True))["QueueUrl"]
        arn = json.loads(command("exec", "-T", "localstack", "awslocal", "sqs", "get-queue-attributes",
                                 "--queue-url", dead, "--attribute-names", "QueueArn", capture=True))["Attributes"]["QueueArn"]
        attributes = json.dumps({"VisibilityTimeout": "120", "MessageRetentionPeriod": "345600",
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
                "--attributes", "RawMessageDelivery=true")
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

    token = request("/realms/master/protocol/openid-connect/token", "POST", {
        "grant_type": "password", "client_id": "admin-cli", "username": "local-bootstrap",
        "password": "mock-local-identity-admin"}, form=True)["access_token"]
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
    for fixture in realm["users"]:
        signed = request("/realms/nigson-local/protocol/openid-connect/token", "POST", {
            "grant_type": "password", "client_id": "nigson-local-fixtures",
            "username": fixture["username"], "password": password}, form=True)
        if not signed.get("access_token"):
            raise ValueError("Real local identity fixture login failed")
    print("PASS: 13 deterministic Keycloak fixtures and real signed logins; domain seeds pending DATA-001")


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
            bootstrap()
        elif args.action == "migrate":
            migrate()
        elif args.action == "seed":
            seed(config)
        elif args.action == "run-core":
            migrate()
            command("--profile", "app", "build", *OWNERS)
            command("--profile", "app", "up", "--detach", "--no-recreate", "--wait", "--wait-timeout", "180", *OWNERS)
        elif args.action == "run-ai":
            if os.environ.get("PROFILE") != "ai":
                raise ValueError("run-ai requires PROFILE=ai")
            migrate()
            command("build", "ai")
            command("up", "--detach", "--no-recreate", "--wait", "--wait-timeout", "180", "ai")
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
