# Build and deploy the UI to AWS staging

The UI repository owns its build/deployment workflow:
`.github/workflows/aws-stage-ui.yml` (**build and deploy UI to AWS staging**).
Backend Terraform remains in `gopivr/nigson`. Neither repository needs to be moved.
This workflow deploys only `stage.nigson.com`; it never activates production.

## 1. Prepare infrastructure once

First merge the backend UI-deployment support into the infrastructure default
branch, including `scripts/infra/deploy-first-account-frontend.py` and the sensitive
`deployment_configuration` Terraform output. Run **bootstrap first AWS account**
with **plan**, then **apply**, using the existing account, region and S3 state.
Do not bootstrap new resources or apply the standalone `aws/frontend/stage` root
over the existing first-account deployment.

This records the complete applied inputs in encrypted Terraform state, so a UI
release can retain existing application activation flags, image digests and
runtime settings. If backend applications are already activated, apply the new
output using their current full Terraform input set; the foundation-only workflow
intentionally refuses runtime changes. Retain the current `frontend_release_id`
when doing subsequent manual full-root/runtime activation applies.

The frontend bucket/distribution must exist, the Squarespace `stage` NS delegation
must resolve, and HTTPS must be ready. The UI workflow discovers the destination
from that same remote state; no bucket or distribution ID needs to be copied into
GitHub variables.

## 2. Configure this UI repository

Open **Settings → Secrets and variables → Actions** in
**TechXplorers1/nigson-micro-frontend**. Backend repository secrets are not inherited.
You may use repository secrets or the UI repository's `stage` environment secrets.

Required **Secrets**:

| Name | Value |
| --- | --- |
| `AWS_ACCOUNT_ID` | Same twelve-digit account used by infrastructure. |
| `AWS_ACCESS_KEY_ID` | Authorized deployment IAM access key ID. |
| `AWS_SECRET_ACCESS_KEY` | Matching secret access key. |
| `INFRASTRUCTURE_READ_TOKEN` | Fine-grained GitHub token with Contents:read access to the private Terraform repository. |
| `AWS_SESSION_TOKEN` | Only when using temporary AWS credentials. |

Create the read token from an authorized GitHub user's **Settings → Developer
settings → Personal access tokens → Fine-grained tokens**. Choose the Terraform
repository's owner (currently `gopivr`), restrict repository access to **nigson**,
and grant **Repository permissions → Contents → Read-only**. No repository write
or workflow-dispatch permission is needed. Store the token only as the UI secret
above; do not paste it into code or chat. Organization restrictions may require
the repository owner's normal token approval process.

Required **Variables**:

| Name | Value |
| --- | --- |
| `AWS_REGION` | Same infrastructure region: `us-east-2` for this setup. |
| `NIGSON_GOOGLE_RECAPTCHA_SITE_KEY` | Real public Google reCAPTCHA site key registered for `stage.nigson.com`. This is the browser site key, not the secret key. |

Optional variables:

- `INFRASTRUCTURE_REPOSITORY`: defaults to `gopivr/nigson`.
- `INFRASTRUCTURE_REF`: pin a reviewed backend commit SHA if desired. Empty uses
  that repository's default branch. The actual checked-out revision is recorded
  in the deployment summary. It must contain the new deployment helper.

The AWS identity needs access to the existing bootstrap/platform state and lock
objects, the owned frontend bucket, CloudFront read/update/invalidation, and
Terraform's read access to frontend dependencies (ACM, WAF, Route53 and S3).
The existing authorized provisioner can perform the first setup; deployment
identities must remain within the account's approved IAM/SCP permissions.
No AWS keys, private GitHub token, reCAPTCHA secret or runtime secrets enter the
browser build. Only the public reCAPTCHA site key is compiled into the UI.

## 3. Run the workflow

After both repository changes are merged and configuration is set, either:

- Push/merge to the UI repository's `main` branch: build and staging deployment
  run automatically.
- Open **Actions → build and deploy UI to AWS staging → Run workflow** on the UI
  default branch.

Pull requests run tests and build, but never receive AWS/private-infrastructure
credentials or deploy. Other branches cannot deploy. Existing `stage` environment
protection rules still apply. A missing/invalid public site key fails the build;
no dummy key is substituted.

The jobs perform these steps:

1. Install dependencies with npm ci on pinned Node24.15.0; run packaging/workflow
   checks and checkout/order regressions.
2. Build the Angular shell and every configured remote in production mode with
   same-origin federation URLs. Seal every file with SHA256 and the UI commit.
3. Transfer only the sealed static artifact to a fresh deployment job. Artifact
   ID comes from the successful build, so retrying only a failed deployment job
   can reuse that exact artifact.
4. Refuse an obsolete UI build if a newer default-branch commit already exists;
   check out the private Terraform tooling with the read-only token.
5. Authenticate to AWS; verify account, state configuration, bucket ownership and
   the managed distribution's exact staging alias.
6. Load the complete applied Terraform input snapshot. Change only
   frontend_release_id and plan the managed CloudFront distribution. A strict
   guard permits only an in-place static origin release-path update; backend
   origin, TLS/WAF/security changes, extra resource changes and replacement fail.
7. Upload into a new immutable S3 prefix, then apply that exact saved plan.
   Existing prefixes (including interrupted uploads) are never overwritten.
8. Wait for CloudFront deployment, invalidate cached paths and wait for completion.
   Verify exact bytes for the shell, federation manifest and remote entries over
   HTTPS. A failed probe fails the job; it does not claim a healthy application.

Release IDs are `<UI-commit-SHA>-<GitHub-run-ID>-<attempt>`. Deployment jobs
serialize within this repository and do not cancel an in-progress release.
Terraform uses the existing encrypted S3 backend/locks; a stale saved plan is
rejected if another repository's Terraform operation has changed state.

Terraform remains the owner of CloudFront. Release activation is an intentionally
scoped plan targeting the distribution, with a stricter post-plan check; it does
not edit CloudFront behind Terraform's back or create a second frontend state.
The infrastructure workflow preserves the currently applied frontend release on
later plan/apply runs, so it does not reset the UI to `initial`.

## Failures and rollback

If `deployment_configuration` is missing/incomplete, first apply the updated
infrastructure using its existing inputs. If the release guard detects frontend
infrastructure drift, reconcile it in the infrastructure workflow, then retry UI
deployment. Do not disable the guard or change state keys/account/region.

Retrying a failed deployment creates a new attempt prefix and does not overwrite
a partial upload. If Terraform apply or a probe fails after activation, inspect
the candidate/previous release and Terraform state; there is no silent automatic
rollback. Old release objects are retained. The normal rollback is a reviewed Git
revert merged to UI main, which builds/deploys a fresh release of the restored code.

Only static UI delivery is qualified by this workflow. Backend images, migrations,
Keycloak login, BFF origin/redirect configuration and real Paystack/PandaGo behavior
remain separate application activation/acceptance steps.
