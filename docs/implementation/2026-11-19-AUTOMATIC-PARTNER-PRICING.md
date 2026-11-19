# Session report: AUTOMATIC-PARTNER-PRICING
Date: 2026-11-19, Asia/Calcutta
Specification: 1.1.0; ADR-030; status IN_PROGRESS.

User decision: automatic pricing, no Personal/Business switch. This increment implements the prerequisite Admin pricing workflow only. Automatic customer pricing and cart repricing are NOT implemented or enabled by it.

Admin product editing now has Business selling prices for saved SKUs with pricing authority. Explicit WHOLESALE/DISTRIBUTOR unit prices and quantity tiers can be saved as a draft and published separately. Published source books are immutable. Copying preserves entries for other SKUs; replacing this SKU replaces only its own tiers. Publication does not silently reassign any organization. Old disabled price placeholders point to the working section. Editing is restricted to existing normal-Admin or combined Sales/Finance pricing capabilities; product-page access permissions remain unchanged.

Approved partner application details now expose business price assignment using acknowledged organizationId. Only active currently effective books of the proper segment are offered. The current assignment version and scoped minimum rules are read before PUT; null rule fields are omitted, numeric strings are normalized exactly, existing quantity/order rules are preserved, and stale updates require reload. Unknown outcomes reuse operation keys; success is shown only on backend acknowledgment. Component generation checks prevent old assignment responses from overwriting another business's screen. Assignment does not activate suspended access.

Backend API-219 GET /api/v1/staff/organizations/{id}/price-assignment?marketId=UUID returns a bounded snapshot with version, book ID and scoped minimum rules. API-067 and this read support persisted normal-login ADMIN or existing SALES authority. CSRF, idempotency, current If-Match, exact partner segment, audit, immutable books and suspension rules remain. No new database migrations, runtime data, prices or approvals were applied.

Changed files: pricing/PriceAssignmentService.java and PricingController.java; PricingPersistenceTest.java; scripts/contracts/pricing.py, generated Commerce OpenAPI, master API-219 entry, ADR-030 and status. Frontend new shared-ui business-pricing.service.ts, business-price-editor.component.ts, business-price-assignment.component.ts and business-pricing.spec.ts; shared-ui public-api.ts; admin.service.ts organization mapping; product-edit.component.ts; applications.component.ts; vitest.admin.config.mjs. Earlier task changes were preserved.

Verification: Commerce Java build and full PricingPersistenceTest + NormalAdminPricingTest passed (23 tests, zero skips; business-admin-tests.log). After read-decimal normalization and stronger no-MFA/suspension assertions, focused native test plus six normal-Admin tests passed (7 tests, business-admin-final.log). Angular Admin-shop ngc --noEmit passed. Initial full Admin suite passed 76 tests. Latest focused business-control tests recorded below. A Python generation attempt initially passed a schema reference object instead of its schema name; corrected before generation/check. A focused frontend test initially imported Angular components before the JIT compiler; corrected import order, failure evidence retained. No asset bundles generated.

Commands: Java 25/Maven 3.10 offline, -pl services/commerce -am -Dtest=PricingPersistenceTest,NormalAdminPricingTest test; focused -Dtest=PricingPersistenceTest#currentAssignmentSupportsVersionedNormalAdminPricingWithoutActivatingAccess,NormalAdminPricingTest. Node ngc -p projects/admin-shop/tsconfig.app.json --noEmit; node node_modules/vitest/vitest.mjs run --config vitest.admin.config.mjs, then same command filtered to projects/shared-ui/src/lib/business-pricing.spec.ts. Bundled Python scripts/contracts authorization.main() --check; both repositories git diff --check.

Evidence: .build/wishlist-pg/business-admin-compile.log, business-admin-tests.log, business-admin-final.log, business-admin-ui-tests.log, business-editor-final.log (initial import failure), business-editor-final2.log. Native PostgreSQL is isolated on port 56432 and will be stopped after checks. No running application data changed. No commit created; actual browser acceptance NOT_RUN.

Current limitations: the selectors show the latest 100 books, explicitly labeled in the editor. No automatic reassignment on publication. Business pricing access is still not automatically selected by the public storefront. Do not claim the full customer purchasing flow is complete.

Next dependency-ready work: backend automatic account resolution with suspension/missing-price/ambiguous-account failure rules; minimum-quantity-aware product price previews; atomic reprice/review of existing carts; checkout enforcement and frozen-order preservation; then full browser acceptance. No customer-facing switch is planned.

Final focused frontend checks: four business pricing tests PASS after import fix; draft preservation, unknown retry and stale assignment checks included. Contract and both diff checks PASS. Isolated PostgreSQL stopped cleanly.

Superseding UI increment: see 2026-11-19-SIMPLE-PRODUCT-PRICES.md. Routine draft/publish/manual assignment controls were replaced with the requested single commercial-price form. Automatic storefront/cart integration remains pending.

Customer integration implemented in the follow-up report 2026-11-19-AUTOMATIC-PARTNER-CUSTOMER-PRICING.md: automatic context and standard prices, minimum-aware previews, cart refresh/review and checkout guards. Earlier statements that storefront integration was pending describe the previous increment; current regression/browser acceptance status is in that report.
