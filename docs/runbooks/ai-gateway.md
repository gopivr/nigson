# AI gateway operations

## Offline controls

The AI owner exposes staff controls for immutable prompt revisions, model rates,
model-policy drafts and policy activation. Configure a provider region and an
explicit allowlist of model identifiers together; a partial pair fails service
startup. Rate creation also requires an approval reference that appears in the
deployment's approved-reference allowlist. The reference is written to audit as
a checksum. The repository contains no approved production model, rate, budget,
retention value or activation decision.

The local provider mock is available only in `local` and `test`. It is bounded,
deterministic and stateful for request replay. It can simulate a known
pre-acceptance outage and an uncertain post-acceptance timeout; the latter is
reconciled by its stable request ID and must not be blindly retried. Production
does not fall back to the mock. The current increment does not wire a real model
transport, so no external model call is made by these controls.

## Budget reservation behavior

The budget helper reserves the configured maximum input and output token cost in
an AI-owned database transaction before a provider invocation. PostgreSQL
advisory locking serializes reservations by provider/model/currency, including
requests using different rate revisions. The active-run count is model-wide and
the daily spend uses UTC boundaries. The lock and database transaction must be
released before any provider HTTP call.

Known provider unavailability can retry against the same durable run and
idempotency record after a new reservation check. Unknown acceptance preserves
the pessimistic reservation and blocks a blind retry until reconciliation.
Successful completion replaces the reservation with cost computed from bounded
provider token counts. These mechanics enforce the budget configured on the
policy; they do not select or approve a budget.

## Activation

Activation requires a fresh MFA assertion, the current policy version, a passing
evaluation result for the same policy and prompt revision, an explicitly
configured provider/model capability, and a configured effective rate. Failed
gates leave the policy disabled. Rate, prompt and evaluation revisions are
immutable. There are no gateway-specific outbox events in the contract; writes
and audit records commit in the AI schema transaction.

Do not enable live model execution until deployment owners provide and approve
provider/model, rate, budget and policy inputs. No retention period is inferred
by this runbook. INFRA-001 stage and restore qualification remains separately
input-bound.
