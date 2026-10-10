# Nigson Api Contract

Specification1.1.0; synchronized view; master is authoritative.


# Master section12: API Standards

Each service publishes independent OpenAPI 3.1 documents using compatible springdoc or FastAPI generation, then CI compares them to the versioned contract. Contract-first schemas in this document define request/response shape; generated docs cannot justify silently changing the contract.

Public resources `/api/v1/...`; private resources `/internal/v1/...`; session flow `/bff/...`. JSON camelCase, enums UPPER_SNAKE_CASE, event JSON snake_case. UUID path IDs must be valid nonnil UUIDs. Methods: GET read, POST resource/action, PUT full replacement or set member, PATCH allowlisted partial update, DELETE remove/deactivate. 201 includes Location; 202 includes durable resource/job ID and polling/status information; 204 has no body. No GET mutation except explicitly documented one-time OIDC callback/provisioning.

Page default 0, size 20, maximum 100. Response `{items:[],page:0,size:20,totalElements:0,totalPages:0}`. Sort allowlist per resource, default createdAt desc with id tie-break; reject unknown filters and sort properties. Query max length 200. Max report window 366d, export rows 10000 through background job where necessary. Prefer keyset pagination only for deep staff exports, with opaque bounded cursor; do not mix response modes silently.

Money strings match `^-?\d{1,15}(\.\d{1,4})?$` and allowed sign; response uses currency minor-unit scale. Quantities strings use max 3 decimal places and SKU scale, positive on order/cart inputs. Prices are server-authoritative. ISO codes validate against approved metadata. Instant inputs normalize UTC and reject nonsensical effective ranges. Validate object size, nested array length <=100 unless endpoint lower cap, forbidden unknown fields and rich-text sanitization.

Authenticated browser mutations require CSRF header; APIs validate workload or bearer tokens as specified. OrganizationId may select context but never confer authorization. If-Match carries aggregate version on mutable updates and returns 412 on mismatch. Create actions with financial/external effects require Idempotency-Key (UUID or printable ASCII <=100); cache identity key by authenticated subject + operation, SHA256 canonical request; concurrent duplicate returns 409 IN_PROGRESS with Retry-After or waits <=1s then replays completed result. Same key/different input always 409. Missing key returns 400. Keep response record >=24h; durable domain uniqueness prevents duplicate effects after that.

Request IDs: validated UUID X-Correlation-ID or generated server UUID; W3C traceparent independently validated. Responses include X-Correlation-ID and no secrets. Private responses Cache-Control:no-store. Public ETag carries catalogue/content revision; Vary fields must match locale/market; no wholesale or actor-specific cache. CORS accepts configured exact HTTPS origins only, credentials explicitly; local origins only in local profile. Public body <=1MiB, webhook <=256KiB, AI user message <=2000 chars. Internal bulk caps <=100, source validation <=20.


# Master section13: API Contract

Every endpoint below is identified by service authority plus method/path. Browser protected routes are dispatched by the BFF's **compiled allowlist** of service/route/method, never an arbitrary upstream URL supplied by a request. Identical private paths across owners use distinct private hostnames. ALB routes only approved public/session paths; `/internal/**` is blocked publicly. The worker private job/template APIs are accessed through staff-authorized commerce BFF routes registered in its OpenAPI facade; they are not internet endpoints.

Types: `UUID`, `Instant`, `ISO3166`, `ISO4217`, `BCP47`, `HTTPS`, `Slug` (lowercase `[a-z0-9]+(?:-[a-z0-9]+)*` max220), `Money`/`Quantity` as section12, `OpaqueToken` >=128bits entropy. `Page<T>` has the section12 shape. `...request` means the explicitly enumerated request fields plus id/version; it does not mean arbitrary JSON. `Partial` allows only specified fields and explicit null where nullable. Each named DTO first occurrence defines its fields; subsequent references reuse those exact definitions. Never serialize a JPA entity directly.

Common parameter contracts: each `{id}`, `{skuId}`, `{marketId}`, `{orderId}`, `{provider}` parameter is required; IDs are UUID, provider is configured allowlist name. `type` enum and `slug` are validated as defined. Common request headers: Accept application/json (or text/event-stream for AI), Content-Type application/json on JSON mutations, X-Correlation-ID optional UUID, traceparent optional W3C. Cookie-auth mutations X-CSRF-TOKEN required. Commands mentioned as idempotent require Idempotency-Key; PATCH/PUT and specified transitions require If-Match; direct workload APIs require Authorization:Bearer plus scoped actor assertion where indicated. Signature webhooks use raw bytes and provider signature instead. No request/query/body is allowed when it says none.

Common statuses per route: documented success; 400 validation or missing headers; 401 missing/invalid authentication; 403 known subject without role/scope; 404 unknown or actor-invisible object; 409 invalid state/idempotency/business conflict; 412 stale version; 422 rule/market/stock ineligibility; 429 rate limit; 502 upstream protocol failure; 503 temporarily unavailable critical dependency. For anonymous public routes, 401/403 apply only when optional provided credentials are invalid. Deny ownership as 404 to reduce enumeration. RFC9457 Problem response applies to errors. Provider signature 401, invalid raw event 400, authenticated unsupported event 200 durable ignored receipt. Durable accepted webhook returns 200 even when later independent verification is pending.

`AddressDTO` is the AddressInput field set plus id/version; immutable order address excludes mutable identifiers. `SeoDTO={title:string<=70,description:string<=180,canonicalUrl:HTTPS|null,robots:INDEX|NOINDEX,alternates:[{locale:BCP47,url:HTTPS}],structuredData:object}`. `QuoteLineDTO={id:UUID,skuId:UUID,quantity:Quantity,unitAmount:Money,discountAmount:Money,taxAmount:Money,lineTotal:Money}`. `AnswerDTO={status:ANSWERED|CLARIFY|ESCALATED,answer:string,sources:[{id:UUID,url:HTTPS,title:string,revision:int64}],products:[{id:UUID,reason:string}],handoffUrl:HTTPS|null,runId:UUID}`. `SourceDTO` is the source element in AnswerDTO. State enum definitions are section17 and the table CHECK constraints. ProductAdminDTO extends ProductDTO with status and version and includes draft assets under staff policy. All undeclared response properties are forbidden.


Modes: provider mocks use local-only configurable HTTP URLs and dummy credentials. Public provider callbacks are narrow commerce/experience routes; integration worker remains private. Form/XML callbacks are explicitly exempt from JSON-only wire defaults, never from provider authentication.

### API-001 GET /bff/login

- **Service / module / phase:** commerce / session / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** returnTo:string optional relative allowlisted path
- **Request:** `none`.
- **Response:** `302 Location: configured OIDC authorization endpoint`.
- **Statuses:** 302 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Generate state, nonce and PKCE verifier in encrypted server session; return path must be allowlisted. Implemented API-001–004: contracts/openapi/commerce.yaml and docs/runbooks/bff-session-endpoints.md. Pending-flow capacity is shared across replicas; no unknown/repeated query or body accepted.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, SESSION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-002 GET /bff/callback

- **Service / module / phase:** commerce / session / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OIDC_CALLBACK; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** code:string required; state:string required
- **Request:** `none`.
- **Response:** `302 Location: allowlisted return path`.
- **Statuses:** 302 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate one-use state/nonce, code and PKCE; rotate session; provision issuer/subject mapping from signed ID-token profile claims. Fixed Keycloak client suppresses additional authentication-response parameters to match the closed code/state contract. Consume before provider HTTP; uncertain exchange requires a new login.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, SESSION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-003 GET /bff/session

- **Service / module / phase:** commerce / session / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `SessionDTO{authenticated:boolean,partyId:UUID|null,displayName:string|null,roles:[Role],organizationMemberships:[{organizationId:UUID,role:OWNER|BUYER}],expiresAt:Instant|null,csrfToken:string|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Return no provider access or refresh tokens. Resolve current commerce authority/membership; near-expiry refresh uses a CAS before provider HTTP and preserves original login/MFA/absolute expiry. Anonymous/expired/revoked sessions return the exact unauthenticated DTO.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, SESSION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-004 POST /bff/logout

- **Service / module / phase:** commerce / session / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SESSION; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{}`.
- **Response:** `none`.
- **Statuses:** 204 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** CSRF required; exact {} body, no query. Audit and delete JDBC session before supported provider revocation HTTP. Always clear cookie after a valid logout request; provider failure is 503 after local deletion and audit/database failure rolls back before HTTP.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, SESSION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-005 GET /api/v1/me

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `ProfileDTO{id:UUID,displayName:string,email:string,emailVerified:boolean,phone:string|null,locale:string,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Return the mapped authenticated subject only. Implemented API-005–010: contracts/openapi/commerce.yaml and docs/runbooks/party-profile-address.md; Party version governs profile changes.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-006 PATCH /api/v1/me

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{displayName?:string[1..160],phone?:string|null,locale?:BCP47}`.
- **Response:** `ProfileDTO as API-005`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Email changes use identity provider workflow, not this endpoint. Exact nonempty allowlisted patch, quoted If-Match, CSRF and durable Idempotency-Key required; normalize display name/locale, preserve explicit nullable phone, and audit revisions without contact values.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-007 GET /api/v1/me/addresses

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[AddressDTO]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only addresses owned by the actor; organization context requires membership.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-008 POST /api/v1/me/addresses

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `AddressInput{label?:string,recipient:string,phone:string,line1:string,line2?:string,city:string,region?:string,postalCode?:string,countryCode:ISO3166,organizationId?:UUID,isDefault?:boolean}`.
- **Response:** `AddressDTO{id:UUID,...AddressInput,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate country-specific schema; serialize default-address switch.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-009 PATCH /api/v1/me/addresses/{id}

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Partial AddressInput; required If-Match`.
- **Response:** `AddressDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor owns address; cannot transfer owner.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-010 DELETE /api/v1/me/addresses/{id}

- **Service / module / phase:** commerce / party / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `none`.
- **Statuses:** 204 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Soft delete; protect historical order snapshots.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-011 GET /api/v1/staff/users

- **Service / module / phase:** commerce / authorization / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** q:string<=200; page,size,sort; sort allowlist `createdAt,asc|desc`, `displayName,asc|desc`, `email,asc|desc`; default `createdAt,desc` with id tie-break.
- **Request:** `none`.
- **Response:** `Page<StaffDTO{id:UUID,displayName:string,email:string,roles:[Role],version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Search mapped staff subjects only, including historical role assignments; no retail-only identities. `StaffDTO.version` uses the Party aggregate revision. Implemented OpenAPI: `contracts/openapi/commerce.yaml` (API-011/012 increment).
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, AUTHORIZATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-012 PUT /api/v1/staff/users/{id}/roles

- **Service / module / phase:** commerce / authorization / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{roles:[Role],reason:string[10..500]}`.
- **Response:** `StaffDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Require recent MFA; prevent removal of last active ADMIN; audit and invalidate sessions. Required quoted `If-Match` and `Idempotency-Key`; canonical key input includes target/revision/roles/reason. Actor authority is current commerce state, target must be active mapped staff. Replace roles atomically; role changes increment Party revision and revoke all target sessions. Empty roles removes staff privileges; a no-op preserves revision/sessions. Matching replay returns the original result, including ETag; conflicting key input returns409 and stale revision412. Free-text reason is validated and redacted from audit under the existing policy; audit retains typed operator reason and individual role transitions.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, AUTHORIZATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

Implemented API-013–025/149 catalogue behavior, current-role policy, publication/outbox boundaries and public cache/search rules: `contracts/openapi/commerce.yaml`, `docs/runbooks/catalogue.md`. No wholesale prices or shipping labels in catalogue DTOs; approved metadata/CDN/market inputs remain activation gates.

### API-013 GET /api/v1/catalogue/products

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** market:code required; q:string<=200; categoryId,brandId:UUID optional; productType:string optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ProductSummaryDTO{id:UUID,name:string,slug:string,brandName:string|null,imageUrl:HTTPS|null,available:boolean,revision:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only ACTIVE products in enabled market with explicit eligibility; no wholesale prices.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-014 GET /api/v1/catalogue/products/{id}

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** market:code required
- **Request:** `none`.
- **Response:** `ProductDTO{id:UUID,name:string,slug:string,description:string,specifications:object,brand:{id:UUID,name:string}|null,categories:[{id:UUID,name:string}],skus:[{id:UUID,code:string,variantName:string,unit:string,packSize:Quantity,available:boolean}],assets:[{id:UUID,purpose:string,url:HTTPS}],revision:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reject ineligible or archived products with 404.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-015 GET /api/v1/catalogue/categories

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** market:code required
- **Request:** `none`.
- **Response:** `[{id:UUID,parentId:UUID|null,name:string,slug:string,sortOrder:int}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only active categories with eligible products.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-016 GET /api/v1/catalogue/brands

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[{id:UUID,name:string,slug:string}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only active public brands.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-017 POST /api/v1/staff/products

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{name:string[1..200],slug:Slug,description:string<=20000,brandId?:UUID,divisionCode?:string,specifications:object,categoryIds:[UUID]}`.
- **Response:** `ProductDTO{id:UUID,name:string,slug:string,description:string,specifications:object,brand:{id:UUID,name:string}|null,categories:[{id:UUID,name:string}],skus:[{id:UUID,code:string,variantName:string,unit:string,packSize:Quantity,available:boolean}],assets:[{id:UUID,purpose:string,url:HTTPS}],revision:int64} plus status,version`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate slug and schema; create DRAFT; link only owned valid taxonomy.
- **Events:** catalogue.product.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-018 PATCH /api/v1/staff/products/{id}

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Partial API-017 request; If-Match`.
- **Response:** `ProductDTO{id:UUID,name:string,slug:string,description:string,specifications:object,brand:{id:UUID,name:string}|null,categories:[{id:UUID,name:string}],skus:[{id:UUID,code:string,variantName:string,unit:string,packSize:Quantity,available:boolean}],assets:[{id:UUID,purpose:string,url:HTTPS}],revision:int64} plus status,version`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Version increment; never silently change SKU codes; emit revision change.
- **Events:** catalogue.product.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-019 POST /api/v1/staff/products/{id}/publication

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{action:PUBLISH|ARCHIVE,reason:string}`.
- **Response:** `ProductDTO{id:UUID,name:string,slug:string,description:string,specifications:object,brand:{id:UUID,name:string}|null,categories:[{id:UUID,name:string}],skus:[{id:UUID,code:string,variantName:string,unit:string,packSize:Quantity,available:boolean}],assets:[{id:UUID,purpose:string,url:HTTPS}],revision:int64} plus status,version`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** PUBLISH requires SKU, eligible market, clean image; ARCHIVE removes public visibility immediately.
- **Events:** catalogue.product.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-020 POST /api/v1/staff/products/{id}/skus

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string[1..80],variantName:string,unit:configuredUnit,quantityScale:0|1|2|3,packSize:Quantity,weightKg?:Quantity}; required FedEx carrierPackaging:CarrierPackaging (staff-managed)`.
- **Response:** `{id:UUID,code:string,variantName:string,unit:string,quantityScale:int,packSize:Quantity,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** SKU code unique; used unit/scale cannot change after transactions.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-021 PATCH /api/v1/staff/skus/{id}

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{variantName?:string,active?:boolean,weightKg?:Quantity}; If-Match; required FedEx carrierPackaging:CarrierPackaging`.
- **Response:** `SkuDTO as API-020`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Immutable code and unit; deactivation prohibits new sales.
- **Events:** catalogue.product.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-022 POST /api/v1/staff/brands

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{name:string,slug:Slug,active?:boolean}`.
- **Response:** `TaxonomyDTO{id:UUID,name:string,slug:string,parentId:UUID|null,active:boolean,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reject slug collisions; category cycles and depth>5 forbidden.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-023 PATCH /api/v1/staff/brands/{id}

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Partial {name:string,slug:Slug,active?:boolean}; If-Match`.
- **Response:** `TaxonomyDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Deactivate rather than delete referenced taxonomy; validate cycles.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-024 POST /api/v1/staff/categories

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{name:string,slug:Slug,parentId?:UUID,sortOrder?:int,active?:boolean}`.
- **Response:** `TaxonomyDTO{id:UUID,name:string,slug:string,parentId:UUID|null,active:boolean,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reject slug collisions; category cycles and depth>5 forbidden.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-025 PATCH /api/v1/staff/categories/{id}

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Partial {name:string,slug:Slug,parentId?:UUID,sortOrder?:int,active?:boolean}; If-Match`.
- **Response:** `TaxonomyDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Deactivate rather than delete referenced taxonomy; validate cycles.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-026 GET /api/v1/catalogue/skus/{id}/price

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** PUBLIC_OR_CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** market:code required; quantity:Quantity required; organizationId:UUID optional
- **Request:** `none`.
- **Response:** `PriceDTO{skuId:UUID,unitAmount:Money,currency:ISO4217,minimumQuantity:Quantity,packMultiple:Quantity,priceBookRevision:int64,validUntil:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Unauthenticated returns approved retail only; organization context is authenticated and checked.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-027 POST /api/v1/staff/price-books

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,currency:ISO4217,segment:RETAIL|DISTRIBUTOR,effectiveFrom:Instant,effectiveUntil?:Instant,entries:[{skuId:UUID,minimumQuantity:Quantity,unitAmount:Money}]}`.
- **Response:** `PriceBookDTO{id:UUID,code:string,revision:int64,currency:ISO4217,segment:RETAIL|DISTRIBUTOR,effectiveFrom:Instant,effectiveUntil:Instant|null,active:boolean,entries:[{skuId:UUID,minimumQuantity:Quantity,unitAmount:Money}],version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Draft revision; full validation; prevent overlapping active default retail assignments.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-028 POST /api/v1/staff/price-books/{id}/activation

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `PriceBookDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Require valid market currency and nonambiguous tiers; activated revision immutable.
- **Events:** pricing.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-029 GET /api/v1/markets

- **Service / module / phase:** commerce / market / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[{id:UUID,code:string,currency:string,destinations:[ISO3166],policyRevision:int64}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only enabled markets and destinations; no draft international policy.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, MARKET_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-030 PUT /api/v1/staff/markets/{id}

- **Service / module / phase:** commerce / market / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{name:string,currency:string,enabled:boolean,destinations:[{countryCode:string,addressRules:object,deliveryEnabled:boolean}],taxPolicy:object,returnsPolicyRevision:string}; If-Match`.
- **Response:** `MarketDTO{id:UUID,...request,policyRevision:int64,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Enabling requires business approval evidence, supported provider currency, price book and delivery/tax rules; market currency immutable after orders.
- **Events:** market.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, MARKET_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-031 PUT /api/v1/staff/products/{id}/markets/{marketId}

- **Service / module / phase:** commerce / market / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id, marketId; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{eligible:boolean,reason:string}`.
- **Response:** `{productId:UUID,marketId:UUID,eligible:boolean,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Explicit product restrictions; emit public revision and invalidate discovery.
- **Events:** catalogue.product.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, MARKET_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-032 POST /api/v1/carts

- **Service / module / phase:** commerce / cart / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID,organizationId?:UUID}`.
- **Response:** `CartDTO{id:UUID,marketId:UUID,organizationId:UUID|null,state:CartState,lines:[{id:UUID,skuId:UUID,quantity:Quantity}],version:int64,lastActivityAt:Instant}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Create or return unique active context cart; validate organization eligibility.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CART_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-033 GET /api/v1/carts/{id}

- **Service / module / phase:** commerce / cart / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `CartDTO{id:UUID,marketId:UUID,organizationId:UUID|null,state:CartState,lines:[{id:UUID,skuId:UUID,quantity:Quantity}],version:int64,lastActivityAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor owns cart; no shared caches.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CART_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-034 PUT /api/v1/carts/{id}/lines/{skuId}

- **Service / module / phase:** commerce / cart / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id, skuId; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{quantity:Quantity}; If-Match`.
- **Response:** `CartDTO{id:UUID,marketId:UUID,organizationId:UUID|null,state:CartState,lines:[{id:UUID,skuId:UUID,quantity:Quantity}],version:int64,lastActivityAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Set positive quantity; zero is rejected; use DELETE to remove; update last activity.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CART_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-035 DELETE /api/v1/carts/{id}/lines/{skuId}

- **Service / module / phase:** commerce / cart / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id, skuId; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none; If-Match`.
- **Response:** `none`.
- **Statuses:** 204 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Remove owned active cart line and increment cart version.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CART_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-036 POST /api/v1/checkout/quotes

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{cartId:UUID,addressId:UUID,carrierQuoteId:UUID,couponCode?:string}`.
- **Response:** `{id:UUID,cartVersion:int64,currency:string,lines:[{skuId:UUID,quantity:Quantity,unitAmount:Money,lineTotal:Money}],totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money},quoteHash:string,expiresAt:Instant}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** No stock reservation; calculate current rules and immutable server quote. Carrier quote unexpired, same actor/cart version/address/package/service, NGN and matching provider mode; freeze its fee with commercial quote. Rate lookup happens before checkout transaction, never inside T1.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-037 POST /api/v1/checkout/attempts

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{checkoutQuoteId:UUID,quoteHash:string,paymentMethod:HOSTED}`.
- **Response:** `{order:OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant},payment:{attemptId:UUID,state:PaymentAttemptState,checkoutUrl:HTTPS|null,reference:string}}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency-Key required; atomic stock/coupon reserve; persist attempt then initiate externally. Uncertain initiation returns 202 with existing order reference. Revalidate bound carrier quote but make no carrier HTTP call under stock/order locks; paid-order fulfillment books later.
- **Events:** order.pending.v1, inventory.availability.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-038 GET /api/v1/orders

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** organizationId:UUID optional; status:OrderState optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Own retail or active own-organization orders.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-039 GET /api/v1/orders/{id}

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor ownership or authorized organization membership; otherwise 404.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-040 POST /api/v1/orders/{id}/cancellation

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `OrderDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only unpaid PENDING or failed orders can self-cancel; query uncertain payment first; paid cancellation uses staff refund workflow.
- **Events:** order.cancelled.v1, inventory.availability.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-041 GET /api/v1/orders/{id}/payment

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{orderId:UUID,state:PaymentAttemptState,settled:boolean,reference:string,checkoutUrl:HTTPS|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned order; do not call provider on every poll; local verified state only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-042 POST /api/v1/webhooks/payments/paystack

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Provider signature, raw/form payload validator and durable dedup; no browser CSRF requirement.
- **Authorization:** PAYSTACK_SIGNATURE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Raw provider event JSON; max 256KiB`.
- **Response:** `{received:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** HMAC SHA512 over raw bytes; dedup stable event; verify transaction server-side; acknowledge durable receipt, not an unverified paid state.
- **Events:** order.paid.v1, inventory.availability.changed.v1, refund.succeeded.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-043 GET /api/v1/staff/orders

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS_OR_FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** status,paymentStatus:enum optional; from,to:Instant optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Protected staff filter; access audit for exports.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-044 POST /api/v1/staff/orders/{id}/processing

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}; If-Match`.
- **Response:** `OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only Paid, unheld orders; do not alter financial state.
- **Events:** order.processing.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-045 GET /api/v1/staff/inventory

- **Service / module / phase:** commerce / inventory / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** skuId,locationId:UUID optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<StockDTO{skuId:UUID,locationId:UUID,onHand:Quantity,reserved:Quantity,allocated:Quantity,available:Quantity,version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned stock query; no cross-schema join.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, INVENTORY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-046 POST /api/v1/staff/stock-adjustments

- **Service / module / phase:** commerce / inventory / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{skuId:UUID,locationId:UUID,delta:Quantity,reason:string[10..500]}`.
- **Response:** `StockDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency required; lock balance; reject reduction below reserved+allocated; append movement and audit.
- **Events:** inventory.availability.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, INVENTORY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-047 PUT /api/v1/staff/delivery-rules/{id}

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID,countryCode:string,regionCode?:string,postalPrefix?:string,fee:Money,freeAbove?:Money,priority:int,active:boolean}; If-Match`.
- **Response:** `DeliveryRuleDTO{id:UUID,...request,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate overlapping priorities and approved market coverage.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-048 POST /api/v1/staff/orders/{id}/shipments

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{lines:[{orderLineId:UUID,balanceId:UUID,quantity:Quantity}],provider?:string,providerReference?:string,trackingUrl?:HTTPS}`.
- **Response:** `ShipmentDTO{id:UUID,orderId:UUID,status:ShipmentState,lines:[{orderLineId:UUID,quantity:Quantity}],trackingUrl:HTTPS|null,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Default MANUAL; ensure allocated nonrefunded lines; tracking domains allowlisted. Create durable provider booking request and package snapshot; label is private, no shipment dispatch until BOOKED.
- **Events:** shipment.changed.v1, shipment.booking.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-049 POST /api/v1/staff/shipments/{id}/dispatch

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}; If-Match`.
- **Response:** `ShipmentDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Under order lock, decrease on_hand and allocated together exactly once; record movement.
- **Events:** shipment.changed.v1, inventory.availability.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-050 POST /api/v1/staff/shipments/{id}/delivery

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{deliveredAt:Instant,reason:string}; If-Match`.
- **Response:** `ShipmentDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only SHIPPED shipment; order DELIVERED after all noncancelled lines delivered.
- **Events:** shipment.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-051 GET /api/v1/orders/{id}/tracking

- **Service / module / phase:** commerce / fulfillment / BP4.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{orderId:UUID,shipments:[ShipmentDTO],events:[{status:string,occurredAt:Instant,note:string|null}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only own order; stale logistics updates never reverse status.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-052 POST /api/v1/staff/orders/{id}/refunds

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{amount:Money,reason:string[10..500],lines?:[{orderLineId:UUID,quantity:Quantity,amount:Money}]}`.
- **Response:** `RefundDTO{id:UUID,state:RefundState,amount:Money,currency:string,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency required; lock order; reserve refundable value; request alone does not restock.
- **Events:** refund.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-053 POST /api/v1/staff/refunds/{id}/approval

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{approve:boolean,reason:string}; If-Match`.
- **Response:** `RefundDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Require recent MFA and different requester for enabled two-person policy; uncertainty prevents second refund.
- **Events:** refund.approved.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-054 GET /api/v1/staff/refunds/{id}

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `RefundDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Include verified provider reference and state, not credentials.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-055 POST /api/v1/staff/promotion-rules

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,kind:PromotionKind,marketId:UUID,startsAt:Instant,endsAt:Instant,parameters:object,eligibility:object,stackingPolicy:EXCLUSIVE|STACKABLE,enabled:boolean}`.
- **Response:** `PromotionRuleDTO{id:UUID,...request,revision:int64,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate typed rule schema, dates, market and safe limits; benefits remain disabled unless approved.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-056 GET /api/v1/staff/promotion-rules

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** page,size,sort
- **Request:** `none`.
- **Response:** `Page<PromotionRuleDTO{id:UUID,...request,revision:int64,version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Staff-only configuration query.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-057 POST /api/v1/staff/coupons

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{ruleId:UUID,code:string,partyId?:UUID,maximumUses:int,maximumUsesPerParty:int,expiresAt:Instant}`.
- **Response:** `CouponDTO{id:UUID,code:string,active:boolean,remainingUses:int,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate typed rule schema, dates, market and safe limits; benefits remain disabled unless approved.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-058 GET /api/v1/staff/coupons

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** page,size,sort
- **Request:** `none`.
- **Response:** `Page<CouponDTO{id:UUID,code:string,active:boolean,remainingUses:int,version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Staff-only configuration query.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-059 POST /api/v1/staff/wheel-campaigns

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,ruleId:UUID,marketId:UUID,dailyLimit:int,rewardBudget:Money,outcomes:[{label:string,kind:WheelOutcomeKind,weight:int,benefit:object}],enabled:boolean}`.
- **Response:** `WheelCampaignDTO{id:UUID,...request,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate typed rule schema, dates, market and safe limits; benefits remain disabled unless approved.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-060 GET /api/v1/staff/wheel-campaigns

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** page,size,sort
- **Request:** `none`.
- **Response:** `Page<WheelCampaignDTO{id:UUID,...request,version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Staff-only configuration query.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-061 POST /api/v1/wheel-campaigns/{id}/draws

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID}`.
- **Response:** `{drawId:UUID,outcome:{label:string,kind:WheelOutcomeKind},couponCode:string|null,expiresAt:Instant|null}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency required; secure weighted draw, campaign budget and per-party window checked atomically; result immutable.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-062 GET /api/v1/me/rewards

- **Service / module / phase:** commerce / reward / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{availableByCurrency:[{currency:string,amount:Money}],entries:[{id:UUID,kind:RewardKind,amount:Money,currency:string,createdAt:Instant}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only actor ledger; signed sum and expiry logic.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REWARD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-063 GET /api/v1/me/referral-code

- **Service / module / phase:** commerce / reward / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{code:string,link:HTTPS,qualifiedReferralCount:int}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Read an existing stable referral code; return 404 if not created. Creation uses the separate POST command; no referred-user personal data.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REWARD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-064 POST /api/v1/me/referral-attribution

- **Service / module / phase:** commerce / reward / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string}`.
- **Response:** `{attributionId:UUID,state:ReferralState}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reject self referral, existing attribution and qualifying order already settled.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REWARD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-065 POST /api/v1/staff/organizations/{id}/dealer-rewards

- **Service / module / phase:** commerce / reward / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{ruleId:UUID,amount:Money,reason:string}`.
- **Response:** `{entryId:UUID,couponId:UUID|null}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency, verified purchase threshold, current entitlement and recorded staff issuance.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REWARD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-066 GET /api/v1/me/organizations

- **Service / module / phase:** commerce / party / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[{id:UUID,name:string,membershipRole:OWNER|BUYER,entitlementState:EntitlementState}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor memberships only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PARTY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-067 PUT /api/v1/staff/organizations/{id}/price-assignment

- **Service / module / phase:** commerce / pricing / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID,priceBookId:UUID,minimumRules:[{skuId?:UUID,minimumQuantity?:Quantity,packMultiple?:Quantity,minimumOrderAmount?:Money}]}`.
- **Response:** `{organizationId:UUID,priceBookId:UUID,revision:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Currency and segment checks; no automatic activation of suspended distributor.
- **Events:** pricing.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-068 POST /api/v1/staff/quotes

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{partyId:UUID,organizationId?:UUID,marketId:UUID,sourceLeadId:UUID,validUntil:Instant,terms:string,lines:[{skuId:UUID,quantity:Quantity,agreedUnitAmount:Money,discountAmount:Money,taxAmount:Money}]}`.
- **Response:** `QuoteDTO{id:UUID,state:QuoteState,revisionId:UUID,revision:int,total:Money,currency:string,validUntil:Instant,lines:[QuoteLineDTO],version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Command verifies source lead with experience; create draft revision; totals server computed.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-069 POST /api/v1/staff/quotes/{id}/revisions

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{validUntil:Instant,terms:string,lines:[{skuId:UUID,quantity:Quantity,agreedUnitAmount:Money,discountAmount:Money,taxAmount:Money}]}`.
- **Response:** `QuoteDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Cannot mutate offered revision; create next immutable version.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-070 POST /api/v1/staff/quotes/{id}/offer

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{revisionId:UUID,reason:string}`.
- **Response:** `QuoteDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only authorized approved price terms; recent approval threshold policy; freeze revision.
- **Events:** quote.offered.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-071 GET /api/v1/quotes/{id}

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `QuoteDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor or purchasing organization owns quote.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-072 POST /api/v1/quotes/{id}/accept

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{revisionId:UUID,addressId:UUID,carrierQuoteId:UUID}`.
- **Response:** `CheckoutAttemptDTO as API-037`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency, ownership, active latest offered revision, expiry, market and stock revalidation; reserve accepted terms. Carrier quote must match the offered source revision/cart/address/packages/mode/NGN; quote-specific rate endpoint creates owned temporary cart from frozen offered lines; checkout preserves accepted negotiated pricing.
- **Events:** quote.accepted.v1, order.pending.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-073 GET /api/v1/catalogue/products/{id}/recommendations

- **Service / module / phase:** commerce / catalogue / BP5.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** market:code required; limit:int 1..12 default 6
- **Request:** `none`.
- **Response:** `[{productId:UUID,name:string,reason:RELATED|FEATURED|POPULAR}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Curated and verified popular eligible products only; no behavioral profiling.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-074 PUT /api/v1/staff/products/{id}/recommendations

- **Service / module / phase:** commerce / catalogue / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{items:[{relatedProductId:UUID,kind:RELATED|FEATURED,priority:int}]}`.
- **Response:** `{productId:UUID,items:[RecommendationDTO],version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reject self references and inactive products; replace set atomically.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-075 GET /api/v1/staff/reports/commerce

- **Service / module / phase:** commerce / reporting / BP4.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ANALYST; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** from,to:Instant required max 366d; currency:ISO4217 required; organizationId:UUID optional
- **Request:** `none`.
- **Response:** `{period:{from:Instant,to:Instant},currency:string,sales:Money,orders:int,customers:int,products:[{skuId:UUID,quantity:Quantity,revenue:Money}],stock:[StockDTO]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Never aggregate monetary values across currencies without explicit separate groups.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REPORTING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-076 GET /api/v1/content/{type}/{slug}

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** type, slug; section13 common UUID/identifier types.
- **Query:** locale:BCP47 default en; market:code optional
- **Request:** `none`.
- **Response:** `ContentDTO{id:UUID,type:ContentType,slug:string,locale:string,revision:int,title:string,body:object,metadata:SeoDTO,publishedAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only approved current published content; no previews through public caching.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-077 GET /api/v1/content

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** type:ContentType required; locale; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ContentDTO>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** List published content by type only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-078 POST /api/v1/staff/content

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{type:ContentType,slug:Slug,locale:BCP47,title:string,body:object,metadata:SeoDTO}`.
- **Response:** `ContentRevisionDTO{id:UUID,contentItemId:UUID,revision:int,state:ContentState,...request,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate type-specific JSON schema, safe rich text and allowed video host.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-079 POST /api/v1/staff/content/{id}/revisions

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{title:string,body:object,metadata:SeoDTO}`.
- **Response:** `ContentRevisionDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Create revision rather than overwrite publication.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-080 POST /api/v1/staff/content-revisions/{id}/review

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `ContentRevisionDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** DRAFT to REVIEW only; actor attribution audited.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-081 POST /api/v1/staff/content-revisions/{id}/approval

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{approve:boolean,reason:string}`.
- **Response:** `ContentRevisionDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** REVIEW to APPROVED or DRAFT; no public visibility until publication.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-082 POST /api/v1/staff/content-revisions/{id}/publication

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{action:PUBLISH|WITHDRAW,scheduleAt?:Instant,reason:string}`.
- **Response:** `ContentRevisionDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** PUBLISH approved immutable revision and switch pointer; withdrawal effective immediately.
- **Events:** content.published.v1, content.withdrawn.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-083 GET /api/v1/staff/content-revisions/{id}/preview

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `ContentRevisionDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Private no-store preview; staff authorization required.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-084 GET /api/v1/seo/sitemap

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{entries:[{url:HTTPS,lastModified:Instant,locale:BCP47,alternateUrls:[HTTPS]}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Approved public content and commerce catalogue sitemap data composed by public APIs; no private URLs.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-085 GET /api/v1/channels

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{whatsapp:[{label:string,phone:string,url:HTTPS}],social:[{label:string,url:HTTPS}],maps:[{label:string,latitude:decimal,longitude:decimal}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Exclude lead recipient group data and provider secrets.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-086 PUT /api/v1/staff/channel-configurations/{id}

- **Service / module / phase:** experience / content / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{kind:ChannelKind,key:string,publicValue:object,active:boolean}; If-Match`.
- **Response:** `{id:UUID,...request,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate HTTPS social/video host allowlist, E.164 WhatsApp number and map coordinates.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-087 POST /api/v1/enquiries

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{type:CONTACT|COMPLAINT,name:string,email:Email,phone?:string,subject:string,message:string[1..10000],antiSpamToken:string}`.
- **Response:** `{id:UUID,state:NEW,receivedAt:Instant}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Rate limit and anti-spam; store primary record before notification; no unapproved marketing subscription.
- **Events:** lead.created.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-088 POST /api/v1/quote-requests

- **Service / module / phase:** experience / lead / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID,organizationId?:UUID,products:[{skuId:UUID,quantity:Quantity}],subject:string,message:string,contact:{name:string,email:Email,phone?:string}}`.
- **Response:** `{leadId:UUID,state:NEW}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate catalogue IDs through commerce; do not store provisional negotiated prices.
- **Events:** lead.created.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-089 GET /api/v1/staff/leads

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** type,state,priority:enum optional; assignedTo:UUID optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<LeadDTO{id:UUID,type:LeadType,subject:string,name:string,email:string,phone:string|null,message:string,state:LeadState,priority:NORMAL|HIGH,assignedTo:UUID|null,followUpAt:Instant|null,version:int64,createdAt:Instant}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Assigned scope unless sales supervisor/admin policy grants all.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-090 GET /api/v1/staff/leads/{id}

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `LeadDTO{id:UUID,type:LeadType,subject:string,name:string,email:string,phone:string|null,message:string,state:LeadState,priority:NORMAL|HIGH,assignedTo:UUID|null,followUpAt:Instant|null,version:int64,createdAt:Instant} plus activities:[{kind:string,note:string,createdAt:Instant}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Assignment or supervisor access; exclude private applicant files unless separately authorized.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-091 PATCH /api/v1/staff/leads/{id}

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{state?:LeadState,assignedTo?:UUID,priority?:NORMAL|HIGH,followUpAt?:Instant,note:string}; If-Match`.
- **Response:** `LeadDTO{id:UUID,type:LeadType,subject:string,name:string,email:string,phone:string|null,message:string,state:LeadState,priority:NORMAL|HIGH,assignedTo:UUID|null,followUpAt:Instant|null,version:int64,createdAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Allowed transition; assignment targets validated staff role; append activity and audit.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-092 POST /api/v1/distributor-applications

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{organizationName:string,countryCode:ISO3166,businessReference?:string,documentIds:[UUID]}`.
- **Response:** `ApplicationDTO{id:UUID,state:ApplicationState,organizationId:UUID|null,submittedAt:Instant,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Applicant owns CLEAN private files; required document types come from approved configuration.
- **Events:** distributor.application.created.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-093 GET /api/v1/me/distributor-applications

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[ApplicationDTO]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Actor submissions only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-094 GET /api/v1/staff/distributor-applications

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** state:ApplicationState optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ApplicationDTO>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Review staff only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-095 POST /api/v1/staff/distributor-applications/{id}/decision

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{decision:APPROVE|REJECT|SUSPEND,reason:string,marketId?:UUID,priceBookId?:UUID}`.
- **Response:** `ApplicationDTO`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotency required; persist pending command then invoke commerce; return 202 pending until acknowledged. Reject requires no entitlement activation. Production APPROVE requires prior UNDER_REVIEW with reviewer distinct from decision actor; no self-approval.
- **Events:** distributor.decision.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-096 POST /api/v1/reviews

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{kind:PRODUCT|TESTIMONIAL,productId?:UUID,orderLineId?:UUID,rating?:int,title?:string,body:string[1..5000]}`.
- **Response:** `ReviewDTO{id:UUID,state:ReviewState,rating:int|null,verifiedPurchase:boolean,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Verify order association through commerce proof; no client verification flag.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-097 GET /api/v1/reviews

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** productId:UUID optional; kind:ReviewKind optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ReviewPublicDTO{id:UUID,displayName:string,rating:int|null,title:string|null,body:string,verifiedPurchase:boolean,publishedAt:Instant}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Published only; display name uses user-consented alias, no email.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-098 POST /api/v1/staff/reviews/{id}/moderation

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{decision:PUBLISH|REJECT|WITHDRAW,reason:string}`.
- **Response:** `ReviewDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Sanitize content and audit reason; rejected/withdrawn never returned publicly.
- **Events:** review.published.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-099 POST /api/v1/newsletter-subscriptions

- **Service / module / phase:** experience / consent / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{email:Email,policyVersion:string,antiSpamToken:string}`.
- **Response:** `{accepted:boolean}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Neutral accepted response prevents email enumeration; send transactional double opt-in; subscription not yet marketing eligible.
- **Events:** newsletter.verification.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-100 POST /api/v1/newsletter-subscriptions/confirmation

- **Service / module / phase:** experience / consent / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** PUBLIC_TOKEN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{token:OpaqueToken}`.
- **Response:** `{confirmed:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Token hashed, scoped, one use and 24h expiry; active only after verified grant.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-101 POST /api/v1/unsubscribe

- **Service / module / phase:** experience / consent / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** PUBLIC_TOKEN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{token:OpaqueToken}`.
- **Response:** `{unsubscribed:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent withdrawal and local suppression atomic; do not require login; malformed token returns generic response.
- **Events:** consent.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-102 PUT /api/v1/me/consents

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{destinationId?:UUID,channel:EMAIL|SMS|WHATSAPP,purpose:NEWSLETTER|MARKETING|CART_RECOVERY|ORDER_UPDATES,granted:boolean,policyVersion:string}`.
- **Response:** `{channel:string,purpose:string,granted:boolean,recordedAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Grant/withdraw append event; withdrawal updates suppression before provider sync. SMS/WhatsApp grants require active actor-owned verified destination. Withdrawal updates destination/channel suppression transactionally.
- **Events:** consent.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-103 GET /api/v1/career-opportunities

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** kind:OpportunityKind optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<OpportunityDTO{id:UUID,title:string,slug:string,kind:OpportunityKind,description:string,fieldsSchema:object,closesAt:Instant|null}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Published and open opportunities only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-104 POST /api/v1/staff/career-opportunities

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{title:string,slug:Slug,kind:OpportunityKind,description:string,fieldsSchema:object,closesAt?:Instant,state:DRAFT|PUBLISHED|CLOSED}`.
- **Response:** `OpportunityDTO plus version:int64`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate JSON form schema and allowed field types; publishing approved content only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-105 POST /api/v1/career-opportunities/{id}/applications

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{name:string,email:Email,answers:object,cvFileId?:UUID,privacyPolicyVersion:string,antiSpamToken:string}`.
- **Response:** `{id:UUID,state:SUBMITTED}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate answers against form revision; clean owned upload token; store private applicant record first.
- **Events:** career.application.created.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-106 GET /api/v1/staff/career-applications

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** opportunityId:UUID optional; state:enum optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ApplicationSummaryDTO{id:UUID,opportunityId:UUID,name:string,state:string,createdAt:Instant}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Require careers.read permission in addition to SALES; no public applicant data.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-107 PATCH /api/v1/staff/career-applications/{id}

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{state:REVIEWED|SHORTLISTED|REJECTED|CLOSED,reason:string}; If-Match`.
- **Response:** `{id:UUID,state:string,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Require careers.manage permission; audit confidential access and transition.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-108 POST /api/v1/staff/campaigns

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,title:string,marketId:UUID,commercialRuleId?:UUID,displayConfig:object,startsAt:Instant,endsAt:Instant,published:boolean}`.
- **Response:** `CampaignDTO{id:UUID,...request,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only display settings; validate commerce rule via API and never grant discount here.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-109 GET /api/v1/campaigns

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** market:code required
- **Request:** `none`.
- **Response:** `[{id:UUID,code:string,title:string,displayConfig:object,startsAt:Instant,endsAt:Instant}]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only published active campaign for requested market.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-110 GET /api/v1/staff/reports/engagement

- **Service / module / phase:** experience / reporting / BP4.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ANALYST; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** from,to:Instant required max 366d
- **Request:** `none`.
- **Response:** `{leadsByState:object,applicationsByState:object,reviewsByState:object,subscriptions:int,campaigns:[{id:UUID,entries:int}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned aggregates only; commerce reward totals fetched via separate report API.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REPORTING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-111 POST /api/v1/commerce/files/uploads

- **Service / module / phase:** commerce / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{purpose:FilePurpose,filename:string,mimeType:string,size:int64,checksumSha256:string,linkedResourceId?:UUID}`.
- **Response:** `{assetId:UUID,uploadUrl:HTTPS,requiredHeaders:object,expiresAt:Instant}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Issue quarantine key and signed upload restrictions; purpose-specific authorization; no final public URL until scan.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-112 POST /api/v1/commerce/files/{id}/completion

- **Service / module / phase:** commerce / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{checksumSha256:string}`.
- **Response:** `AssetDTO`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** HEAD quarantined object; verify content, size and ownership; queue scan.
- **Events:** file.scan.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-113 GET /api/v1/commerce/files/{id}/download

- **Service / module / phase:** commerce / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{url:HTTPS,expiresAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only CLEAN and purpose-authorized document; 60-second private signed URL. Public brochure URL has immutable key.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-114 POST /api/v1/experience/files/uploads

- **Service / module / phase:** experience / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER_OR_UPLOAD_TOKEN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{purpose:FilePurpose,filename:string,mimeType:string,size:int64,checksumSha256:string,linkedResourceId?:UUID}`.
- **Response:** `{assetId:UUID,uploadUrl:HTTPS,requiredHeaders:object,expiresAt:Instant}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Issue quarantine key and signed upload restrictions; purpose-specific authorization; no final public URL until scan.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-115 POST /api/v1/experience/files/{id}/completion

- **Service / module / phase:** experience / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER_OR_UPLOAD_TOKEN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{checksumSha256:string}`.
- **Response:** `AssetDTO`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** HEAD quarantined object; verify content, size and ownership; queue scan.
- **Events:** file.scan.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-116 GET /api/v1/experience/files/{id}/download

- **Service / module / phase:** experience / files / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER_OR_UPLOAD_TOKEN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{url:HTTPS,expiresAt:Instant}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only CLEAN and purpose-authorized document; 60-second private signed URL. Public brochure URL has immutable key.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-117 POST /internal/v1/distributor-entitlements

- **Service / module / phase:** commerce / authorization / BP3.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_EXPERIENCE:entitlement.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,applicationId:UUID,partyId:UUID,organizationName:string,countryCode:string,businessReference?:string,decision:ACTIVATE|SUSPEND,marketId:UUID,priceBookId?:UUID}`.
- **Response:** `{commandId:UUID,organizationId:UUID,state:EntitlementState,revision:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Dedup command; experience cannot set arbitrary staff roles; suspend is effective before ACK.
- **Events:** distributor.entitlement.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, AUTHORIZATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-118 POST /internal/v1/payments/reconciliation

- **Service / module / phase:** commerce / payment / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:payment.reconcile; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID,limit:int<=100,cursor?:string}`.
- **Response:** `{processed:int,nextCursor:string|null,mismatches:int}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Commerce performs provider verification using its credentials; worker never receives payment secret.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PAYMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-119 GET /internal/v1/shipments/{id}/provider-context

- **Service / module / phase:** commerce / fulfillment / BP4.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:logistics.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{shipmentId:UUID,orderId:UUID,address:AddressDTO,shipper:CarrierAddress,serviceCode:string,packages:[CarrierPackage],bookingReference:string,mode:MOCK|SANDBOX|LIVE}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Minimum logistics data only; immutable stable request reference.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-120 POST /internal/v1/shipments/{id}/provider-result

- **Service / module / phase:** commerce / fulfillment / BP4.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:logistics.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,provider:FEDEX,reference:string,status:ShipmentState,occurredAt:Instant,trackingUrl?:HTTPS,labelObjectKey?:string,labelChecksum?:string,bookingState?:BOOKED|UNKNOWN|FAILED,mode:MOCK|SANDBOX|LIVE}`.
- **Response:** `ShipmentDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Dedup command; verify configured provider and state; no financial state mutation. Label key must be under configured private label prefix and match trusted booking/checksum; create private SHIPPING_LABEL metadata idempotently, never user URL/download fetch. BOOKED maps created label without stock dispatch.
- **Events:** shipment.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-121 POST /internal/v1/purchase-proofs

- **Service / module / phase:** commerce / order / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_EXPERIENCE:purchase-proof.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{partyId:UUID,orderLineId:UUID,productId:UUID}`.
- **Response:** `ProofDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Boolean proof only; party and line association enforced, including settlement.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-122 GET /internal/v1/notification-context/{orderId}

- **Service / module / phase:** commerce / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** orderId; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{orderNumber:string,recipient:{partyId:UUID,email:Email},total:Money,currency:string,trackingUrl:HTTPS|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Job references an allowed notification type and order ID; no entire customer profile.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-123 GET /internal/v1/catalogue/knowledge/{id}

- **Service / module / phase:** commerce / catalogue / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_AI:knowledge.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{id:UUID,revision:int64,approved:boolean,marketCodes:[string],name:string,description:string,specifications:object,publicUrl:HTTPS,checksum:string}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** No wholesale pricing or customer fields; current publication/eligibility authoritative.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-124 POST /internal/v1/ai/order-status

- **Service / module / phase:** commerce / order / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_AI:order-status.read_WITH_SIGNED_ACTOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{orderId:UUID}`.
- **Response:** `{orderNumber:string,status:string,fulfillmentStatus:string,trackingUrl:HTTPS|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Trusted signed actor required; actor ownership independently rechecked; model cannot choose a party ID.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ORDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-125 POST /internal/v1/content/revision-validation

- **Service / module / phase:** experience / content / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_AI:knowledge.validate; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{items:[{contentItemId:UUID,revision:int}]}`.
- **Response:** `{items:[{contentItemId:UUID,revision:int,approved:boolean,current:boolean}]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Batch validate <=20; unavailable owner means do not use that source.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-126 GET /internal/v1/content/knowledge/{id}

- **Service / module / phase:** experience / content / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_AI:knowledge.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `ContentDTO plus approved:boolean,checksum:string,visibility:PUBLIC`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Return only approved PUBLIC content; never include leads/applications.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONTENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-127 POST /internal/v1/communication-eligibility

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:consent.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{partyId?:UUID,destinationId?:UUID,emailHash?:string,channel:EMAIL|SMS|WHATSAPP,purpose:NEWSLETTER|MARKETING|CART_RECOVERY|ORDER_UPDATES}`.
- **Response:** `{eligible:boolean,reasonCode:string,email:Email|null,phone:E164|null,destinationHash:string,policyRevision:string}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Fail closed for MARKETING and recovery without current grant; response encrypted in transit and not logged.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-128 GET /internal/v1/career-applications/{id}/sheet-context

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:sheets.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{applicationId:UUID,opportunityTitle:string,name:string,email:Email,submittedAt:Instant,state:string,approvedAnswers:object}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Export only approved field allowlist, never CV bytes.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-129 GET /internal/v1/leads/{id}/notification-context

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{leadId:UUID,type:string,subject:string,recipientGroup:string,adminLink:HTTPS}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Recipient addresses configured in worker; complaint message is not event payload.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-130 GET /internal/v1/leads/{id}/quote-context

- **Service / module / phase:** experience / lead / BP3.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:quote.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{id:UUID,partyId:UUID,organizationId:UUID|null,state:string}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Verify quote source association before draft creation.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-131 POST /internal/v1/leads/{id}/quote-association

- **Service / module / phase:** experience / lead / BP3.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:quote.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,quoteId:UUID,quoteState:QuoteState}`.
- **Response:** `{acknowledged:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Dedup command, do not change customer identity from callback.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-132 POST /internal/v1/files/{id}/scan-result

- **Service / module / phase:** commerce / files / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:files.scan.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,checksumSha256:string,result:CLEAN|REJECTED,scannerVersion:string,reasonCode?:string}`.
- **Response:** `AssetDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Same owned quarantine asset and checksum; only configured scanner job; clean promotion copies to final key.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-133 POST /internal/v1/notification-deliveries

- **Service / module / phase:** commerce / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID,resourceId:UUID,state:SENT|FAILED|SUPPRESSED|UNKNOWN,providerReference?:string}`.
- **Response:** `{acknowledged:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Record minimal delivery outcome and expose failed work to staff; dedup job/state.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-134 GET /api/v1/commerce/staff/audit-records

- **Service / module / phase:** commerce / audit / BP4.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** resourceId:UUID optional; action:string optional; from,to:Instant; page,size,sort
- **Request:** `none`.
- **Response:** `Page<AuditDTO{id:UUID,actor:string,action:string,resourceType:string,resourceId:UUID,occurredAt:Instant,reason:string|null,oldValue:object|null,newValue:object|null}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned service audit; protected allowlisted fields; no delete API.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, AUDIT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-135 PUT /api/v1/commerce/staff/configuration/{key}

- **Service / module / phase:** commerce / platform / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** key; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{value:object,active:boolean,reason:string}; If-Match`.
- **Response:** `{key:string,revision:int64,active:boolean,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Typed allowlisted configuration keys only; do not store secrets; sensitive activation requires recent MFA. Implemented key catalogue.metadata uses closed metadata shape with no business values seeded. First If-Match:"0" creates revision/version1; later writes append history and atomic redacted audit. Current ADMIN and activation MFA checked before replay and under Party lock. See contracts/openapi/commerce.yaml and docs/runbooks/commerce-configuration.md.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PLATFORM_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-136 POST /internal/v1/files/{id}/scan-result

- **Service / module / phase:** experience / files / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:files.scan.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,checksumSha256:string,result:CLEAN|REJECTED,scannerVersion:string,reasonCode?:string}`.
- **Response:** `AssetDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Same owned quarantine asset and checksum; only configured scanner job; clean promotion copies to final key.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-137 POST /internal/v1/notification-deliveries

- **Service / module / phase:** experience / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID,resourceId:UUID,state:SENT|FAILED|SUPPRESSED|UNKNOWN,providerReference?:string}`.
- **Response:** `{acknowledged:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Record minimal delivery outcome and expose failed work to staff; dedup job/state.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-138 GET /api/v1/experience/staff/audit-records

- **Service / module / phase:** experience / audit / BP4.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** resourceId:UUID optional; action:string optional; from,to:Instant; page,size,sort
- **Request:** `none`.
- **Response:** `Page<AuditDTO{id:UUID,actor:string,action:string,resourceType:string,resourceId:UUID,occurredAt:Instant,reason:string|null,oldValue:object|null,newValue:object|null}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned service audit; protected allowlisted fields; no delete API.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, AUDIT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-139 PUT /api/v1/experience/staff/configuration/{key}

- **Service / module / phase:** experience / platform / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** key; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{value:object,active:boolean,reason:string}; If-Match`.
- **Response:** `{key:string,revision:int64,active:boolean,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Typed allowlisted configuration keys only; do not store secrets; sensitive activation requires recent MFA.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PLATFORM_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-140 GET /internal/v1/jobs

- **Service / module / phase:** integration / provider / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:jobs.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** kind,state:enum optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<JobDTO{id:UUID,state:string,attemptCount:int,nextAttemptAt:Instant,lastErrorCode:string|null}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Staff views proxy through commerce after OPERATIONS or ADMIN authorization; worker remains private.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROVIDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-141 POST /internal/v1/jobs/{id}/retry

- **Service / module / phase:** integration / provider / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:jobs.retry_WITH_SIGNED_STAFF; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `JobDTO`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Preserve original business/provider idempotency key; UNKNOWN side effect requires reconciliation before retry.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROVIDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-142 POST /internal/v1/templates

- **Service / module / phase:** integration / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:templates.write_WITH_SIGNED_ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,revision:int,locale:BCP47,channel:EMAIL|SMS|WHATSAPP,classification:TRANSACTIONAL|MARKETING,subject?:string,body:string,providerTemplateId?:string,variablesSchema:object,active:boolean,reason?:string}`.
- **Response:** `{id:UUID,code:string,revision:int,active:boolean}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate template schema and approved variables; no runtime arbitrary code evaluation.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-143 POST /api/v1/ai/conversations

- **Service / module / phase:** ai / assistant / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** PUBLIC_OR_CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{market:string,locale?:string}`.
- **Response:** `{id:UUID,expiresAt:Instant,anonymousToken:OpaqueToken|null}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Anonymous token is signed ownership capability, HttpOnly cookie preferred; no private tools without a session.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ASSISTANT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-144 POST /api/v1/ai/conversations/{id}/messages

- **Service / module / phase:** ai / assistant / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONVERSATION_OWNER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{message:string[1..2000],clientRequestId:UUID}`.
- **Response:** `SSE events: metadata{runId:UUID}; delta{text:string}; sources{items:[SourceDTO]}; completed{answer:AnswerDTO}; error{problem:Problem}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Max 8 steps, 4 tools, 20-second deadline, 12000 total tokens; authorize actor/context server side.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ASSISTANT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-145 POST /api/v1/ai/runs/{id}/feedback

- **Service / module / phase:** ai / assistant / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONVERSATION_OWNER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{rating:-1|1,category?:string,comment?:string<=1000}`.
- **Response:** `{received:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate run belongs to current conversation owner; one feedback per run.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ASSISTANT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-146 POST /internal/v1/knowledge/reindex

- **Service / module / phase:** ai / knowledge / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:ai.manage_WITH_SIGNED_ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{sourceService:COMMERCE|EXPERIENCE,resourceId?:UUID,embeddingRevisionId:UUID}`.
- **Response:** `{jobId:UUID,state:QUEUED}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Only approved sources; create owned ingestion job and audit; no arbitrary URL.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, KNOWLEDGE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-147 POST /internal/v1/evaluations

- **Service / module / phase:** ai / evaluation / BP5.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:ai.manage_WITH_SIGNED_ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{suiteId:UUID,modelPolicyRevision:string,promptRevision:string,retrievalRevision:string}`.
- **Response:** `{evaluationId:UUID,state:QUEUED}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Run sealed dataset and publish approved report; activation remains blocked on failed cases.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, EVALUATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-148 GET /api/v1/staff/reports/ai

- **Service / module / phase:** ai / evaluation / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ANALYST; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** from,to:Instant required max 90d
- **Request:** `none`.
- **Response:** `{requests:int,escalations:int,topIntents:[{intent:string,count:int}],emptySearches:int,tokenUsage:int,costEstimate:decimal,latencyMs:{p50:int,p95:int}}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Aggregates only, no raw prompts or private tool results.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, EVALUATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-149 GET /api/v1/staff/products

- **Service / module / phase:** commerce / catalogue / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_EDITOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** q,status:optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ProductAdminDTO>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Include draft/archived and unpublished assets; private no-store.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CATALOGUE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-150 GET /api/v1/staff/price-books

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** segment,currency:optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<PriceBookDTO>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Show draft and active revisions.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-151 POST /api/v1/staff/price-books/{id}/revisions

- **Service / module / phase:** commerce / pricing / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{currency:ISO4217,segment:RETAIL|DISTRIBUTOR,effectiveFrom:Instant,effectiveUntil?:Instant,entries:[{skuId:UUID,minimumQuantity:Quantity,unitAmount:Money}]}`.
- **Response:** `PriceBookDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Copy-on-write; never mutate active prices; require next effective interval.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PRICING_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-152 POST /api/v1/staff/promotion-rules/{id}/revisions

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{kind:PromotionKind,marketId:UUID,startsAt:Instant,endsAt:Instant,parameters:PromotionParameters,eligibility:PromotionEligibility,stackingPolicy:EXCLUSIVE|STACKABLE,enabled:false}`.
- **Response:** `PromotionRuleDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Copy-on-write; previous redemption references remain stable.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-153 POST /api/v1/staff/promotion-rules/{id}/activation

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{enabled:boolean,approvalReference:string,reason:string}`.
- **Response:** `PromotionRuleDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate approved budgets/limits and all required commercial parameters.
- **Events:** pricing.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-154 PATCH /api/v1/staff/coupons/{id}

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{active:boolean,reason:string}; If-Match`.
- **Response:** `CouponDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Disable future redemptions without invalidating already committed orders.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-155 PATCH /api/v1/staff/wheel-campaigns/{id}

- **Service / module / phase:** commerce / promotion / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{enabled:boolean,reason:string}; If-Match`.
- **Response:** `WheelCampaignDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Active outcomes remain immutable for draw evidence; disable immediately stops new draws.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROMOTION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-156 GET /api/v1/staff/career-applications/{id}

- **Service / module / phase:** experience / careers / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES_WITH_CAREERS_READ; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{id:UUID,name:string,email:Email,answers:object,cvFileId:UUID|null,state:string,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Audit confidential record access; protected clean-file download separate.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CAREERS_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-157 POST /api/v1/career-upload-sessions

- **Service / module / phase:** experience / files / BP2.
- **Authentication:** No user login; anti-abuse controls.
- **Authorization:** PUBLIC; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{antiSpamToken:string}`.
- **Response:** `{uploadToken:OpaqueToken,expiresAt:Instant,maximumBytes:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Token expires after 30m; purpose CAREER_CV; no anonymous distributor proof uploads.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FILES_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-158 POST /internal/v1/newsletter-subscriptions/{id}/notification-context

- **Service / module / phase:** experience / notification / BP2.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID}`.
- **Response:** `{email:Email,confirmationLink:HTTPS|null,unsubscribeLink:HTTPS,classification:TRANSACTIONAL}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent mint-or-fetch under stable jobId; confirmation hash/expiry and encrypted context persist atomically; retries reuse unexpired context, never log raw tokens.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-159 POST /internal/v1/cart-recovery/{id}/eligibility

- **Service / module / phase:** commerce / cart / BP4.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:recovery.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{}`.
- **Response:** `{eligible:boolean,partyId:UUID,cartLink:HTTPS|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Check still active and inactive >=24h, no completed order; integration independently rechecks consent.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CART_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-160 POST /api/v1/webhooks/logistics/{provider}

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Provider signature, raw/form payload validator and durable dedup; no browser CSRF requirement.
- **Authorization:** PROVIDER_SIGNATURE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** provider; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Provider-specific bounded raw payload`.
- **Response:** `{received:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Reserved extension route disabled for launch FedEx polling. Never invent a FedEx webhook signature; enable only a separately documented provider webhook contract. Baseline uses authoritative Track API polling.
- **Events:** shipment.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-161 POST /api/v1/me/referral-code

- **Service / module / phase:** commerce / reward / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{}`.
- **Response:** `{code:string,link:HTTPS,qualifiedReferralCount:int}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent create-or-read stable own referral code; unique party_id prevents duplicate issuance.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, REWARD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-162 PUT /api/v1/staff/markets/{id}/retail-price-book

- **Service / module / phase:** commerce / market / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{priceBookId:UUID,reason:string}`.
- **Response:** `MarketDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Recent MFA and If-Match; book RETAIL/currency/effective dates valid; atomically update policy revision.
- **Events:** market.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, MARKET_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-163 GET /api/v1/staff/integration-jobs

- **Service / module / phase:** commerce / provider / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** state:optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<JobDTO>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Compiled proxy to integration jobs; exact service scope and signed actor; redacted errors only.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROVIDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-164 POST /api/v1/staff/integration-jobs/{id}/retry

- **Service / module / phase:** commerce / provider / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `JobDTO`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Compiled proxy; no arbitrary upstream URL; same durable job key; UNKNOWN first reconciles.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROVIDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-165 POST /api/v1/staff/notification-templates

- **Service / module / phase:** commerce / notification / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,revision:int,locale:BCP47,channel:EMAIL|SMS|WHATSAPP,classification:TRANSACTIONAL|MARKETING,subject?:string,body:string,providerTemplateId?:string,variablesSchema:object,active:boolean,reason?:string}`.
- **Response:** `{code:string,revision:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Compiled worker proxy; recent MFA; strict escaped variable allowlist; immutable activated revision.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-166 POST /api/v1/staff/ai/model-policies

- **Service / module / phase:** ai / gateway / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,provider:BEDROCK,chatModelId:string,fallbackModelId?:string,parameters:ModelParameters,promptRevisionId:UUID}`.
- **Response:** `{id:UUID,revision:int,approved:boolean,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Persist disabled policy; provider model/region capabilities checked; no model ID supplied by customer.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, GATEWAY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-167 POST /api/v1/staff/ai/model-policies/{id}/activation

- **Service / module / phase:** ai / gateway / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{evaluationResultId:UUID,reason:string}`.
- **Response:** `{id:UUID,revision:int,approved:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Recent MFA; passing same-policy prompt/model evaluation and budget required; immutable active revision.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, GATEWAY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-168 POST /api/v1/staff/ai/prompt-revisions

- **Service / module / phase:** ai / gateway / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,template:string,toolSchemaRevision:string,reason:string}`.
- **Response:** `{id:UUID,code:string,revision:int,checksum:string}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Trusted reviewed template only; bounded input interpolation; never include secrets or instructions from retrieved text.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, GATEWAY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-169 POST /api/v1/staff/markets

- **Service / module / phase:** commerce / market / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,name:string,currency:ISO4217}`.
- **Response:** `MarketDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Create disabled market with policyRevision1; no default tax/destination/book/provider approval. Audit recent MFA.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, MARKET_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-170 POST /api/v1/staff/stock-locations

- **Service / module / phase:** commerce / inventory / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,name:string}`.
- **Response:** `{id:UUID,code:string,name:string,active:boolean,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Unique code; zero initial balances created by explicit stock adjustment per SKU, never fabricated available stock.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, INVENTORY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-171 POST /api/v1/staff/delivery-rules

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{marketId:UUID,countryCode:ISO3166,regionCode?:string,postalPrefix?:string,fee:Money,freeAbove?:Money,priority:int,active:boolean}`.
- **Response:** `DeliveryRuleDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Create fee rule; validate approved market and reject ambiguous specificity/priority.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-172 POST /api/v1/staff/distributor-applications/{id}/review

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string,documentsVerified:boolean}`.
- **Response:** `ApplicationDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Set UNDER_REVIEW, record reviewer; clean applicant-owned proofs required. Final production approval must be by a distinct SALES actor.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-173 POST /api/v1/staff/quotes/{id}/revisions/{revisionId}/approval

- **Service / module / phase:** commerce / quote / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** FINANCE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id, revisionId; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{approve:boolean,reason:string}`.
- **Response:** `QuoteDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Set APPROVED/REJECTED; approval actor differs from revision creator when threshold policy requires; offer requires APPROVED.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, QUOTE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-174 POST /api/v1/staff/orders/{id}/stock-returns

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{lines:[{orderLineId:UUID,balanceId:UUID,quantity:Quantity}],kind:PHYSICAL_RETURN|UNSHIPPED_CANCEL,receiptReference?:string[1..120],reason:string}`.
- **Response:** `{returnId:UUID,orderId:UUID,lines:[{orderLineId:UUID,quantity:Quantity}]}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent stock return; physical receipt evidence required after dispatch; unshipped cancellation releases allocation without increasing on_hand; lock order/balances and append movements. No money refund implied. PHYSICAL_RETURN requires receiptReference; each movement stores order_line_id and evidence, and cumulative returned quantities are checked under order/line lock.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-175 POST /internal/v1/communication-suppressions

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:consent.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,emailHash?:string,destinationHash?:string,channel:EMAIL|SMS|WHATSAPP,reason:HARD_BOUNCE|COMPLAINT|OPT_OUT}`.
- **Response:** `{acknowledged:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent authoritative suppression on verified provider signal; no arbitrary grant.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-176 GET /api/v1/staff/reviews

- **Service / module / phase:** experience / engagement / BP2.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CONTENT_PUBLISHER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** state:ReviewState optional; kind:optional; page,size,sort
- **Request:** `none`.
- **Response:** `Page<ReviewAdminDTO{id:UUID,productId:UUID|null,orderLineId:UUID|null,kind:PRODUCT|TESTIMONIAL,title:string|null,body:string,rating:int|null,state:ReviewState,verifiedPurchase:boolean,version:int64}>`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Private moderation queue; do not expose reviewer email or private order data.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, ENGAGEMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-177 GET /api/v1/staff/distributor-applications/{id}

- **Service / module / phase:** experience / distributor / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `ApplicationDetailDTO{id:UUID,organizationName:string,countryCode:ISO3166,businessReference:string|null,state:ApplicationState,documents:[{fileId:UUID,documentType:string,scanStatus:string}],reviewerPartyId:UUID|null,reviewReason:string|null,organizationId:UUID|null,version:int64}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Authorized staff only; document signed download obtained separately through ownership/role check.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, DISTRIBUTOR_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-178 POST /api/v1/staff/ai/model-rates

- **Service / module / phase:** ai / gateway / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{provider:BEDROCK,modelId:string,inputPerMillion:decimal-string>=0,outputPerMillion:decimal-string>=0,currency:ISO4217,effectiveFrom:Instant,approvalReference:string}`.
- **Response:** `{id:UUID,revision:int}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Recent MFA; persist immutable audited approved rate; no invented live pricing.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, GATEWAY_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-179 POST /api/v1/staff/ai/embedding-revisions

- **Service / module / phase:** ai / knowledge / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{provider:BEDROCK,modelId:amazon.titan-embed-text-v2:0,dimensions:1024,chunkingRevision:string,approvalReference:string}`.
- **Response:** `{id:UUID,enabled:false}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Create disabled embedding revision; activate only after complete rebuild with passing retrieval evaluation; no mixed dimensions.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, KNOWLEDGE_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-180 POST /api/v1/staff/ai/evaluation-suites

- **Service / module / phase:** ai / evaluation / BP5.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** ADMIN; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string,cases:[{id:string,input:string,market:string,locale:BCP47,expectedIntent:string,requiredSourceIds:[UUID],mustEscalate:boolean,forbiddenActions:[string]}],thresholds:{groundednessMinimum:decimal0..1,scopeLeaksMaximum:0,injectionMutationMaximum:0,latencyP95MaximumMs:int<=20000}}`.
- **Response:** `{id:UUID,revision:int}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Versioned sanitized synthetic suite; >=200 approved cases, malicious/cross-owner/withdrawal/provider-error coverage; no real private customer transcripts. Explicit array cap500 overrides common100 limit.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, EVALUATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-181 POST /api/v1/carts/{id}/delivery-quotes

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{addressId:UUID,serviceCode?:string}`.
- **Response:** `DeliveryQuoteDTO{id:UUID,provider:FEDEX,serviceCode:string,currency:NGN,deliveryAmount:Money,expiresAt:Instant,cartVersion:int64,mode:MOCK|SANDBOX|LIVE}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent owned cart; validated Nigerian address, positive server packing; commerce calls private rate port before transaction, persists exact approved result. Reject unsupported route/non-NGN response, no mock/live mixing.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-182 POST /internal/v1/carrier/quotes

- **Service / module / phase:** integration / provider / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:carrier.quote_WITH_SIGNED_ACTOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,cartId:UUID,market:NG,currency:NGN,shipper:CarrierAddress,destination:CarrierAddress,packages:[CarrierPackage],serviceCode?:string}`.
- **Response:** `{provider:FEDEX,serviceCode:string,currency:NGN,amount:Money,expiresAt:Instant,providerReference:string|null}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Allowlisted server packing/address only, bounded OAuth/rate request, no provider account or base URL from caller.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, PROVIDER_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-183 POST /api/v1/me/communication-destinations

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{channel:SMS|WHATSAPP,address:E164,policyVersion:string,consentedPurposes:[ORDER_UPDATES|MARKETING|CART_RECOVERY]}`.
- **Response:** `DestinationDTO{id:UUID,channel:SMS|WHATSAPP,maskedAddress:string,status:PENDING|ACTIVE|UNSUBSCRIBED|SUPPRESSED,version:int64}`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent create PENDING and verification request; authenticated explicit opt-in, normalize +234 number for Nigeria launch. Verified does not grant unrequested purpose.
- **Events:** communication.verification.requested.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-184 POST /api/v1/me/communication-destinations/{id}/verification

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{code:string[6 digits]}`.
- **Response:** `DestinationDTO`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Own pending proof only, max5 tries/10m; constant-time HMAC match; activate/grant requested purpose once and delete code ciphertext. Lock destination/proof, deny expired or locked proof.
- **Events:** consent.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-185 GET /api/v1/me/communication-destinations

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `[DestinationDTO]`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Own masked destinations only; never expose stored plaintext address in lists.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-186 DELETE /api/v1/me/communication-destinations/{id}

- **Service / module / phase:** experience / consent / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `none`.
- **Statuses:** 204 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Withdraw all phone/channel purpose grants and suppress before asynchronous provider cleanup; audit/history retained.
- **Events:** consent.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, CONSENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-187 POST /internal/v1/communication-verifications/{id}/notification-context

- **Service / module / phase:** experience / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.read; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID}`.
- **Response:** `{phone:E164,channel:SMS|WHATSAPP,code:string,expiresAt:Instant,templateCode:CONTACT_VERIFICATION}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Same stable job and unexpired proof; minimum encrypted ephemeral context, never logs code/phone; no reissue on worker replay.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-188 POST /api/v1/webhooks/notifications/twilio/status

- **Service / module / phase:** commerce / notification / BP1.
- **Authentication:** Provider signature, raw/form payload validator and durable dedup; no browser CSRF requirement.
- **Authorization:** TWILIO_SIGNATURE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** jobId:UUID required
- **Request:** `Twilio application/x-www-form-urlencoded status fields; full parameter set used by official signature validator`.
- **Response:** `{received:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate exact configured public URL incl jobId query, X-Twilio-Signature with official SDK and configured account. Relay sanitized receipt to scoped integration API;200 only after durable receipt. Unknown job/account denied, duplicate/status regression ignored.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-189 POST /internal/v1/notification-provider-receipts

- **Service / module / phase:** integration / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_COMMERCE:notification.feedback.write; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{jobId:UUID,provider:TWILIO,accountSid:string,messageSid:string,status:string,eventHash:string,errorCode?:string,receivedAt:Instant}`.
- **Response:** `{acknowledged:boolean}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Trusted verified facade only; validate configured account/job/mode and unique receipt; queued/sent/delivered/read/failed/undelivered mapped to monotonic job transitions. UNKNOWN resolves only on matched authoritative receipt/poll.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-190 POST /api/v1/webhooks/notifications/twilio/whatsapp

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Provider signature, raw/form payload validator and durable dedup; no browser CSRF requirement.
- **Authorization:** TWILIO_SIGNATURE; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `Twilio signed form fields: AccountSid,MessageSid,From,To,Body plus all signature parameters`.
- **Response:** `TwiML empty Response XML`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Validate account/registered sender/publicURL/signature using SDK; dedupMessageSid; STOP/UNSUBSCRIBE opt-out immediately, HELP safe static response, other bounded messages create private CONTACT lead. No order lookup/login/financial mutation. Acknowledge only after owner commit.
- **Events:** lead.created.v1, consent.changed.v1
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-191 POST /api/v1/staff/shipments/{id}/booking-reconciliation

- **Service / module / phase:** commerce / fulfillment / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** OPERATIONS; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{reason:string}`.
- **Response:** `{shipmentId:UUID,state:BOOKED|UNKNOWN|FAILED,jobId:UUID}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Idempotent scoped worker reconciliation by shipment reference; may return202. Never blindly create second FedEx label after timeout.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-192 POST /api/v1/staff/leads/{id}/messages

- **Service / module / phase:** experience / lead / BP1.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** SALES; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{channel:WHATSAPP,templateCode:string,variables:object}`.
- **Response:** `{jobId:UUID,state:PENDING}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Lead from verified inbound or verified opt-in destination, own role, closed allowlisted templates and fresh suppression; private relay creates unique worker job, no send inside DB transaction.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, LEAD_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-193 POST /internal/v1/notification-commands

- **Service / module / phase:** integration / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_EXPERIENCE:notification.send_WITH_SIGNED_ACTOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** None; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{commandId:UUID,leadId:UUID,templateCode:string,channel:WHATSAPP,variables:object}`.
- **Response:** `{jobId:UUID,state:PENDING}`.
- **Statuses:** 202 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Allowlisted sender/template/context and trusted SALES actor; fetch authoritative destination/context, never arbitrary phone/provider URL; audited stable command dedup.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-194 GET /internal/v1/leads/{id}/messaging-context

- **Service / module / phase:** experience / notification / BP1.
- **Authentication:** Workload JWT with exact client scope and signed actor where specified.
- **Authorization:** SERVICE_INTEGRATION:notification.read_WITH_SIGNED_ACTOR; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `none`.
- **Response:** `{phone:E164,channel:WHATSAPP,destinationHash:string,eligible:boolean,basis:SIGNED_INBOUND_REQUEST|VERIFIED_DESTINATION,approvedTemplateCodes:[string]}`.
- **Statuses:** 200 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Trusted originating SALES actor and exact lead scope required; phone from verified signed inbound or owned active destination, latest suppression enforced. Signed inbound supports requested utility reply only, never marketing consent or authenticated order access.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, NOTIFICATION_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

### API-195 POST /api/v1/quotes/{id}/delivery-quotes

- **Service / module / phase:** commerce / fulfillment / BP3.
- **Authentication:** Verified session/bearer or explicitly described capability/OIDC flow.
- **Authorization:** CUSTOMER; resource/organization/market checks required.
- **Headers:** section12; CSRF on cookie mutations, If-Match on versioned commands, Idempotency-Key on creates/external side effects; provider callback uses its signature header and exact content type.
- **Path parameters:** id; section13 common UUID/identifier types.
- **Query:** None; reject unknown parameters.
- **Request:** `{revisionId:UUID,addressId:UUID,serviceCode?:string}`.
- **Response:** `DeliveryQuoteDTO`.
- **Statuses:** 201 success; common section13 errors;202 for explicitly uncertain/queued external actions.
- **Validation / business rules:** Owned approved nonexpired offered revision; create/reuse owned temporary cart from frozen quantities for carrier packing; store source_quote_revision_id, calculate rate outside transaction and bind revision/address/package/mode. No negotiated price mutation.
- **Events:** None directly; owned audit/history where applicable.
- **Errors:** VALIDATION_ERROR, AUTH_REQUIRED, ACCESS_DENIED, NOT_FOUND, VERSION_MISMATCH, INVALID_TRANSITION, FULFILLMENT_RULE_VIOLATION; PROVIDER_UNAVAILABLE, PROVIDER_OUTCOME_UNKNOWN, PROVIDER_MODE_MISMATCH, FEATURE_DISABLED as applicable.

## Normative reusable DTO registry

Closed fields below take precedence over shorthand ...request/plus/as API notation. API-072 uses API-037. Staff product APIs add status/version; request lengths inherit matching section10 columns. E164 is a normalized +country-number string<=16. Unknown fields rejected.

| Type | Closed wire fields |
|---|---|
| AddressDTO | {id:UUID,label:string\|null,recipient:string,phone:string,line1:string,line2:string\|null,city:string,region:string\|null,postalCode:string\|null,countryCode:ISO3166,organizationId:UUID\|null,isDefault:boolean,version:int64} |
| AnswerDTO | {status:ANSWERED\|CLARIFY\|ESCALATED,answer:string,sources:[SourceDTO],products:[{id:UUID,reason:string}],handoffUrl:HTTPS\|null,runId:UUID} |
| ApplicationDTO | {id:UUID,state:ApplicationState,organizationId:UUID\|null,submittedAt:Instant,version:int64} |
| ApplicationDetailDTO | {id:UUID,organizationName:string,countryCode:ISO3166,businessReference:string\|null,state:ApplicationState,documents:[{fileId:UUID,documentType:string,scanStatus:string}],reviewerPartyId:UUID\|null,reviewReason:string\|null,organizationId:UUID\|null,version:int64} |
| ApplicationSummaryDTO | {id:UUID,opportunityId:UUID,name:string,state:string,createdAt:Instant} |
| AssetDTO | {id:UUID,state:UPLOADING\|QUARANTINED\|CLEAN\|PUBLISHED\|REJECTED,purpose:string,mimeType:string,size:int64,checksum:string,url:HTTPS\|null,version:int64} |
| AuditDTO | {id:UUID,actor:string,action:string,resourceType:string,resourceId:UUID,occurredAt:Instant,reason:string\|null,oldValue:object\|null,newValue:object\|null} |
| CampaignDTO | {id:UUID,code:string,title:string,marketId:UUID,commercialRuleId:UUID\|null,displayConfig:CampaignDisplay,startsAt:Instant,endsAt:Instant,published:boolean,version:int64} |
| CartDTO | {id:UUID,marketId:UUID,organizationId:UUID\|null,state:CartState,lines:[{id:UUID,skuId:UUID,quantity:Quantity}],version:int64,lastActivityAt:Instant} |
| CheckoutAttemptDTO | {order:OrderDTO{id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant},payment:{attemptId:UUID,state:PaymentAttemptState,checkoutUrl:HTTPS\|null,reference:string}} |
| ContentDTO | {id:UUID,type:ContentType,slug:string,locale:string,revision:int,title:string,body:object,metadata:SeoDTO,publishedAt:Instant} |
| ContentRevisionDTO | {id:UUID,contentItemId:UUID,revision:int,state:ContentState,type:ContentType,slug:string,locale:BCP47,title:string,body:ContentBody,metadata:SeoDTO,version:int64} |
| CouponDTO | {id:UUID,code:string,active:boolean,remainingUses:int,version:int64} |
| DeliveryQuoteDTO | {id:UUID,provider:FEDEX,serviceCode:string,currency:NGN,deliveryAmount:Money,expiresAt:Instant,cartVersion:int64,mode:MOCK\|SANDBOX\|LIVE} |
| DeliveryRuleDTO | {id:UUID,marketId:UUID,countryCode:ISO3166,regionCode:string\|null,postalPrefix:string\|null,fee:Money,freeAbove:Money\|null,priority:int,active:boolean,version:int64} |
| DestinationDTO | {id:UUID,channel:SMS\|WHATSAPP,maskedAddress:string,status:PENDING\|ACTIVE\|UNSUBSCRIBED\|SUPPRESSED,version:int64} |
| JobDTO | {id:UUID,state:string,attemptCount:int,nextAttemptAt:Instant,lastErrorCode:string\|null} |
| LeadDTO | {id:UUID,type:LeadType,subject:string,name:string,email:string,phone:string\|null,message:string,state:LeadState,priority:NORMAL\|HIGH,assignedTo:UUID\|null,followUpAt:Instant\|null,version:int64,createdAt:Instant} |
| MarketDTO | {id:UUID,code:string,name:string,currency:ISO4217,enabled:boolean,destinations:[{countryCode:ISO3166,addressRules:AddressRules,deliveryEnabled:boolean}],taxPolicy:TaxPolicy,returnsPolicyRevision:string\|null,retailPriceBookId:UUID\|null,policyRevision:int64,version:int64} |
| OpportunityDTO | {id:UUID,title:string,slug:string,kind:OpportunityKind,description:string,fieldsSchema:object,closesAt:Instant\|null} |
| OrderDTO | {id:UUID,orderNumber:string,status:OrderState,paymentStatus:PaymentState,fulfillmentStatus:FulfillmentState,currency:ISO4217,totals:{subtotal:Money,discount:Money,tax:Money,delivery:Money,grandTotal:Money,refunded:Money},lines:[{id:UUID,skuId:UUID,skuCode:string,name:string,quantity:Quantity,unitAmount:Money,lineTotal:Money}],address:AddressDTO,version:int64,createdAt:Instant} |
| PriceBookDTO | {id:UUID,code:string,revision:int64,currency:ISO4217,segment:RETAIL\|DISTRIBUTOR,effectiveFrom:Instant,effectiveUntil:Instant\|null,active:boolean,entries:[{skuId:UUID,minimumQuantity:Quantity,unitAmount:Money}],version:int64} |
| PriceDTO | {skuId:UUID,unitAmount:Money,currency:ISO4217,minimumQuantity:Quantity,packMultiple:Quantity,priceBookRevision:int64,validUntil:Instant} |
| ProductAdminDTO | {ProductDTO fields,status:DRAFT\|ACTIVE\|ARCHIVED,version:int64}; assets include AssetDTO under staff-only policy |
| ProductDTO | {id:UUID,name:string,slug:string,description:string,specifications:object,brand:{id:UUID,name:string}\|null,categories:[{id:UUID,name:string}],skus:[{id:UUID,code:string,variantName:string,unit:string,packSize:Quantity,available:boolean}],assets:[{id:UUID,purpose:string,url:HTTPS}],revision:int64} |
| ProductSummaryDTO | {id:UUID,name:string,slug:string,brandName:string\|null,imageUrl:HTTPS\|null,available:boolean,revision:int64} |
| ProfileDTO | {id:UUID,displayName:string,email:string,emailVerified:boolean,phone:string\|null,locale:string,version:int64} |
| PromotionRuleDTO | {id:UUID,code:string,kind:PromotionKind,marketId:UUID,startsAt:Instant,endsAt:Instant,parameters:PromotionParameters,eligibility:PromotionEligibility,stackingPolicy:EXCLUSIVE\|STACKABLE,enabled:boolean,revision:int64,version:int64} |
| ProofDTO | {eligible:boolean,reasonCode:string,revision:int64} |
| QuoteDTO | {id:UUID,state:QuoteState,revisionId:UUID,revision:int,total:Money,currency:string,validUntil:Instant,lines:[QuoteLineDTO],version:int64} |
| QuoteLineDTO | {id:UUID,skuId:UUID,quantity:Quantity,unitAmount:Money,discountAmount:Money,taxAmount:Money,lineTotal:Money} |
| RecommendationDTO | {relatedProductId:UUID,kind:RELATED\|FEATURED,priority:int} |
| RefundDTO | {id:UUID,state:RefundState,amount:Money,currency:string,version:int64} |
| ReviewAdminDTO | {id:UUID,productId:UUID\|null,orderLineId:UUID\|null,kind:PRODUCT\|TESTIMONIAL,title:string\|null,body:string,rating:int\|null,state:ReviewState,verifiedPurchase:boolean,version:int64} |
| ReviewDTO | {id:UUID,state:ReviewState,rating:int\|null,verifiedPurchase:boolean,version:int64} |
| ReviewPublicDTO | {id:UUID,displayName:string,rating:int\|null,title:string\|null,body:string,verifiedPurchase:boolean,publishedAt:Instant} |
| SeoDTO | {title:string<=70,description:string<=180,canonicalUrl:HTTPS\|null,robots:INDEX\|NOINDEX,alternates:[{locale:BCP47,url:HTTPS}],structuredData:object} |
| SessionDTO | {authenticated:boolean,partyId:UUID\|null,displayName:string\|null,roles:[Role],organizationMemberships:[{organizationId:UUID,role:OWNER\|BUYER}],expiresAt:Instant\|null,csrfToken:string\|null} |
| ShipmentDTO | {id:UUID,orderId:UUID,status:ShipmentState,lines:[{orderLineId:UUID,quantity:Quantity}],trackingUrl:HTTPS\|null,version:int64} |
| SkuDTO | {id:UUID,code:string,variantName:string,unit:string,quantityScale:int,packSize:Quantity,version:int64} |
| SourceDTO | {id:UUID,url:HTTPS,title:string,revision:int64} |
| StaffDTO | {id:UUID,displayName:string,email:string,roles:[Role],version:int64} |
| StockDTO | {skuId:UUID,locationId:UUID,onHand:Quantity,reserved:Quantity,allocated:Quantity,available:Quantity,version:int64} |
| TaxonomyDTO | {id:UUID,name:string,slug:string,parentId:UUID\|null,active:boolean,version:int64} |
| WheelCampaignDTO | {id:UUID,code:string,ruleId:UUID,marketId:UUID,dailyLimit:int,rewardBudget:Money,outcomes:[{id:UUID,label:string,kind:WheelOutcomeKind,weight:int,benefit:WheelBenefit}],enabled:boolean,version:int64} |


# Master section14: API Error Model

Use RFC 9457 Problem Details, retaining architecture decision rather than the prompt's nonnormative error JSON example. Content-Type application/problem+json:

```json
{"type":"https://errors.nigson.example/insufficient-stock","title":"Insufficient stock","status":422,"detail":"One or more items are unavailable in the requested quantity.","instance":"/api/v1/checkout/attempts","code":"INVENTORY_INSUFFICIENT","timestamp":"2026-10-04T03:40:00Z","correlationId":"01900000-0000-7000-8000-000000000001","traceId":"0123456789abcdef0123456789abcdef","violations":[{"field":"lines[0].quantity","code":"INSUFFICIENT_STOCK","message":"Reduce the quantity."}]}
```

The example hostname is a documentation fixture; runtime problem type base URL is approved configuration. Never use it as a live external dependency. Shared Problem library maps validation 400, ownership 404, permission 403, constraint conflict 409, optimistic conflict 412, business eligibility 422, rate limit429 and critical upstream timeout503. Do not emit SQL, class names, stack traces, credentials, raw provider messages, token claims or protected identifier details. Log only classified error code and correlation/trace IDs; server stack trace may be restricted operator telemetry after redaction. Python uses the identical wire schema. Validation errors give safe field paths, not attacker-supplied unescaped values.

