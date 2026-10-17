# Release Configuration

Specification1.1.0; master authoritative.

### ASSUMPTION-001

- **Description:** Account-only checkout at launch
- **Reason:** Source leaves guest checkout undecided.
- **Implementation impact:** All carts/orders require mapped party; guest checkout is deferred, not silently implemented.

### ASSUMPTION-002

- **Status:** Confirmed by user; replaces specification1.0.0.
- **Description:** Nigeria and Nigerian Naira approved for launch
- **Reason:** User clarification, 4 October2026.
- **Implementation impact:** Seed market code NG, destination country NG, currency NGN with minor_unit2. The country/currency approval is complete. Tax, returns, retail prices and actual serviceable delivery areas remain separate configuration inputs; never substitute a guessed tax rate.

### ASSUMPTION-003

- **Status:** Confirmed by user; replaces specification1.0.0.
- **Description:** Paystack is the only launch payment gateway
- **Reason:** User explicitly selected Paystack and requested a mock server before adding demo/live credentials.
- **Implementation impact:** Implement real initialize/verify/refund adapters and signed webhook verification through PaymentProvider. Implement a local stateful HTTP mock with dummy keys; PAYSTACK_MODE=mock|test|live selects configuration, not different domain logic. Real test/live keys are added on release; no other payment gateway in launch code.

### ASSUMPTION-004

- **Status:** Confirmed by user; replaces specification1.0.0.
- **Description:** Carrier API logistics required at launch; FedEx selected as initial adapter
- **Reason:** User authorized FedEx or another prominent Nigeria carrier and mock-first implementation.
- **Implementation impact:** Implement FedEx OAuth, rate requests, shipment creation/labels, cancellation and tracking polling through CarrierProvider, plus local stateful mocks. Carrier work is BP1 required, not deferred to BP4. Real service/account/route availability is checked with release credentials; no claim that every domestic Nigerian route supports a FedEx service. Manual exception handling is a contingency only.

### ASSUMPTION-005

- **Description:** Java BFF shares the commerce process
- **Reason:** Source specifies BFF in commerce.
- **Implementation impact:** Use Spring Session JDBC in commerce; default idle TTL 30m, absolute 12h, staff recent MFA <=15m; encrypted refresh tokens.

### ASSUMPTION-006

- **Description:** One location, extensible stock ledger
- **Reason:** Source launch inventory is platform-managed.
- **Implementation impact:** Location allocation order is configured; launch permits one location per line. Additional locations do not change SKU units.

### ASSUMPTION-007

- **Description:** Reservation TTL 15m and priced quote TTL 5m
- **Reason:** Exact timing not specified.
- **Implementation impact:** Configuration validates TTL bounds; late settlement follows explicit hold/re-reservation rules; never assume expired equals unpaid.

### ASSUMPTION-008

- **Description:** Paystack full capture, no partial order payments
- **Reason:** Source does not request installments or split payment.
- **Implementation impact:** One accepted settlement per order; partial refunds supported; decline partial settlement as mismatch for resolution.

### ASSUMPTION-009

- **Description:** Single company with organizations, not SaaS tenancy
- **Reason:** Source is Nigson platform with distributors.
- **Implementation impact:** Do not generate tenant onboarding or one-database-per-customer infrastructure; party may belong to several organizations.

### ASSUMPTION-010

- **Description:** Currency minor-unit rounding HALF_UP
- **Reason:** No rounding/tax policy specified.
- **Implementation impact:** Use BigDecimal; round each line net/tax under configured policy and derive totals from rounded lines. Tax rules and rates need explicit activation.

### ASSUMPTION-011

- **Description:** Commercial features disabled until rules approved
- **Reason:** Amounts and thresholds are missing.
- **Implementation impact:** Implement versioned rule engines and staff configuration; no production free rewards or blanket discounts seeded.

### ASSUMPTION-012

- **Status:** Confirmed by user; replaces specification1.0.0.
- **Description:** Email, SMS and WhatsApp integrations required
- **Reason:** User expanded automated communications beyond entry links.
- **Implementation impact:** Keep email and newsletter double opt-in; add Twilio SMS and Twilio WhatsApp Business adapters, signed status/inbound callbacks, verified destinations, purpose/channel consent and templates. Mock-first implementation uses dummy credentials and synthetic recipients. Credentials, sender identities and approved WhatsApp templates are supplied for provider testing/release.

### ASSUMPTION-013

- **Status:** Confirmed by user; replaces specification1.0.0.
- **Description:** Keycloak is the identity provider in every environment, including production
- **Reason:** User explicitly replaced the Cognito production recommendation.
- **Implementation impact:** Deploy and configure Keycloak with supported PostgreSQL identity database, OIDC/PKCE, confidential BFF/workload clients, staff MFA, TLS/proxy settings, private admin access, HA/backup/upgrade runbooks. Spring Session JDBC BFF sessions remain in commerce; business schemas never own password hashes.

### ASSUMPTION-014

- **Description:** Bedrock reference model/embedding provider
- **Reason:** AWS reference hosting and approved AI service in architecture.
- **Implementation impact:** Use boto3 Converse/ConverseStream for compatible selected chat model, InvokeModel Titan v2 for normalized 1024 embeddings; chat model ID is required activation config, not fabricated default.

### ASSUMPTION-015

- **Description:** No raw transcripts retained by default
- **Reason:** Privacy and AI retention undecided.
- **Implementation impact:** Encrypt bounded checkpoints <=24h; redact run logs. Optional approved transcript retention requires explicit policy.

### ASSUMPTION-016

- **Description:** SQS standard queues, SNS routing
- **Reason:** Architecture explicitly picks SNS/SQS.
- **Implementation impact:** At-least-once, no global ordering, consumer-local dedup. Use aggregate versions and authoritative reads; no exactly-once transport claim.

### ASSUMPTION-017

- **Description:** Terraform selected from IaC options
- **Reason:** Architecture allows Terraform or OpenTofu.
- **Implementation impact:** Use Terraform pinned stable 1.x in EP00; no Helm/EKS manifests unless separate approved architecture change.

### ASSUMPTION-018

- **Description:** Recovery profile and load fixture are engineering targets
- **Reason:** Production demand/budget unspecified.
- **Implementation impact:** Initial test workload: 100 concurrent sessions, 20 catalogue RPS plus 2 checkout RPS, 30m; 10k products/20k SKUs synthetic. Verify real sizing before commitment.

### ASSUMPTION-019

- **Description:** Retention defaults are provisional
- **Reason:** Operating jurisdiction unspecified.
- **Implementation impact:** Configurable defaults: outbox 30d after publish, dedup 90d, notification context 30d, careers 180d, audit/financial 7y, AI checkpoints 24h; approval required before production retention activation.

### ASSUMPTION-020

- **Description:** Two-person sensitive finance/entitlement approval supported
- **Reason:** Source expects governance but not exact thresholds.
- **Implementation impact:** Finance refund policy and sales discount thresholds are config. Default require second actor on refund approval and distributor approval for production; business may approve adjusted controls.

### ASSUMPTION-021

- **Description:** CMS structured JSON, sanitized rich text
- **Reason:** No CMS product named.
- **Implementation impact:** Implement content schemas and revision workflows in experience, not a new service or external CMS dependency.

### ASSUMPTION-022

- **Description:** Signed actor delegation for private BFF/tool hops
- **Reason:** Independent APIs need trusted context without trusting model/body IDs.
- **Implementation impact:** Workload OAuth token plus short-lived signed actor assertion; audience-bound, operation-scoped, validated at target; detailed security contract in section 15.

Missing real credentials/account configuration, catalogue, tax/returns and service-area policy are production activation blockers only. Continue independent local/sandbox implementation and record their status. Never mark their production tests as passed without evidence.


Remaining activation inputs are credentials/accounts/senders/templates, Nigeria tax/returns/prices/service areas, hosting/data location and unchanged campaign/AI policies. Nigeria/NGN, Paystack selection, automated messaging and production Keycloak are no longer unresolved provider/market decisions. Dummy values are local/test-only and cannot authenticate to real providers.

Profiles local, dev, test, qa, stage, prod; qa may reuse staging infrastructure shape with its own state/secrets. Use typed @ConfigurationProperties/Pydantic Settings with startup validation. Local/test may supply deterministic adapters; stage/prod reject fake payment/model adapters, permissive CORS, HTTP issuer and embedded credentials.

Required groups: service identity/public/internal base URLs; JDBC owner URL/user/secret ref; OIDC issuer/client/resource scopes; BFF allowed return origins and signing KMS ref; SNS/SQS ARNs/URLs; S3 bucket/purpose policies; payment provider/secret/merchant/approved currencies; email provider/sender/recipient groups; Sheet ID/credential ref/field allowlist; required FedEx carrier; feature flags; market/tax/reward/retention policies; AI region/model/prompt/rate/budget revisions; observability exporters and sampling. No environment-specific URL in Java/Python source. `.env.example` contains examples/empty secret references only, no production endpoint assumption.

Runtime flags default false for rewards/wheel/international/AI/carrier/MCP. Activation validates complete policy plus approval evidence and writes audit. A feature disabled still rejects unauthorized access and reports FEATURE_DISABLED consistently; flags cannot bypass role checks. Missing enabled-provider config causes startup/activation failure, not silent fallback to a mock. Session/financial deadlines must be within documented limits. Use Secrets Manager runtime references and ECS task IAM; no secrets in Terraform state values, image args, Git history, completion reports or .env.example.


## Launch provider configuration and credential replacement

| Group | Required settings |
|---|---|
| Market | LAUNCH_MARKET=NG, LAUNCH_COUNTRY=NG, LAUNCH_CURRENCY=NGN, minor_unit2; tax/returns/book/origin/service-area policies separately versioned |
| Paystack | PAYMENT_PROVIDER=paystack only; PAYSTACK_MODE=mock\|test\|live, PAYSTACK_BASE_URL, PAYSTACK_SECRET_KEY_REF, merchant/account reference, public callback/webhook URLs |
| FedEx | CARRIER_PROVIDER=fedex; FEDEX_MODE=mock\|sandbox\|live, FEDEX_BASE_URL, CLIENT_ID/SECRET refs, account number ref, shipper origin, enabled service/package policies, poll interval and label bucket |
| Twilio | SMS_PROVIDER=twilio, WHATSAPP_PROVIDER=twilio; independent channel enabled flags; TWILIO_MODE=mock\|sandbox\|live, BASE_URL, ACCOUNT_SID/AUTH_TOKEN refs, SMS sender/MessagingServiceSid, WhatsApp sender/ContentSid map, exact status/inbound public URLs |
| Keycloak | KEYCLOAK_IMAGE_DIGEST, dedicated DB/secret refs, realm issuer, public hostname/private admin hostname, trusted proxy settings, BFF/workload client secret refs, MFA/realm/client/audience policy |

Local/test defaults point to private Compose mock hostnames and visibly synthetic credentials such as mock-paystack-secret, mock-fedex-client and mock-twilio-token. .env.example may include these non-secret mock values and empty real secret references. No fake key is sent to a genuine provider endpoint. Mode selects target/auth/config, never skips business invariants or callback signature verification. Mock callback secrets/signatures are real algorithms over dummy secrets.

Production profile rejects mock/sandbox targets or dummy credentials for enabled financial/carrier/messaging features; real test/trial credentials run against provider staging or explicitly non-production release verification. Missing enabled-provider config fails validation rather than silently falling back. Staging can run mock or genuine sandbox with recipient allowlist and separate data. A live environment with features disabled may start for infrastructure checks but cannot claim completed live checkout/fulfillment/messages. Keycloak production configuration is required for production identity regardless of provider test mode.

## Ordered provider/identity launch qualification

1. Complete offline mocks/adapters/contracts and all mandatory financial/security tests.
2. Provision hardened Keycloak/DB/client/MFA/domain and verify replica/restore/private-admin controls.
3. Configure Nigeria tax/returns/book/origin/service areas; metadata NG/NGN already approved.
4. Add genuine Paystack test key, FedEx sandbox/account and Twilio trial/sandbox senders/approved templates in Secrets Manager; apply provider target allowlist and test recipient restrictions.
5. Record actual provider checkout/refund/rate/book/cancel/track/SMS/WhatsApp/callback evidence. Verify FedEx services cover approved routes/currency; keys alone cannot establish service availability. Fix account/config gaps without pretending mocks prove live support.
6. Switch configuration to live secrets/endpoints/senders/templates; no adapter/domain source rewrite. Production validation refuses dummy/mock targets. Run approved bounded live smoke and record commit/config revisions, mode/account and receipts without raw credentials.
7. Verify alerts/reconciliation/backup/rollback and update IMPLEMENTATION_STATUS.md with actual results. Existing project deployment authority governs release, no new permission workflow introduced here.

## Identity deployment artifact and policy inputs

IDP-001 supplies a digest-pinned optimized Keycloak image and an identity
Terraform module with private administration, two replicas, dedicated encrypted
RDS, trusted TLS proxy settings, scoped secrets and operator alerts. The default
service count is zero until explicit bootstrap/provisioning. Use actual approved
DNS/callback/realm/certificates/CA/KMS/secret references, database minor version,
operator/destinations and backup/log/audit retention. The module's mock-provider
fixtures are synthetic test metadata and confer no production approval. Private
bootstrap credentials belong only to the one-off task and must be retired after
approved permanent MFA administration succeeds. See
[Keycloak deployment/recovery](runbooks/keycloak.md). BP1 live carrier/messaging/
payment/identity/security/infrastructure/restore gates remain independent of
engineering task numbering and require genuine qualification evidence.

## SEC-001 private/session deployment inputs

Use the explicit configuration and key-rotation procedure in [session/private trust](runbooks/session-trust.md). `infrastructure/terraform/modules/private-trust/` supplies distinct KMS signing/session keys, scoped commerce task permissions and source-security-group private ingress; it does not create another service. Real ECS roles, key administrators/provisioner, private source/target security groups, DNS/client/callback/secret/public-key references and approved policy revision must be supplied. KMS/ALB/IAM qualification is NOT_RUN until that deployment exists. The BFF callback must be exactly `/bff/callback` per API-002; replace the earlier IDP tooling `/login/oauth2/code/keycloak` redirect in the environment realm/client revision before activation. No existing nigson-local realm/container/volume is rewritten by isolated qualification.

## NGN-001 approved metadata baseline —10 October2026

Additive BP1 migration024 seeds deterministic NG/Nigeria, active NGN2 and disabled destination NG; new market/delivery remain disabled with unresolved tax/address/returns/book. Country/currency approval is complete. Existing compatible policies/IDs are preserved; incompatible scale/currency fails before writes. No service-area/tax/price/provider approval is fabricated. See docs/runbooks/nigeria-baseline.md. Currency support never implies permission to sell.

## Paystack qualification gate

Use [Paystack qualification](runbooks/paystack-qualification.md) for the exact offline command and genuine read-only prerequisites. Known configuration-only/synthetic credentials are rejected before HTTP; missing or invalid stage/TEST/official endpoint/reference/kobo configuration stops before builds. A matching successful verification response does not establish hosted checkout/refund/callback/account or whole BP1 release qualification. Actual merchant/key association and integration metadata remain required activation inputs.

ADR-016 required carrier identity configuration: commerce requires NIGSON_CARRIER_ACCOUNT_FINGERPRINT when NIGSON_PRICING_CHECKOUT_ENABLED or NIGSON_FULFILLMENT_ENABLED is true. Supply the exact qualified integration adapter fingerprint, never an account guess/sample/default. API-182 returns the actual identity in private X-Nigson-Carrier-Account-SHA256 metadata; private API-119 carries the frozen identity. Coordinate both owner deployments with quote/checkout/creation paused; legacy snapshots cannot be silently backfilled or granted fresh execution. Same-account credential renewal retains identity; account/transport-mode rotation requires review and new legitimate quote contexts. No public DTO, secret sharing or provider selection change. Actual account/route/deployment/restore qualification remains NOT_RUN.

MSG-001 / ADR-017 phone consent: commerce and experience default `NIGSON_PHONE_CONSENT_ENABLED=false`. Commerce enabling requires security, approved `NIGSON_EXPERIENCE_BASE_URL` (fixed private target), the201–204 workload scopes, existing KMS actor signing/current JDBC sessions and CSRF. Experience enabling requires security, approved `NIGSON_PHONE_POLICY_VERSIONS`, positive `NIGSON_PHONE_HASH_KEY_VERSION`, explicit `NIGSON_PHONE_CIPHER_REVISION`, `NIGSON_PHONE_KMS_KEY_ARN`, `NIGSON_PHONE_WRAPPED_HASH_KEY` and `NIGSON_PHONE_WRAPPED_CIPHER_KEY`. Optional previous cipher revisions are a strict object in `NIGSON_PHONE_PREVIOUS_CIPHER_KEYS` (max2). KMS encryption context purpose is `nigson-experience-phone`, kind lookup-proof/cipher, revision hash-version/cipher-revision respectively. Local/test only may explicitly supply separate32-byte base64 `NIGSON_PHONE_LOCAL_HASH_KEY` and `NIGSON_PHONE_LOCAL_CIPHER_KEY`, never mixed with wrapped policy. Production has no dummy/default fallback. Keep lookup/proof HMAC identity stable until a separately reviewed migration; ciphertext keys can overlap decrypt revisions. Proof expiry scans at most100 rows every60s (explicit interval override); no provider request occurs in cleanup. No real sender/template/account/policy/retention or live qualification approval is inferred.

MSG-001 / ADR-018 intake: `NIGSON_MESSAGING_VERIFICATION_INTAKE_ENABLED=false` by default. Enabling requires private security, explicit account/service, actual transport mode, creation-generation UUID, locale and separately approved SMS/WhatsApp template revisions; see [notification operations](runbooks/notifications.md). Apply additive integration BP1 notification_dispatch migration before enabling intake. Do not backfill historical jobs or overwrite original snapshots on rotation. A durable PENDING job is not an implemented sender or delivery claim. Provider dispatch/status/inbound workflows and genuine activation remain pending.
