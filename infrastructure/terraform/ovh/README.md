# OVH Public Cloud

Stage/prod roots provision private Docker VMs, an HTTPS TCP load balancer, private networking/NAT, separate domain/Keycloak managed PostgreSQL clusters, encrypted/versioned private S3 buckets and a managed private registry. Production requires two VMs per mandatory role and a multi-node database plan. Data and registry deletion are protected.

1. Export OVH API credentials (`OVH_APPLICATION_KEY`, `OVH_APPLICATION_SECRET`, `OVH_CONSUMER_KEY`) and source your project's OpenStack RC or application credentials. Credentials stay outside Terraform files.
2. Copy `environments/stage/terraform.tfvars.example` to `terraform.tfvars` in that directory. Fill actual project/tenant IDs, regional catalog IDs, public SSH key, private administrator/VPN ranges, DNS/time servers and immutable image digests. VM images must be Ubuntu 24.04 with working cloud-init. Verify regional PostgreSQL 18, pgvector 0.8.2 and owner-role privileges before setting `capabilities_verified=true`; unsupported regions/plans cannot run this application's database unchanged.
3. Precreate a private S3 state bucket with locking/versioning supported. Fill `backend.hcl.example` into a local backend config and export that bucket's S3 credentials. Use distinct OVH stage/prod keys; never point at AWS state.

```bash
terraform -chdir=infrastructure/terraform/ovh/environments/stage init -backend-config=backend.hcl
terraform -chdir=infrastructure/terraform/ovh/environments/stage plan -out=ovh.tfplan
# Review the plan before applying it.
```

No cloud resources have been applied. To validate code without credentials or spending: `bash scripts/infra/validate-terraform.sh ovh`.

After a separately approved apply, configure DNS to `deployment.public_ip`. Reach private VMs through your existing VPN/routed administrator connection; no public VM SSH is opened. Export `compose_files` and upload each node's document as `/etc/nigson/compose.yaml`. Inject root-only `runtime.env`, secrets and configuration outside Terraform, authenticate Docker to the registry, bootstrap databases with `bootstrap_sql`, run the existing owner migration image per owner, then `sudo systemctl start nigson`. Hosts deliberately do not start incomplete workloads. Cloud-init installs Docker; security groups allow apt HTTP and registry/provider HTTPS, with other outbound ports configurable. Supply a real edge image/configuration that serves the frontend, terminates TLS on 443 and routes only approved public endpoints; never expose `/internal` or Keycloak administrative endpoints.

This is infrastructure provisioning, not yet an OVH-qualified application release. Existing SNS/SQS, KMS/session signing, AWS-specific storage configuration, AWS workload credentials and telemetry need genuine external AWS services or separate application adapters before activation. OVH buckets are not automatically substituted into the current AWS-only storage adapter. No LocalStack, mock provider, dummy secret or disabled security fallback is installed. Database backup retention/restore, VM patching, certificate renewal, production monitoring/WAF and real deployment acceptance remain operator qualification gates. Credentials, actual catalog availability and private routing were not available for a live plan.
