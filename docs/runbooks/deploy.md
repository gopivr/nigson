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

Image publication does not deploy or run migrations. The production migration
launcher, per-owner credential provisioning and migration-aware ECS sequencing
remain required before a stage rollout can meet AC-INFRA-001. Do not treat a
successful image scan as a deployment or migration acceptance.
