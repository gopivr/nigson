"""Check mock Terraform plans for unique log groups and lossless partial-apply recovery."""
import json
from pathlib import Path
import sys

OWNERS = ("commerce", "experience", "integration")


def verify(events):
    summaries = [e["test_summary"] for e in events if e.get("type") == "test_summary"]
    if not summaries or summaries[-1].get("status") != "pass":
        raise ValueError("Terraform mock tests did not pass")
    plans = {e.get("@testrun"): e["test_plan"] for e in events if "test_plan" in e}
    fresh = plans["create_foundation_without_images_or_running_apps"]["resource_changes"]
    names = [c["change"]["after"]["name"] for c in fresh if c.get("type") == "aws_cloudwatch_log_group"]
    if len(names) != len(set(names)):
        raise ValueError("Multiple Terraform resources would create the same log group")
    changes = plans["resume_partial_apply_moves_groups_to_compute"]["resource_changes"]
    indexed = {c["address"]: c for c in changes}
    if any(c["address"].startswith('module.observability.aws_cloudwatch_log_group.owner[') for c in changes):
        raise ValueError("Observability still owns duplicate application groups")
    for owner in OWNERS:
        target = f'module.compute.aws_cloudwatch_log_group.service["{owner}"]'
        source = f'module.observability.aws_cloudwatch_log_group.owner["{owner}"]'
        resource = indexed[target]
        change = resource["change"]
        if resource.get("previous_address") != source or change["actions"] not in [["no-op"], ["update"]]:
            raise ValueError("Log group must be moved without creation, replacement or deletion: " + owner)
        before, after = change["before"], change["after"]
        if any(not before.get(field) or before[field] != after.get(field) for field in ["id", "arn", "name"]):
            raise ValueError("Log group identity must survive the state move: " + owner)
        if after["name"] != "/nigson/stage/" + owner:
            raise ValueError("Unexpected migrated log group name")
    return f"PASS: unique log group names; {len(OWNERS)} state moves preserve IDs/ARNs/names without recreation"


def main():
    # Verbose Terraform events repeat large provider schemas; retain only the evidence we check.
    events = []
    with Path(sys.argv[1]).open() as source:
        for line in source:
            if not line.strip():
                continue
            event = json.loads(line)
            if "test_plan" in event:
                changes = event["test_plan"]["resource_changes"]
                event = {"@testrun": event.get("@testrun"), "test_plan": {
                    "resource_changes": [c for c in changes if c.get("type") == "aws_cloudwatch_log_group"]}}
                events.append(event)
            elif event.get("type") in {"test_summary", "diagnostic"}:
                events.append(event)
    for event in events:
        if event.get("type") == "test_summary":
            print(event.get("@message", "Terraform test summary"))
        if event.get("type") == "diagnostic" and event.get("diagnostic", {}).get("severity") == "error":
            print(event.get("@message", "Terraform test error"))
            print(event["diagnostic"].get("detail", ""))
    try:
        print(verify(events))
    except (ValueError, KeyError, TypeError) as failure:
        raise SystemExit("FAIL: " + str(failure)) from None


if __name__ == "__main__":
    main()
