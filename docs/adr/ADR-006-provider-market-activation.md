# ADR-006: provider market activation

## Context

Architecture does not approve merchants, markets, campaign rates or carrier accounts.

## Decision

Paystack/Bedrock are reference implementations under assumptions; typed fail-closed activation validates approved provider/business inputs.

## Alternatives

Hardcoded merchant/currency/tax/reward rates; speculative carrier API.

## Consequences

Independent implementation can proceed; live activation needs recorded real approval and contract fixtures.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.

## Supersession notice —4 October2026

Conflicting identity/provider/launch assumptions superseded by ADR-008 and master1.1.0; unchanged transaction/BFF/activation-safety decisions retained. This record is historical.
