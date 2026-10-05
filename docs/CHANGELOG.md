# Blueprint Changelog

## 1.1.0 —4 October2026

- ASSUMPTION-002: Nigeria/NGN confirmed; baseline metadata and exact kobo conversion, separate unapproved tax/returns/rules retained.
- ASSUMPTION-003: Paystack exclusive launch gateway; same real adapter against stateful mock/test/live config.
- ASSUMPTION-004: FedEx carrier API required at BP1; quotes, booking/labels, cancellation, tracking/reconciliation and stateful mock, manual contingency only.
- ASSUMPTION-012: SMS and WhatsApp via Twilio added to email; contact proof/consent, channel templates/jobs, signed callbacks/inbound/staff replies and mocks.
- ASSUMPTION-013: Keycloak production and local; dedicated vendor identity DB, hardened replicas/admin/TLS/MFA/backup/upgrade.
- Six new tables,15 new endpoints,two new events,six new tasks; all original task IDs retained. Revised schemas/contracts/traceability/tests/infra/supporting docs/status and ADR-008.
- No application source/executable mock generated; build/test NOT_RUN. Original architecture1.0 remains source history; ADR-008 supersedes changed recommendations.
