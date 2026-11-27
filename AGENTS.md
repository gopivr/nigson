# Nigson monorepo implementation instructions

Read the relevant source, contracts and operational guides before editing.
Start with `README.md` and `docs/README.md`. The backend specification is detailed
reference for existing generators; current implementation and later ADRs govern.
Preserve unrelated work. Do not create task-status files, session reports or
committed build evidence. Document maintained behavior and commands concisely.

## Boundaries and launch decisions

- Commerce owns catalogue, pricing, cart, orders, payments, inventory, rewards,
  quotes, fulfillment and the Java BFF in one local transaction boundary.
- Experience, integration and BP5 AI access only their own PostgreSQL schemas.
  Separate runtime/migration roles; no cross-owner SQL, FKs or shared entities.
  Owner boundary changes require a new ADR with contracts and migration analysis.
- Keycloak supplies identity in every environment. Retain PostgreSQL-backed,
  server-side BFF sessions. Integration remains private. AI is introduced in BP5.
- Nigeria NG / NGN with minor unit 2; Paystack payments, PandaGo logistics, email,
  and approved messaging. Do not invent tax, prices, campaigns, account
  identifiers, route eligibility, sender/template approvals or retention approvals.
- ECS Fargate, SNS/SQS, Terraform. Do not introduce Kafka, Kubernetes, Helm,
  gateways, discovery servers or additional domain services.

- Carrier integration uses PandaGo only. Retired provider adapters and credentials must not be reintroduced.

## Working behavior

Work on the requested feature or fix.
Read referenced schemas, APIs, events, acceptance and transaction rules. Implement
complete behavior with validation, authorization, safe errors, audit, observability,
migrations and applicable tests. No core TODOs, canned production success or fake
completion. Directory markers reserve paths and are not implemented features.

Never hold DB locks across provider HTTP. Persist intent, release transactions,
call providers, then persist outcomes. Preserve UNKNOWN after uncertain side
effects; reconcile rather than blindly retry. Use stable business keys and durable
dedup; duplicate/out-of-order events are expected.

Application runtimes use official live provider APIs with real credentials in
production. Per the user's 11 November update, local uses Paystack TEST and
PandaGo stage endpoints; local dummy Termii/SMTP credentials fail on access.
Missing configuration fails closed; never route an
application request to a provider mock or send dummy credentials to a real
provider. Stateful HTTP simulators and synthetic fixtures are permitted only in
the isolated automated test profile. Keep LocalStack/Mailpit scoped to local
infrastructure; do not wire them as business-provider fallbacks. Test signatures,
duplicates, failures, out-of-order callbacks and timeout after acceptance.
Record genuine provider qualification BLOCKED/NOT_RUN separately until approved
credentials and account metadata are supplied. See ADR-026.

Use section 46 release composition: BP1 includes infrastructure, restore,
security and provider/identity gates; EP numbers are not a sequential release
schedule. Do not defer required launch carrier/messaging to BP4/BP2.

## Verification and handoff

Run relevant build, migration, contract, security, integration and race checks.
Resolve failures before DONE. File existence is not evidence of behavior. Never claim unexecuted checks
passed. Record exact conflicts/proposed resolutions and continue independent work.

Summarize changes, checks and remaining limitations in the handoff. Keep generated
logs and evidence under ignored `.build/`. Do not log secrets or customer data.
Keep shared packages limited to protocol utilities.

## Repository layout

Backend Maven modules, infrastructure and deployment scripts remain at the root.
The Angular shell and microfrontends live in `frontend/`, with their own npm lockfile.
GitHub workflows live only in root `.github/workflows/`. Both components share
one Git revision; CI uses the built-in repository token.
