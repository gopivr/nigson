# SNS/SQS delivery, dead-letter queues and replay

Use this runbook for the event transport configured by PLAT-001 and INFRA-001. Resolve queue/topic names and ARNs from the reviewed environment outputs; do not guess names or replay against production without the existing incident/release authority.

## Detect and inspect

- Observe queue age/backlog, visible/in-flight counts, receive/delete failures, DLQ depth and oldest DLQ message age using the registered CloudWatch metrics. Thresholds and named responders are environment-specific reviewed inputs, not values defined here.
- Confirm publisher outbox progress and consumer owner health independently. SQS redelivery and duplicate/out-of-order events are expected. A provider job's durable business key and owner inbox/processed-event record are the idempotency authority.
- Inspect only sanitized event type, schema version, event ID, owner, correlation and safe error code. Do not paste event payloads, personal data, secrets or provider evidence into shared dashboards.

## Recover safely

1. Pause only the affected consumer or subscription when processing would violate an owner invariant; avoid stopping unrelated queues.
2. Correct the specific code/configuration/permission cause and validate against the pinned local SNS/SQS mock first.
3. Re-drive a DLQ item only through the approved redrive policy and destination. Do not purge a queue, edit an event, create a new event ID or bypass the owner inbox. Repeated delivery must resolve to the original processed receipt or durable job.
4. For an event that initiated a provider write, check the durable provider outcome first. `UNKNOWN` requires provider reconciliation, not ordinary message retry. After restore, reconcile owner state before replay because restored database state can be older than the provider.
5. Verify publisher outbox marked delivered only after broker acceptance, consumer receipt committed before SQS deletion, no duplicate business effect, and a bounded DLQ return to zero or an explicitly documented operator hold.

Useful offline checks: `make platform-check` exercises owner PostgreSQL and local SNS/SQS delivery, duplicate/replay and crash recovery; the scoped Java provider and carrier harnesses cover provider timeout/UNKNOWN behavior. These checks do not qualify AWS IAM, production redrive policy or deployed queue alarms. See `docs/runbooks/integration-jobs.md` for authorized staff retries and durable job states.
