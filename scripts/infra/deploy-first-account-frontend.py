#!/usr/bin/env python3
"""Publish an immutable UI build and apply a guarded Terraform frontend-only release."""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


infra = load("first_account_pipeline", ROOT / "scripts/infra/first-account-pipeline.py")
publisher = load("frontend_publisher", ROOT / "scripts/infra/publish-frontend.py")
ADDRESS = "module.frontend.aws_cloudfront_distribution.frontend"
RELEASE = r"[a-f0-9]{40}-[0-9]+-[0-9]+"
COMPUTED = {"etag", "last_modified_time", "status", "in_progress_validation_batches", "trusted_signers", "trusted_key_groups"}


def validate_artifact(site, proof, commit):
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Expected an immutable UI commit")
    manifest = json.loads(proof.read_text())
    files = publisher.validate(site)
    actual = {p.relative_to(site).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    if manifest.get("schema_version") != 1 or manifest.get("source_commit") != commit or manifest.get("files") != actual:
        raise ValueError("UI artifact does not match its source commit and sealed file hashes")
    return files


def state_resource(state, address):
    def visit(module):
        for resource in module.get("resources", []):
            if resource["address"] == address:
                return resource["values"]
        for child in module.get("child_modules", []):
            found = visit(child)
            if found is not None:
                return found
        return None
    found = visit(state.get("values", {}).get("root_module", {}))
    if not found:
        raise ValueError("Infrastructure is not ready: missing " + address)
    return found



def changed_field_paths(before, after, *, limit=20):
    """Describe plan differences without logging values, headers, tags or secrets."""
    paths = []
    missing = object()

    def visit(left, right, path):
        if len(paths) >= limit:
            return
        if isinstance(left, dict) and isinstance(right, dict):
            for key in sorted(left.keys() | right.keys()):
                # Only fixed Terraform schema names enter diagnostics; map keys
                # (tags, custom headers, etc.) may themselves contain private data.
                if key not in left or key not in right or left[key] != right[key]:
                    visit(left.get(key, missing), right.get(key, missing), path + '/' + str(key))
        elif isinstance(left, list) and isinstance(right, list):
            for index in range(max(len(left), len(right))):
                visit(left[index] if index < len(left) else missing,
                      right[index] if index < len(right) else missing, path + '/' + str(index))
        elif type(left) is not type(right) or left != right:
            paths.append(path or '/')

    # Compare each top-level schema field separately. Report nested block indices
    # only for known CloudFront blocks; arbitrary user map keys are never emitted.
    blocks = {'origin', 'default_cache_behavior', 'ordered_cache_behavior',
              'viewer_certificate', 'restrictions', 'logging_config'}
    for key in sorted(before.keys() | after.keys()):
        left, right = before.get(key, missing), after.get(key, missing)
        if type(left) is type(right) and left == right:
            continue
        if key in blocks:
            visit(left, right, '/' + key)
        elif len(paths) < limit:
            paths.append('/' + key)
    return paths


def frontend_plan(plan, release, distribution):
    changed = [c for c in plan.get("resource_changes", []) if c["change"]["actions"] not in [["no-op"], ["read"]]]
    if len(changed) != 1 or changed[0]["address"] != ADDRESS or changed[0]["change"]["actions"] != ["update"]:
        raise ValueError("Release plan must update only the existing CloudFront distribution; apply infrastructure changes separately")
    change = changed[0]["change"]
    before, after = copy.deepcopy(change["before"]), copy.deepcopy(change["after"])
    if before.get("id") != distribution:
        raise ValueError("Release plan targets a different distribution")
    for key in COMPUTED:
        before.pop(key, None)
        after.pop(key, None)
    for key in ["id", "arn", "domain_name", "hosted_zone_id"]:
        if change.get("after_unknown", {}).get(key) is True:
            before.pop(key, None)
            after.pop(key, None)
    old = {origin["origin_id"]: origin for origin in before.get("origin", [])}
    new = {origin["origin_id"]: origin for origin in after.get("origin", [])}
    if len(before.get("origin", [])) != 2 or len(after.get("origin", [])) != 2 or set(old) != {"static", "backend"} or set(new) != set(old):
        raise ValueError("Unexpected CloudFront origin topology")
    if new["static"].get("origin_path") != "/releases/" + release:
        raise ValueError("Release plan does not activate the uploaded release")
    # AWS 6.15's SDK state uses empty strings/collections for omitted origin
    # fields; a replacement set element can plan these as null or omit them.
    # Normalize only empty optional values. Never discard a configured OAC, IP
    # address type, header/config block or positive response timeout.
    for origin in [*old.values(), *new.values()]:
        if origin.get("response_completion_timeout") in (None, 0):
            origin.pop("response_completion_timeout", None)
        for key in ("origin_access_control_id", "origin_path"):
            if origin.get(key) in (None, ""):
                origin.pop(key, None)
        for key in ("custom_header", "custom_origin_config", "origin_shield",
                    "s3_origin_config", "vpc_origin_config"):
            if origin.get(key) in (None, []):
                origin.pop(key, None)
        for config in origin.get("custom_origin_config", []):
            if config.get("ip_address_type") in (None, ""):
                config.pop("ip_address_type", None)
    old["static"]["origin_path"] = "/releases/" + release
    before["origin"] = [old[k] for k in sorted(old)]
    after["origin"] = [new[k] for k in sorted(new)]
    if before != after:
        fields = ", ".join(changed_field_paths(before, after))
        raise ValueError("Release plan changes CloudFront settings beyond the static release path. "
                         "Changed fields (values withheld): " + fields +
                         ". Review these fields in the Terraform resource diff; reconcile infrastructure changes separately before retrying UI deployment.")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


def smoke(site, opener=None):
    opener = opener or urllib.request.build_opener(NoRedirect())
    paths = ["index.html", "federation.manifest.json", "remoteEntry.json"]
    manifest = json.loads((site / "federation.manifest.json").read_text())
    paths += [url.lstrip("/") for url in manifest.values()]
    for path in paths:
        url = "https://stage.nigson.com/" + path
        request = urllib.request.Request(url, headers={"User-Agent": "NigsonReleaseProbe/1.0", "Cache-Control": "no-cache"})
        with opener.open(request, timeout=15) as response:
            content = response.read(2 * 1024 * 1024 + 1)
            if response.status != 200 or response.geturl() != url or content != (site / path).read_bytes():
                raise ValueError("Frontend release probe failed for " + path)


def deploy(site, proof, release, commit, *, commands=None, work=None, probe=smoke):
    if not re.fullmatch(RELEASE, release) or not release.startswith(commit + "-") or len(release) > 80:
        raise ValueError("Use a release ID built from UI commit, run ID and run attempt")
    validate_artifact(site, proof, commit)
    account, region = os.environ.get("AWS_EXPECTED_ACCOUNT_ID", ""), os.environ.get("AWS_REGION", "eu-west-1")
    pipeline = infra.Pipeline(account, region, "plan", work=work or ROOT / ".build" / ("ui-" + release), commands=commands)
    pipeline.prepare_bootstrap()
    configuration = json.loads(pipeline.tf(pipeline.bootstrap, "output", "-json", "configuration", capture=True))
    base, backend = infra.inputs_module.render(configuration)
    if any(base[key] != value for key, value in {"expected_account_id": account, "region": region, "environment": "stage", "domain_name": "nigson.com"}.items()):
        raise ValueError("Bootstrap state does not match the staging destination")
    backend_file = pipeline.work / "platform-backend.hcl"
    backend_file.write_text(backend)
    platform = ROOT / "infrastructure/terraform/aws/first-account"
    pipeline.init(platform, "-reconfigure", "-backend-config=" + str(backend_file))
    state = json.loads(pipeline.tf(platform, "show", "-json", capture=True))
    variables = infra.deployment_configuration(state, account, region)
    if variables is None:
        raise ValueError("Apply the updated first-account infrastructure once to record deployment_configuration before publishing UI")
    if any(variables.get(key) != value for key, value in base.items()):
        raise ValueError("Applied configuration belongs to a different bootstrap zone/account")
    bucket = f"nigson-stage-{account}-frontend"
    if state_resource(state, "module.frontend.aws_s3_bucket.frontend").get("bucket") != bucket:
        raise ValueError("Frontend bucket does not match this staging account")
    distribution = state_resource(state, ADDRESS)["id"]
    if not re.fullmatch(r"E[A-Z0-9]+", distribution):
        raise ValueError("Invalid managed CloudFront distribution")
    pipeline.commands.aws("s3api", "head-bucket", "--bucket", bucket, "--expected-bucket-owner", account, "--region", region)
    live = pipeline.commands.aws("cloudfront", "get-distribution", "--id", distribution)["Distribution"]
    if live.get("ARN") != f"arn:aws:cloudfront::{account}:distribution/{distribution}" or set(live["DistributionConfig"]["Aliases"].get("Items", [])) != {"stage.nigson.com"}:
        raise ValueError("Live CloudFront distribution is not the managed staging destination")
    previous = variables["frontend_release_id"]
    variables = dict(variables, frontend_release_id=release)
    varfile = pipeline.work / "release.tfvars.json"
    with open(varfile, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as output:
        json.dump(variables, output)
    plan = pipeline.work / "frontend.tfplan"
    # Scoped release operation; the guard still rejects any dependent infrastructure change.
    pipeline.tf(platform, "plan", "-input=false", "-lock-timeout=5m", "-target=" + ADDRESS, "-var-file=" + str(varfile), "-out=" + str(plan))
    frontend_plan(json.loads(pipeline.tf(platform, "show", "-json", str(plan), capture=True)), release, distribution)
    pipeline.summary(f"## UI release candidate\n\nCandidate: `{release}`\n\nPreviously applied release: `{previous}`\n\nActivation is pending the upload and guarded Terraform apply.\n")
    pipeline.commands.call(["python3", str(ROOT / "scripts/infra/publish-frontend.py"), "--site", str(site), "--bucket", bucket, "--release", release])
    pipeline.tf(platform, "apply", "-input=false", "-lock-timeout=5m", str(plan))
    pipeline.commands.call(["aws", "cloudfront", "wait", "distribution-deployed", "--id", distribution])
    invalidation = pipeline.commands.aws("cloudfront", "create-invalidation", "--distribution-id", distribution, "--paths", "/*")["Invalidation"]["Id"]
    pipeline.commands.call(["aws", "cloudfront", "wait", "invalidation-completed", "--distribution-id", distribution, "--id", invalidation])
    probe(site)
    pipeline.summary(f"## UI staging deployment\n\nRelease: `{release}`\n\nPrevious release: `{previous}`\n\nURL: https://stage.nigson.com\n\nStatic shell and all remote-entry probes passed. Backend login/provider qualification is separate.\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", required=True, type=Path)
    parser.add_argument("--proof", required=True, type=Path)
    parser.add_argument("--release", required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    deploy(args.site.resolve(), args.proof, args.release, args.source_commit)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as error:
        raise SystemExit(str(error)) from None
