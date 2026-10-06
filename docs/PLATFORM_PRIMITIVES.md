# Owned reliable delivery primitives

Specification 1.1.0 / PLAT-001. Commerce, experience and integration each have their
own `platform` module and hardcoded owned SQL. BP5 AI has an independent Python
implementation. Shared packages provide wire validation, hashes and signature/
transport protocols only. No repository or aggregate is shared.

`executeLocal` / `execute_local` receives the already authenticated principal,
operation and request key from its domain caller. It hashes canonical JSON,
reserves a short lease in a committed transaction, then locks the owned record
while applying **local database work only**. The effect and cached status/body/
resource commit together. Matching retries return the saved response; changed
inputs and active or fenced leases raise a safe 409 conflict. A failed local
transaction rolls back its effect and permits recovery. Domain APIs map these
codes into the PLAT-002 Problem contract. The foundation's expiration metadata
is a local/test fixture window; no production retention/deletion is activated.

Append an event inside the aggregate transaction. The declared `outbox_event`
catalog has no separate organization/correlation/trace columns; its JSONB
`payload` holds the **complete validated wire envelope**, while indexed envelope
fields are also stored in their declared columns. This preserves every section16
field without changing DATA-001. A relay claims up to10 rows with SKIP LOCKED,
commits its120s lease, publishes outside the transaction, then conditionally
acknowledges the claimed generation. A lost publication acknowledgement may
produce duplicates. Expired claims recover; stale generations cannot acknowledge
new claims. Provider/database failures back off5/20/60/120/300s, with failed rows
retained for review after the configured bound.

Consumers validate closed EVT-001 through EVT-028 envelopes and exact producer/
phase subscriptions before local work. An advisory transaction lock serializes
canonical event IDs for each consumer. The handler's owned effect/job and
append-only `processed_event` insert commit together; SQS delete follows commit.
Each handler must additionally use its domain business unique key, since distinct
transport event IDs may describe the same effect. It may return IGNORED only
following an authoritative refresh. An unavailable handler leaves the message
unacknowledged. No placeholder handler is installed. Integration and AI have no
registered producing event in specification1.1.0; invalid producer appends fail.

SQS polls20s, receives at most10 concurrent messages, starts120s visibility and
extends it every30s while bounded work runs. Handler work must finish within90s;
Java transaction deadlines and Python SQL statement deadlines protect database
work. Permanent wire/security errors go to a classified DLQ record containing
only message ID, digest and code. Transient failures retain/requeue delivery.
Business handlers must not perform provider calls inside these transactions;
Java provider adapters actively reject an active Spring transaction. External
UNKNOWN, reconciliation, business audit and permission checks remain owned by
their domain tasks, rather than generic transport handlers.

The Terraform messaging module creates an encrypted standard SNS topic and
owner SQS/DLQ pairs with raw delivery, exact event/producer filters and scoped
publisher/consumer IAM. `product_phase` selects BP1–BP5; AI subscription exists
only in BP5. A phase with no subscribed events uses `event_type exists:false`,
which denies all valid closed envelopes and avoids AWS's invalid empty arrays.
LocalStack uses the same generated filters. No production deletion/retention
activation or AWS IAM qualification is inferred from local tests.

Run `make platform-check ENV=test UV=.build/uv-package/bin/uv` with the pinned
Oracle Java25 toolchain. It migrates all four owners on an isolated PostgreSQL18/
pgvector instance and exercises concurrent commands, rollback, hash conflicts,
stale leases, SNS lost acknowledgements, SQS replay/business uniqueness and poison
quarantine. Existing `nigson-local` container/start/mount/volume state is compared
before and after, and only this run's disposable containers are removed.
