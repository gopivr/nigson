# First AWS account through GitHub Actions

This provisions a fresh Nigson **staging** account with Terraform. No state bucket,
VPC, security groups, certificates, load balancers, ECR or GitHub OIDC role needs
to exist first. It uses the first-account roots, not the legacy environment roots.
The domain remains nigson.com at Squarespace; staging uses stage.nigson.com.

## 1. Add repository secrets

In the **backend GitHub repository**, open **Settings → Secrets and variables →
Actions → Secrets → New repository secret**. Add these exact names:

| Name | Value |
| --- | --- |
| `AWS_ACCOUNT_ID` | Your twelve-digit AWS account ID. This is not an access key. |
| `AWS_ACCESS_KEY_ID` | The provisioner's AWS IAM access key ID. |
| `AWS_SECRET_ACCESS_KEY` | The matching AWS secret access key. |
| `AWS_SESSION_TOKEN` | Only if the keys are temporary STS credentials; otherwise omit. |

The AWS identity must already be permitted to provision the resources, including
IAM roles, KMS, Route53, S3, networking, ECS, RDS, CloudFront, WAF, Secrets Manager,
SNS/SQS and monitoring. An account ID and a secret access key alone are not a
usable credential pair. Initial authentication/permissions cannot be created by
a workflow that has no working AWS credentials yet. Use the authorized IAM
provisioner, not credentials embedded in source, Terraform variables or .env.

No credentials file needs to be checked in or added to the runner manually.
The pinned AWS credential action supplies the standard credential environment;
the script verifies STS account identity before accessing state or provisioning.

Optional: under **Variables**, add `AWS_REGION`. Default is `eu-west-1`.
Choose it before the first run and keep it unchanged afterwards. The workflow
uses GitHub environment `stage`, so environment-level secrets/variables can be
used instead and existing environment protection rules apply. The new workflow
requires no pre-created OIDC provider or role. Existing artifact/application
release workflows retain their separate OIDC and runtime setup requirements.

## 2. Run bootstrap

Merge/push these reviewed workflow files to the repository's default branch.
Then open **Actions → bootstrap first AWS account → Run workflow**:

- Branch: the repository's default branch (other branches do not execute).
- Action: **bootstrap**.

This creates only the state bucket and environment DNS foundation. It first
uses a Terraform target for the state bucket alone, immediately moves bootstrap
state into encrypted S3 with native lockfiles, then applies the complete bootstrap
root using remote state. It outputs four nameservers in the run's **Summary**.
It does not create the application platform yet.

State paths in the same private state bucket:

- Bootstrap: `nigson/bootstrap/stage/terraform.tfstate`.
- Platform: `nigson/first-account/stage/terraform.tfstate`.

The bucket name is generated from account and region:
`nigson-<account-id>-<region>-terraform-state`. Full bootstrap enables versioning,
AES256 encryption, public-access blocking and TLS-only access. Subsequent runners
load remote state rather than trying to recreate the bucket or DNS zone. State,
plan binaries and credential files are not uploaded as GitHub artifacts.

## 3. Add the Squarespace delegation

Take the **real nameservers from your run**, not from test fixtures or examples.
At Squarespace open **Domains → nigson.com → DNS → DNS settings → Custom records**.
Add four NS records with **Host = stage**, one for each nameserver. Keep the same
TTL. Keep existing root nameservers and MX/email records. Allow DNS propagation.

This registrar action is required because AWS credentials cannot update your
Squarespace account. Certificates are created and DNS-validated by Terraform
after public delegation works.

## 4. Plan and apply the platform

Run the same workflow with action **plan** and review its Terraform output.
This reads existing bootstrap state and plans VPC/subnets, security groups,
certificates, WAF, ALBs, private RDS, ECS/ECR, identity infrastructure, S3/CDN,
secrets metadata, messaging and monitoring without applying platform changes.

Then run it with action **apply**, on the same reviewed default-branch revision.
This verifies the public NS delegation, creates a fresh plan, checks the plan,
and applies that exact saved plan in the same job. It creates billable AWS
resources. Initial provisioning can take time for databases, certificates and
CloudFront; the job allows two hours. If another commit lands after plan review,
review a new plan for that commit before dispatching apply.

Infrastructure starts with application task definitions and services unpublished.
It creates no placeholder images, secret values, provider credentials or working
storefront. Use the separate application activation steps in
[the first-account guide](../../infrastructure/terraform/aws/first-account/README.md)
for real image publication, DB roles/migrations, Keycloak provisioning, runtime
secrets and frontend release. This foundation workflow deliberately rejects any
plan containing deletion/replacement or ECS task definitions/services, including
existing runtime resources, so it cannot silently disable an activated deployment.

## Reruns and recovery

Runs are serialized with the existing staging infrastructure plan concurrency
group; cancel-in-progress is false. Terraform also locks remote state. Use one
state owner and avoid simultaneous local bootstrap operations.

- If full bootstrap fails after state migration, rerun **bootstrap**; tracked
  partial progress is in S3. If platform apply fails, fix the reported cause,
  review **plan**, then rerun **apply** using the same account and region.
- An interrupted initial bucket-only apply can be resumed by importing that
  bucket if its account and Nigson/stage/Terraform tags match and no public stage
  zone exists. A foreign or untagged bucket is not adopted automatically.
- If bootstrap state appears concurrently before initial migration, the run
  stops instead of force-copying over it; rerun against the existing state.
- If you previously applied bootstrap from a laptop and have a public staging
  zone but no CI bootstrap state, migrate the original local state first. Do not
  delete resources, run an import blindly or recreate the hosted zone. From the
  original bootstrap working directory with its real terraform.tfstate and
  original authenticated AWS profile, add the following partial backend to a
  new backend.tf (only if no backend is already declared):

  ```hcl
  terraform {
    backend "s3" {
    }
  }
  ```

  Then migrate using the actual account ID and original region:

  ```bash
  terraform init -migrate-state \
    -backend-config="bucket=nigson-<account-id>-eu-west-1-terraform-state" \
    -backend-config="key=nigson/bootstrap/stage/terraform.tfstate" \
    -backend-config="region=eu-west-1" \
    -backend-config="encrypt=true" \
    -backend-config="use_lockfile=true"
  ```

  Retain its protected state backup. Never copy platform state into the
  bootstrap key.
- AccessDenied/403, expired credentials and network errors stop the run. They
  are never interpreted as “account is empty.”

No real AWS plan/apply or GitHub workflow dispatch was performed while authoring
this pipeline. Offline tests verify orchestration and guards; first live execution
with the supplied repository secrets remains required.

References: [AWS credential action](https://github.com/aws-actions/configure-aws-credentials),
[Terraform S3 backend](https://developer.hashicorp.com/terraform/language/backend/s3).
