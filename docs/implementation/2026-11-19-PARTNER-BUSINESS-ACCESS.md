# Session report: PARTNER-BUSINESS-ACCESS
Date: 2026-11-19 (Asia/Calcutta)
Specification: 1.1.0; owner boundaries unchanged; ADR-030
Task status: IN_PROGRESS — this is a pricing-storage foundation, not a completed partner workflow.

Implemented: WHOLESALE is accepted by price-book request/query and repository validation. Existing decimal, tier, role, idempotency, revision and activation validation remains. Additive migration 028 expands the database segment constraint; applied migration checksums are preserved. Rollback refuses wholesale records. Existing distributor price assignments still reject wholesale books, preventing accidental access before the typed approval workflow exists.

User-confirmed policy: one partner type per customer; Admin explicitly enters prices and minimum quantities; business purchasing blocked until valid prices are assigned. These approval/purchasing policies are recorded but not yet implemented by this increment.

Changed paths: pricing/PricingValues.java, pricing/JdbcPriceBookRepository.java; PricingValuesTest.java and PricingPersistenceTest.java; Commerce changelog 028 SQL/rollback/YAML and BP1 release include; scripts/database/generate.py; scripts/contracts/pricing.py; generated schema-manifest and Commerce OpenAPI; master section 10; ADR-030; IMPLEMENTATION_STATUS.md.

OpenAPI generation also synchronizes existing ADR-027 normal-admin pricing descriptions and retailBindings to the already implemented runtime. No additional authorization relaxation was introduced.

Verification: Java 25 Commerce and dependencies compiled. Initial credential-free run: 17 passed, 16 database tests skipped. Database/catalog and authorization contract generator --check PASS. Isolated PostgreSQL 18 upgrade of previous BP5 schema PASS (028 applied); empty rollback and reapply PASS. The first database run failed because rollback verification overlapped the tests, temporarily restoring the old segment constraint; the old isolated fixture also lacked its original audit INSERT grant. Reapplied 028, restored only the original test-database audit grant and reran sequentially. Original failure log retained at .build/wishlist-pg/partner-tests.log; final results recorded below.

No running application data, credentials, business prices or approvals were changed. No frontend code changed. Build outputs and logs remain under ignored .build. No commit created.

Remaining: typed wholesaler application and account status UI; independent review and acknowledged typed entitlement activation; transactional one-partner-type invariant across concurrent approvals; matching price-book assignment; Admin pricing UI; personal/business shopping context throughout listing/detail/cart/checkout; suspension/replay/race/security/browser acceptance. Existing contact leads must not be treated as approved wholesalers. Affiliate is excluded.

Final verification: 33 tests passed, 0 failures, 0 errors, 0 skips (.build/wishlist-pg/partner-tests-final.log). Populated rollback correctly refused with 'Wholesale books exist: retain segment support'; the synthetic row was inserted in a transaction that rolled back on connection close. Isolated PostgreSQL stopped cleanly. git diff --check PASS. Application/browser acceptance NOT_RUN because the complete workflow is still pending. Last successful commit for this task: NONE. Next dependency-ready task: typed Experience wholesaler applications and transactional Commerce exclusive partner entitlement activation.

## Continuation: typed partner applications and approval (19 November 2026)
Status: IN_PROGRESS; engineering verification underway. This extends the foundation above; the full Admin pricing and storefront account-selection UI is not complete.

Both forms now submit authenticated, typed application records using the existing signed Commerce-to-Experience route. A clean owned registration document is required. Wholesale is no longer a contact enquiry, and distributor submission no longer depends on public enquiry/reCAPTCHA intake. Submitted business details are retained for staff review. Existing contact enquiries remain leads and are never automatically approved. Unknown submission retries keep their operation key while the form remains open, and changed details cannot reuse the unconfirmed operation.

Experience migration 043 adds partner_type (legacy default DISTRIBUTOR) and bounded business_details. Typed status survives submit/list/detail/review/acknowledgment. Independent Sales review and approval remain; approval rechecks current scan eligibility before persisting new activation intent. The post-commit private command carries partnerType. Commerce migration 046 adds partner_type to the acknowledged entitlement. Activation locks the current customer row and rejects an opposite partner type across existing business memberships, including suspended entitlements; an organization also cannot acquire a conflicting type. No staff roles are granted. Lock contention returns a retryable problem. Pricing assignments and resolution now require the matching approved segment, and missing assignments still fail closed. Wholesale must not inherit distributor-only promotions.

Frontend: verified proof control for both forms; correct wholesaler classification, tab and review actions in Admin; retained business details displayed separately from review notes; application status in customer profile. Existing leads remain explicitly lead workflows. No retail/wholesale/distributor prices, quantities, provider configuration or real approvals were invented or changed.

New owner migrations preserve applied migration SQL. Both rollback scripts refuse wholesale records; Experience rollback also refuses non-null retained business details. Contract generators preserve the existing decisionReason field and synchronize the existing document type property with the runtime DTO. A duplicate Commerce release include found during verification was corrected before reapplying migration; no deployed checksum was cleared.

Evidence: .build/wishlist-pg/typed-commerce-migration-fixed.log and typed-experience-migration.log; initial failed runs typed-tests.log (missing column because duplicate include prevented migration), typed-tests-fixed.log (test changed its key while reusing a command ID), and final typed-tests-final.log. Frontend form and Admin checks recorded separately. Final counts and migration checks will be recorded below.

Remaining after this increment: Admin business price-book editing/assignment interface and personal/business shopping context through the storefront/cart/checkout, then authenticated browser acceptance. Approval alone does not expose business prices in the current retail storefront.

### Verified outcome of the application/approval increment
- Commerce: 38 tests passed, zero failures/errors/skips, including opposite-type concurrency, required matching prices, minimum quantity, suspension and legacy retail/distributor regressions (`typed-tests-final.log`).
- Experience: final 10 tests passed, zero failures/errors/skips (`typed-experience-final.log`). Explicit Commerce rule denial returns to UNDER_REVIEW with audit; ambiguous timeout stays pending. Together, 48 distinct backend tests pass.
- Frontend: 16 form/service/reCAPTCHA tests pass (`forms-final.log`); 72 Admin tests pass (`admin-final.log`). Customer profile, marketing and Admin-shop Angular type/template checks pass with --noEmit; no asset bundles were generated.
- Both owner migrations apply to previous test schemas and the additional isolated qualification database. Empty typed-column rollback succeeds inside a rolled-back transaction; populated rollback is refused for both owners. Original failure evidence retained. Contract/catalog generator --check and both repository diff checks pass.
- Isolated PostgreSQL stopped cleanly. No running application database was migrated, no real application was approved, no price or role data changed, and no commit was created. Browser/provider qualification remains NOT_RUN.

To check this application stage after rebuilding/restarting the backend and frontend: sign in as a customer, upload genuine proof and wait for CLEAN, submit a wholesaler or distributor application, inspect the matching Admin tab, complete review with one Sales-authorized staff account and approve with a different one, then refresh customer profile status. The one-partner-type rule is enforced by Commerce even if requests race. Approved access with no valid assigned prices cannot make business purchases through the organization APIs. The current storefront still uses personal retail context until the next UI increment.

Next dependency-ready task: business price-book editing and explicit organization assignment UI, followed by personal/business context selection and minimum-quantity-aware storefront pricing, cart and checkout integration. Overall task remains IN_PROGRESS; do not describe this as an end-to-end completed partner purchasing feature.

Recovery follow-up: see 2026-11-19-PARTNER-ACTIVATION-RECOVERY.md. Local entitlement configuration, preflight and original-approver saved-command retry are implemented; running-stack acceptance remains pending.
