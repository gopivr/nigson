# Nigson backend implementation instructions

Read `BACKEND_IMPLEMENTATION_MASTER.md` (specification 1.1.0),
`IMPLEMENTATION_STATUS.md`, the task's supporting documents and ADRs before editing.
The master and explicit user decisions govern; ADR-008 supersedes historical
provider/identity assumptions. Read master sections 3, 17, 46, 47, 48 and 54.

Use this workspace root as the monorepo. The untouched
`Nigson_Backend_Implementation_Blueprint/` is the supplied historical package;
root documents and `docs/` govern current implementation. Preserve unrelated work.
The original architecture 1.0 / specification 1.0.0 and its 1.1.0 revision were
document deliverables without application source or executed backend tests.

## Boundaries and launch decisions

- Commerce owns catalogue, pricing, cart, orders, payments, inventory, rewards,
  quotes, fulfillment and the Java BFF in one local transaction boundary.
- Experience, integration and BP5 AI access only their own PostgreSQL schemas.
  Separate runtime/migration roles; no cross-owner SQL, FKs or shared entities.
  Owner boundary changes require a new ADR with contracts and migration analysis.
- Keycloak supplies identity in every environment. Retain PostgreSQL-backed,
  server-side BFF sessions. Integration remains private. AI is introduced in BP5.
- Nigeria NG / NGN with minor unit 2; Paystack only; FedEx API logistics; email,
  Twilio SMS and WhatsApp required. Do not invent tax, prices, campaigns, account
  identifiers, route eligibility, sender/template approvals or retention approvals.
- ECS Fargate, SNS/SQS, Terraform. Do not introduce Kafka, Kubernetes, Helm,
  gateways, discovery servers or additional domain services.

## Working behavior

Choose dependency-ready roadmap tasks. Record IN_PROGRESS before implementation.
Read referenced schemas, APIs, events, acceptance and transaction rules. Implement
complete behavior with validation, authorization, safe errors, audit, observability,
migrations and applicable tests. No core TODOs, canned production success or fake
completion. Directory markers reserve paths and are not implemented features.

Never hold DB locks across provider HTTP. Persist intent, release transactions,
call providers, then persist outcomes. Preserve UNKNOWN after uncertain side
effects; reconcile rather than blindly retry. Use stable business keys and durable
dedup; duplicate/out-of-order events are expected.

Real HTTP adapters use stateful local provider mocks and dummy credentials.
Configuration selects mock/test/live targets without changing domain logic.
Never send dummy credentials to real providers. Production fails closed and
cannot fall back to mocks. Test signatures, duplicates, failures, out-of-order
callbacks and timeout after acceptance. Record genuine provider qualification
BLOCKED/NOT_RUN separately until credentials and account metadata are supplied.

Use section 46 release composition: BP1 includes infrastructure, restore,
security and provider/identity gates; EP numbers are not a sequential release
schedule. Do not defer required launch carrier/messaging to BP4/BP2.

## Verification and handoff

Run relevant build, migration, contract, security, integration and race checks.
Resolve failures before DONE. Section 54 requires actual commit/acceptance
evidence; file existence is not evidence of behavior. Never claim unexecuted checks
passed. Record exact conflicts/proposed resolutions and continue independent work.

Update `IMPLEMENTATION_STATUS.md` and write
`docs/implementation/YYYY-MM-DD-TASK-ID.md` using section 48. Preserve failure and
blocked history, exact safe commands, evidence paths, actual commit hashes and
the next dependency-ready task. Do not log secrets or customer data. Builds/cache
outputs stay ignored. Keep shared packages limited to protocol utilities.
