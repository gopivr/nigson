#!/usr/bin/env python3
"""First-account staging CI: durable bootstrap, read-only platform plan, or foundation apply."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("first_account_inputs", ROOT / "scripts/infra/prepare-first-account.py")
inputs_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inputs_module)


class Commands:
    def call(self, args, capture=False):
        result = subprocess.run(args, text=True, stdout=subprocess.PIPE if capture else None,
                                stderr=subprocess.PIPE if capture else None, check=False)
        if result.returncode:
            raise RuntimeError("Command failed: " + args[0] + "; see the preceding tool output")
        return result.stdout if capture else None

    def aws(self, *args, missing=False):
        result = subprocess.run(["aws", *args, "--output", "json", "--no-cli-pager"],
                                text=True, capture_output=True, check=False)
        if result.returncode:
            match = re.search(r"An error occurred \(([A-Za-z0-9]+)\)", result.stderr)
            code = match.group(1) if match else "REQUEST_FAILED"
            if missing and code in {"404", "NoSuchBucket", "NoSuchKey", "NotFound"}:
                return None
            # Never treat forbidden, expired credentials or a network failure as absence.
            raise RuntimeError("AWS " + args[0] + "/" + args[1] + " failed: " + code)
        return json.loads(result.stdout) if result.stdout.strip() else {}


def foundation_plan(plan):
    """Reject runtime ownership and destruction, including replacement, before any apply."""
    for change in plan.get("resource_changes", []):
        actions = change["change"]["actions"]
        if "delete" in actions:
            raise ValueError("Foundation pipeline refuses resource deletion/replacement: " + change["address"])
        if change.get("type") in {"aws_ecs_task_definition", "aws_ecs_service"}:
            raise ValueError("Runtime tasks already exist or are proposed; use the application deployment flow")


def deployment_configuration(state, account, region):
    output = state.get("values", {}).get("outputs", {}).get("deployment_configuration")
    if output is None:
        return None
    snapshot = output.get("value")
    if not isinstance(snapshot, dict):
        raise ValueError("Missing applied deployment configuration")
    variables = snapshot.get("variables", {})
    if snapshot.get("schema_version") != 1 or not isinstance(variables, dict):
        raise ValueError("Unsupported applied deployment configuration")
    fields = set(re.findall(r'^variable\s+"([^\"]+)"', (ROOT / "infrastructure/terraform/aws/first-account/variables.tf").read_text(), re.MULTILINE))
    if set(variables) != fields:
        raise ValueError("Apply the updated infrastructure configuration before deploying UI; its input snapshot is incomplete")
    if (variables.get("expected_account_id") != account or variables.get("region") != region or
            variables.get("environment") != "stage" or variables.get("zone_name") != "stage.nigson.com" or
            not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", variables.get("frontend_release_id", ""))):
        raise ValueError("Applied deployment configuration does not match this staging account")
    return variables


class Pipeline:
    def __init__(self, account, region, action, *, root=ROOT, work=None, commands=None):
        if not re.fullmatch(r"[0-9]{12}", account or ""):
            raise ValueError("AWS_ACCOUNT_ID must be the twelve-digit account ID")
        if not re.fullmatch(r"[a-z]{2}-[a-z]+-[0-9]+", region):
            raise ValueError("Invalid AWS region")
        if action not in {"bootstrap", "plan", "apply"}:
            raise ValueError("Choose bootstrap, plan or apply")
        if os.environ.get("TF_WORKSPACE", "default") != "default":
            raise ValueError("This pipeline uses the default workspace only")
        self.account, self.region, self.action = account, region, action
        self.root = root
        self.work = work or root / ".build/aws-first-stage/ci"
        self.bootstrap = self.work / "bootstrap"
        self.bucket = f"nigson-{account}-{region}-terraform-state"
        self.key = "nigson/bootstrap/stage/terraform.tfstate"
        self.commands = commands or Commands()

    def tf(self, directory, *args, capture=False):
        return self.commands.call(["terraform", f"-chdir={directory}", *args], capture=capture)

    def s3(self, command, *, key=None, missing=False):
        args = ["s3api", command, "--bucket", self.bucket, "--expected-bucket-owner", self.account,
                "--region", self.region]
        if key is not None:
            args += ["--key", key]
        return self.commands.aws(*args, missing=missing)

    def init(self, directory, *args):
        self.tf(directory, "init", "-input=false", "-lockfile=readonly", *args)

    def plan_apply(self, directory, name, *, apply, extra=()):
        plan = self.work / (name + ".tfplan")
        self.tf(directory, "plan", "-input=false", "-lock-timeout=5m", *extra, "-out=" + str(plan))
        foundation_plan(json.loads(self.tf(directory, "show", "-json", str(plan), capture=True)))
        if apply:
            self.tf(directory, "apply", "-input=false", "-lock-timeout=5m", str(plan))

    def prepare_bootstrap(self):
        # Identity is checked before copying config, reading remote state or creating anything.
        identity = self.commands.aws("sts", "get-caller-identity")
        if identity.get("Account") != self.account:
            raise ValueError("AWS credentials resolve to a different account")
        self.bootstrap.mkdir(parents=True, exist_ok=False)
        source = self.root / "infrastructure/terraform/aws/bootstrap"
        for name in ["main.tf", ".terraform.lock.hcl"]:
            shutil.copyfile(source / name, self.bootstrap / name)
        bootstrap_inputs = {
            "expected_account_id": self.account, "region": self.region,
            "environment": "stage", "domain_name": "nigson.com",
            "github_repository": os.environ.get("GITHUB_REPOSITORY", "gopivr/nigson")}
        for name in ("GITHUB_REPOSITORY_ID", "GITHUB_REPOSITORY_OWNER_ID"):
            value = os.environ.get(name)
            if value:
                if not re.fullmatch(r"[0-9]+", value):
                    raise ValueError("GitHub repository/owner IDs must be numeric")
                bootstrap_inputs["github_repository_id" if name == "GITHUB_REPOSITORY_ID" else "github_owner_id"] = value
        provider = os.environ.get("GITHUB_OIDC_PROVIDER_ARN")
        if provider:
            bootstrap_inputs["github_oidc_provider_arn"] = provider
        (self.bootstrap / "ci.auto.tfvars.json").write_text(json.dumps(bootstrap_inputs) + "\n")
        bucket_exists = self.s3("head-bucket", missing=True) is not None
        state_exists = bucket_exists and self.s3("head-object", key=self.key, missing=True) is not None
        backend = self.work / "bootstrap-backend.hcl"
        backend.write_text("\n".join(f"{k} = {json.dumps(v)}" for k, v in {
            "bucket": self.bucket, "key": self.key, "region": self.region,
            "encrypt": True, "use_lockfile": True}.items()) + "\n")
        if not state_exists:
            if self.action != "bootstrap":
                raise ValueError("Bootstrap state is missing; run the bootstrap action first")
            zones = self.commands.aws("route53", "list-hosted-zones-by-name", "--dns-name", "stage.nigson.com", "--max-items", "1")
            if any(z["Name"].rstrip(".") == "stage.nigson.com" and not z.get("Config", {}).get("PrivateZone", False)
                   for z in zones["HostedZones"]):
                raise ValueError("Existing staging DNS has no CI bootstrap state; migrate its owning local state first")
            self.init(self.bootstrap)
            if bucket_exists:
                tags = self.s3("get-bucket-tagging")
                tags = {t["Key"]: t["Value"] for t in tags["TagSet"]}
                if any(tags.get(k) != v for k, v in {
                        "Application": "nigson", "Environment": "stage", "ManagedBy": "terraform"}.items()):
                    raise ValueError("Refuse to adopt an unrecognized state bucket")
                # Recover only the bucket from an interrupted bucket-only first apply.
                self.tf(self.bootstrap, "import", "-input=false", "-lock-timeout=5m", "aws_s3_bucket.state", self.bucket)
            else:
                self.plan_apply(self.bootstrap, "state-bucket", apply=True, extra=("-target=aws_s3_bucket.state",))
        (self.bootstrap / "backend.tf").write_text('terraform {\n  backend "s3" {\n  }\n}\n')
        if state_exists:
            self.init(self.bootstrap, "-backend-config=" + str(backend))
        else:
            # Another provisioner must not be overwritten if it created state since our initial check.
            if self.s3("head-object", key=self.key, missing=True) is not None:
                raise ValueError("Bootstrap state appeared concurrently; rerun instead of overwriting it")
            # Persist even a partial future bootstrap apply on S3; runner-local state is never an artifact.
            self.init(self.bootstrap, "-migrate-state", "-force-copy", "-backend-config=" + str(backend))
        if self.action == "bootstrap":
            self.plan_apply(self.bootstrap, "bootstrap", apply=True)
            self.summary("\nGitHub image publishing role: "
                         f"`arn:aws:iam::{self.account}:role/nigson-stage-github-publish`\n"
                         "GitHub deployment role: "
                         f"`arn:aws:iam::{self.account}:role/nigson-stage-github-deploy`\n")

    def run(self):
        self.prepare_bootstrap()
        configuration = json.loads(self.tf(self.bootstrap, "output", "-json", "configuration", capture=True))
        inputs, backend = inputs_module.render(configuration)
        if inputs["environment"] != "stage" or inputs["expected_account_id"] != self.account or inputs["region"] != self.region or inputs["domain_name"] != "nigson.com":
            raise ValueError("Bootstrap outputs do not match the requested staging account")
        nameservers = json.loads(self.tf(self.bootstrap, "output", "-json", "squarespace_nameservers", capture=True))
        if not isinstance(nameservers, list) or len(nameservers) != 4 or not all(
                isinstance(n, str) and re.fullmatch(r"ns-[0-9]+\.awsdns-[0-9]+\.(?:com|net|org|co\.uk)\.?", n) for n in nameservers):
            raise ValueError("Invalid Route 53 nameserver output")
        summary = ["## Nigson staging infrastructure", "", "Squarespace DNS: add these four NS records with host **stage**:", ""]
        summary += ["- `" + n + "`" for n in nameservers]
        summary += ["", "Frontend: https://stage.nigson.com", "API: https://api.stage.nigson.com", "Applications remain inactive in this infrastructure workflow."]
        self.summary("\n".join(summary) + "\n")
        if self.action == "bootstrap":
            return
        if self.action == "apply":
            answer = self.commands.call(["dig", "+short", "NS", "stage.nigson.com"], capture=True)
            if {n.rstrip(".").lower() for n in answer.split()} != {n.rstrip(".").lower() for n in nameservers}:
                raise ValueError("Delegate stage.nigson.com in Squarespace and wait for DNS propagation before apply")
        platform = self.root / "infrastructure/terraform/aws/first-account"
        varfile = self.work / "platform.tfvars.json"
        varfile.write_text(json.dumps(inputs) + "\n")
        platform_backend = self.work / "platform-backend.hcl"
        platform_backend.write_text(backend)
        self.init(platform, "-reconfigure", "-backend-config=" + str(platform_backend))
        state = json.loads(self.tf(platform, "show", "-json", capture=True))
        applied = deployment_configuration(state, self.account, self.region)
        previous = (state.get("values", {}).get("outputs", {}).get("infrastructure", {}).get("value") or {})
        release = applied["frontend_release_id"] if applied is not None else previous.get("frontend_release", "initial")
        if not isinstance(release, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", release):
            raise ValueError("Invalid applied frontend release")
        inputs["frontend_release_id"] = release
        varfile.write_text(json.dumps(inputs) + "\n")
        self.plan_apply(platform, "platform", apply=self.action == "apply", extra=("-var-file=" + str(varfile),))
        self.summary("\nPlatform " + ("apply completed" if self.action == "apply" else "plan completed; no infrastructure applied") + ". Review the Terraform output in this run.\n")

    @staticmethod
    def summary(text):
        print(text)
        destination = os.environ.get("GITHUB_STEP_SUMMARY")
        if destination:
            with open(destination, "a") as output:
                output.write(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["bootstrap", "plan", "apply"])
    args = parser.parse_args()
    Pipeline(os.environ.get("AWS_EXPECTED_ACCOUNT_ID", ""), os.environ.get("AWS_REGION", "eu-west-1"), args.action).run()


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        raise SystemExit(str(error)) from None
