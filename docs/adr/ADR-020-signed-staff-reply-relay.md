# ADR-020: Signed actor relay and suppression checks for staff WhatsApp replies

Status: Accepted technical implementation design for MSG-001 API-192/193; live sender/template qualification remains NOT_RUN.
Date: 17 October 2026.

## Context

The Commerce Java BFF owns Keycloak sessions and current SALES authorization. Integration owns notification commands, dispatch and provider calls. Experience owns inbound leads, verified destinations and suppression. API-193 needs a trusted current SALES actor and an authoritative API-194 context read, but an API-193 actor proof is audience-, route-, body- and correlation-bound. Its 60-second lifetime cannot be persisted for an asynchronous worker. Integration cannot mint Commerce actor assertions, and it cannot read Experience tables.

The dispatch worker also needs a fresh owner-authoritative suppression check immediately before provider HTTP. A successful API-194 read at enqueue time cannot authorize a send after a later STOP.

## Decision

API-192 is implemented at the Commerce BFF boundary. It checks the current session/CSRF and SALES role, creates a stable command UUID from the required idempotency key, then signs two short-lived assertions over the exact same actor: API-193 is bound to the exact Integration route and canonical request body; the companion API-194 assertion is bound to the exact Experience GET path, correlation and empty body. No browser-supplied actor, phone, destination hash, template approval, provider target or provider mode is trusted.

API-193 requires both assertions. It validates the ordinary API-193 workload proof, verifies the companion assertion with pinned Commerce actor keys and requires the actor values to match exactly, then uses a fixed Integration workload identity to call Experience API-194 outside any database transaction. The API-194 result is accepted only when it is eligible, is WHATSAPP, contains the configured template code, and has a valid phone/hash/basis shape. Integration commits its stable, deduplicated command, encrypted recipient/context and paired dispatch intent in its own transaction; it never calls Twilio in that transaction. Duplicate commands preserve the original job and immutable intent; changed request bytes conflict.

Add private Experience API-205, POST `/internal/v1/notification-suppression-checks`, for the Integration workload. Its exact request is `{destinationHash,channel:WHATSAPP}` and its exact response is `{suppressed:boolean}`. It reveals no destination or lead data. It acquires the same destination advisory lock as STOP/API-194, reads current suppression, audits a bounded decision, and commits before returning. The worker calls it outside Integration transactions immediately before the dispatch fence/provider request. A failed or unavailable check prevents a new send; uncertain provider acceptance remains UNKNOWN and is reconciled only by existing provider identity.

The Commerce BFF, Integration worker and Experience API-205 have distinct roles and schemas. There are no cross-owner SQL statements, foreign keys or shared domain entities. The API-194/API-205 and API-193 request bodies remain closed and contain no phone. API-192 public response and other public DTOs remain unchanged.

## Consequences and validation

API-193 needs a dedicated Integration-owned encrypted dispatch context and bounded template-variable validation; existing verification-only worker behavior must not be reused for STAFF_LEAD_REPLY. The staff reply worker must use Twilio WhatsApp stateful local mocks, durable single-send fencing, account/template snapshots, current suppression checks, and status receipt projection. API-205 tests cover actor/workload scope, malformed hashes, suppression, concurrent STOP serialization, duplicate checks and safe response shape. End-to-end tests cover wrong/revoked SALES role, route/body/correlation mismatch, companion-assertion mismatch/expiry, API-194 denial, idempotent command replay/conflict, worker crash/UNKNOWN, suppression between enqueue and send, and no provider HTTP while an owner transaction is open.

Live account, sender, template, credential and policy approval are not inferred by this ADR. They gate live activation, not offline implementation with dummy credentials and stateful local mocks.
