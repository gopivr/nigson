# Carrier adapter operational contract

This document defines the provider boundary used by CARRIER-001 and OPS-001. FedEx is the only configured launch adapter. The local HTTP mock is stateful and exercises the same adapter and durable job path; it is test evidence, not approval of an account, service, route, origin, pickup policy or label stock.

## Identity and transport

The adapter owns OAuth credentials and FedEx account configuration. Its immutable fingerprint is SHA-256 over canonical provider (`FEDEX`), configured transport (`MOCK`, `TEST`, or `LIVE`) and the actual configured account identifier. `TEST` is exposed to Commerce as `SANDBOX`. Credentials may rotate without changing account identity; changing account, provider or transport creates a different identity. Integration and Commerce must agree on the fingerprint captured by quote, order, shipment and provider context. Missing or changed identity fails closed; never rewrite historical snapshots or replay a command under a new identity.

Only explicit configuration selects the local mock, sandbox or live HTTP target. Dummy credentials stay on the local mock. Production rejects mock/test transport and cannot fall back to it. Provider HTTP, OAuth, label storage and private source acknowledgement execute outside database transactions. Persist the intent/checkpoint first; after a timeout or malformed/lost response preserve `UNKNOWN` and reconcile with the original FedEx reference. Never blindly resubmit shipment creation or cancellation.

## Operations and outcomes

| Operation | Durable evidence before side effect | Successful outcome | Uncertain outcome |
| --- | --- | --- | --- |
| Rate quote (API-182; cart API-181 or negotiated quote API-195) | Stable Commerce idempotency key, cart/quote revision and server-computed address/package snapshot | One eligible FEDEX ACCOUNT rate in NGN, bound to mode and account fingerprint; short-lived quote | Safe provider error; no invented fee or fallback |
| Booking (EVT-027 / API-119 / API-120) | Integration booking, LOGISTICS_CREATE job, request hash, lease and account/mode checkpoint | Exactly one provider booking; private PDF label verified and stored; source ACK persisted | UNKNOWN, reconciled by the original stable business reference; no fresh create permission |
| Tracking | Existing booked pieces and poll checkpoint | Monotonic authoritative piece scans, then source ACK | Retain last accepted state and retry only a safe read; never regress terminal delivery/cancellation |
| Cancellation (API-197/198/199/200) | Source dispatch exclusion, immutable cancellation intent, fenced SUBMITTING state and saved piece tracking references | Authoritative CANCELLED or REJECTED outcome is acknowledged to Commerce | UNKNOWN remains dispatch-excluding; reconcile original physical-piece references, never issue another cancellation blindly |

HTTP payloads and provider responses are validated against the adapter's closed Java value types and pinned local fixtures. The integration API does not forward browser tokens, provider credentials, raw provider diagnostics, addresses, labels or account identifiers. Preserve safe problem codes and correlation only. `BOOKED` means the label/booking exists; it does not mean the parcel was physically handed to FedEx. Only explicit source handover evidence dispatches inventory. Delivery and cancellation are terminal and cannot be inferred from a label or stale scan.

## Mock qualification matrix

The stateful local provider regression and carrier persistence acceptance must cover, at minimum:

- Rate success, unsupported service/currency/mode, malformed response and provider outage.
- Booking success, duplicate event/command, timeout after provider acceptance, lost acknowledgement, async result polling, label storage/HEAD/checksum failure, and process restart/reconciliation.
- Tracking duplicate, stale and out-of-order scans, multiple pieces, unreachable provider, terminal-state regression rejection and source-ACK replay.
- Cancellation acceptance/rejection, lost result, duplicate/out-of-order result, active dispatch exclusion and UNKNOWN recovery without a second write.
- Account or mode rotation, production mock-target rejection, production dummy-credential rejection and secret/payload redaction.

The mock must retain state across repeated HTTP calls for each scenario; assertions must observe provider write counts and durable owner state, rather than return canned success. Release evidence records test cases, failures, skips and exact evidence paths. Synthetic fixtures never qualify a real FedEx route or account.

## Dashboard data boundary

Build one dashboard from Integration-owned carrier bookings, jobs, tracking and cancellations, and a separate Commerce-owned source outcome view. Do not join owner schemas or run cross-owner SQL. Dashboards should report bounded aggregate counts and age (mode, state, safe error code, due/overdue age); never label metrics with shipment IDs, booking references, account fingerprints, tracking numbers, addresses, provider body, credentials or label keys. Any alert threshold is an explicit operational/SLO decision and must be configured from reviewed launch inputs; this contract invents none.
