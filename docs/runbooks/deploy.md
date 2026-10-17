# Deployment and infrastructure plans

Stage and production have separate Terraform roots and remote state keys. The
AWS provider rejects credentials for any account other than the explicitly
configured environment account. The dispatch-only plan workflow obtains short-
lived stage/prod role credentials through OIDC and creates a read-only Terraform
plan; it has no apply command. Configure the environment's plan role trust to a
specific repository/environment subject and grant only state lock/read plus
AWS plan permissions. Supply the remote backend bucket, region, key,
encryption and lockfile settings to `terraform init` from the protected pipeline;
never commit backend config, environment values, state or plans containing
sensitive values. Configure encrypted S3 state and S3 lockfile locking in the
state account before the first plan. Separate stage and production accounts and
short-lived GitHub OIDC roles are required.

Before planning, provide reviewed non-secret `TF_VAR_*` values for the target
region, account ID, policy revision, VPC range, AZs and per-AZ edge/application/
data CIDRs; product phase, private namespace, KMS key ARN, WAF ACL ARN, TLS
certificate ARN, approved log retention, measured service CPU target, DB
connection budget, service image digests, non-secret environment values, secret
ARN references, explicit service egress CIDRs and allowlisted public route map.
Every service CPU/memory pair and max-replica DB pool is validated against the
aggregate reserved database budget. The plan must be reviewed for CIDR overlap,
endpoint availability, NAT cost, external egress, WAF/TLS inputs, replacements
and destructive operations. S3 bucket names, CloudFront distribution binding,
exact task-role/purpose prefixes and all current/noncurrent/multipart lifecycle
periods are explicit; no task role is granted delete access. RDS requires an explicit PostgreSQL 18 minor,
instance class, storage ceiling, backup/log retention and deletion protection.
A plan is not deployment
authority. Production apply remains subject to the repository's protected
production environment and release decision.

Example offline syntax check (does not contact AWS):

```sh
terraform -chdir=infrastructure/terraform/modules/network fmt -check -recursive
terraform -chdir=infrastructure/terraform/environments/stage fmt -check -recursive
terraform -chdir=infrastructure/terraform/environments/prod fmt -check -recursive
```

Before any stage apply, build and publish an immutable service image digest,
generate and retain its SBOM and vulnerability scan, apply only reviewed
expand-compatible migrations, verify private task reachability and public
`/internal/**` denial, then run health/readiness and API contract smoke checks.
For rollback, stop rollout and restore the previous task-definition image digest
only when schema changes remain backward compatible; repair forward when data
has crossed an incompatible boundary. Record plan and image digests, migration
IDs, smoke results and recovery action without credentials or customer data.

No cloud staging smoke, rollback or production deployment is claimed by the
offline module work. They require a provisioned account, approved network and
retention policy, TLS certificates, deployment role, operator alert destination
and a reviewed release plan.

## Immutable image publication

After Terraform has created the environment's immutable ECR repositories, run
the manual `publish infrastructure images` workflow in that environment. It
uses only a short-lived GitHub OIDC role (`AWS_PUBLISH_ROLE_ARN`) and verifies
the returned AWS account against `AWS_EXPECTED_ACCOUNT_ID`. Scope the role to
ECR upload and scan-read actions for that environment's commerce, experience,
integration, identity, and BP5-only AI repositories. Do not grant ECR delete,
Terraform, ECS deployment, IAM, or secret-read permissions.

Choose the ECS architecture explicitly. The workflow builds the app,
commerce migration, and Keycloak images for that architecture, tags them with
the source commit and architecture, and uses BuildKit to attach SBOM and
provenance attestations. ECR scan-on-push results must reach COMPLETE with no
HIGH or CRITICAL findings or the run fails. Existing immutable tags are reused
and rechecked rather than overwritten. The workflow run summary records each
repository, tag, content digest, and scan counts; use those digests in a
separately reviewed Terraform plan. Keep every task definition's CPU
architecture aligned with its image set. AI is built only when product phase 5
is explicitly selected and its ECR repository exists.

ECR lifecycle expiry is intentionally not configured until an approved image
and attestation retention period exists. Do not manually delete image tags or
untagged attestation manifests as part of routine cleanup.

Image publication does not deploy or run migrations. Provision each owner's
`migration_password` secret out of band with the matching database role, and
bootstrap those roles using the reviewed database-owner procedure before
running application migrations. The migration task can read only its owner's
secret. It runs the commerce image at the reviewed immutable digest, connects
to the private commerce RDS hostname with `sslmode=verify-full`, and is launched
inside the VPC without a public IP. The production target guard rejects local,
non-RDS, mismatched-host, and non-TLS endpoints. AI migrations exist only at BP5.

After reviewing and applying the environment's Terraform plan, launch one owner
at a time from the repository root with credentials for that environment:

```sh
AWS_REGION=<environment-region> AWS_EXPECTED_ACCOUNT_ID=<12-digit-account> \
  scripts/infra/run-owner-migration.sh stage commerce
```

Use `stage` or `prod` and one of `commerce`, `experience`, or `integration`;
include `ai` only at BP5. The launcher checks the account and environment,
submits exactly one ECS task, disables public IP assignment, and waits for the
container exit. A timeout, ECS launch failure, or missing/nonzero exit is an
ambiguous result: inspect the task and CloudWatch logs plus the owner's
`databasechangelog` before deciding whether another run is safe. The launcher
never retries automatically. The operator's ECS launch role must be limited to
these exact task definitions and `iam:PassRole` for the migration task and
execution roles; the task role has no application permissions.

Only after every required owner migration has a confirmed exit code of zero
should the separately reviewed application deployment proceed. Use the manual
`deploy reviewed stage release` workflow, whose `stage` GitHub environment must
have required reviewers and branch restrictions configured. Commit both
reviewed JSON manifests beneath `docs/releases/stage/`; enter their
repository-relative paths at dispatch. The workflow accepts only those paths,
uses short-lived OIDC credentials, and checks the resolved AWS account. Its
`AWS_DEPLOY_ROLE_ARN` must be limited to the stage cluster/services, named task
definitions and required `iam:PassRole` ARNs. It must not have Terraform,
Secrets Manager, IAM administration, or production access. For local operator
execution, use `scripts/infra/deploy-stage.sh <release.json> <rollback.json>`
with stage-scoped credentials, `AWS_REGION`, and `AWS_EXPECTED_ACCOUNT_ID`.
The release manifest
must name the stage cluster, product phase, exact required owner list, each
successful migration task ARN and zero exit code, immutable service task
definition ARNs and matching ECR image digests, plus HTTPS health URLs. The
rollback manifest must contain the previously reviewed task definition for
each service. The script checks the AWS account and actual stopped migration
tasks, verifies task definition image digests, rolls services, waits for ECS
stability, checks HTTPS health, and submits the reviewed prior task definitions
if rollout or health fails. Rollback is refused unless it covers the same exact
service set. The ECS deployment role should be restricted to these stage
services and task definitions. It should not have Terraform, Secrets Manager,
IAM administration, or production permissions.

Run `scripts/infra/smoke-stage.sh <approved-smoke.json>` from the required
network vantages. Its JSON lists private service readiness URLs that must return
HTTP 200 and public `/internal` URLs that must return HTTP 404. Run private
probes inside the VPC and public-denial probes outside it; a failed connection
is never treated as a successful denial. Record both vantage locations and
results before accepting stage. The smoke runner validates targets but cannot
create or prove the network vantage itself. For rollback, restore the previous
task-definition image digest only when schema changes remain backward
compatible; repair forward when data has crossed an incompatible boundary.
Record plan and image digests, migration IDs, smoke results and recovery action
without credentials or customer data.

No cloud staging smoke, rollback or production deployment is claimed by the
offline module work. They require a provisioned account, approved network and
retention policy, TLS certificates, deployment role, operator alert destination
and a reviewed release plan. A successful image scan or local Terraform test is
not deployment, migration, restore, or endpoint acceptance.
