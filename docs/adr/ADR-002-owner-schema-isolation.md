# ADR-002: owner schema isolation

## Context

One RDS instance is initially cost-effective but services need ownership.

## Decision

Separate owner schemas/runtime/migration roles; no cross-owner SQL/FKs.

## Alternatives

Shared entities/repositories; separate RDS per service at launch.

## Consequences

Contracts/eventual projections required; physical split remains possible.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.
