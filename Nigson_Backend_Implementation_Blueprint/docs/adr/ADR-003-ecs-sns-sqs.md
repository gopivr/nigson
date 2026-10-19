# ADR-003: ecs sns sqs

## Context

Source favors managed AWS with a small operational footprint.

## Decision

ECS Fargate, owner outboxes, SNS fanout/SQS per consumer with DLQ.

## Alternatives

Kubernetes/Helm/Kafka/Eureka.

## Consequences

At-least-once and unordered delivery require dedup/version checks; no unused launch infrastructure.

## Status

Accepted technical recommendation; provider/business activation approval remains provisional.

## References

Architecture1.0; master sections3,17,53; specification1.0.0.

## Supersedes

None. Future changes require a new numbered ADR.
