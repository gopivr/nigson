# Carrier operations and recovery

This runbook covers offline-accepted FedEx adapter behavior. Use the stateful local HTTP mock and isolated owner databases for rehearsal. Live response-time targets, alert thresholds, route eligibility, account actions and named escalation contacts are deployment inputs; set them through the operational review, never infer them from these examples.

## Dashboard panels

Keep panels owner-scoped. The Integration dashboard uses only the Integration database and its own bounded `nigson.integration.jobs` / `nigson.integration.carrier.tracking` counters plus read-only aggregates of `integration_job`, `carrier_booking` and `carrier_cancellation`. Show counts by kind/state/mode, oldest pending age, overdue due-time count, expired-lease count, `UNKNOWN` count/age, safe error-code count and provider HTTP availability. The Commerce dashboard reads Commerce-owned shipment, carrier quote and carrier cancellation projections only; show safe source-state counts, expired quote counts, active dispatch exclusions and unapplied ACK count. Never publish identifiers or PII as metric tags. Configure each alert threshold only after the operator/SLO values are approved.

Example Integration panels (run only with a read-only Integration owner role):

```sql
-- Work queue age and safe error grouping.
SELECT kind, state, error_code, count(*) AS jobs,
       extract(epoch FROM (now() - min(created_at)))::bigint AS oldest_age_seconds
FROM integration.integration_job
WHERE kind = 'LOGISTICS_CREATE'
GROUP BY kind, state, error_code
ORDER BY state, error_code;

-- Carrier booking state, mode, age and safe error code.
SELECT mode, state, error_code, count(*) AS bookings,
       extract(epoch FROM (now() - min(created_at)))::bigint AS oldest_age_seconds
FROM integration.carrier_booking
GROUP BY mode, state, error_code
ORDER BY mode, state, error_code;

-- Overdue due work and expired leases, without exposing business keys.
SELECT 'job_due' AS source, kind AS work_kind, state, count(*) AS items
FROM integration.integration_job
WHERE kind = 'LOGISTICS_CREATE' AND state IN ('PENDING','UNKNOWN')
  AND next_attempt_at <= now()
GROUP BY kind, state
UNION ALL
SELECT 'booking_poll' AS source, 'FEDEX' AS work_kind, state, count(*) AS items
FROM integration.carrier_booking
WHERE next_poll_at <= now() AND state IN ('PENDING','CLAIMED','BOOKED','UNKNOWN')
GROUP BY state
UNION ALL
SELECT 'expired_job_lease' AS source, kind AS work_kind, state, count(*) AS items
FROM integration.integration_job
WHERE kind = 'LOGISTICS_CREATE' AND lease_until <= now()
GROUP BY kind, state
UNION ALL
SELECT 'expired_booking_lease' AS source, 'FEDEX' AS work_kind, state, count(*) AS items
FROM integration.carrier_booking
WHERE lease_until <= now()
GROUP BY state;

-- Cancellation uncertainty and oldest age.
SELECT mode, state, error_code, count(*) AS cancellations,
       extract(epoch FROM (now() - min(created_at)))::bigint AS oldest_age_seconds
FROM integration.carrier_cancellation
GROUP BY mode, state, error_code
ORDER BY mode, state, error_code;
```

Commerce panels must be backed by Commerce-only queries and may not join Integration tables. Do not copy these SQL examples into an Integration role that can access Commerce or vice versa. Public dashboard access is aggregate-only and carries no operational controls.

## Provider outage or ambiguous write

1. Check adapter health, the configured transport mode and safe provider error aggregates. Confirm that the active release and provider mode are approved; do not print environment values or credentials.
2. Stop automatic new creation if database restore, account rotation, identity mismatch or loss of execution evidence is suspected. Preserve durable records and original references.
3. For `UNKNOWN` booking, use the authorized API-191 reconciliation path against the saved booking identity/reference. This schedules observation of existing evidence and cannot grant fresh creation permission. If FedEx cannot establish the outcome, retain `UNKNOWN` and escalate through the configured provider support route.
4. For cancellation `UNKNOWN`, keep the source dispatch exclusion in place. Reconcile the original piece tracking references; never dispatch while exclusion is active and never make a second cancellation write based only on timeout.
5. For tracking outage, retain the last accepted state, allow safe read polling per worker schedule and inspect terminal monotonicity. A successful label or `BOOKED` state is not handover evidence and must not decrement stock.
6. After recovery, verify durable Integration and Commerce outcomes independently, stable duplicate replay, audit evidence and safe metrics before restoring worker intake. Account/mode rotation requires a reviewed two-owner cutover with new legitimate contexts; do not mutate historical rows.

API-191 requires current OPERATIONS authority and recent MFA. API-197/200 follow their registered staff authorization and version/idempotency rules. Never perform manual SQL state changes, retry a provider write outside the durable workflow, or interpret timeout as rejection. Use `docs/runbooks/carrier-quotes.md` for quote/API-195 behavior and `docs/runbooks/integration-jobs.md` for generic queue recovery. Live qualification remains NOT_RUN until genuine FedEx account, service, route, origin, load and release evidence is collected.
