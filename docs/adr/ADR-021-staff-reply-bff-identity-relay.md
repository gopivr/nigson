# ADR-021: Commerce BFF owns staff reply session and actor relay

Status: Accepted for MSG-001 implementation, 2026-10-17

## Context

API-192 is documented as an Experience operation, while the verified Keycloak
session and CSRF boundary is Commerce-owned. API-193 was registered for the
`nigson-experience` confidential workload client, but the public session cannot
be safely delegated to Experience: Commerce alone validates the live server-side
session and signs actor assertions. The API-194 context read is Experience-owned
and API-205 suppression remains Experience-owned.

## Decision

Commerce implements the public API-192 BFF endpoint. It verifies the current
session, CSRF token, and current Commerce SALES role before making private calls.
Commerce signs two short-lived actor assertions for the same session identity:
one bound to the exact API-194 GET, and one bound to the exact API-193 POST and
its canonical body. It sends the context assertion only on its fixed Experience
GET and the command assertion only on the Integration POST. The API-193
workload identity is `nigson-commerce`, matching the BFF that owns the session;
the caller still cannot select a destination, identity or role.

Integration validates both assertions and confirms they identify the same
current SALES actor before fetching Experience context or committing a durable
Integration-owned dispatch intent. Experience and Integration retain their
existing schemas and database boundaries. API-205 remains a fresh Experience
suppression check immediately before the unlocked provider call.

## Consequences

The API-192 entry is implemented in Commerce as required by the session boundary,
even though the historical API registry labels its service as Experience. The
workload scope registry and API-193 OpenAPI client identity must match the actual
Commerce caller. Any later move of BFF/session ownership requires a new ADR and
contract review; no session or cross-owner database access is introduced.

## Alternatives considered

- Let Experience call API-193 using its own client credentials: rejected because
  Experience cannot independently validate the live Commerce session or sign
  the second assertion without moving session authority across the owner boundary.
- Let Commerce call Integration using `nigson-commerce`: selected because it
  keeps the current session and actor signer in the existing BFF owner.
- Add a new public service or give Integration Commerce database access: rejected
  because both change the accepted architecture and violate the owner-schema
  boundary.

## Affected requirements and verification

Affected contracts are API-192, API-193, API-194 and API-205; MSG-001 acceptance
and `contracts/security/workload-scopes.json` are updated together. API-193 uses
the existing Commerce workload client and still requires both exact signed actor
assertions. The implementation is verified with the Commerce OpenAPI contract,
Integration private route tests, native owner PostgreSQL tests and the stateful
Twilio mock. No database owner or cross-owner SQL changes are introduced.

## Migration plan

Add the Integration-only 025 staff-reply envelope migration after the existing
dispatch table migration. It is nullable for legacy verification rows, requires
all five staff fields together, and refuses rollback when staff reply context is
populated. No Commerce or Experience migration is required.

## References

`BACKEND_IMPLEMENTATION_MASTER.md` sections 4, 9, 10, 13, 15, 17, 27 and 53;
ADR-004; ADR-020; SEC-001; MOCK-001; API-192/193/194/205.
