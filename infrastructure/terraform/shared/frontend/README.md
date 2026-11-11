# Microfrontend hosting

Independent roots: `aws/frontend/{stage,prod}` and `ovh/frontend/{stage,prod}`. Copy the selected root's `.tfvars.example` and backend example, fill actual inputs, then run `terraform init -backend-config=backend.hcl` and review `terraform plan`. Frontend state keys are separate from backend state. No cloud apply has been run.

Build in the sibling microfrontend repository:

```bash
npm ci
npm run build:release -- --output /absolute/path/to/new-release
```

This builds the shell and all seven configured remotes with a same-origin federation manifest. Development proxy files are not deployment servers.

AWS: private versioned/encrypted S3 + signed CloudFront origin access, HTTPS, supplied global WAF and optional Route53 records. Supply an ACM certificate in us-east-1. Upload the reviewed artifact with `python3 scripts/infra/publish-frontend.py --site PATH --bucket BUCKET --release RELEASE`; use `--validate-only` for an offline check. The publisher requires AWS credentials and refuses existing release prefixes. Activate the matching `release_id` through a reviewed Terraform plan. Keep old releases for rollback.

OVH: use the backend stack's `frontend_network` output for existing private network/keypair inputs. Build and publish the container using the frontend repository's `deployment/Dockerfile`, with the artifact directory as Docker context; pin the resulting image digest and match the VM architecture. Private Ubuntu24.04 VMs sit behind a public TCP/443 load balancer. Set DNS to the frontend public IP. Through your VPN, authenticate Docker to the registry and install TLS certificate/key on each VM at `/etc/nigson/frontend/secrets/tls.crt` and `tls.key`, owner root/group101, directory0750/key0640. Start `sudo systemctl start nigson-frontend` after installing certificates; renew them and reload Nginx as part of operations. TLS/private registry credentials are never Terraform inputs.

Both providers serve SPA deep links while missing JS/remote entries remain errors. API/BFF calls preserve cookies, CSRF, queries and backend errors, with caching disabled. `backend_origin_hostname` must be a distinct, valid HTTPS backend origin that already routes public Commerce/Experience APIs and BFF paths. Configure backend BFF public origin, cookie/CSRF/CORS policy and Keycloak redirects for the frontend HTTPS hostname before login qualification. Integration stays private. Live DNS/TLS/login, WAF/monitoring, credentials and regional quotas remain deployment gates; frontend hosting does not remove existing backend cloud dependencies or qualify existing LocalStack-only product-media uploads.

Offline checks: `bash scripts/infra/validate-terraform.sh all` and `node scripts/infra/tests/frontend-routing-test.cjs`. Docker HTTPS acceptance: `python3 scripts/infra/tests/frontend-https-test.py --image YOUR_BUILT_IMAGE` (isolated test network; no real backend/provider).
