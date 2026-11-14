# Session report: PRODUCT-SELLING-PRICE

> Subsequent user policy update: ADR-027 permits current persisted ADMIN to complete price changes with normal login, without OTP. See 2026-11-14-ADMIN-NORMAL-LOGIN-PRICING.md. MFA descriptions below record the earlier implementation and are superseded for this admin pricing path.
Date: 2026-11-14 UTC
Specification/architecture: 1.1.0 / architecture 1.0 with current ADRs
Task status: IN_PROGRESS ? implementation and local checks pass; isolated PostgreSQL and authenticated runtime acceptance remain pending.
Current engineering/product phase: existing product-phase configuration is unchanged.

## Result

Product edits save selling prices through the existing Commerce price-book APIs rather than specifications.starting_price. A unique immutable replacement retail book preserves other SKUs and all unchanged quantity tiers. SALES creates the book; FINANCE with recent MFA activates it and binds the market using its captured version. Existing published books, market activation, tax, organization assignments and providers are unchanged. Failed or uncertain activation/binding is reported separately from saved product details and retains the replacement identifier for reconciliation.

API-150 exposes the NG retail binding and optimistic-concurrency version in the same authorized repeatable-read snapshot as the staff price-book page. The extension is documented in OpenAPI and API_CONTRACT.md. No database migrations, new write endpoints, ownership changes or event types were introduced; API-027, API-028 and API-162 retain their current authorization, idempotency, audit and event behavior.

Storefront catalogue/detail/card prices come from API-026; metadata and browser overrides cannot supply a selling price. The indefinite browser price cache is removed. Details reprice for the cart quantity, unconfirmed prices cannot enable Add to Cart, and entering the cart reloads backend prices. The product editor no longer invents wholesale/distributor discounts. Existing descriptive prices were not bulk-applied to the database.

## Files changed

Backend:
- services/commerce/src/main/java/com/nigson/commerce/pricing/PriceBookService.java
- services/commerce/src/main/java/com/nigson/commerce/pricing/PricingValues.java
- services/commerce/src/test/java/com/nigson/acceptance/pricing/PriceBookReadTest.java
- services/commerce/src/test/java/com/nigson/acceptance/pricing/PricingValuesTest.java
- services/commerce/src/test/java/com/nigson/acceptance/pricing/PricingPersistenceTest.java
- contracts/openapi/commerce.yaml
- docs/API_CONTRACT.md
- IMPLEMENTATION_STATUS.md
- docs/implementation/2026-11-14-PRODUCT-SELLING-PRICE.md

Frontend (sibling nigson-micro-frontend repository):
- projects/shared-ui/src/lib/retail-pricing.service.ts
- projects/shared-ui/src/lib/retail-pricing.service.spec.ts
- projects/shared-ui/src/lib/catalog.service.ts
- projects/shared-ui/src/lib/admin/admin.service.ts
- projects/shared-ui/src/lib/admin/inventory-mapping.ts
- projects/shared-ui/src/public-api.ts
- projects/admin-shop/src/app/inventory/product-edit.component.ts
- projects/shop/src/app/products/product-card.component.ts
- projects/shop/src/app/products/product-detail.component.ts
- projects/shop/src/app/cart/cart.component.ts
- vitest.admin.config.mjs

## Executed verification

- Frontend: node node_modules/vitest/vitest.mjs run --config vitest.admin.config.mjs ? PASS, 21 tests across 5 files. Tests include immutable copy and tier preservation, denied finance activation, market version conflict without retry, old-backend fail-closed behavior, exact decimal validation, metadata-price rejection and fresh price reads.
- Frontend: node node_modules/typescript/bin/tsc -p projects/admin-shop/tsconfig.app.json --noEmit ? PASS. Shell TypeScript check initially found an incomplete metadata cleanup; corrected, and the subsequent check passed.
- Frontend production: node node_modules/@angular/cli/bin/ng.js build admin-shop; build shop; build shell ? PASS. Shop/shell were rebuilt after the cart reload and unconfirmed-price controls. Existing CSS selector warnings remain (16 skipped selectors per build).
- Backend: verified Oracle JDK archive SHA-256 against Oracle's checksum; project-local toolchain reports 25.0.2+10-LTS-69. No system Java installation was changed.
- Backend: with JAVA_HOME pointing to .build/toolchains/jdk-25.0.2, ran Maven 3.10.0 with -o -B -Dmaven.repo.local=C:/Techxplorers/nigson-proj/nigson/.build/m2 -pl services/commerce -am test -Dtest=PricingValuesTest,PriceBookReadTest,PricingPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false ? PASS build and test compilation; 12 tests passed, 15 persistence tests skipped because NIGSON_TEST_JDBC_URL is absent. New tests verify read-only repeatable-read binding access and authorization before database access. Surefire evidence: services/commerce/target/surefire-reports/.
- OpenAPI: JSON parse succeeds; existing response-key order and unrelated contract formatting preserved.
- git diff --check ? PASS in both repositories (line-ending notices only).

## Preserved failure history and limits

The wrapper initially could not launch in this Windows shell; the existing Maven distribution was invoked directly. Default offline cache lacked the Spring parent; workspace cache lacked plugin dependencies. Sandboxed dependency requests failed with Permission denied: getsockopt. Authorized Maven network access resolved those dependencies. System Java 21 was rejected by the project's Java requirement; the verified project-local JDK fixed the toolchain without weakening checks. A frontend regression test initially caught the missing authoritative-price enrichment in single-product reads; that was corrected and all tests then passed.

Isolated PostgreSQL acceptance, migration/contract Python checks and authenticated browser acceptance were NOT_RUN: no isolated test database, Python CLI or frontend session was available. Port 4200 refused connections; port 8081 answered an unauthenticated health request with HTTP 401, which is not health qualification. No backend runtime restart, staff grants, MFA bypass, provider calls, payment attempts or live price mutations were performed. Database-gated skips are not passing integration acceptance.

The running Commerce service must be updated before the editor can use retailBindings; an older service fails closed. After restarting the updated frontend/backend, an authorized staff user should enter the intended retail selling price again, save with recent MFA, and compare details/cart/checkout at the same quantity. Do not migrate legacy metadata prices without reviewing the intended prices. Stale market bindings reject concurrent updates rather than silently overwriting them; unbound replacement books are retained audit evidence.

Last successful existing backend commit: 88f12eb90e64f2daf8388c84433692a69811d8e7. This change is uncommitted; no acceptance commit exists.
Next dependency-ready task: PRODUCT-SELLING-PRICE-ACCEPTANCE ? run isolated pricing persistence/HTTP checks and authenticated editor-to-cart/checkout acceptance with the updated Commerce runtime.
