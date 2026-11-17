# Stage and production deployment environments

Audited the AWS and OVH Terraform roots and the release scripts. Both clouds
already have independent stage/prod roots, backend-key examples (AWS core roots
were missing them), and account-fenced providers. The gap was the release path:
the workflow, deployment script, smoke runner and tests were stage-only, with
`nigson-stage-*`, `nigson/stage/*`, `*.svc.stage.nigson.internal` and
`docs/releases/stage/` hard-coded. A production dispatch could not use the same
reviewed controls, and environment mistakes were not rejected before ECS calls.

Implementation commits `3c5d71e`, `c60880a` and `de58fee`.

## Changes

- Replaced `deploy-stage.sh` with `deploy-environment.sh <stage|prod> ...`.
  It now enforces the selected environment's cluster name, service and task
  definition prefix, ECR repository path, account and region before any ECS
  update, and still blocks rollout until every migration receipt is
  `STOPPED` with exit code 0. Health failure applies the reviewed rollback
  definitions and returns nonzero.
- Replaced `smoke-stage.sh` with `smoke-environment.sh <stage|prod> ...`.
  The spec's `environment` and private `*.svc.<environment>.nigson.internal`
  hostnames must match the selected environment.
- Replaced `deploy-stage.yml` with `deploy-environment.yml`. The dispatch input
  is `stage` or `prod`; the job runs in `environment: ${{ inputs.environment }}`,
  so each target receives only its GitHub Environment's `AWS_REGION`,
  `AWS_EXPECTED_ACCOUNT_ID` and `AWS_DEPLOY_ROLE_ARN`. Manifest paths are
  constrained to `docs/releases/<environment>/`, and concurrency is per
  environment. The workflow remains OIDC-only, read-only for repository
  contents, and cannot run Terraform or read Secrets Manager.
- Added `environments/{stage,prod}/backend.hcl.example` with distinct state keys
  (`nigson/stage/terraform.tfstate`, `nigson/prod/terraform.tfstate`).
- Added `docs/releases/{stage,prod}/README.md` documenting the manifest contract
  and the production approval requirement.
- Updated `docs/runbooks/deploy.md` with the per-environment variable matrix and
  the production environment protection requirements.
- Updated `infra-plan.yml` and `workflows-test.sh` to the environment-neutral
  paths; the workflow test now also rejects any remaining stage-only deployment
  references and verifies the `stage`/`prod` choice and environment-scoped
  variables.

## Verification

- `bash scripts/infra/validate-terraform.sh aws` — PASS; 16 roots initialized
  and validated, 39 mocked Terraform tests passed.
- `bash scripts/infra/validate-terraform.sh ovh` — PASS; 6 roots validated and
  12 mocked Terraform tests passed.
- `bash scripts/infra/tests/workflows-test.sh` — PASS; YAML parses, actions are
  pinned, and the deploy workflow keeps its permission boundaries.
- `bash scripts/infra/tests/deploy-environment-test.sh` — PASS for both stage
  and prod: successful rollout, health-failure rollback, ECS update-failure
  rollback, failed migration receipt blocking all updates, and rejection of a
  release manifest carrying the other environment.
- `bash scripts/infra/tests/smoke-environment-test.sh` — PASS for both stage and
  prod, including private readiness, public `/internal` denial, failed
  connectivity, wrong environment, and a cross-environment spec.
- `bash scripts/infra/tests/run-owner-migration-test.sh` — PASS (unchanged
  launcher already accepted `stage|prod`).
- `ruby -e 'require "yaml"; ...'` parsed the changed workflows; `git diff
  --check` passed.

No AWS or OVH plan/apply, GitHub Environment creation, OIDC role change, image
publication, migration launch, smoke probe or deployment was executed. Those
remain external qualification gates and require the per-environment account,
state bucket, roles, manifests, certificates, network policy and approved
release inputs. OVH frontend publication remains a manual documented procedure;
no cloud resources were changed by this change.
