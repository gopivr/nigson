# ADR-007: contract versioning

## Context

Long-lived sessions and event consumers need stable contracts.

## Decision

Versioned APIs/events, Liquibase expand-contract migrations and reviewed OpenAPI/schema diff.

## Alternatives

Unreviewed runtime auto-DDL or silent response change.

## Consequences

Additional compatibility work; safer rollback and reproducible implementation.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.
