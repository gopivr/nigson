# Session report: AWS-BOOTSTRAP-CICD-002

Date: 26 November 2026, America/New_York. Specification1.1.0. Phase: BP1 infrastructure. Status: implementation verified offline; first live GitHub/AWS execution and acceptance commit remain pending.

## Result

Added a manual default-branch GitHub Actions workflow for first-account staging: bootstrap, plan and apply. Authentication uses AWS_ACCOUNT_ID, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY repository/environment secrets, plus optional AWS_SESSION_TOKEN. An account ID and secret alone do not authenticate; access key ID is required. Region defaults eu-west-1. No pre-existing VPC, state bucket, certificates, ALBs or GitHub OIDC role required. Existing application workflows retain their own activation/OIDC setup.

New orchestration verifies STS identity before state access, creates only the state bucket in a targeted Terraform bootstrap apply, then migrates bootstrap state to encrypted/locked S3 before applying remaining bootstrap resources. Later hosted runners initialize directly against remote state. Interrupted bucket-only creation is recoverable by importing a recognized owned/tagged bucket; existing public stage DNS without CI state requires migration of its actual owning local state. Concurrently appeared state is not overwritten. Forbidden/network/expired-credential failures are not empty-account signals.

Squarespace delegation of stage.nigson.com remains a necessary external action. Workflow summary displays actual Route53 nameservers; apply requires matching public NS answers. Plan never applies platform/bootstrap resources. Apply generates and validates a fresh plan then applies that exact saved binary. Foundation plan guard refuses deletions/replacements and ECS task definitions/services, including existing runtime resources. This prevents this workflow from silently disabling later application activation. No secret values, fake images, application tasks, storefront release or provider credentials are created. State/plan binaries are not GitHub artifacts.

Manual staging runs serialize and do not cancel one another. Credentials are absent from PR/offline jobs. The credential action, checkout and Terraform setup references are SHA pinned; secrets are provided via action inputs/environment, never command-line values or Terraform/backend variables. No new approval requirement was introduced; existing stage environment protection still applies.

## Files changed

- .github/workflows/aws-first-account-bootstrap.yml
- .github/workflows/aws-first-account-validate.yml
- scripts/infra/first-account-pipeline.py
- scripts/infra/tests/test_first_account_pipeline.py
- scripts/infra/tests/workflows-test.sh
- docs/runbooks/aws-first-account-actions.md
- docs/runbooks/deploy.md
- infrastructure/terraform/aws/first-account/README.md
- docs/adr/ADR-035-aws-first-account.md
- IMPLEMENTATION_STATUS.md
- docs/implementation/2026-11-26-AWS-BOOTSTRAP-CICD-002.md

Contracts/migrations/events: unchanged. Existing local bootstrap Terraform root and default module behavior remain supported. ADR-035 records the CI state ownership extension. Pre-existing .env edits were preserved.

## Validation and evidence

- `python3 -m unittest discover -s scripts/infra/tests -p 'test_first_account*.py' -v`: PASS18, comprising5 existing input/artifact helpers plus13 new orchestration tests. Evidence .build/aws-bootstrap-cicd002/helper-tests.log. Covers first and repeat runs, interrupted bucket import, foreign/unrecognized resources, concurrent state, wrong account, missing bootstrap, read-only plan, DNS gate, exact saved-plan apply, destructive/runtime rejection and AWS404 versus403/network handling. All AWS/Terraform mutations are simulated in these tests.
- `bash scripts/infra/tests/workflows-test.sh`: PASS, YAML parsing, SHA pinning, old permission boundaries and new manual-only/default-branch/staging/concurrency/no-artifact/no-shell-input-injection boundaries. Evidence .build/aws-bootstrap-cicd002/workflows.log.
- Generated a CI bootstrap working directory using FakeCommands only, then `terraform -chdir=.build/aws-bootstrap-cicd002/validation/bootstrap init -backend=false -input=false -plugin-dir="$PWD/infrastructure/terraform/aws/modules/compute/.terraform/providers"`: PASS using cached pinned AWS provider. This initializes no real backend.
- `terraform -chdir=.build/aws-bootstrap-cicd002/validation/bootstrap fmt -check`: PASS.
- `terraform -chdir=.build/aws-bootstrap-cicd002/validation/bootstrap validate`: PASS; provider IPC used approved offline execution. Evidence terraform-validate.log.
- `terraform -chdir=infrastructure/terraform/aws/bootstrap test`: PASS2 mock plan tests, no AWS calls. Evidence bootstrap-tests.log.
- `git diff --check`: PASS.

No failed acceptance assertion or live AWS mutation in this session. No broad application/backend tests rerun: changes are workflow/orchestration/docs only. GitHub service-side execution, IAM permission qualification, actual state migration, hosted runner behavior, public DNS and live Terraform plans/applies NOT_RUN. Offline evidence does not qualify real provider/deployment acceptance.

Last successful repository commit: b1a5e407c533cc322138f793f98a1ec629f5a60f. No acceptance commit or workflow dispatch created by this session.

## Next task

Merge the reviewed changes into the backend default branch, add the documented AWS repository secrets, run bootstrap, add its NS records in Squarespace, review plan and run apply. Record the first real workflow run and AWS resource/state/DNS evidence before declaring deployment complete. Existing live/manual environments must migrate their bootstrap state rather than be recreated.
