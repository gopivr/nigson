# ADR-005: bounded phase5 ai

## Context

AI must not become a payment/authorization dependency.

## Decision

Separate Python service with Bedrock provisional adapter, approved public RAG, read-only scoped live tools and evaluated policy.

## Alternatives

Embed autonomous model mutation in checkout; private data in common RAG.

## Consequences

Extra BP5 deployable; guardrails/evaluation and spend caps required; outages degrade assistant.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.
