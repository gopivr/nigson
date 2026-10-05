# ADR-001: commerce local transaction

## Context

Orders, money and stock need one consistent boundary.

## Decision

Keep catalogue/pricing/cart/order/payment/inventory/reward/quote modules in commerce and preserve local transactions.

## Alternatives

Many independent domain services/distributed locks.

## Consequences

Simpler invariant verification; scale modules together; extraction requires reviewed transaction redesign.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.
