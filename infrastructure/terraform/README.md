# Terraform

- `aws/`: existing AWS modules and stage/prod roots, unchanged resource addresses and state keys. Use the new directory with your existing backend configuration; do not create a fresh state for an existing deployment.
- `ovh/`: separate OVH Public Cloud configuration, state and credentials. See `ovh/README.md`.

Validate without creating resources: `bash scripts/infra/validate-terraform.sh all`.
