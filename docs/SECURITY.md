# Nigson Security

Specification1.1.0; synchronized view; master is authoritative.


# Master section15: Security Architecture

Use OIDC authorization code + PKCE S256, state and nonce through the BFF. Use the secure authorization-code/PKCE profile; exact supported Keycloak features/version are pinned and tested in IDP-001. Keycloak is the identity provider in local/stage/production, with separately managed environment realms and workload clients. Never store password hashes in business schemas.

Validate issuer exact match, signature algorithm allowlist RS256/ES256 appropriate to provider, JWKS kid, audience/client/resource scope per access-token format, expiration and not-before with <=60s skew. Keycloak access-token validation includes expected aud, authorized client azp/client_id as configured, required scopes and issuer realm; do not treat an ID token as an API access token. Cache JWKS public keys <=5m with bounded refresh and fail closed if no valid key is available. Reject unsigned/none algorithms, unexpected issuers and tokens supplied in query parameters. Refresh tokens stay encrypted server-side. Authorization remains business role/entitlement driven, not inferred from email domains.

Session cookies: Secure, HttpOnly, SameSite=Lax, Path=/, host-only; use `__Host-nigson-session` in HTTPS environments. 30m idle /12h absolute; local HTTP explicit insecure-cookie exception only under local profile. Rotate ID on login; invalidate all subject sessions on staff role changes. CSRF token bound to session and required for Cookie-auth POST/PUT/PATCH/DELETE; OIDC callback state validation is the alternative control. Logout deletes session and performs supported provider revocation. Never return access/refresh tokens to JavaScript. Maintain X-Content-Type-Options:nosniff, strict production HSTS and response cache classification.

| Role/scope | Permissions |
|---|---|
| CUSTOMER | Own profile/address/cart/order/review/referral; active organization membership grants that organization's buying scope only. |
| CONTENT_EDITOR | Draft catalogue/CMS, taxonomy and media; no price/reward/refund authority. |
| CONTENT_PUBLISHER | Approve/publish/withdraw content and moderate reviews. |
| SALES | Assigned leads, distributor decisions, quote creation/offer within thresholds, commercial draft rules; careers permissions separately assigned. |
| OPERATIONS | Stock and fulfillment commands, job inspection; no arbitrary refunds or organization role grants. |
| FINANCE | Price/rule activation, settlement reconciliation and refund approval under two-actor policy. |
| ANALYST | Sanitized owned reporting, no confidential applications or token material. |
| ADMIN | Staff permissions and typed policy configuration, audited recent-MFA actions. ADMIN does not skip ownership or finance approval invariants. |
| SERVICE_EXPERIENCE | Only entitlement command/purchase proof permissions explicitly registered for its OAuth client. |
| SERVICE_INTEGRATION | Only scoped notification/context/scan/logistics/reconciliation APIs; no broad customer list or generic export. |
| SERVICE_AI | Approved public knowledge plus bounded live tools; private order tool requires validated actor delegation. |
| SERVICE_COMMERCE | Lead quote context/association, private staff facade to worker/AI; actor delegation preserved. |

ABAC checks supplement roles: party ownership, active organization membership, current entitlement, lead assignment, market eligibility, file purpose and classification, approved source revision and recent staff MFA. Never trust requested organizationId/partyId or model tool arguments as proof. A retail customer may buy within several organizations; selecting another organization does not transfer account ownership.

Private transport: network allowlist plus workload client-credentials JWT with resource-specific scope. For acting-user calls the BFF issues an additional ES256 signed `X-Nigson-Actor` compact JWT using a dedicated KMS key. Claims: iss=nigson-commerce-bff, aud=exact target service, sub=party UUID, identity_issuer, identity_subject, organization_id optional, roles/scopes from current verified policy, operation, iat, exp<=60s, jti and correlation_id. Target requires both valid permitted workload identity and valid matching actor assertion. Signer key/JWKS allowlist is pinned infrastructure config; model/user cannot provide assertion content. Restrict operation to compiled route, method and resource context; private tools return 403/404 on mismatch. Replay of a read is harmless; mutations still require domain idempotency and resource ownership. Do not forward assertions or browser tokens to arbitrary external providers/MCP servers.

Prod secret management: Secrets Manager references and KMS encryption; ECS task roles or service OAuth credentials, no static cloud key. Require startup config validation. Secrets never pass into events, logs or image build arguments. Rotate IdP clients, signing keys and provider secrets with overlap strategy. Files are quarantine-scanned; private downloads expire in 60s. Rate limiting uses WAF and application policy; security-critical distributed limits such as wheel budget and coupon uses are database-authoritative, never process-local only. A Redis/Valkey cache is not permission storage.

Data export/erasure is a controlled staff workflow: anonymize personal fields where policy permits, preserve required financial records and append audit evidence; backups follow approved retention. No declaration of GDPR or other legal compliance is made without confirmed jurisdiction/control review.


## Exact special permission interpretation

Session roles are trusted commerce assignments, not request flags. OPERATIONS_OR_FINANCE means either named role with refund/operation-specific restrictions. SALES_WITH_CAREERS_READ means SALES plus explicit careers.read permission flag in the typed role policy; absent flag denies applicant PII. PUBLIC_OR_CUSTOMER permits anonymous intake with anti-abuse; CUSTOMER context is validated when supplied. PUBLIC_TOKEN is the specific one-use newsletter capability. CUSTOMER_OR_UPLOAD_TOKEN permits authenticated purpose-authorized uploads or the short-lived CAREER_CV-only capability; a token can never download others' assets. CONVERSATION_OWNER means authenticated matching party or server-bound expiring anonymous token matching conversation. SESSION requires verified cookie; OIDC_CALLBACK validates one-use state. PUBLIC still enforces rate limits/object public state.

Private permissions below map exactly to registered OAuth client scopes. `_WITH_SIGNED_ACTOR` requires ES256 audience/operation assertion and owner policy; `_WITH_SIGNED_ADMIN` additionally requires ADMIN/recent MFA from commerce. ADMIN cannot skip finance/two-actor controls. `_WITH_SIGNED_STAFF` for jobs retry requires the authorized OPERATIONS actor and recent MFA, matching the public facade; scope alone never confers staff authority. Ensure compiled proxy target operations equal signed assertion operation. Email normalized hashes use HMAC-SHA256 with environment-specific KMS-managed hashing key; encryption key is separate. Normalize trimmed NFC and lowercase domain/address for dedup policy, and record key-version migration instead of silently rotating hashes.

| Private permission | Required workload client and exact operation |
|---|---|
| SERVICE_AI:knowledge.read | ai client; knowledge.read |
| SERVICE_AI:knowledge.validate | ai client; knowledge.validate |
| SERVICE_AI:order-status.read_WITH_SIGNED_ACTOR | ai client; order-status.read_WITH_SIGNED_ACTOR |
| SERVICE_COMMERCE:ai.manage_WITH_SIGNED_ADMIN | commerce client; ai.manage_WITH_SIGNED_ADMIN |
| SERVICE_COMMERCE:jobs.read | commerce client; jobs.read |
| SERVICE_COMMERCE:jobs.retry_WITH_SIGNED_STAFF | commerce client; jobs.retry_WITH_SIGNED_STAFF |
| SERVICE_COMMERCE:quote.read | commerce client; quote.read |
| SERVICE_COMMERCE:quote.write | commerce client; quote.write |
| SERVICE_COMMERCE:templates.write_WITH_SIGNED_ADMIN | commerce client; templates.write_WITH_SIGNED_ADMIN |
| SERVICE_EXPERIENCE:entitlement.write | experience client; entitlement.write |
| SERVICE_EXPERIENCE:purchase-proof.read | experience client; purchase-proof.read |
| SERVICE_INTEGRATION:consent.read | integration client; consent.read |
| SERVICE_INTEGRATION:consent.write | integration client; consent.write |
| SERVICE_INTEGRATION:files.scan.write | integration client; files.scan.write |
| SERVICE_INTEGRATION:logistics.read | integration client; logistics.read |
| SERVICE_INTEGRATION:logistics.write | integration client; logistics.write |
| SERVICE_INTEGRATION:notification.read | integration client; notification.read |
| SERVICE_INTEGRATION:notification.write | integration client; notification.write |
| SERVICE_INTEGRATION:payment.reconcile | integration client; payment.reconcile |
| SERVICE_INTEGRATION:recovery.read | integration client; recovery.read |
| SERVICE_INTEGRATION:sheets.read | integration client; sheets.read |


## Production Keycloak boundary

No Cognito resources or Cognito-specific token_use check in current configuration. Keycloak discovery issuer is exact HTTPS realm URL; configure audience mapper per service and check authorized client plus required workload scopes. BFF is a confidential client with PKCE; no browser-held refresh token. Keep commerce-assigned roles/entitlements authoritative and invalidate sessions on role/entitlement revocation. Staff TOTP/WebAuthn MFA policy remains; messaging proof is separate from login.

Production identity is an additional platform deployable, not a fifth domain microservice. Use a dedicated logical PostgreSQL identity database with no grants to business runtime roles. Pin a Keycloak/database combination on the official supported matrix, enforce TLS/strict hostname and trusted reverse-proxy headers, isolate administration paths, bound queues/pools and test2-replica cluster/session behavior. Use start --optimized, never start-dev in prod. Pin image digest, bootstrap administrator through secrets/runbook then remove bootstrap credentials; realm config versioned without secrets. A tested database/realm/signing-key backup and restore is mandatory; BFF session restore and Keycloak restore are separate procedures. Official guide: https://www.keycloak.org/server/configuration-production.


# Master section26: File Storage

S3 buckets/access classes: quarantined-upload, public-media and private-document, encrypted/versioned. Each domain owns its metadata; integration scanner has read quarantine, write scan result and permitted promotion copy, not blanket public write. Key format `{env}/{owner}/{purpose}/{assetUuid}/{revision}/{safeExtension}`. No original filename in the key, no filesystem paths or user-selected bucket.

Defaults: product image <=5MiB JPEG/PNG/WebP, brochure <=10MiB PDF, distributor proof <=10MiB PDF/JPEG/PNG, CV <=5MiB PDF. Upload signed policy expires5m, private download60s; anonymous career upload capability30m. Validate content-length, declared MIME and sniffed content, checksum SHA256, image decode dimensions <=10000x10000, PDF <=100pages, file name display <=120 chars without control characters. Reject ZIP, HTML, SVG uploads and executable/polyglot files unless an explicit specialized safe pipeline is approved; internally generated trusted images are a separate path.

Workflow: authorized session/capability creates metadata UPLOADING and quarantine key; upload directly using constrained signed request; completion verifies S3 object metadata and checksum, transitions QUARANTINED and outboxes scan request. Private scanner job validates resource-specific policy, runs malware scan with network isolation and60s CPU/wall budget, submits signed owned result. Domain promotes CLEAN bytes to immutable final key through scoped storage port; public publish requires business publication as well as scan. Rejected bytes remain inaccessible, are audited and expire after configured7d; orphan incomplete uploads1d. No scan result from browser is accepted.

Anonymous CV: obtain upload_session token, create file metadata linked to that session, upload/scan, then claim clean asset exactly once in career submission with the same capability. Other application proof uploads require authenticated customer. File ownership check is not merely knowing an asset UUID. Staff file reads require purpose role and related-resource access. Do not write binaries to PostgreSQL or return cloud credentials. Derived images use immutable format/size keys; metadata lists safe variants. Lifecycle jobs honor approved retention/holds, and delete metadata/object jointly with recoverable job state rather than pretending an S3 deletion is a database transaction.


Generated SHIPPING_LABEL files are PRIVATE,<=2MiB PDF/PNG, linked to the owned shipment and never published/CDN-served or accepted through customer/staff browser uploads. Integration receives base64 bytes or strictly allowlisted provider-host label data, validates size/MIME/checksum, writes the private label bucket and reports metadata through scoped commerce provider-result API; arbitrary provider-returned URLs are not fetchable. Only OPERATIONS with shipment access receives a60s signed label download. Booking mode/label metadata are audited.


# Master section29: Audit Architecture

Every sensitive command commits append-only audit_record with actor subject/kind, action, resource type/id, timestamp, correlation_id, reason and allowlisted old/new values. Store an HMAC IP fingerprint where needed instead of unrestricted raw IP; exact IP retention requires approved policy. Actions include staff role grant/revoke, entitlement approval/suspension, price/promotion activation, stock adjustment, refund request/approval/verification, quote override, content publication/withdrawal, moderation, private file download, applicant export, feature activation, queue replay and AI policy activation.

Audit insertion is in the owner transaction for mutations; sensitive read audit can fail closed if policy requires and audit persistence fails. Runtime grants audit INSERT/SELECT only; no UPDATE/DELETE. Do not put complete encrypted token fields, webhook payloads, personal forms or raw model input in old/new JSON. Audit schema defines allowed field names and masking. Scheduled owner export to access-separated encrypted/versioned storage supports investigation; retention/deletion requires policy owner and documented legal holds. Staff query endpoints are section13 with max100 rows/page, protected filters and export audit. RDS admin remains a privileged infrastructure boundary; append-only app grants are not a claim of cryptographic tamper-proof storage.


# Master section45: Security Checklist

During implementation, verify with executable tests/evidence:

- [ ] All routes deny by default and apply object/organization permission, including internal and worker facades.
- [ ] OIDC token-use/issuer/audience/client/scope/signature/expiry checks; staff MFA and session rotation; no password business table.
- [ ] Browser CSRF, secure cookies, exact credentialed CORS and return URL allowlist.
- [ ] Validated schemas, enums, money/quantity scale, nested/body limits, pagination/sort allowlist.
- [ ] Parameterized SQL; no arbitrary URL fetch, provider redirect, user-generated SQL or model code execution.
- [ ] Signature on raw provider webhook, durable dedup and independent payment amount/currency/reference verification.
- [ ] Private files quarantine scanned, ownership/purpose check and expiring signed URLs; anonymous CV capability cannot read other files.
- [ ] Secrets Manager/KMS/task roles; no secret in Git/image/log/events/status report.
- [ ] Audit sensitive actions, redacted old/new fields, least privilege owner schema and immutable evidence grants.
- [ ] Model tools read-only and bounded, sources public/approved/current, order tool trusted owner, injection/SSRF attempts denied.
- [ ] Provider/queue retries preserve business idempotency and UNKNOWN outcome accounting.
- [ ] Dependency/image/IaC/secret scans, approved scoped exceptions and migration drift validation.
- [ ] Consent/suppression fail closed for marketing; applicant/private data excluded from shared analytics/AI.
- [ ] Backup encryption, isolated restore, retention/hold rules and operator runbooks documented.

This is the requested engineering checklist, not a legal certification or proof of runtime behavior. Check boxes only when the corresponding tests/control evidence exist.



- [ ] Live provider mode cannot accept dummy keys/mock endpoints or mock-paid/label/message identifiers.
- [ ] Twilio signatures/account/publicURL/complete parameters validated, channel destination verified and fresh consent/suppression checked.
- [ ] Carrier label is private; booking cannot dispatch stock; package/currency/mode binding and UNKNOWN reconciliation tested.
- [ ] Keycloak prod uses supported persistent DB/TLS/private admin/MFA/tested replicas and restore; no start-dev/default admin credentials.


## IDP-001 implemented identity boundary

The [Keycloak runbook](runbooks/keycloak.md) describes the optimized two-replica
private/admin/public deployment, dedicated identity database, explicitly supplied
secrets and retention, TOTP/admin retirement, backup/restore and version upgrade
procedure. `contracts/security/workload-scopes.json` derives exact client grants
from the authoritative master API contracts, including launch carrier/messaging
operations. Java and BP5 Python validators enforce Keycloak access-token trust
with bounded fail-closed JWKS caching. No Cognito-specific claim is authority.
Production identity does not enable business routes or replace commerce business
roles/BFF server sessions. SEC-001 and the remaining BP1 infrastructure/provider/
restore/security release gates remain required; real environment qualification
must use supplied actual inputs and must not silently use local fixtures.

## SEC-001 executable security foundation

Typed authenticated-encrypted JDBC identity/pending-login records, one-use state, fixation-safe rotation, 30m idle/12h absolute lifetime, guarded refresh/logout and current commerce authority are implemented in `services/commerce/.../security/`. Private Java owner filters enforce compiled workload client/scope/phase, socket ingress and body-bound ES256 actor proof. Production signing uses a dedicated P-256 KMS key; session AES256 key material is separately wrapped and unwrapped only at startup. Public session and authorization endpoint DTOs/commands remain their explicit COM tasks. See [session/private trust runbook](runbooks/session-trust.md) and `contracts/security/actor-assertion.json`.
