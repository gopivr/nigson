#!/usr/bin/env python3
"""Render non-secret platform inputs from applied bootstrap state. Never reads AWS credentials."""
import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def render(configuration):
    required = {"expected_account_id", "region", "environment", "domain_name", "zone_name", "zone_id", "state_bucket", "state_key"}
    if not isinstance(configuration, dict) or set(configuration) != required:
        raise ValueError("Apply the bootstrap root first; expected its exact configuration output")
    c = configuration
    if not re.fullmatch(r"[0-9]{12}", c["expected_account_id"]) or c["environment"] not in {"stage", "prod"}:
        raise ValueError("Invalid bootstrap account/environment")
    if not re.fullmatch(r"[a-z]{2}-[a-z]+-[0-9]+", c["region"]):
        raise ValueError("Invalid AWS region")
    if not re.fullmatch(r"[a-z0-9][a-z0-9.-]+\.[a-z]{2,}", c["domain_name"]):
        raise ValueError("Invalid registered domain")
    expected_zone = c["domain_name"] if c["environment"] == "prod" else c["environment"] + "." + c["domain_name"]
    if c["zone_name"] != expected_zone or not re.fullmatch(r"Z[A-Z0-9]+", c["zone_id"]):
        raise ValueError("Unexpected hosted zone")
    if c["state_bucket"] != f'nigson-{c["expected_account_id"]}-{c["region"]}-terraform-state' or c["state_key"] != f'nigson/first-account/{c["environment"]}/terraform.tfstate':
        raise ValueError("Unexpected state bucket/key; refuse cross-environment state")
    inputs = {key: c[key] for key in ("expected_account_id", "region", "environment", "domain_name", "zone_name", "zone_id")}
    backend = "\n".join(f"{key} = {json.dumps(value)}" for key, value in {
        "bucket": c["state_bucket"], "key": c["state_key"], "region": c["region"], "encrypt": True, "use_lockfile": True
    }.items()) + "\n"
    return inputs, backend


def capture_images(configuration, tag, run=subprocess.check_output):
    render(configuration)
    if not re.fullmatch(r"[a-f0-9]{40}-amd64", tag):
        raise ValueError("Use the reviewed commit's amd64 tag; this starter root uses X86_64")
    account = run(["aws", "sts", "get-caller-identity", "--query", "Account", "--output", "text", "--no-cli-pager"], text=True).strip()
    if account != configuration["expected_account_id"]:
        raise ValueError("AWS credentials resolve to a different account")
    services = ["commerce", "experience", "integration", "identity"]
    def digest(service, image_tag):
        value = run(["aws", "ecr", "describe-images", "--region", configuration["region"],
            "--repository-name", f'nigson/{configuration["environment"]}/{service}',
            "--image-ids", "imageTag=" + image_tag, "--query", "imageDetails[0].imageDigest",
            "--output", "text", "--no-cli-pager"], text=True).strip()
        if not re.fullmatch(r"sha256:[a-f0-9]{64}", value):
            raise ValueError("Published image digest is missing or invalid")
        return value
    return {"publish_task_definitions": True,
        "image_digests": {service: digest(service, tag) for service in services},
        "migration_image_digest": digest("commerce", tag + "-migration")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=["stage", "prod"], default="stage")
    parser.add_argument("--image-tag", help="Optionally read already-published amd64 image digests using the active AWS profile")
    args = parser.parse_args()
    bootstrap = ROOT / "infrastructure/terraform/aws/bootstrap"
    result = subprocess.run(["terraform", f"-chdir={bootstrap}", "output", "-json", "configuration"], capture_output=True, text=True, check=True)
    inputs, backend = render(json.loads(result.stdout))
    if inputs["environment"] != args.environment:
        raise ValueError("Bootstrap state belongs to another environment; do not reuse it")
    folder = ROOT / ".build" / ("aws-first-" + args.environment)
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "platform.tfvars.json").write_text(json.dumps(inputs, indent=2) + "\n")
    (folder / "backend.hcl").write_text(backend)
    if args.image_tag:
        images = capture_images(json.loads(result.stdout), args.image_tag)
        (folder / "images.tfvars.json").write_text(json.dumps(images, indent=2) + "\n")
    print("Prepared non-secret inputs in " + str(folder))
    print("No AWS resources were created; no credentials were read or written.")


if __name__ == "__main__":
    main()
