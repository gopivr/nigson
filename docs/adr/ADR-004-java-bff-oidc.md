# ADR-004: java bff oidc

## Context

Browser tokens must stay server-side; source places BFF in commerce.

## Decision

Java BFF, managed OIDC, JDBC encrypted typed sessions, signed private actor delegation.

## Alternatives

Browser localStorage token; independent launch gateway service.

## Consequences

Commerce bears session/proxy load; compiled routes and private ingress enforce trust.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.

## Supersession notice —4 October2026

Conflicting identity/provider/launch assumptions superseded by ADR-008 and master1.1.0; unchanged transaction/BFF/activation-safety decisions retained. This record is historical.
