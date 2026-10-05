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

ROOT = Path(__file__).resolve().parents[2]


def command(*arguments: str, capture: bool = False) -> str:
    envfile = ROOT / ".env"
    if not envfile.is_file():
        envfile = ROOT / ".env.example"
    project = os.environ.get("COMPOSE_PROJECT_NAME", "nigson-local")
    if project != "nigson-local" and not re.fullmatch(r"nigson-test-[a-z0-9-]+", project):
        raise ValueError("Refusing nonlocal Compose project name")
    result = subprocess.run(
        ["docker", "compose", "--project-name", project, "--env-file", str(envfile),
         "--file", str(ROOT / "docker-compose.yml"), *arguments],
        cwd=ROOT, check=True, text=True, capture_output=capture, timeout=240,
    )
    return result.stdout if capture else ""


def validate_config(config: dict) -> dict:
    for name, service in config["services"].items():
        if not re.fullmatch(r"[a-z0-9./:_-]+@sha256:[a-f0-9]{64}", service["image"]):
            raise ValueError(f"Unpinned image: {name}")
        if service.get("environment", {}).get("APP_ENV") != "local":
            raise ValueError(f"Refusing nonlocal configuration: {name}")
        for port in service.get("ports", []):
            if port.get("host_ip") != "127.0.0.1":
                raise ValueError(f"Refusing externally published dependency port: {name}")
    return config


def validate() -> dict:
    if os.environ.get("ENV", "local") not in {"local", "test"}:
        raise ValueError("Local commands require ENV=local|test")
    config = json.loads(command("config", "--format", "json", capture=True))
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
            with urllib.request.urlopen(url, timeout=2) as response:
                document = json.load(response)
            if document["issuer"] != f"http://localhost:{int(port)}/realms/nigson-local":
                raise ValueError("Unexpected local identity issuer")
            print("Real local Keycloak discovery ready; trust/session qualification is SEC-001")
            return
        except (urllib.error.URLError, TimeoutError, ConnectionResetError, http.client.RemoteDisconnected):
            time.sleep(1)
    raise ValueError("Local Keycloak did not become ready within 180 seconds")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["validate", "up", "down", "bootstrap", "status"])
    args = parser.parse_args()
    try:
        config = validate()
        if args.action == "up":
            command("up", "--detach", "--wait", "--wait-timeout", "180")
            wait_identity(config)
            bootstrap()
        elif args.action == "down":
            command("down", "--timeout", "30")  # Preserve named volumes.
        elif args.action == "bootstrap":
            bootstrap()
        elif args.action == "status":
            command("ps")
        else:
            print("PASS: local-only Compose configuration and immutable image references")
        return 0
    except (ValueError, subprocess.SubprocessError, OSError) as exc:
        # Never echo config bodies, credentials or entire failed subprocess arguments.
        print(f"Local dependency operation failed: {type(exc).__name__}. Check local daemon/configuration.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
