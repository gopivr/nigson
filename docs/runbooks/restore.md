# Isolated database restore and reconciliation

This runbook applies to the production PostgreSQL recovery process described by INFRA-001/OPS-002. A local PostgreSQL migration test is not an RDS restore; an RDS verifier PASS is not an RPO/RTO result until a real isolated restore has completed and its timestamps are recorded.

## Before restore

1. Name the incident commander, data owner, recovery window and approved source recovery point. Record the backup timestamp and incident start time without copying customer data into the evidence report.
2. Create a unique restore target in the same approved stage account/region with encryption and the approved KMS key. Keep it private in the approved database subnet group. Attach only the restore database security group and a separate auditor security group with no ingress. Never restore over the production database or a shared developer database.
3. Obtain the restore-auditor secret through the approved secret channel; do not put it in CLI arguments, shell history, logs or evidence. Use TLS `verify-full`, the exact restored RDS hostname and PostgreSQL port 5432.
4. Run `scripts/infra/verify-rds-restore.sh <nigson-stage-restore-unique-id>` with the exact expected account, region, KMS key ARN, subnet group, security groups, product phase and read-only auditor inputs. The verifier only inspects the target and reads schema/Liquibase state; it does not create or repair a restore.

## Validate and reopen

- Verify PostgreSQL 18, private reachability, encryption/KMS binding, exact endpoint TLS identity, owner schemas and Liquibase histories. Keep each owner query within its own schema and credential.
- Compare counts/checksums only through approved privacy-safe aggregates. Record restore start, database availability, validation completion and API recovery times. Calculate observed RPO from the backup point and RTO from incident start; compare with the approved 15-minute/4-hour targets. A missed target blocks release and requires investigation; never adjust targets after observing the result.
- Reconcile provider state after restore before reopening side effects. Payments, refunds, carrier booking/cancellation and messaging writes with erased or ambiguous evidence remain `UNKNOWN`; recover them against the original stable business/provider reference. Missing local evidence is not proof that a remote write did not occur.
- Reopen one owner/workflow at a time after current identity/configuration, migration history, outbox/inbox, durable job and audit evidence are checked. Keep the restore target isolated until the incident owner signs off on disposition.

Never promote restored data over a live database, disable TLS/security checks, broaden database ingress for convenience, or use schema rollback to reconcile provider outcomes. Real RDS restore, RPO and RTO evidence remains NOT_RUN until approved AWS inputs and an isolated target exist. See `docs/runbooks/deploy.md` for immutable release and rollback controls.
