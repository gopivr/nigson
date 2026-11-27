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

## Environment-specific configuration

Stage and production use the same module source but never share values. The
`stage` and `prod` GitHub Environments hold independent repository variables;
the plan workflow maps them into `TF_VAR_*`, and the release workflow maps them
into `AWS_*`. Configure at least:

| Setting | Stage | Production |
| --- | --- | --- |
| GitHub Environment | `stage` with branch restrictions and reviewers | `prod` with a separate required approval and no bypass |
| AWS account | dedicated stage account | dedicated production account |
| `AWS_REGION` | stage region | production region |
| `AWS_EXPECTED_ACCOUNT_ID` | stage account ID | production account ID |
| Deployment OIDC role | bootstrap-managed `nigson-stage-github-deploy` | separately bootstrapped `nigson-prod-github-deploy` |
| `TF_STATE_BUCKET` and key | `nigson/stage/terraform.tfstate` | `nigson/prod/terraform.tfstate` |
| `TF_VAR_*` network, phase, sizing, retention and route values | stage inputs | independently reviewed production inputs |
| Resource names | `nigson-stage-*` | `nigson-prod-*` |
| ECR repositories | `nigson/stage/*` | `nigson/prod/*` |
| Private service namespace | `*.svc.stage.nigson.internal` | `*.svc.prod.nigson.internal` |
| Release selection | latest successful stage image artifact | latest successful prod image artifact |

Copy `environments/<environment>/backend.hcl.example` to an untracked backend
configuration for that environment's state. Never point stage and production at
the same state key, account, role, bucket, or ECR repository.

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
terraform -chdir=infrastructure/terraform/aws/modules/network fmt -check -recursive
terraform -chdir=infrastructure/terraform/aws/environments/stage fmt -check -recursive
terraform -chdir=infrastructure/terraform/aws/environments/prod fmt -check -recursive
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
the manual `Backend — Build and Publish Images` workflow in that environment. It
uses the bootstrap-managed `nigson-<environment>-github-publish` OIDC role and verifies
the returned AWS account against `AWS_EXPECTED_ACCOUNT_ID`. Bootstrap scopes the role to
ECR upload/pull and scan-read actions for that environment's commerce, experience,
integration, identity, and BP5-only AI repositories. Do not grant ECR delete,
Terraform, ECS deployment, IAM, or secret-read permissions.

Choose the ECS architecture explicitly. The workflow builds the app,
commerce migration, and Keycloak images for that architecture, tags them with
the source commit and architecture, and uses BuildKit to attach SBOM and
provenance attestations. ECR scan-on-push results must reach COMPLETE (or enhanced ACTIVE with a completed
initial scan) with no HIGH or CRITICAL findings, or the run fails. Attested image
indexes are resolved to their selected Linux platform digest for scanning; the
summary retains both the published index digest and scanned image digest. Existing immutable tags are reused
and rechecked rather than overwritten. The workflow run summary records each
repository, tag, content digest, and scan counts. A complete `release.json`
artifact binds all required images to that successful publication. Keep every task definition's CPU
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

## Deploy the latest backend artifacts

Run **Backend — Build and Publish Images**, then **Backend — Deploy Release**.
Select only `stage` or `prod` in the deploy workflow. It selects the latest successful
manual publication on the default branch with a complete matching-environment
`backend-release-…` artifact. Expired artifacts require another publication; builds
from before this artifact contract are not eligible. No committed release/rollback
manifests are needed by this workflow.

The selected GitHub Environment supplies `AWS_REGION` and `AWS_EXPECTED_ACCOUNT_ID`
(or the `AWS_ACCOUNT_ID` secret). Configure environment reviewers and branch restrictions;
production requires its own approval. Bootstrap creates the OIDC role automatically.
Rerun bootstrap once to install the updated task-registration and migration permissions.

Before first deployment, provision healthy active services, reviewed task-family
templates, real secret values and private migration networking/security groups.
Foundation-only infrastructure is inactive and fails deployment preflight before writes.
The workflow checks account, release provenance, complete image cohort, architecture
and fresh ECR scan results. It preserves each latest task template's configuration,
changes the immutable image/architecture, and captures running service revisions for
rollback. Terraform ignores the service task-definition pointer so subsequent
infrastructure applies do not reset a deployed release. Service task definitions
retain old revisions for rollback after template updates.

All required owner migrations must exit zero before rollout. Identity rolls first,
then application services. ECS must report the candidate revision healthy and stable;
a circuit breaker returning to an older revision is a failed release. Application
failures restore captured revisions. Identity schema rollback requires manual
compatibility review; it is never blindly downgraded. Migration timeout or ambiguous
launch is recorded as `UNKNOWN`: inspect the ECS task, CloudWatch logs and owner
`databasechangelog` before deciding whether to rerun. Do not blindly retry migrations.

The `backend-deployment-…` artifact contains image digests, task ARNs and outcomes.
It excludes task-template contents and secret values. Publication and deployment
artifacts are retained for 30 days. The legacy `deploy-environment.sh` utility remains
available for explicitly reviewed local manifest-based operations; the GitHub workflow
does not use it. Successful ECS rollout does not replace the network/API acceptance
checks below.

Run `scripts/infra/smoke-environment.sh <stage|prod> <approved-smoke.json>` from
the required network vantages. Its JSON lists private service readiness URLs
that must return HTTP 200 and public `/internal` URLs that must return HTTP 404,
and it rejects a spec whose `environment` or private service hostname does not
match the selected environment. Run private probes inside the VPC and
public-denial probes outside it; a failed connection is never treated as a
successful denial. Record both vantage locations and results before accepting
the environment. The smoke runner validates targets but cannot create or prove
the network vantage itself. For rollback, restore the previous task-definition
image digest only when schema changes remain backward compatible; repair forward
when data has crossed an incompatible boundary. Record plan and image digests,
migration IDs, smoke results and recovery action without credentials or customer
data.

No cloud staging smoke, rollback or production deployment is claimed by the
offline module work. They require a provisioned account, approved network and
retention policy, TLS certificates, deployment role, operator alert destination
and a reviewed release plan. A successful image scan or local Terraform test is
not deployment, migration, restore, or endpoint acceptance.

## Stage RDS restore qualification

Perform restore qualification only against a separate stage restore instance;
never point the verifier at `nigson-stage-commerce` or production. Select and
record an approved point-in-time before restore. Use the stage subnet group and
KMS key, `PubliclyAccessible=false`, and a dedicated restore database security
group that admits only the one-time restore auditor security group on TCP 5432.
The auditor group must have no ingress. Keep the restored endpoint isolated from
application and migration task security groups. Do not delete the source or
restored instance as part of verification.

Provision a read-only `RESTORE_AUDIT_DB_USER` outside Terraform, its password in
a `PGPASSFILE` with mode 0600, and the approved RDS CA file as `PGSSLROOTCERT`.
Run from the dedicated auditor network vantage with the expected account, region,
KMS key ARN, isolated database/auditor security group IDs, stage database subnet
group, and declared product phase set explicitly:

```sh
AWS_REGION=<stage-region> AWS_EXPECTED_ACCOUNT_ID=<12-digit-stage-account> \
AWS_EXPECTED_KMS_KEY_ARN=<stage-kms-key-arn> \
AWS_EXPECTED_RESTORE_SECURITY_GROUP_ID=<isolated-restore-db-sg> \
AWS_EXPECTED_RESTORE_AUDITOR_SECURITY_GROUP_ID=<restore-auditor-sg> \
AWS_EXPECTED_DATABASE_SUBNET_GROUP=nigson-stage-commerce \
RESTORE_AUDIT_DB_USER=<read-only-auditor-role> RESTORE_PRODUCT_PHASE=<1-5> \
PGPASSFILE=<protected-passfile> PGSSLROOTCERT=<approved-rds-ca.pem> \
scripts/infra/verify-rds-restore.sh nigson-stage-restore-<approved-id>
```

The verifier is read-only. It confirms the target is available, private,
encrypted with the expected KMS key, in the expected stage subnet group, and
uses the dedicated security groups. It then connects with `verify-full` TLS and
checks expected owner schema ownership and Liquibase history, including AI at
BP5. Record the selected restore time, source snapshot/instance reference,
restored identifier, verifier output, application/API recovery evidence and
rollback decision in the release record. This verifier alone does not prove
application recovery or restore-point completeness; retain the restored target
until those checks pass and remove it only through the approved cleanup change.


## First-account path

For a new account/environment without any pre-created AWS resources, see
`infrastructure/terraform/aws/first-account/README.md` and ADR-035. The bootstrap
root creates remote-state storage and DNS, and the first-account root creates
certificates, WAF, networking, private databases and image-free compute
infrastructure. AWS credentials use the normal CLI profile chain. Domain
delegation stays with the registrar. Existing environment roots keep their state
and addresses; never apply both roots to the same live resources. With the new
root, `NIGSON_TERRAFORM_ROOT=infrastructure/terraform/aws/first-account` selects
its guarded migration outputs. Application/identity activation, real secret values,
owner DB bootstrap, migration receipts and release/provider acceptance remain
explicit release gates; infrastructure provisioned is not application deployed.

First-account staging can also be provisioned through the manual GitHub workflow `backend-aws-infrastructure.yml`, using repository AWS credentials and durable S3 bootstrap state. Follow [the Actions bootstrap guide](aws-first-account-actions.md). This foundation workflow does not activate application releases or replace the existing environment release process.
