# AWS-FIRST-ACCOUNT-001

Implemented 25 November 2026; started 24 November. Base commit: `2241a375d1618250008b5e7b9e733269061fd664`. No acceptance commit created. Status: implementation verified offline; deployment acceptance pending.

## Scope and decisions

User requested Terraform provisioning into an empty AWS account and confirmed nigson.com is registered at Squarespace. ADR-035 records the architecture. Initial staging uses stage.nigson.com so apex and email DNS remain under existing registrar control. No console-created AWS resource prerequisites. AWS account ID alone is not authentication: CLI/SDK credentials require an access key ID plus secret access key, or SSO. No secrets are written into Terraform variables or generated files.

Added bootstrap and first-account roots, pinned provider locks, tests, runtime input example and step-by-step first-account README. Bootstrap creates protected private/versioned/encrypted state storage and DNS. Platform composes network, security groups, regional/global certificates and WAF, ALBs, private RDS, ECS/ECR, identity, secret metadata, storage, frontend, messaging and monitoring. ACM validation depends on actual Squarespace delegation. Application/identity activation is separate from infrastructure and requires real artifacts, secrets and owner database/identity readiness. No placeholder images or fake provider success.

Existing compute/identity modules retain provision-tasks defaults; identity address changes have moved blocks. Compute supports method-specific review read routes. Frontend exports its distribution ARN; storage can omit unapproved object expiry without changing existing defaults. Migration launcher accepts only explicit supported roots. Added nonsecret input/artifact helper, focused CI and runbook/master traceability. No API, event, migration or transaction-boundary changes.

## Verification

- Terraform validate: bootstrap and first-account PASS.
- Terraform mock tests: bootstrap2, first-account2, compute4, identity9, frontend4, storage2: 23 passed. No real AWS calls/deployment.
- `python3 -m unittest discover -s scripts/infra/tests -p test_first_account.py`: 5 passed.
- `bash scripts/infra/tests/run-owner-migration-test.sh`: PASS, including alternate root and environment rejection.
- `git diff --check`: PASS. Terraform formatting checked separately.

Evidence: `.build/aws-first-bootstrap-tests-second.log`, `.build/aws-first-platform-tests-complete.log`, `.build/aws-first-platform-validate-final.log`, `.build/aws-first-compute-tests-verified.log`, `.build/aws-first-identity-tests.log`, `.build/aws-first-frontend-tests.log`, `.build/aws-first-storage-tests.log`. New-root validation/mock tests were repeated successfully at handoff.

Failure history retained: sandbox provider IPC initially failed; approved offline Terraform execution resolved it. Initial bootstrap mock apply teardown conflicted with prevent_destroy, so bootstrap tests now inspect mock plans. Initial list comparison and heterogeneous conditional map type errors were corrected; successful subsequent logs supersede these failures without removing history.

## Remaining acceptance and next task

Follow `infrastructure/terraform/aws/first-account/README.md`: configure local AWS CLI profile, review/apply bootstrap plan, add stage NS records in Squarespace, generate backend inputs, then review/apply platform plan. Actual DNS delegation, AWS plans/applies, email subscription confirmation, real image publication, database role bootstrap/migrations, secret values, Keycloak realm/MFA provisioning, frontend release and runtime/provider qualification are NOT_RUN. Secret metadata is not a configured business integration. Runtime environment example is a shape, not a complete deployable secret/config set. Required application storage permissions and provider egress remain explicit activation inputs.

No live AWS resources were created, no credentials supplied, and no cloud/provider acceptance claimed. Existing user application changes and .env were preserved. Next dependency-ready work is reviewing the bootstrap plan with the user's AWS profile; acceptance commit and deployed health/security/provider evidence remain required before DONE.
