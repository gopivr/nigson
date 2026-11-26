# Session report: AWS-PROVIDER-LOCK-003

Date: 26 November 2026, America/New_York. Specification1.1.0. Phase: BP1 infrastructure. Status: fix implemented; Linux regression verification in progress.

## Problem and change

User's first-account Actions run failed with Required plugins are not installed: cached hashicorp/aws6.15.0 does not match lock hashes. The original two pipeline root locks included one macOS ARM h1 plus signed release zh archive hashes. The pipeline deliberately uses init -lockfile=readonly; Linux installation can validate a ZIP by zh without persisting the Linux h1 subsequently required for the unpacked provider cache. The previous offline test ran on macOS and therefore did not exercise this path.

Regenerated checksums with `terraform -chdir=infrastructure/terraform/aws/bootstrap providers lock -platform=linux_amd64 -platform=darwin_arm64 -platform=linux_arm64`. Terraform reported all three packages signed by HashiCorp. Retained AWS6.15.0, the original macOS hash and every original archive hash; added only two Linux unpacked hashes. Applied the same verified lock to first-account, whose sole provider/version/constraint match exactly. No checksum bypass, upgrade, cache deletion, state mutation or provisioning change.

Changed first-account PR validation so bootstrap and first-account init use readonly locks on Linux before validate/test, matching provisioning. Other module init behavior is preserved. Added runbook diagnosis and instructions to commit/push and start a NEW workflow run on the corrected revision; rerunning a failed old revision cannot pick up this fix.

Files: infrastructure/terraform/aws/bootstrap/.terraform.lock.hcl; infrastructure/terraform/aws/first-account/.terraform.lock.hcl; .github/workflows/aws-first-account-validate.yml; docs/runbooks/aws-first-account-actions.md; IMPLEMENTATION_STATUS.md; this report. No API, event, schema, state, IAM or credential changes. No architecture/ADR change.

## Verification

- Official registry provider-lock command: PASS, evidence .build/aws-provider-lock003/providers-lock.log.
- `python3 -m unittest discover -s scripts/infra/tests -p 'test_first_account*.py'`: PASS18. Evidence helper-tests.log.
- `bash scripts/infra/tests/workflows-test.sh`: PASS YAML/pins/permission boundaries, evidence workflows.log.
- Linux regression result recorded below. Disposable official Terraform1.12.2 Linux AMD64 container, pinned image hashicorp/terraform@sha256:f5ac787eee9d292b6a3b97d40f04019ce08189d356233fc73d5ec7ef8529cce2. Only a sanitized Terraform source copy is mounted read-only: no credentials, provider caches, state or application files. Fresh temporary backend-disabled copies use a temporary provider cache. Original HEAD bootstrap lock is tested first, then corrected bootstrap and first-account locks with readonly init/validate and mock tests; cmp confirms readonly operations do not rewrite locks. Exact runner .build/aws-provider-lock003/linux-check.sh, output linux-regression.log.

Initial reported CI failure is retained here; no live workflow rerun or AWS apply was performed. No broad application tests required for checksum/CI changes. `git diff --check`: pending final check.

Base commit:935952f (actual full hash recorded below). No acceptance commit created. Next dependency-ready step: push reviewed lock/CI changes and start a new bootstrap workflow run from the updated default branch. Real GitHub/AWS qualification remains pending.
