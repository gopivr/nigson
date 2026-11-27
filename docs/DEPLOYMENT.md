# Deploy to AWS staging

Use the GitHub repository `gopivr/nigson`. Staging uses `us-east-2`,
https://stage.nigson.com and https://api.stage.nigson.com.

## 1. GitHub settings

In **Settings → Secrets and variables → Actions**, configure:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `AWS_ACCOUNT_ID` | Twelve-digit account ID |
| Secret | `AWS_ACCESS_KEY_ID` | Authorized provisioner's access key ID |
| Secret | `AWS_SECRET_ACCESS_KEY` | Matching secret key |
| Secret | `AWS_SESSION_TOKEN` | Only for temporary credentials |
| Variable | `AWS_REGION` | `us-east-2`; keep the existing infrastructure region |
| Variable | `NIGSON_GOOGLE_RECAPTCHA_SITE_KEY` | Public site key for stage.nigson.com |

The workflows use the `stage` GitHub Environment. Its protection rules and
AWS secrets apply to deployment. Put the public site key in a repository variable
because the build runs outside that environment. No cross-repository GitHub token
is required. Never place AWS keys or the private reCAPTCHA key in frontend code.
The provisioner's IAM permissions and organization policies must allow the resources.

## 2. Create infrastructure

Open **Actions → Backend — AWS Infrastructure → Run workflow**, select `main`:

1. Choose **bootstrap**. This creates the state bucket and staging DNS zone.
2. In Squarespace DNS custom records, add the four NS records shown in the run
   summary with host **stage**. Keep the root domain nameservers unchanged.
3. Wait for DNS propagation, then run the same workflow with **plan**.
4. Review the plan and run it with **apply**.

Terraform provisions the VPC, security groups, certificates, load balancers and
other platform resources. Application activation is a separate step.
State remains in the existing account/region-specific bucket at:

- `nigson/bootstrap/stage/terraform.tfstate`
- `nigson/first-account/stage/terraform.tfstate`

Do not point the legacy environment roots at this first-account installation.
See [first-account infrastructure](../infrastructure/terraform/aws/first-account/README.md)
for configuration and [application deployment](runbooks/deploy.md) for runtime activation.

## 3. Publish the UI

Merge UI changes to `main`, or run **Actions → Frontend — Build and Deploy Staging**
on the default branch. Pull requests test/build without deploying.

The workflow tests and builds the shell and all remotes, seals a static artifact,
then publishes it to an immutable S3 prefix. It updates only the managed
CloudFront static origin path through Terraform and verifies the deployed files
over HTTPS. It preserves backend configuration from the applied state snapshot.
Disable the old standalone UI repository's deployment workflow to avoid competing
releases. Production is not deployed by the staging UI workflow.

## Troubleshooting

- **Missing deployment_configuration:** run infrastructure **plan**, review it,
  then **apply** once from the updated default branch; rerun the failed UI deploy
  job. **bootstrap** alone does not record the platform output. If runtime tasks
  are already active, preserve their complete inputs using the runtime deployment
  procedure; the foundation workflow refuses runtime changes.
- **AccessDenied with explicit SCP deny:** the AWS organization policy must be
  corrected by its administrator. IAM allow permissions cannot override it.
- **Certificate pending:** check the Squarespace `stage` NS delegation.
- **Missing public site key:** set the repository variable before rerunning build.
- **Release plan changes CloudFront settings beyond the static release path:** the
  guard now prints changed field paths without their values. Review those fields
  in the Terraform resource diff. A single changed distribution can include
  several changed settings. The guard treats empty/null optional origin fields
  equivalently for AWS provider 6.15; configured OACs, headers, IP types, timeouts,
  backend paths and TLS/WAF settings remain protected.
  Share the field paths for diagnosis; do not upload
  the full state or plan JSON. The targeting warning alone does not cause this error.
- **Unexpected Terraform changes:** reconcile the infrastructure plan before retrying;
  do not bypass the UI release guard or change the state key/account/region.

Rollback a UI change by reverting it on `main` and deploying the resulting new
release. Existing immutable release objects are retained.

## Other retained workflows

| Workflow | Purpose |
| --- | --- |
| `backend-infrastructure-validate.yml` | Offline validation of first-account Terraform and helpers |
| `backend-images-publish.yml` | Publish immutable backend images; requires configured OIDC role |
| `backend-deploy.yml` | Deploy reviewed backend manifests; requires runtime setup |

Backend image publishing and deployment require the configured application
release roles and manifests. They are not prerequisites for publishing the static
staging UI. Infrastructure planning uses **Backend — AWS Infrastructure → plan**.
