# Session report: PANDAGO-MERGE-005

Date: 25 November 2026, America/New_York. Specification1.1.0. Phase: BP1 provider acceptance. Status: scoped offline revalidation PASS; deployment/provider acceptance and acceptance commit remain pending.

## Findings and changes

User requested revalidation after accepting incoming main changes. Backend HEAD f389bd915dd64adc45211aa2813692dfda1420c0 and frontend HEAD c193053db20ead57395a6605fde7746efc7ba8d7 were clean at start. Checkout instruction capture and line2 preservation, immutable Commerce address/context, mandatory pickup code, recipient-only notes, supported payment/scheduling, durable booking/cancellation and operator code display survived. No scanned merge conflict markers were found.

Found a real merge regression in Admin Orders: incoming main replaced the old status dropdown with tabs, but the merge retained this.statusFilter() after removing that signal. Angular compilation failed TS2339 and a real constructed component failed when filteredOrders was read. The merge also removed incoming product/SKU search. Removed the obsolete dropdown filter and combined order/customer/product/SKU/pickup-code search under the retained tabs. Added four regression cases covering initial list evaluation, signal search, tabs and cancellation exclusion for shipment create/dispatch/delivery. No incoming fulfillment controls or cancellation guards were removed. Original list failed3 new regression cases; all4 new cases now pass.

The broader test run exposed assertions still expecting the policy before main commit32d5d9b2064027249940e71f9c0588e1c95b06d8. IMPLEMENTATION_STATUS.md explicitly records password-only staff login and the shared MFA guard change; docs/runbooks/staff-authorization.md explicitly permits assigning an ACTIVE registered party its first role. Updated test expectations to those existing decisions, not production policy: real HTTP role promotion verifies the persisted FINANCE assignment; old-MFA current-admin access is accepted; refund request/replay works with absent/old MFA while current roles, revocation and separate approver checks remain; metadata activation with empty units is still rejected by domain validation (422 PLATFORM_RULE_VIOLATION); a current operator with old MFA cannot retry an outdated job version or create another side effect.

A subsequent HTTP assembly run reached a catalogue assertion that searched the entire public JSON body for the substring status, now legitimately present in merged SKU stock.status. Changed that assertion to reject the private top-level product lifecycle status structurally, retaining all existing version/price/checksum/objectKey/label redaction checks. Production DTO serialization is unchanged.

## Exact changed files

Backend:
- IMPLEMENTATION_STATUS.md
- services/commerce/src/test/java/com/nigson/acceptance/AuthorizationHttpAcceptance.java
- services/commerce/src/test/java/com/nigson/acceptance/catalogue/CatalogueHttpAcceptance.java
- services/commerce/src/test/java/com/nigson/acceptance/configuration/ConfigurationHttpAcceptance.java
- services/commerce/src/test/java/com/nigson/acceptance/payment/PaymentPersistenceTest.java
- services/commerce/src/test/java/com/nigson/acceptance/provider/JobHttpAcceptance.java
- docs/implementation/2026-11-25-PANDAGO-MERGE-005.md

Frontend:
- projects/admin-shop/src/app/sales/orders.component.ts
- projects/admin-shop/src/app/sales/orders.component.spec.ts
- docs/pandago-qualification.md

Contracts/migrations/events: unchanged. No production Java, credentials, provider configuration, application data or external DNS/infrastructure changes. No architecture/owner boundary change requiring an ADR.

## Commands and evidence

All native tests use scripts/platform/verify.py with disposable PostgreSQL/LocalStack and loopback provider fixtures, preserving local application resources. Exact one-shot runners and full output are in .build/pandago-merge005.

1. `python3 .build/pandago-merge005/verify.py`: initial broad reactor FAIL,111 Commerce tests executed,109 passed and2 failed. SecurityAssemblyTest stopped at the former retail-promotion404 assertion; PaymentPersistenceTest stopped at former stale-MFA denial. Integration was skipped by Maven reactor failure. Both are pre-existing merged-policy/test mismatches; failure history retained in native.log. Passed Commerce classes include pickup code, quote/config/rates/reconciliation, order/cancellation persistence, result states and physical shipments.
2. `python3 .build/pandago-merge005/verify-integration.py`: PASS61/no skips, covering PandagoAdapterTest23, PandagoAutomaticAddressTest8, PandagoBookingPersistenceTest12, PandagoConfigurationTest5, PandagoCommerceClientTest2 and PandagoCancellationPersistenceTest11. Evidence integration-native.log and integration-database-evidence.
3. `python3 .build/pandago-merge005/verify-authorization.py`: after first test alignment, PaymentPersistenceTest PASS34/no skips; HTTP assembly still failed at old configuration-MFA assertion (expected403, actual422 domain rejection). Evidence authorization-native.log. Updated that assertion and the equivalent operations retry assertion to preserve actual domain/version/side-effect checks.
4. `python3 .build/pandago-merge005/verify-security.py`: FAIL at the stale catalogue blanket-status assertion; authorization/configuration HTTP progressed past their corrected assertions. Evidence security-native.log. Follow-up `python3 .build/pandago-merge005/verify-security-final.py`: final result below; evidence security-final-native.log and security-final-database-evidence.
5. Frontend `npx vitest run --config vitest.checkout.config.mjs projects/shop/src/app/checkout/checkout.component.spec.ts`: PASS26 (frontend-checkout.log).
6. Frontend `npx vitest run --config vitest.cancellation.config.mjs`: PASS31 after fix (frontend-cancellation-fixed.log). Before adding constructor-based coverage27 existing tests passed despite the bug; new coverage correctly reproduced3 failing cases before the fix (admin-regression-before.log).
7. Frontend `npx ngc -p projects/shop/tsconfig.app.json --noEmit`, corresponding admin-shop and account commands: PASS. Original admin compiler TS2339 retained in compiler-admin.log; final compiler-admin-fixed.log is clean.
8. unittest discovery for test_fulfillment_contract.py, test_order_contract.py, test_payment_contract.py, test_provider_contract.py: PASS21, with an explicit nonzero-case assertion for each pattern (contracts-complete.log). Initial guessed cancellation filename matched0 tests; not counted as validation.
9. `git diff --check` in both repositories: PASS.

No tests contact real PandaGo/Paystack. No skips in the native classes that executed. This is scoped verification, not a claim the whole repository test suite or an actual deployment passed. Frontend production assets/browser checks and stage provider submission NOT_RUN. No schema change; fixture migrations exercised by each isolated native runner. No acceptance commit created.

Next dependency-ready task: deploy the reviewed commits and perform a new approved PandaGo stage qualification order per docs/runbooks/pandago-qualification.md. Actual provider order IDs and acceptance remain separate from offline fixture results.

## Final result

`python3 .build/pandago-merge005/verify-security-final.py` PASS: SecurityAssemblyTest1/no skips, including the assembled HTTP acceptance calls for authorization, audit, party, wishlist, rewards, configuration, catalogue, files, provider jobs, notifications, phone, inventory, markets/pricing, carrier delivery, cart, physical fulfillment and orders (subject to existing fixture gates). Security chain/private route and readiness checks also pass. No assertions or production behavior were disabled.

Final cohort:172 distinct selected native backend tests pass across scoped runs:76 unchanged Commerce cases from the first run,34 payment cases from authorization rerun,61 Integration cases, and1 final HTTP security assembly. There was no final all-in-one rerun; initial failures and their corrected follow-up evidence are retained. Frontend57 tests pass (26 checkout +31 cancellation/order/admin);21 contracts pass; three Angular compilers pass; final whitespace checks pass. No remaining failing check in this selected cohort. Full repository tests, production asset bundles, real browser and stage provider checks were not executed.

No new backend production behavior was introduced. Only the Admin Orders filtering/search regression required a production source fix. Authorization/stock changes from main were preserved and their stale acceptance assertions updated after reviewing the merged implementation and documented decisions. No acceptance commit created in this session.
