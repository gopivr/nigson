#!/usr/bin/env python3
"""TEST-LOCAL-COLD-START against fresh, isolated named volumes. Never deletes local state."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("local", ROOT / "scripts/local/dependencies.py")
local = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(local)


def docker(*args: str) -> str:
    return subprocess.run(["docker", *args], check=True, capture_output=True, text=True, timeout=60).stdout


def existing() -> dict:
    ids = sorted(docker("ps", "-aq", "--filter", "label=com.docker.compose.project=nigson-local").split())
    containers = json.loads(docker("inspect", *ids)) if ids else []
    return {"containers": {c["Id"]: {"started": c["State"]["StartedAt"], "mounts": c["Mounts"]} for c in containers},
            "volumes": sorted(docker("volume", "ls", "--filter", "label=com.docker.compose.project=nigson-local", "--format", "{{.Name}}").split())}


def http(port: int, path: str, token: str | None = None) -> tuple[int, dict]:
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        headers = {"Authorization": "Bearer " + token} if token else {}
        request = urllib.request.Request(f"http://127.0.0.1:{port}{path}", headers=headers)
        with opener.open(request, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read()
        return error.code, json.loads(body) if body else {}


def sql(query: str, database: str = "postgres", role: str = "nigson_bootstrap", succeeds: bool = True) -> str:
    db = "nigson" if database == "postgres" else "nigson_vector_test"
    try:
        result = local.command("exec", "-T", database, "psql", "-U", role, "-d", db,
                               "-v", "ON_ERROR_STOP=1", "-At", "-c", query, capture=True).strip()
    except subprocess.CalledProcessError:
        if not succeeds:
            return "denied"
        raise
    if not succeeds:
        raise AssertionError("Forbidden database operation succeeded")
    return result


def snapshot(config: dict) -> dict:
    result = {"changes": sql("SELECT id,author,filename,md5sum FROM commerce.databasechangelog UNION ALL SELECT id,author,filename,md5sum FROM experience.databasechangelog UNION ALL SELECT id,author,filename,md5sum FROM integration.databasechangelog ORDER BY 1"),
              "vector_changes": sql("SELECT id,author,filename,md5sum FROM ai.databasechangelog ORDER BY id", "vector-db")}
    topic = json.loads(local.command("exec", "-T", "localstack", "awslocal", "sns", "list-subscriptions", capture=True))
    result["subscriptions"] = sorted((s["Endpoint"], s["SubscriptionArn"]) for s in topic["Subscriptions"])
    assert len(result["subscriptions"]) == 3
    for name, path in (("commerce", "/actuator/health/readiness"), ("experience", "/actuator/health/readiness"),
                       ("integration", "/actuator/health/readiness"), ("ai", "/health/ready")):
        port = int(config["services"][name]["ports"][0]["published"])
        assert http(port, path) == (200, {"status": "UP"}), name
        if name != "ai":
            assert http(port, "/protected-foundation-check")[0] == 401
    assert sql("SELECT extversion FROM pg_extension WHERE extname='vector'", "vector-db") == "0.8.2"
    assert sql("SELECT '[1,2,3]'::ai.vector <-> '[1,2,4]'::ai.vector", "vector-db", "nigson_ai_app") == "1"
    return result


def run(action: str, succeeds: bool = True, overrides: dict | None = None) -> None:
    result = subprocess.run(["make", action], cwd=ROOT, env={**os.environ, **(overrides or {})}, timeout=1200)
    assert (result.returncode == 0) == succeeds, action


def main() -> None:
    before = existing()
    project = "nigson-test-cold-" + uuid.uuid4().hex[:10]
    os.environ.update(COMPOSE_PROJECT_NAME=project, PROFILE="ai", ENV="test", FIXTURE_PROFILE="foundation")
    # Reserve all configurable host ports until Compose is validated.
    sockets = []
    for name in ("NIGSON_DB_PORT", "KEYCLOAK_PORT", "LOCALSTACK_PORT", "MAILPIT_HTTP_PORT", "MAILPIT_SMTP_PORT",
                 "COMMERCE_PORT", "EXPERIENCE_PORT", "INTEGRATION_PORT", "AI_PORT"):
        reserved = socket.socket()
        reserved.bind(("127.0.0.1", 0))
        os.environ[name] = str(reserved.getsockname()[1])
        sockets.append(reserved)
    config = local.validate()
    assert not docker("ps", "-aq", "--filter", "label=com.docker.compose.project=" + project).strip()
    assert not docker("volume", "ls", "--filter", "label=com.docker.compose.project=" + project, "--format", "{{.Name}}").strip()
    for reserved in sockets:
        reserved.close()
    print("Fresh isolated project:", project, flush=True)
    evidence = {"test": "TEST-LOCAL-COLD-START", "project": project, "checks": [], "result": "FAIL"}
    try:
        for iteration in range(2):
            for action in ("local-up", "migrate", "seed", "run-core", "run-ai"):
                run(action)
            current = snapshot(config)
            if iteration == 0:
                first = current
            else:
                assert first == current, "Migration/subscription/health drift after repeated setup"
            evidence["checks"].append(f"setup-pass-{iteration + 1}: real DB, migrations, 13 signed fixture logins, four healthy apps, vector query, subscriptions")
        for owner in local.OWNERS:
            sql("CREATE TABLE " + owner + ".forbidden_runtime_ddl(id int)", role=f"nigson_{owner}_app", succeeds=False)
            for other in local.OWNERS:
                if owner != other:
                    sql("SELECT * FROM " + other + ".databasechangelog", role=f"nigson_{owner}_app", succeeds=False)
        evidence["checks"].append("runtime DDL and all cross-owner reads denied")
        # Actual upstream Spring Session JDBC persistence, cascade and deadline constraint.
        sid = "00000000-0000-0000-0000-000000000003"
        sql(f"INSERT INTO commerce.spring_session VALUES ('{sid}','{sid}',0,0,60,60000,'fixture'); INSERT INTO commerce.spring_session_attributes VALUES ('{sid}','fixture',decode('01','hex')); DELETE FROM commerce.spring_session WHERE primary_id='{sid}';", role="nigson_commerce_app")
        assert sql(f"SELECT count(*) FROM commerce.spring_session_attributes WHERE session_primary_id='{sid}'", role="nigson_commerce_app") == "0"
        sql(f"INSERT INTO commerce.spring_session VALUES ('{sid}','{sid}',0,0,0,0,'fixture')", role="nigson_commerce_app", succeeds=False)
        evidence["checks"].append("session CRUD/cascade and positive interval constraint")
        # Corrupt tracking checksum only inside the isolated test DB; ensure fail closed.
        checksum = sql("SELECT md5sum FROM commerce.databasechangelog WHERE id='commerce-20261005-001'")
        sql("UPDATE commerce.databasechangelog SET md5sum='9:00000000000000000000000000000000' WHERE id='commerce-20261005-001'")
        run("migrate", succeeds=False)
        sql("UPDATE commerce.databasechangelog SET md5sum='" + checksum + "' WHERE id='commerce-20261005-001'")
        run("migrate")
        evidence["checks"].append("checksum drift refused, restored test checksum and migration recovered")
        sql("ALTER TABLE commerce.spring_session DROP CONSTRAINT spring_session_interval_ck")
        run("migrate", succeeds=False)
        sql("ALTER TABLE commerce.spring_session ADD CONSTRAINT spring_session_interval_ck CHECK (max_inactive_interval > 0)")
        run("migrate")
        evidence["checks"].append("manual session constraint drift refused and recovery verified")
        identity_port = int(config["services"]["keycloak"]["ports"][0]["published"])
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        form = urllib.parse.urlencode({"grant_type": "password", "client_id": "nigson-local-fixtures",
                                      "username": "retailA", "password": local.settings()["KEYCLOAK_FIXTURE_PASSWORD"]}).encode()
        with opener.open(urllib.request.Request(f"http://127.0.0.1:{identity_port}/realms/nigson-local/protocol/openid-connect/token", data=form), timeout=10) as response:
            token = json.load(response)["access_token"]
        for owner in local.OWNERS:
            port = int(config["services"][owner]["ports"][0]["published"])
            assert http(port, "/protected-foundation-check", token)[0] == 403
            assert http(port, "/protected-foundation-check", token.rsplit(".", 1)[0] + ".AA")[0] == 401
        evidence["checks"].append("real signed tokens validated; foundation routes denied; invalid signatures rejected")
        for action in ("local-up", "migrate", "seed", "run-core", "run-ai"):
            run(action, succeeds=False, overrides={"ENV": "prod"})
        run("migrate", succeeds=False, overrides={"COMMERCE_MIGRATION_PASSWORD": ""})
        run("seed", succeeds=False, overrides={"KEYCLOAK_FIXTURE_PASSWORD": ""})
        run("seed", succeeds=False, overrides={"FIXTURE_PROFILE": "production"})
        evidence["checks"].append("production commands, missing credentials and unapproved fixtures refused")
        port = int(config["services"]["commerce"]["ports"][0]["published"])
        local.command("stop", "postgres")
        assert http(port, "/actuator/health/liveness") == (200, {"status": "UP"})
        assert http(port, "/actuator/health/readiness")[0] == 503
        local.command("up", "--detach", "--no-recreate", "--wait", "postgres")
        deadline = time.monotonic() + 60
        while http(port, "/actuator/health/readiness")[0] != 200:
            assert time.monotonic() < deadline
            time.sleep(1)
        evidence["checks"].append("DB outage readiness DOWN, liveness UP, recovery UP")
        assert before == existing(), "Existing nigson-local container/start/mount/volume state changed"
        evidence["checks"].append("existing nigson-local container IDs/start times/mounts/volumes preserved")
        evidence["snapshot_sha256"] = hashlib.sha256(json.dumps(first, sort_keys=True).encode()).hexdigest()
        evidence["result"] = "PASS"
    finally:
        # Deliberately retain test volumes, even on failure, for inspection. Stop only this generated namespace.
        try:
            local.command("--profile", "*", "down", "--timeout", "30")
            assert not docker("ps", "-aq", "--filter", "label=com.docker.compose.project=" + project).strip()
            assert before == existing(), "Existing local state changed during test cleanup"
            evidence["checks"].append("all test profile containers stopped; named volumes and original local state retained")
        except (subprocess.SubprocessError, AssertionError):
            evidence["result"] = "FAIL"
            raise
        finally:
            output = ROOT / ".build/evidence/found003-cold-start.json"
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(evidence, indent=2) + "\n")
            print(json.dumps(evidence, indent=2), flush=True)


if __name__ == "__main__":
    main()
