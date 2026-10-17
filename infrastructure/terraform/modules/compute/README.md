# Compute module

Creates an ECS Fargate cluster, immutable/scanned ECR repositories, service task
and execution roles, encrypted logs, Cloud Map private discovery, and a public
WAF-associated TLS ALB. Public routes are explicit; the listener default and a
priority-one `/internal*` rule return 404. Integration and AI services cannot be
public. All tasks run in private application subnets without public IPs, with
read-only root filesystems, UID10001, a bounded `/tmp`, health probes, deployment
circuit-breaker rollback and explicit destination egress rules. Task roles start
without business permissions; owner policy modules attach only their required
permissions. Secret values are never Terraform inputs; tasks receive named
Secrets Manager ARNs.

Service image digests, CPU/memory pairs, health probes, secret references,
egress CIDRs, minimum/maximum task counts and per-task DB connection pools are
explicit reviewed inputs. Maximum scale multiplied by each task's configured DB
pool must fit the reserved database connection budget. Production public APIs
require at least two minimum tasks; API scaling is capped at eight and background
services at four. BP5 alone may include AI. Disabling a service creates its ECR
repository and task definition while setting desired tasks to zero, allowing a
separate immutable image publication before service activation.

External provider destinations require their approved CIDRs in service egress
inputs; the module does not add wildcard internet egress. A reviewed plan must
confirm endpoint routes and egress reachability before enabling a service.
