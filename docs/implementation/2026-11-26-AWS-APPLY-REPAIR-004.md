# Session report: AWS-APPLY-REPAIR-004

Date:26 November2026, America/New_York. Specification1.1.0. Phase:BP1 first-account infrastructure. Status:code and partial-apply recovery verified offline; actual updated AWS apply and acceptance commit pending.

## Reported failures and resulting behavior

The user supplied a real first-account apply failure in us-east-2: operator-alert SNS rejected sns:* in a topic policy; application CloudWatch groups already existed because Compute and Observability declared identical names; identity RDS PostgreSQL log-group creation was rejected by KMS because the application's encryption context covered only /nigson/<environment>/* logs.

Replaced SNS wildcard administration with the explicit eight supported topic actions while retaining exact topic ARN, same-account principal and CloudWatch publish restriction to environment alarms. Compute is now the sole first-account application log-group owner. Observability optionally accepts the complete phase/environment Compute group map; existing standalone callers retain their prior default behavior. Four declarative moved blocks preserve any previously Observability-owned Commerce/Experience/Integration/AI group state at Compute addresses. If groups already belong to Compute, those source moves are no-ops. No groups are deleted or recreated.

Extended the application KMS Logs context allowlist with only the exact environment identity RDS PostgreSQL log ARN. The regional Logs principal, original application-log prefix, account/region scope, encryption and rotation remain. This is an in-place policy update, not a new key or disabled encryption.

Added regression coverage to the first-account Actions matrix for Secrets and Observability. Root tests now verify supported SNS actions/publisher scope, shared group names and actual mocked partial-apply state migration. A JSON plan verifier rejects duplicate log-group names, create/delete/replace during the move, changed IDs/ARNs/names or incorrect source addresses. Five unit tests exercise that verifier's positive and negative paths. CI runs the verifier against real Terraform mock plan JSON, using streaming input to avoid retaining repeated provider schemas.

## Files changed

- infrastructure/terraform/aws/first-account/main.tf
- infrastructure/terraform/aws/first-account/tests/first-account.tftest.hcl
- infrastructure/terraform/aws/first-account/tests/fixtures/legacy-logs/main.tf
- infrastructure/terraform/aws/modules/compute/main.tf
- infrastructure/terraform/aws/modules/observability/main.tf
- infrastructure/terraform/aws/modules/observability/tests/phases.tftest.hcl
- infrastructure/terraform/aws/modules/secrets/main.tf
- infrastructure/terraform/aws/modules/secrets/tests/secrets.tftest.hcl
- scripts/infra/tests/verify_log_group_moves.py
- scripts/infra/tests/test_log_group_moves.py
- .github/workflows/aws-first-account-validate.yml
- docs/runbooks/aws-first-account-actions.md
- docs/adr/ADR-035-aws-first-account.md
- IMPLEMENTATION_STATUS.md
- this report

No API/event/database-schema change, provider upgrade, credential change, owner-domain boundary change or actual AWS mutation. Prior uncommitted AWS-PROVIDER-LOCK-003 status/report updates were preserved.

## Checks and evidence

All evidence paths below are under .build/aws-apply-repair004. Terraform provider IPC used approved execution; all test providers are mocked and no AWS credentials/backend were used.

- terraform validate for first-account, modules/compute, modules/observability and modules/secrets: PASS (root-validate-fixed.log and module *-validate.log).
- `terraform -chdir=infrastructure/terraform/aws/first-account test -verbose -json`: PASS6/no skips, root-tests-final.jsonl. Initial setup mock apply targets only old Observability application groups in isolated in-memory state, then a shared-state root plan uses the actual production moved blocks. Mock teardown succeeded.
- `python3 scripts/infra/tests/verify_log_group_moves.py .build/aws-apply-repair004/root-tests-final.jsonl`: PASS, unique fresh group names plus3 moves preserving exact pre/post IDs/ARNs/names with update-only actions. Evidence log-migration-proof-final.log.
- `terraform -chdir=infrastructure/terraform/aws/modules/observability test`: PASS11; secrets test:PASS3; compute test:PASS4. Total Terraform24/no skips. Evidence observability-tests.log, secrets-tests.log, compute-tests.log.
- `python3 -m unittest discover -s scripts/infra/tests -p test_log_group_moves.py`: PASS5, migration-verifier-final.log.
- `python3 -m unittest discover -s scripts/infra/tests -p 'test_first_account*.py'`: PASS18, pipeline-tests.log. Total Python23.
- `bash scripts/infra/tests/workflows-test.sh`: PASS YAML/action pins/permission and manual dispatch boundaries; workflows.log.
- terraform fmt -check for all edited Terraform files including test fixture:PASS; git diff --check:PASS.

Failure history retained: initial root argument was inserted into the wrong module and validate rejected it; corrected to Observability. Initial policy assertions had unknown computed SNS ARN during plan; a plan-time SNS resource override fixed fixture availability without changing production conditions. Initial direct equality compared Terraform object and map types; converted both to maps, retaining exact expected names. Earlier root-tests-migration.jsonl/root-tests-fixed.jsonl and first-account-validate.log retain these failures; final tests all pass. Expected fixture targeting warnings are isolated mock setup, not a live repair instruction.

No whole-repository Java/frontend suite rerun, because application code/contracts are unchanged. Cloud AWS API policy acceptance, live state ownership and actual updated apply NOT_RUN by this session. Offline mocks do not claim service-side or deployed acceptance.

Base commit:c840279259f8fdf7a4d7d126c47a42dcfe743ed0. No commit created by the agent.

## Recovery and next task

Push the reviewed changes, then start a NEW bootstrap first AWS account workflow on the updated default branch: action plan, inspect policy updates/log address moves, then action apply. Keep the same AWS account, us-east-2 and existing remote state. Do not rerun bootstrap or delete logs, keys, databases or Terraform state. Existing no-delete/runtime gates remain enabled; address moves are update/no-op actions.

Automatic moves handle resources already tracked at either Compute or Observability addresses. Truly untracked/manually created groups need ownership verification and explicit import into the existing first-account state; never blindly adopt a group owned by another state. Exact instructions are in docs/runbooks/aws-first-account-actions.md. If KMS still rejects the request after the scoped policy is applied, inspect account/SCP/key-region restrictions instead of widening access blindly.

Next dependency-ready task: reviewed first-account live plan/apply, followed by actual AWS log encryption, alarm delivery and resource/state evidence. Application/provider activation remains separately gated.

References: [SNS topic actions](https://docs.aws.amazon.com/sns/latest/dg/sns-access-policy-language-api-permissions-reference.html), [CloudWatch Logs KMS context](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html), [Terraform shared test state](https://developer.hashicorp.com/terraform/language/tests).
