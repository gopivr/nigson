# Backend Implementation Status

Specification:1.1.0 | Architecture source:1.0 + ADR-008 | Updated:5 October2026

## Current Phase

EP00 — Repository/build/local mocks; BP1 foundation.

## Current Task

FOUND-003 — IN_PROGRESS. Implement local dependencies, bootstrap and deterministic command surface.

## Completed Tasks

- [x] FOUND-001 — DONE; AC-FOUND-001 / TEST-FOUND-TREE passed against fresh clone of 65da3bd17c1f73a1570c69cd1e0c2796768daba0. No backend feature completed.
- Report: docs/implementation/2026-10-05-FOUND-001.md
- [x] FOUND-002 — DONE; AC-FOUND-002 evidence in docs/implementation/2026-10-05-FOUND-002.md; implementation commit 12a6c932be0b51517dd2c99a67e155a08206b226.

## In Progress

- [ ] FOUND-003 — IN_PROGRESS; pinned local containers, schemas/roles and identity/emulator bootstrap run twice. Remaining: Liquibase migrate/seed command surface, application Docker profiles, optional AI/vector profile and complete TEST-LOCAL-COLD-START. See docs/implementation/2026-10-05-FOUND-003.md.

## Pending

- [ ] DATA-001 — Implement owner roles, migrations and all declared phase schemas (EP01)
- [ ] PLAT-001 — Implement transactional outbox, consumers and idempotency primitives (EP01)
- [ ] PLAT-002 — Implement errors, validation, audit and observability foundation (EP01)
- [ ] SEC-001 — Configure identity realms and token/session trust (EP02)
- [ ] COM-AUTHO-001 — Implement commerce authorization increment (EP02)
- [ ] COM-PARTY-001 — Implement commerce party increment (EP02)
- [ ] COM-SESSI-001 — Implement commerce session increment (EP02)
- [ ] COM-CATAL-001 — Implement commerce catalogue increment (EP03)
- [ ] COM-FILES-001 — Implement commerce files increment (EP03)
- [ ] COM-NOTIF-001 — Implement commerce notification increment (EP03)
- [ ] COM-PLATF-001 — Implement commerce platform increment (EP03)
- [ ] COM-PROVI-001 — Implement commerce provider increment (EP03)
- [ ] EXP-CONTE-001 — Implement experience content increment (EP03)
- [ ] EXP-FILES-001 — Implement experience files increment (EP03)
- [ ] EXP-LEAD-001 — Implement experience lead increment (EP03)
- [ ] EXP-NOTIF-001 — Implement experience notification increment (EP03)
- [ ] EXP-PLATF-001 — Implement experience platform increment (EP03)
- [ ] COM-INVEN-001 — Implement commerce inventory increment (EP04)
- [ ] COM-MARKE-001 — Implement commerce market increment (EP04)
- [ ] COM-PRICI-001 — Implement commerce pricing increment (EP04)
- [ ] COM-CART-001 — Implement commerce cart increment (EP05)
- [ ] COM-FULFI-001 — Implement commerce fulfillment increment (EP05)
- [ ] COM-ORDER-001 — Implement commerce order increment (EP05)
- [ ] COM-PAYME-001 — Implement commerce payment increment (EP05)
- [ ] INT-NOTIF-001 — Implement integration notification increment (EP05)
- [ ] INT-PROVI-001 — Implement integration provider increment (EP05)
- [ ] COM-PROMO-001 — Implement commerce promotion increment (EP06)
- [ ] COM-REWAR-001 — Implement commerce reward increment (EP06)
- [ ] EXP-CAREE-001 — Implement experience careers increment (EP07)
- [ ] EXP-CONSE-001 — Implement experience consent increment (EP07)
- [ ] EXP-ENGAG-001 — Implement experience engagement increment (EP07)
- [ ] EXP-FILES-002 — Implement experience files increment (EP07)
- [ ] EXP-NOTIF-002 — Implement experience notification increment (EP07)
- [ ] INT-PROVI-002 — Implement integration provider increment (EP07)
- [ ] COM-AUTHO-002 — Implement commerce authorization increment (EP08)
- [ ] COM-PARTY-002 — Implement commerce party increment (EP08)
- [ ] COM-PRICI-002 — Implement commerce pricing increment (EP08)
- [ ] COM-QUOTE-001 — Implement commerce quote increment (EP08)
- [ ] COM-REWAR-002 — Implement commerce reward increment (EP08)
- [ ] EXP-DISTR-001 — Implement experience distributor increment (EP08)
- [ ] EXP-LEAD-002 — Implement experience lead increment (EP08)
- [ ] COM-AUDIT-001 — Implement commerce audit increment (EP09)
- [ ] COM-CART-002 — Implement commerce cart increment (EP09)
- [ ] COM-FULFI-002 — Implement commerce fulfillment increment (EP09)
- [ ] COM-REPOR-001 — Implement commerce reporting increment (EP09)
- [ ] EXP-AUDIT-001 — Implement experience audit increment (EP09)
- [ ] EXP-REPOR-001 — Implement experience reporting increment (EP09)
- [ ] AI-EVALU-001 — Implement ai evaluation increment (EP11)
- [ ] AI-GATEW-001 — Implement ai gateway increment (EP11)
- [ ] AI-KNOWL-001 — Implement ai knowledge increment (EP11)
- [ ] AI-ASSIS-001 — Implement ai assistant increment (EP12)
- [ ] COM-CATAL-002 — Implement commerce catalogue increment (EP12)
- [ ] COM-MARKE-002 — Implement commerce market increment (EP12)
- [ ] COM-ORDER-002 — Implement commerce order increment (EP12)
- [ ] EXP-CONTE-002 — Implement experience content increment (EP12)
- [ ] OPS-001 — Deepen FedEx carrier operational qualification at BP4 (EP09)
- [ ] INFRA-001 — Implement ECS reference infrastructure and immutable delivery pipeline (EP10)
- [ ] OPS-002 — Verify resilience, restore and measured capacity (EP10)
- [ ] REL-001 — Qualify each product phase and hand over documented release (EP13)
- [ ] MOCK-001 — Build stateful Paystack FedEx and Twilio mock servers (EP00)
- [ ] IDP-001 — Deploy production Keycloak identity and migrate provider-specific validation (EP02)
- [ ] NGN-001 — Seed and validate approved Nigeria NGN market (EP04)
- [ ] PAYSTACK-001 — Complete exclusive Paystack launch adapter and mock qualification (EP05)
- [ ] CARRIER-001 — Implement required FedEx rate booking cancellation and tracking (EP05)
- [ ] MSG-001 — Implement SMS WhatsApp consent proof callbacks and mock qualification (EP05)

## Build Status

PASS — FOUND-002 final two clean Java builds, optional locked Python builds and root make verify PROFILE=ai, 2026-10-05. No domain implementation build/qualification inferred.

## Test Status

PASS — FOUND-001 9 repository tests; FOUND-002 6 Java compatibility tests and 4 Python compatibility tests, lint/type checks, dependency convergence/hash repeatability and final advisory scans. FOUND-003 partial checks: 5 local isolation tests, two actual local-up/bootstrap runs, PostgreSQL 18.6 role/schema smoke and real Keycloak discovery pass. Full TEST-LOCAL-COLD-START, domain/migrations/mocks/provider tests NOT_RUN.

## Last Successful Commit

12a6c932be0b51517dd2c99a67e155a08206b226 — FOUND-002 reproducible builds and evidence.

## Confirmed Inputs

Nigeria countryNG / marketNG / Nigerian NairaNGN2; Paystack only at launch; required carrier API with initial FedEx; email/SMS/WhatsApp required with initial Twilio; Keycloak local and production. Supersession: ADR-008.

## Known Issues / Remaining Activation Inputs

FOUND-003 is partial, not DONE: no make migrate/make seed or application Dockerfiles/profiles yet; PostgreSQL pgvector AI profile not verified. Business tables and APIs remain unimplemented. Local subscription event filters and full consumer IAM qualification belong to PLAT-001; current emulator has infrastructure subscriptions only. Initial Keycloak readiness disconnect fixed with bounded transient retry. Local vendor containers are running; local-down preserves volumes.

Actual Paystack test/live keys/merchant account; FedEx client/account/shipper/service-area eligibility; Twilio account/auth/sender/WhatsApp templates/permissions; Keycloak deployment/client secrets/domain/DB. Remaining Nigeria tax/returns/retail book/origin/delivery-area policies, campaign budgets/thresholds, retention and unchanged BP5 model approvals. These are not unresolved country/currency/provider selection decisions.

Credentials absent does not block offline implementation: MOCK-001 must create stateful HTTP mock servers and all adapters/contract tests. Genuine sandbox/live smoke remains BLOCKED/NOT_RUN until actual configuration exists; never pass from mock evidence alone.

## Next Recommended Task

Continue FOUND-003: implement owner Liquibase bootstrap masters and guarded make migrate/make seed; finish application/optional-vector profiles and command-surface checks; run complete TEST-LOCAL-COLD-START. Preserve running nigson-local containers/volumes. DATA-001 and MOCK-001 remain PENDING until FOUND-003 acceptance is complete.

## Session Evidence / Protocol

FOUND-001/002 evidence: docs/implementation/2026-10-05-FOUND-001.md and docs/implementation/2026-10-05-FOUND-002.md. FOUND-002 retains initial convergence/security/sandbox failures and successful remedies. Original blueprint remains unchanged. Initial normal-sandbox git init and Maven Central DNS failed; authorized escalated operations succeeded. Copied specification has original EOF blank lines; diff whitespace validation uses core.whitespace=-blank-at-eof to preserve source bytes.
