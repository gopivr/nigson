# First AWS account: Nigson staging from Terraform

This path is for a NEW environment/account with no pre-created VPC, subnets,
security groups, load balancers, certificates, WAF, ECR repositories or state
bucket. Use it instead of `aws/environments/stage` for this environment, not
alongside that root. Existing deployments must retain their original state and
resource ownership. No live AWS apply has been performed to qualify this path.

## GitHub Actions setup

For a fresh staging account, use the [GitHub Actions guide](../../../../docs/runbooks/aws-first-account-actions.md).
Add repository secrets AWS_ACCOUNT_ID, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY;
run **bootstrap first AWS account** with actions **bootstrap**, then **plan** and
**apply** after adding the generated Squarespace NS records. The workflow persists
bootstrap state on S3 so later hosted runners can resume. The local CLI procedure
below remains supported; migrate any existing local bootstrap state before moving
its ownership to Actions.

## Inputs and credentials

Required initial inputs:

- Your twelve-digit AWS account ID.
- An AWS CLI profile with credentials permitted to provision the resources.
  Access-key authentication requires BOTH an access key ID and secret access key;
  the account ID is not the access key ID. SSO profiles also work.
- Control of nigson.com DNS at Squarespace (confirmed by the user).

Everything else has starter Terraform settings: `stage`, `eu-west-1`, two
available AZs, `10.40.0.0/16`, separate edge/application/data subnets, private
Fargate networking, PostgreSQL18, private RDS instances, stage single-AZ application
DB, WAF, HTTPS ALBs, immutable ECR, KMS, secret containers, SNS/SQS, monitoring,
private S3 and CloudFront. The identity module keeps its existing protected
private-admin design. Stage frontend is `stage.nigson.com`, API is
`api.stage.nigson.com`, login is `auth.stage.nigson.com`. Default BP1 excludes AI.

Review these starter settings and the plan for cost and sizing: NAT gateways,
interface endpoints, load balancers, databases, WAF and other provisioned services
are billable even while application tasks are off. Object expiration for business
buckets is disabled until an approved retention policy is supplied; incomplete
multipart uploads are cleaned up. Runtime bucket permissions remain empty until
approved purpose-prefix grants are configured. No provider credentials, fee,
retention approval or merchant account identifiers are invented.

On your Mac, run this yourself in a terminal (never paste the secret into chat):

```bash
aws configure --profile nigson-stage
export AWS_PROFILE=nigson-stage
aws sts get-caller-identity
```

AWS CLI stores profile configuration in `~/.aws/config` and access keys, when
used, in `~/.aws/credentials`. These are your HOME files, outside this repository.
Terraform uses that standard credential chain. Do not put keys in Terraform
variables, backend files, project .env, Dockerfiles or frontend bundles.
The starting identity needs provisioner permissions, including IAM/KMS creation;
Terraform cannot grant itself permissions its initial credentials do not have.

Use repository-pinned Terraform1.12.2 and AWS provider6.15.0. Bootstrap and
first-account roots include provider locks. Docker/Buildx and the repository
build toolchains are needed only when publishing images/artifacts.

## 1. Bootstrap state and DNS

From the backend repository, create the ignored build directory and input file:

```bash
cd /Users/gopiravi/workspace/techx/nigson-monorepo/nigson
mkdir -p .build/aws-first-stage
cp infrastructure/terraform/aws/bootstrap/terraform.tfvars.example \
   infrastructure/terraform/aws/bootstrap/terraform.tfvars
```

Edit only `expected_account_id` initially; defaults already name nigson.com,
staging and eu-west-1. No AWS resource IDs or secrets go in that file.

```bash
terraform -chdir=infrastructure/terraform/aws/bootstrap init
terraform -chdir=infrastructure/terraform/aws/bootstrap plan \
  -out="$PWD/.build/aws-first-stage/bootstrap.tfplan"
terraform -chdir=infrastructure/terraform/aws/bootstrap apply \
  "$PWD/.build/aws-first-stage/bootstrap.tfplan"
terraform -chdir=infrastructure/terraform/aws/bootstrap output squarespace_nameservers
```

Bootstrap creates the private/encrypted/versioned state bucket and a public
Route53 zone for **stage.nigson.com**, then outputs its four nameservers. The
bootstrap itself starts with local state because its state bucket does not exist
before this first apply. Protect/back up its local `terraform.tfstate` and use the
same checkout/state for repeat runs. It is ignored by Git. The main platform uses
remote encrypted S3 state with S3 lockfiles.

## 2. Delegate stage.nigson.com in Squarespace

Squarespace remains the domain registrar. Open Domains -> nigson.com -> DNS ->
DNS settings -> Custom records. Add one NS record for each Terraform nameserver:

```text
Host/name: stage
Type:      NS
Value:     one nameserver from the Terraform output
```

Use the same TTL for all four records. For staging, do not change nigson.com's
root nameservers or its existing email/MX records. Wait until delegation resolves:

```bash
dig NS stage.nigson.com +short
```

AWS cannot change Squarespace DNS with AWS credentials. This one registrar-side
step is required before ACM can validate certificates. No AWS Console resource
setup is required. Squarespace's current instructions:
https://support.squarespace.com/hc/en-us/articles/31093649681037-Adding-NS-records-to-subdomains

## 3. Prepare and apply the AWS platform

The handoff helper reads Terraform's bootstrap output and writes only non-secret
configuration under `.build`; it does not create AWS resources or read key files:

```bash
python3 scripts/infra/prepare-first-account.py --environment stage

terraform -chdir=infrastructure/terraform/aws/first-account init \
  -backend-config="$PWD/.build/aws-first-stage/backend.hcl"
terraform -chdir=infrastructure/terraform/aws/first-account plan \
  -var-file="$PWD/.build/aws-first-stage/platform.tfvars.json" \
  -out="$PWD/.build/aws-first-stage/platform.tfplan"
terraform -chdir=infrastructure/terraform/aws/first-account apply \
  "$PWD/.build/aws-first-stage/platform.tfplan"
terraform -chdir=infrastructure/terraform/aws/first-account output -json infrastructure
```

Terraform discovers AZs and an available PostgreSQL18 minor in the selected region.
It creates DNS certificate-validation records, regional and us-east-1 certificates,
regional/global WAF, networking, IAM, ECR/ECS infrastructure, private application
and identity databases, private identity admin DNS, frontend CDN/bucket and owner
messaging/storage/monitoring. Public internal routes remain denied. Certificate
validation waits for the Squarespace delegation; a pending certificate is not an
application failure or reason to bypass TLS. The first apply intentionally creates
NO application, migration or Keycloak task definition/service with fake images.

An optional `alert_email` Terraform variable creates an SNS email subscription;
its owner must confirm the normal AWS email before alerts can be delivered.
Private identity administration needs your approved private access path/VPN;
this stack does not open the administration endpoint to the internet.

## 4. Publish actual images and task definitions

After reviewing/committing the release source, publish real images to the ECR
repositories Terraform created. This command builds and scans images; it does
not deploy services:

```bash
export AWS_REGION=eu-west-1
export AWS_EXPECTED_ACCOUNT_ID=YOUR_12_DIGIT_ACCOUNT_ID
export GITHUB_STEP_SUMMARY="$PWD/.build/aws-first-stage/images.md"
bash scripts/infra/publish-images.sh stage amd64 1 "$AWS_EXPECTED_ACCOUNT_ID" "$AWS_REGION"

python3 scripts/infra/prepare-first-account.py --environment stage \
  --image-tag "$(git rev-parse HEAD)-amd64"
```

Image capture verifies the AWS account and reads existing immutable ECR digests.
It distinguishes the commerce **migration-target image** from the commerce
application image. `.build/aws-first-stage/images.tfvars.json` enables task
DEFINITIONS only; applications/identity still default to inactive. Initial helper
capture covers the BP1 non-AI services. For BP5, publish/provide an additional
verified AI image explicitly and review the larger infrastructure plan.

```bash
terraform -chdir=infrastructure/terraform/aws/first-account plan \
  -var-file="$PWD/.build/aws-first-stage/platform.tfvars.json" \
  -var-file="$PWD/.build/aws-first-stage/images.tfvars.json" \
  -out="$PWD/.build/aws-first-stage/tasks.tfplan"
terraform -chdir=infrastructure/terraform/aws/first-account apply \
  "$PWD/.build/aws-first-stage/tasks.tfplan"
```

Use both input files for subsequent plans. Do not reset `publish_task_definitions`
to false after tasks have been provisioned; that is an explicit removal operation.
Existing compute/identity module callers retain their prior default behavior;
identity resource-address moves are declared for upgrade compatibility.

## 5. Configure and activate the actual application

AWS infrastructure credentials cannot supply Paystack/PandaGo/SMTP merchant
credentials or authorize their business policies. Secret containers are created,
not populated with dummy credentials. Application activation remains a separate
reviewed operation:

1. Populate the owner app/migration passwords and required identity/client/TLS
   secrets outside Terraform state; configure provider credentials in the named
   secret containers. RDS manages the master credential itself.
2. Bootstrap owner DB roles/schemas using the existing reviewed private-database
   procedure in `aws/database/README.md`; initialize the dedicated Keycloak DB
   role and identity TLS/realm/client/MFA configuration. Ordinary application
   containers must not use the RDS master password.
3. Run owner migrations with the existing launcher, pointed at this new root:

   ```bash
   export NIGSON_TERRAFORM_ROOT=infrastructure/terraform/aws/first-account
   bash scripts/infra/run-owner-migration.sh stage commerce
   bash scripts/infra/run-owner-migration.sh stage experience
   bash scripts/infra/run-owner-migration.sh stage integration
   ```

4. Supply complete service runtime configuration using
   `runtime.auto.tfvars.example` as a SHAPE example, not a complete configuration.
   Secret values belong in Secrets Manager, not in `application_environment`.
   Configure purpose-specific storage grants and explicit provider egress CIDRs;
   wildcard internet egress is not added. Review private workload TLS/identity
   bindings and the frontend BFF origin/Keycloak redirect settings.
5. Enable identity only after its bootstrap is ready, and applications only after
   every required migration has succeeded, via reviewed Terraform plans with real
   image files and runtime settings. Do not replace the workflow with local
   development `.env` settings.
6. Build the frontend with `npm run build:release -- --output PATH` in the sibling
   frontend repository, then upload that reviewed artifact using
   `scripts/infra/publish-frontend.py`. Use the `frontend_bucket` and
   `frontend_release` outputs; no manual S3 bucket creation is needed. Until upload
   and application activation, provisioned URLs are infrastructure endpoints,
   not a working qualified storefront.
7. Run real HTTPS/login/API/internal-denial/provider, backup/restore and rollback
   acceptance before declaring the deployment ready. Production uses separate
   account/state and a deliberate nigson.com root DNS cutover after preserving
   existing DNS/email records. Do not repoint staging state at production.

Full release controls: `docs/runbooks/deploy.md`. This setup does not purchase or
transfer nigson.com, create your AWS account, bypass IAM permissions, put AWS
credentials into Terraform, seed a production administrator, or perform any live
cloud apply during source implementation.

## Offline checks

```bash
terraform -chdir=infrastructure/terraform/aws/bootstrap init -backend=false
terraform -chdir=infrastructure/terraform/aws/bootstrap validate
terraform -chdir=infrastructure/terraform/aws/bootstrap test
terraform -chdir=infrastructure/terraform/aws/first-account init -backend=false
terraform -chdir=infrastructure/terraform/aws/first-account validate
terraform -chdir=infrastructure/terraform/aws/first-account test
python3 -m unittest discover -s scripts/infra/tests -p test_first_account.py -v
bash scripts/infra/tests/run-owner-migration-test.sh
```

These tests use Terraform mock providers and fake local CLI fixtures. They do not
prove actual AWS quota, engine availability, certificate validation, DNS delivery,
application startup or live provider qualification.

## UI repository CI/CD

Use the UI repository's `.github/workflows/aws-stage-ui.yml` and `deployment/AWS.md`. Apply this root's new deployment_configuration output once using current inputs before the first UI deployment. That sensitive state snapshot lets UI-only releases retain backend activation/images/environment. The UI workflow needs only a read-only infrastructure repository token in addition to its authorized AWS secrets and public reCAPTCHA site key; it discovers bucket/distribution from state. First-account Actions preserves the applied frontend release on later infrastructure runs. Preserve that same release ID in any manual full-root/runtime activation inputs.
