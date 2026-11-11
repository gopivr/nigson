# Owner database provisioning

This provider-free Terraform module renders the privileged PostgreSQL bootstrap
used before the owner Liquibase tasks. It creates the four migrator, runtime and
access-separated NOLOGIN audit roles, owns each schema with its migrator, removes
cross-owner schema privileges, and verifies PostgreSQL 18 and pgvector 0.8.2 in
`ai`. Keycloak uses its separate vendor database.

Run `terraform init -backend=false`, `terraform validate`, and `terraform apply`
with the approved database name. Render `terraform output -raw bootstrap_sql` to
a protected file and execute it using `psql -X -v ON_ERROR_STOP=1` as the database
provisioning identity. Supply the eight `nigson_<owner>_{migrator,app}_password`
psql variables from the credential manager. Passwords are absent from Terraform
inputs, outputs and state. Do not use command tracing or print populated SQL.
Missing variables stop the transaction. The bootstrap is repeatable and refuses
an unexpected schema owner or incompatible database/extension.

DATA-001 qualifies this SQL against an isolated PostgreSQL container with dummy
local credentials. This module provisions database objects, not RDS instances,
networking, KMS or ECS. Those deployment resources and credential delivery belong
to INFRA-001. The local-only migration launcher remains fail-closed for production;
the production ECS launcher will use the same versioned owner changelogs.
