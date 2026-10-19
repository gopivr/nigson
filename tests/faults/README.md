# Fault scenario mapping

This directory records OPS-002 fault/race coverage using the real local PostgreSQL migrations and pinned local SNS/SQS/provider mocks. Tests must preserve durable state and assert business invariants; a local mock result never counts as AWS IAM or live provider qualification.

| Scenario | Existing executable coverage | Required invariant |
| --- | --- | --- |
| Outbox crash/restart and publish replay | `make platform-check` (`PlatformPersistenceTest`, `EventTransportTest`) | Publish retries retain the original event identity; consumer dedup and commit-before-ack prevent duplicate effects or lost durable receipts. |
| Queue duplicate/out-of-order/redelivery | `make platform-check`; carrier and messaging acceptance harnesses | Owner inbox/idempotency state is durable; repeated delivery cannot create another job/effect. |
| Provider timeout after remote acceptance | `make provider-contract-test`, `make paystack-check`, `make carrier-check`, `make messaging-check` | Preserve `UNKNOWN`; reconcile original reference; do not blindly repeat a write. |
| Cache disabled | `make provider-contract-test`; carrier rate burst in `tests/providers/src/com/nigson/providers/ProviderQualification.java` | Every mock quote request reaches the HTTP adapter; no cached response hides mode, currency or provider failure. |
| Restore gate negative cases | `bash scripts/infra/tests/verify-rds-restore-test.sh` | Reject public/wrong-engine/wrong-key/wrong-subnet/unisolated targets and require TLS-verified owner schema checks. |

Execute with `ENV=test` and the pinned local test images. Do not point local test commands at a remote Docker context, AWS account or genuine provider. Real isolated RDS restore, IAM enforcement, approved queue redrive, production RPO/RTO and BP4 capacity evidence are separate release gates.
