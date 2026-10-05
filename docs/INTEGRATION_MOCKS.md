# Provider Mock Implementation Contracts

Specification1.1.0; master authoritative. Mock server source must be created during implementation, not included in this authoring task.

Implement these concrete launch adapters behind typed ports; deployment configuration switches endpoints/credentials without changing domain code.

| Provider / owner | Required operations | Local mock and real mode |
|---|---|---|
| Paystack / commerce PaymentProvider | Hosted initialize, verify, refund/create/query, signed webhook receipts | Stateful Paystack mock with dummy key; test/live use real Paystack key and official allowlisted endpoint |
| FedEx / integration CarrierProvider, commerce rate facade | OAuth, rate/service lookup, shipment creation/private label, cancellation, tracking polling/reference reconciliation | Stateful FedEx mock with dummy client/account; real sandbox/live endpoints and account/service configuration |
| Twilio / integration SmsProvider and WhatsAppProvider | SMS/WhatsApp send, status query, approved templates, signed DLR/inbound facade | Stateful Twilio mock and synthetic callbacks; sandbox/trial/live require actual Twilio account/senders/templates |
| Keycloak / identity, BFF/workloads | Discovery/JWKS, code+PKCE, refresh/logout, client credentials, staff MFA | Real local Keycloak container; same provider in production with hardened deployment |
| SES/SMTP, Sheets, S3/scanner, Bedrock | Existing mail/sync/files/BP5 AI ports | Existing bounded private adapters and test fixtures retained; email remains supported |

Paystack HTTP: POST /transaction/initialize sends exact NGN kobo integer, stable reference/email/server metadata/callback allowlist; GET /transaction/verify/{reference} independently verifies terminal status/reference/merchant/amount/currency. POST /refund always specifies transaction and refund amount; GET /refund/{id}, transaction-scoped listing for uncertain issuance. Refund lifecycle maps pending/processing to pending, processed to succeeded, authoritative failure to failed. SHA512 HMAC over raw webhook bytes with x-paystack-signature and constant-time check; durable event/reference dedup, no invented timestamp header or native idempotency guarantee. Original payment/order locks and UNKNOWN handling unchanged. Base https://api.paystack.co for genuine test/live; dummy secrets accepted only by local mock. Docs: https://paystack.com/docs/api/transaction/ and https://paystack.com/docs/api/refund/.

FedEx contract snapshots: capture official versioned JSON/OpenAPI fixtures under contracts/providers/fedex during CARRIER-001. POST /oauth/token form grant_type=client_credentials, client_id/client_secret; cache returned token until actual expires_in minus60s, refresh single-flight and never persist plaintext in logs. POST /rate/v1/rates/quotes reads eligible services/rates from a server-owned shipment request. POST /ship/v1/shipments uses accountNumber/requestedShipment/labelResponseOptions, stable customer reference and approved service/package/address; normalize tracking/private label response. PUT /ship/v1/shipments/cancel sends the saved account/tracking request. POST /track/v1/trackingnumbers or /track/v1/referencenumbers polls authoritative tracking. Import the provider's exact payload schemas rather than a guessed generic carrier schema; contract fixtures test required fields and nested responses. Mapping: label created -> BOOKED (not SHIPPED), accepted/in-transit -> handover evidence/SHIPPED, delivery -> DELIVERED, exception -> visible hold without payment rollback; terminal status cannot regress. Poll active bookings every15m initially, batches<=30/provider lower quota, stop delivered/cancelled; provider reference after ambiguous create must reconcile or enter operator review, never blindly rebook. Connection2s/read8s/total10s; safe reads max2 bounded retries; create/cancel UNKNOWN preserved. FedEx is the initial selected adapter; real Nigeria route/service/currency eligibility is account-specific and verified at release. Docs: https://developer.fedex.com/api/en-us/catalog/authorization/v1/docs.html, https://developer.fedex.com/api/en-us/catalog/rate/v1/docs.html, https://developer.fedex.com/api/en-us/catalog/ship/v1/docs.html, https://developer.fedex.com/api/en-us/catalog/track/v1/docs.html.

Twilio HTTP: POST /2010-04-01/Accounts/{AccountSid}/Messages.json form with To, approved From or MessagingServiceSid, StatusCallback; SMS Body; WhatsApp recipients/sender prefixed whatsapp: and approved ContentSid/ContentVariables for proactive templates. Use account/auth credentials through secrets, not a dummy live call. GET message resource by returned SID reconciles known sends. SDK signature validation uses the exact external callback URL/query and complete received parameter set; do not hand-roll a subset check, trust arbitrary proxy headers or disable validation for mocks. StatusCallback includes owned jobId and maps SID/account/channel to the durable job. WhatsApp inbound creates consent-aware contact leads; approved templates are default for all outbound staff/transactional messages, avoiding a hardcoded conversational window assumption. Production sender/WhatsApp business onboarding/template approval and Nigerian SMS routing permissions are configuration/release inputs, not implied by an API key alone. Docs: https://www.twilio.com/docs/messaging/api/message-resource, https://www.twilio.com/docs/whatsapp/quickstart, https://www.twilio.com/docs/usage/webhooks/webhooks-security, https://www.twilio.com/en-us/guidelines/ng/sms.

All adapters have allowlisted targets, redirects disabled, response limits,2s connect/8s read/10s total initial bounds, safe-read retry<=2, side-effect UNKNOWN reconciliation, bounded concurrency and safe provider/status metrics. Domain transaction never holds locks across network calls. Real account credentials plus endpoint/mode/identity/service/template configuration replace mock configuration; no provider adapter source rewrite is required.

Notification remains a module of the private integration worker. EMAIL, SMS and WHATSAPP are required launch channels, with separate typed provider ports and explicitly configured routing; no new notification microservice. Email newsletters/double opt-in remain BP2; transactional SMS/WhatsApp and opt-in destination proof are BP1.

notification_template is keyed by code/revision/locale/channel; immutable active revisions hold allowlisted escaped variables and provider_template_id for WhatsApp. notification_job business key includes template/resource/revision/channel/destination; duplicate domain events create one job per authorized configured channel. Initial10 messages/s or lower provider quota and bounded concurrency; cap SMS segments before send using provider-compatible encoding calculation and approved maximum, never assume every SMS is one160-character segment. Sender identity, channel quotas and spend cap are environment config. Never send all channels by default or silently switch channels after failure.

Templates: CONTACT_VERIFICATION, ORDER_PENDING/PAID/SHIPPED/DELIVERED/CANCELLED, REFUND_STATUS, LEAD_CREATED, DISTRIBUTOR_APPLICATION/DECISION, QUOTE_OFFERED, NEWSLETTER_CONFIRMATION, CAREER_APPLICATION, STAFF_LEAD_REPLY and BP4 CART_RECOVERY. Default marketing classification applies to promotions/newsletters/recovery; transactional labels do not bypass phone/channel opt-in or suppression. Phone proof is requested by the authenticated user, uses a10m six-digit code and max5 attempts, and does not replace Keycloak login/MFA. Code is fetched only through scoped encrypted ephemeral context and erased on confirm/expiry; events contain IDs only.

Execution: persist CLAIMED/attempt before provider HTTP, fetch minimum owner context/current channel-purpose eligibility, select immutable template, send with stable job callback mapping, store provider SID/outcome, then acknowledge source queue. Late callback may precede HTTP response: matched job exists before send, receipt is persisted and applied under lock. PENDING/queued/sent are not DELIVERED. Timeout after accepted send -> UNKNOWN; reconcile known SID/callback and operator if uncertain; no blind resend or exactly-once external-send claim. Fresh consent/suppression is checked immediately before send; if owner unavailable defer/fail closed. A withdrawal after authorization may race an already in-flight provider call; record that bounded window accurately.

Commerce exposes only signed Twilio status callback facade; experience exposes only signed WhatsApp inbound facade. The integration worker has no public route. Validate official SDK signature/account/URL before forwarding a sanitized receipt; acknowledge only after durable owner receipt/job commit. Inbound STOP/UNSUBSCRIBE suppresses matching canonical phone/channel; START does not silently regrant consent, HELP is static, other messages become private CONTACT leads for staff. An inbound number is not an authenticated party and cannot retrieve private orders. Staff replies use explicit approved templates and fresh destination eligibility. Push/in-app feed, autonomous WhatsApp AI and SMS login/OTP MFA are not added by this change.

SES feedback still uses restricted SNS/SQS subscription, no public worker webhook. SMTP SENT is distinct from delivery. Newsletter tokens/list sync/email suppression and Sheet workflows retain their prior controls.

Generate a multi-stage Dockerfile for each Java/Python deployable during implementation. Java build uses Maven wrapper/Java25, runtime JRE25 minimal supported image pinned by digest, non-root UID10001, read-only root filesystem where feasible, temp volume, graceful SIGTERM and JVM container memory awareness. Python build creates a locked isolated environment, copies only package/runtime dependencies, non-root UID10001, uvicorn bounded workers and async pools. No embedded credential or package-download at runtime. Verify ARM compatibility before switching task architecture.

Health: GET /actuator/health/liveness and /actuator/health/readiness Java; GET /health/live and /health/ready Python. Liveness proves process/event-loop responsiveness, not third-party payment/AI availability. Readiness checks essential owned DB/schema initialization and safe configuration; external email/model outages do not restart otherwise healthy core services. Worker health includes its ability to poll/commit work; probe binds private interface and no public route. Container healthcheck uses the actual port and shell-free minimal command available in image; ECS/ALB health checks duplicate only necessary signals.

Local docker-compose.yml baseline: PostgreSQL18 pgvector-capable image (extension used only AI profile), LocalStack SNS/SQS/S3/SES emulation, Keycloak local realm, Mailpit, optional OTel collector. Full-app profile starts commerce8081, experience8082, worker8083 private, AI8084 only ai profile; dependencies ports localhost only. Valkey is optional cache profile, not default. No Kafka/OpenSearch/Kubernetes/Helm services. DB healthcheck plus one-shot migrations precede application readiness. Persistent named volumes are never deleted by default stop. LocalStack IAM emulation does not replace live staging IAM/security tests.


## Mock-first Compose and production identity

Add provider-mocks profile with separate stateful paystack-mock, fedex-mock and twilio-mock services using pinned WireMock images plus versioned test-only scenario extension/controllers. Compose core local setup includes these mocks and real local Keycloak. Testcontainers starts equivalent instances for CI; mock administration/reset/journal is localhost/internal-test-network only. Callback dispatcher sends signed HTTP to the real app facades; failure/scenario controls are never public production APIs. Tests prohibit external paid network access. Production builds exclude mock controllers, images, dummy credentials and test admin routes.

Keycloak image is pinned/optimized in production, supported external PostgreSQL, private management/health/admin, TLS/proxy/hostname and cluster settings; local start-dev is local only. Do not reuse Keycloak development filesystem DB or default admin credentials for release.

Every implementation task includes focused behavior tests and the applicable real integration tests. Unit tests target money rounding, state transitions, rule selection and authorization; do not mirror getters. Repository tests run real PostgreSQL18/pgvector with production Liquibase masters, validate constraints/index availability/transaction rollback and role isolation. API tests validate generated OpenAPI, exact DTO/error/status/header behavior and unknown-field handling.

Security tests include wrong issuer/audience/algorithm, expired tokens, stale business permissions, CSRF/cookie/CORS, IDOR across two organizations, forged actor delegation, capability purpose/expiry, unauthorized file access, invalid webhook HMAC and no private fields in public responses. Testcontainers/LocalStack validates SNS subscriptions, SQS visibility/dedup/DLQ/replay; live stage verifies AWS IAM because emulators do not enforce all permissions. WireMock or compatible isolated HTTP mock tests provider timeouts and crash recovery; golden fixtures are sanitized official/synthetic payloads.

Critical scenarios with stable test IDs: TEST-CHECKOUT-ATOMIC, TEST-STOCK-RACE (50 contenders for final10 units, exactly10 allocated), TEST-WEBHOOK-DUP (same verified callback20 times), TEST-LATE-SETTLEMENT, TEST-PAYMENT-UNKNOWN, TEST-PARTIAL-REFUND (concurrent requests cannot exceed settlement), TEST-REFUND-UNKNOWN, TEST-QUOTE-IMMUTABLE, TEST-B2B-IDOR, TEST-ENTITLEMENT-ACK, TEST-SUSPENSION-ACK, TEST-COUPON-LIMIT, TEST-WHEEL-BUDGET, TEST-REFERRAL-REVERSAL, TEST-UNSUBSCRIBE-SUPPRESSION, TEST-FILE-QUARANTINE, TEST-CAREER-SHEET-RETRY, TEST-OUTBOX-CRASH, TEST-QUEUE-REPLAY, TEST-AI-INJECTION, TEST-AI-SCOPE, TEST-AI-WITHDRAWN-SOURCE, TEST-AI-PROVIDER-OUTAGE, TEST-RESTORE and TEST-CACHE-OFF. Task registry references these plus generated per-API/per-table contracts.

Contract tests use JSON Schema events and producer/consumer golden vectors; generated OpenAPI diff detects incompatible API changes. E2E backend workflows use HTTP plus real DB/emulator, not browser UI outside this task; optionally Playwright/APIRequestContext drives the frontend contract without generating frontend components. AI offline mocks test plumbing and safety; separately approved live evaluation tests grounding, latency and cost on selected real model. No test result is called PASS without running it.

CI should use deterministic Clock and injectable random source for unit tests; production wheel uses secure randomness. Failure injection simulates commit/publish crash, duplicate/out-of-order messages, provider success+lost response, DB contention, stale TTLs and scan outages. Production hardening tests include load scenario section42, dependency outage and isolated restore. Each named mandatory test maps to a task and requirement below.


## New mandatory scenario specifications

| Test ID | Given / When / Then |
|---|---|
| TEST-MOCK-OFFLINE | Given dummy keys and blocked external network, when adapters initialize/pay/refund/rate/book/track/send, then only mock HTTP targets receive calls and domain invariants hold. |
| TEST-MOCK-STATE | Given persisted provider operation, when callback/status/refund/track request repeats, then mock state is deterministic and test reset fully isolates the next case. |
| TEST-MOCK-SIGNATURE | Given mock callback, when exact signature is valid/altered/missing, then real provider verifier accepts only valid payload. |
| TEST-MODE-ISOLATION | Given LIVE configuration, when dummy key/mock hostname or MOCK quote enters checkout, then configuration/command fails before provider side effect. |
| TEST-NGN-KOBO | Given NGN1234.56, when initialize/refund converts, then integer123456; overprecision/foreign currency rejected without floating point. |
| TEST-NGN-MARKET | Given approved NG/NGN metadata, when setup runs twice, then one baseline exists and missing tax/returns/book/routes still prevents selling. |
| TEST-PAYSTACK-MODE-SWITCH | Given same adapter/domain suite, when config changes mock->real test credential, then contract responses match; recorded sandbox result required, never assumed from mocks. |
| TEST-CARRIER-RATE-BINDING | Given unexpired quote, when cart/address/packages/service/currency/mode change, then checkout rejects stale/mismatched quote rather than undercharging. |
| TEST-CARRIER-UNKNOWN | Given create accepted but response lost, when job replays, then one booking attempt remains UNKNOWN/reconciled and no blind second label appears. |
| TEST-CARRIER-TRACKING | Given duplicate/out-of-order tracking and label creation, when processed, then label alone does not dispatch, stock dispatch once, delivery terminal does not regress. |
| TEST-SMS-MOCK | Given verified SMS purpose grant, when job/callback replays, then one job/SID and correct status; failed/unverified/withdrawn destination blocked. |
| TEST-WHATSAPP-MOCK | Given approved mock ContentSid and opt-in, when template/inbound/STOP/reply runs, then signed message creates one private lead or immediate suppression and no private order lookup. |
| TEST-TWILIO-SIGNATURE | Given callback account/publicURL/query/complete form parameters, when any covered value/signature is altered, then denied before mutation; duplicate valid callback acknowledged. |
| TEST-PHONE-CONSENT | Given10m proof/max5 tries, when expired/forged/cross-party code or unrequested purpose used, then denied; confirmed proof activates once; withdrawal suppresses per destination/channel. |
| TEST-MESSAGE-UNKNOWN | Given send accepted but timed out, when worker restarts and callback arrives before response, then receipt matches existing job and no blind resend; delivered not claimed from queued. |
| TEST-KEYCLOAK-PROD-CONFIG | Given production image/hostname/DB settings, when start-dev, wrong token issuer/aud/client/scope or public admin access attempted, then reject. |
| TEST-KEYCLOAK-REPLICA | Given tested2-replica identity cluster, when one replica stops, then login/refresh/session continuity follows configured policy and BFF remains resource-authorized. |
| TEST-IDENTITY-RESTORE | Given isolated backup, when identity database/realm/config restore occurs, then login/signing/role mapping verified without exposing credentials. |

Existing payment/refund/race/IDOR/audit/restore/AI tests remain mandatory. Provider credentials absent means live/sandbox qualification NOT_RUN/BLOCKED, not mock-test failure; independently complete all offline adapter/contract tests.

Implementation must generate README.md, .env.example, docker-compose.yml, Makefile and idempotent setup scripts. Supported prerequisite: Docker Compose, Java25, Maven wrapper, Python3.12/uv only for ai profile; shell commands work from repository root.

```bash
cp .env.example .env
make local-up
make migrate ENV=local
make seed ENV=local
make run-core
make verify
# only when reaching EP12/BP5
make local-up PROFILE=ai
make run-ai
```

`local-up` starts required dependencies and waits on bounded health checks; it creates local SNS subscriptions/S3 buckets/Keycloak realm through deterministic scripts. `migrate` executes each owner role/master, fails on any schema drift and does not use Hibernate DDL. `seed` rejects any nonlocal/test env and requires explicit approved fixture profile. `run-core` starts Java services in separate processes/Compose app profile; reports URLs/health checks and clean stop handling. User login uses local OIDC flow; direct API tests obtain real local signed access tokens. Do not add a universal insecure bypass header.

Local services binds: BFF/core http://localhost:8081, experience8082, worker8083 localhost/private only, AI8084 optional, PostgreSQL5432 localhost, LocalStack4566, Keycloak8180, Mailpit8025. Origin URLs configurable and not compile-time constants. Use named volumes and separate test DB. .env is gitignored. Local dummy keys are clearly local-only and forbidden by prod validation; local HTTP cookie/issuer exception is profile-limited. Health/smoke checks confirm real persistence and identity flow before showing ready.


## Credential-free integration setup

```bash
make local-up PROFILES=provider-mocks
make migrate ENV=local
make seed ENV=local MARKET=NG CURRENCY=NGN
make mock-reset
make provider-contract-test PROVIDER=all MODE=mock
make provider-scenario PROVIDER=paystack SCENARIO=success
make provider-scenario PROVIDER=fedex SCENARIO=booking-response-lost
make provider-scenario PROVIDER=twilio SCENARIO=duplicate-status
make keycloak-config-check ENV=local
```

Implementation must create these commands in MOCK-001/IDP-001; they are specified commands, not scripts generated in this document-only task. make provider-live-check ENV=stage requires actual user-supplied account/credentials/sender/service config and may perform real provider sandbox operations; never run it automatically while keys are dummy.
