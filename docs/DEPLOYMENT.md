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
   Bootstrap also creates the GitHub OIDC provider and
   `nigson-stage-github-publish` and `nigson-stage-github-deploy` roles.
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

Merge UI changes to `main`, then manually run **Actions → Frontend — Build and Deploy Staging**
on the default branch. Pushes do not start this workflow. Pull requests test/build
without deploying.

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
- **Attestation is not supported for the docker driver:** use the latest
  **Backend — Build and Publish Images** workflow. It creates a `docker-container`
  Buildx builder and selects it explicitly for publication, retaining SBOM and
  provenance. Start a new manual run after the fix is on `main`; an old failed
  run retains its old workflow revision.
- **Timed out waiting for ECR scan:** the publisher resolves the immutable image
  index to the requested Linux platform and scans that image digest. Attestation
  manifests are excluded. The summary records both published and scanned digests.
  It reports permanent AWS scan errors immediately; a remaining timeout includes
  the last status. Check registry scan filters and the named digest in ECR.
  HIGH/CRITICAL findings still block publication. No new IAM permissions are needed.
- **Image has HIGH/CRITICAL findings:** scanning succeeded and blocked the image.
  Download `backend-image-scan-findings-…` from the workflow run's artifacts. It
  contains CVE IDs and package versions; enhanced scans may include fixed versions.
  Use those findings to update affected base-image pins or application dependencies,
  rebuild under a new commit tag and rescan. Do not bypass the gate or overwrite
  existing immutable tags. The Java container security pins are documented in
  [Dependency versions](DEPENDENCY_LOCK.md#production-java-container-security-pins).
  Basic-scan findings may require the linked vendor
  advisory to identify the patched package version.
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

Rollback a UI change by reverting it on `main`, then manually running the frontend
workflow to deploy the resulting new release. Existing immutable release objects are retained.

## Other retained workflows

| Workflow | Purpose |
| --- | --- |
| `backend-infrastructure-validate.yml` | Offline validation of first-account Terraform and helpers |
| `backend-images-publish.yml` | Publish immutable backend images; requires configured OIDC role |
| `backend-deploy.yml` | Deploy latest successful environment image release; requires runtime setup |

Backend image publishing and deployment require the configured application
release roles and manifests. They are not prerequisites for publishing the static
staging UI. Infrastructure planning uses **Backend — AWS Infrastructure → plan**.

## Publish backend images

After merging these changes, run **Backend — AWS Infrastructure → bootstrap**
once, even if the infrastructure already exists. This updates the existing
bootstrap state and adds the GitHub publishing identity; it does not activate
application services. The provisioning identity needs IAM OIDC-provider/role and
inline-policy management permissions. Organization SCPs still apply.

Then run **Backend — Build and Publish Images** on `main`, choose `stage`,
`amd64` for the current first-account X86_64 task definitions, and product phase
`1`. Platform/ECR provisioning must already have completed. The workflow uses
`AWS_REGION` and `AWS_ACCOUNT_ID` from the existing settings (or the optional
`AWS_EXPECTED_ACCOUNT_ID` variable), resolves the bootstrap-managed role ARN and
assumes it through OIDC. No `AWS_PUBLISH_ROLE_ARN` variable or stored publisher
access keys are needed. Image publication does not deploy applications.

Trust is restricted to the exact repository and GitHub Environment. Configure the
`stage` Environment's deployment branch restriction to `main`; the publishing
workflow also rejects non-default branches. Bootstrap records GitHub repository
and owner IDs to support GitHub's immutable OIDC subject format as well as the
legacy named format. The role can push/pull and read scans only in the five
named environment ECR repositories; it cannot deploy ECS, manage IAM, read
Secrets Manager or delete ECR images.

If the account already has a GitHub OIDC provider owned elsewhere, set optional
`GITHUB_OIDC_PROVIDER_ARN` to its ARN **before the initial role bootstrap**. This
reuses it without taking ownership. Do not toggle this setting after bootstrap
has created its own provider; that would propose an ownership/resource change.
When sharing an account across stage/prod roots, reuse the existing provider in
the second root. Provision each environment's role in its own bootstrap state.

## Deploy backend releases

The same **bootstrap** run also creates `nigson-stage-github-deploy`.
**Backend — Deploy Release** resolves its ARN automatically using the selected
environment and account, then assumes it through GitHub OIDC. You do not need
`AWS_DEPLOY_ROLE_ARN`. The workflow accepts the existing `AWS_ACCOUNT_ID` secret
or optional environment-specific `AWS_EXPECTED_ACCOUNT_ID` variable and runs
only from the default branch.

1. Run **Backend — Build and Publish Images** for the target environment. A complete release artifact is published only after every required image passes scanning.
2. Run **Backend — Deploy Release** and select **stage** or **prod**. No manifest paths or image digests are required.
3. Check the job and its `backend-deployment-…` artifact for migration, rollout and rollback results.

Deploy selects the newest successful default-branch image publication with a complete
release artifact for that environment. It pins the artifact ID, verifies its provenance,
rechecks ECR scans, registers immutable task revisions, runs owner migrations, then
updates identity and application services. Failed application rollouts restore the
captured running revisions. Identity schema upgrades require a compatibility review
before rollback; failed or uncertain migrations stop deployment.

Run **bootstrap** once after adopting this workflow to refresh deployment-role
permissions. The role is restricted to the environment's task families, migration
launches, services and required task/execution roles. It cannot create services,
change Terraform, administer IAM, read secrets or publish/delete images.

First-time runtime setup is still required: provision and activate services, populate
real secrets, register approved task templates and configure migration networking.
The foundation-only bootstrap leaves applications inactive; deployment checks this
before making changes. Terraform retains service configuration ownership while the
release workflow owns the deployed task revision. Configuration updates use the latest
registered family template on the next release. See [backend release operations](runbooks/deploy.md).

### First backend deployment

A foundation installation has no application services. Use **Backend — AWS
Infrastructure** with **runtime-plan**, then **runtime-apply**. Supply the same
`runtime_configuration` path for both actions: the existing reviewed, committed,
non-secret Terraform `.tfvars` or `.json` file under
`infrastructure/terraform/aws/first-account/`. The example file is incomplete and
is rejected. Do not commit secret values or use development `.env` files.

The workflow automatically selects the latest complete successful staging image
publication and verifies its provenance, account, current scans, phase and CPU
architecture. It reads the existing foundation state and preserves network,
database and frontend inputs. Only reviewed runtime environment settings, secret
names and provider egress may change. Destruction/replacement, unrelated resource
changes and RDS modifications are refused. Runtime setup is staging-only and uses
the existing infrastructure provisioner; the normal OIDC release role is unchanged.

`runtime-apply` performs these phases in order:

1. Create immutable application, identity and migration task definitions. No ECS
   services or autoscaling are created yet. Both ARM64 and X86_64 publications are supported.
2. Check that required Secrets Manager entries have current versions. Legacy
   target TLS secrets are not required. If values are missing, it stops with their
   names so they can be populated before resuming.
3. Run commerce, experience and integration owner migrations once each, waiting
   for confirmed zero exits. Database roles/schemas/passwords must already be
   prepared using the owner database provisioning procedure; this workflow never
   supplies RDS master credentials to application or migration tasks.
4. Run Keycloak's isolated initial administrator bootstrap while all identity
   nodes are stopped, then create its permanent service and wait for health.
5. Verify the staging realm discovery endpoint over certificate-verified HTTPS.
   If realm/client provisioning has not been completed through the private admin
   endpoint with `scripts/identity/provision.py`, the workflow stops here with
   backend services still absent. Configure the reviewed realm policy and resume
   the same release. It does not invent client secrets or administrator accounts.
6. Create and activate the three backend services and verify candidate revisions
   are healthy. Phase 5 also includes its explicitly configured AI owner.

The sanitized `backend-runtime-stage-…` artifact records migration task ARNs,
bootstrap outcome and final status. Raw Terraform plans, state, task payloads and
secret values are never uploaded. An ambiguous task launch/timeout is `UNKNOWN`;
inspect ECS, CloudWatch and database changelogs before manually rerunning. There
are no automatic mutation retries or destructive cleanup on failure.

Partial setup retains its state. After identity activation, the same cohort can
resume without rerunning the offline administrator bootstrap or stopping identity.
Avoid publishing another staging cohort during partial setup. Changing the
selected release during partial identity setup is refused until schema
compatibility is reviewed; the CLI can explicitly resume the original receipt. Once applications are activated, use normal
**Backend — Deploy Release**, selecting only the environment. That workflow and
infrastructure setup share a concurrency group to prevent concurrent promotions.

No successful cloud activation is implied by a local test or a successful plan.
Runtime settings, populated secrets, owner database setup and realm/client
configuration are required before application acceptance.

### Identity TLS is provisioned automatically

The regional ACM certificate covers `api.stage.nigson.com`, `auth.stage.nigson.com`
and `identity-admin.stage.nigson.com`. Terraform attaches it to the HTTPS listeners;
ACM handles public certificate renewal. Do not request a second exportable public
certificate for Keycloak or copy an ACM private key into Secrets Manager.

The first-account identity tasks use `generated` target TLS. Each task generates a
private RSA PKCS12 keystore at startup in its private writable data volume, with a
random password and owner-only file permissions. Replacement/restart generates a
new key. The self-signed target certificate has a ten-year validity and is never
served directly to browsers. The ALBs continue using HTTPS on ports 8443/9000 to
reach Keycloak, with task ingress restricted to the existing security groups.
ALBs do not validate target certificate trust; public identity comes from the ACM
listener certificate. No key is stored in Terraform state, the image or workflow artifacts.

You no longer need values in `nigson/stage/identity/tls_key` or `tls_certificate`.
Existing secret containers are retained to avoid destructive infrastructure changes,
but generated-mode tasks neither read them nor receive permission to read them.
The Keycloak database password and regional database CA secret remain required.
Legacy environment roots keep their explicit secret-PEM mode for compatibility.

To adopt this change, publish a new identity image using **Build and Publish Images**
and provision the updated first-account task configuration with that digest. Existing
task templates need the new `NIGSON_IDENTITY_TLS_MODE=generated` setting and reduced
secret references; publishing a new image alone does not change their configuration.
This addresses TLS provisioning; database/realm setup and first runtime activation
remain separate prerequisites.

References: [AWS ALB HTTPS target behavior](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html)
and [Keycloak keystore configuration](https://www.keycloak.org/server/enabletls).
