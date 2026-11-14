# Session report: ADMIN-NORMAL-LOGIN-PRICING
Date: 2026-11-14 UTC
Specification/architecture: 1.1.0 / architecture 1.0 with ADR-027
Task status: IN_PROGRESS (implementation and local checks pass; isolated database/live acceptance and acceptance commit pending)

## User decision and resulting behavior

The user explicitly requested selling-price changes through normal admin login without OTP. A current persisted ADMIN role now permits price-book listing, creation, revision and activation plus retail price-book market assignment. The editor enables price editing for ADMIN alone. Existing non-admin Sales/Finance alternatives keep their prior requirements. The exception is scoped to PricingAuthority.requirePriceBook and MarketAuthority.requireRetailBook; generic authority checks and MFA requirements remain unchanged for other Finance/market operations. Party/identity status, organization exclusion and current non-revoked ADMIN assignment are checked before replay and inside write transactions. The existing price-book immutability, request validation, CSRF, idempotency, optimistic concurrency, audit and events remain unchanged.

## Files changed in this follow-up

- services/commerce/src/main/java/com/nigson/commerce/pricing/PricingAuthority.java
- services/commerce/src/main/java/com/nigson/commerce/pricing/PriceBookService.java
- services/commerce/src/main/java/com/nigson/commerce/market/MarketAuthority.java
- services/commerce/src/main/java/com/nigson/commerce/market/MarketService.java
- services/commerce/src/test/java/com/nigson/acceptance/pricing/NormalAdminPricingTest.java
- services/commerce/src/test/java/com/nigson/acceptance/pricing/PriceBookReadTest.java
- contracts/openapi/commerce.yaml
- docs/API_CONTRACT.md
- docs/adr/ADR-027-normal-admin-pricing.md
- BACKEND_IMPLEMENTATION_MASTER.md
- IMPLEMENTATION_STATUS.md
- docs/implementation/2026-11-14-ADMIN-NORMAL-LOGIN-PRICING.md
- sibling frontend: projects/shared-ui/src/lib/retail-pricing.service.ts
- sibling frontend: projects/shared-ui/src/lib/retail-pricing.service.spec.ts
- sibling frontend: projects/admin-shop/src/app/inventory/product-edit.component.ts

Prior PRODUCT-SELLING-PRICE work is preserved. No migrations, credentials, staff grants, identity/MFA configuration or provider configuration were changed.

## Verification

Frontend: node node_modules/vitest/vitest.mjs run --config vitest.admin.config.mjs ? PASS, 24 tests. Admin-only permission, customer/single-role rejection and legacy Sales/Finance availability are covered.
Frontend: node node_modules/@angular/cli/bin/ng.js build admin-shop and build shell ? PASS. Both retain the existing 16 skipped CSS-selector warnings.
Backend: Oracle JDK 25.0.2 and Maven 3.10.0, offline workspace cache. Command: mvn -o -B -Dmaven.repo.local=C:/Techxplorers/nigson-proj/nigson/.build/m2 -pl services/commerce -am test -Dtest=NormalAdminPricingTest,PriceBookReadTest,PricingValuesTest,PricingPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false ? PASS: build/test compilation succeeded; 18 unit tests passed, 15 persistence tests skipped because NIGSON_TEST_JDBC_URL is absent. Evidence: services/commerce/target/surefire-reports/com.nigson.acceptance.pricing.NormalAdminPricingTest.txt and related pricing reports.
New authorization tests cover ADMIN without MFA, stale MFA for the admin exception, revoked/unmapped ADMIN denial, customer/organization/anonymous denial and unchanged generic FINANCE/general-market MFA requirements. Existing binding read tests now assert the pricing-specific authority call.
OpenAPI remains parseable JSON with targeted description edits; no endpoint or wire schema was added in this follow-up. git diff --check passed in both repositories, with line-ending notices only.

## Acceptance limits and next work

The running Commerce process was not restarted; the updated runtime is needed before the current session can use the new permission. No live price edits, provider requests or financial operations were performed. Isolated database and authenticated browser acceptance remain pending; missing test database must be reported as skips rather than successful integration qualification. Earlier reports describing mandatory OTP for admins are superseded by ADR-027, not rewritten as historical evidence.

Last successful existing backend commit: 88f12eb90e64f2daf8388c84433692a69811d8e7. No new commit was made.
Next dependency-ready task: restart/deploy the updated Commerce and frontend, then verify the same active admin session can save a price without OTP and see the backend price in details, cart and checkout.
