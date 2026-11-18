# Session report: PANDAGO-DELIVERY-002

Date: 2026-11-17 America/New_York (verification logs use UTC as indicated).
Specification: 1.1.0; explicit user delivery/payment choices captured in ADR-028.
Task status: IN_PROGRESS — option/payload implementation verified offline; automatic shipment execution is an existing unfinished workflow, and no acceptance commit was created.
Phase: EP05 / BP1 checkout and fulfillment.

## Result

Resumed existing uncommitted checkout and PandaGo edits. Fixed the missing scheduledInput test helper that prevented Integration test compilation. Immediate and scheduled delivery are independent of prepaid and COD selection. PandaGo payloads explicitly send preordered_for null or Unix seconds and payment_method PAID or CASH_ON_DELIVERY. Added all-four-combinations HTTP assertions. Preserve HOSTED in the application contract for prepaid checkout; private shipment context maps it to PAID.

COD creates no Paystack attempt, remains UNPAID, atomically allocates inventory, enters PROCESSING and converts its cart. Stable replay performs no second allocation. Emit order.processing rather than order.pending to avoid triggering payment reconciliation. Prepaid verification requirements remain. Freeze schedule/payment in carrier snapshots, reject stale scheduled checkout, return VALIDATION_ERROR on malformed dates, allow the configured PandaGo outlet to supply origin, and retain choices in private shipment context. Update public/private OpenAPI generators and tests. Checkout success details must load before COD success is displayed.

## Verification

- PASS: `mvn -pl services/commerce,services/integration -am test -Dtest=PandagoAdapterTest,PandagoAutomaticAddressTest,CarrierValuesTest,OrderValuesTest,HttpCarrierRatesTest,FulfillmentValuesTest -Dsurefire.failIfNoSpecifiedTests=false` — 47 tests, no failures/errors/skips. Evidence `.build/pandago-delivery-002/provider-tests.log`.
- PASS: `npx vitest run --config vitest.checkout.config.mjs` in frontend — 26 tests across seven files. Evidence `.build/pandago-delivery-002/ui-tests.log`.
- PASS: `NG_BUILD_MAX_WORKERS=2 npx ng build shop --configuration development` in frontend. Evidence `.build/pandago-delivery-002/ui-build.log`.
- PASS: `python3 -m unittest discover -s nigson/tests/contract -p 'test_*contract.py'` from workspace — 64 tests. Evidence `.build/pandago-delivery-002/contracts.log`.
- PASS: `python3 scripts/contracts/authorization.py --check`, `python3 scripts/contracts/integration.py --check` in backend; both repositories `git diff --check`.
- PASS: `python3 scripts/order/verify.py` — 30 tests across Commerce/Experience/Integration, no failures/errors/skips, including 21 order persistence tests and actual authenticated HTTP security acceptance. Fresh/repeat owner migrations and preserved local container state passed. COD test checks stock allocation, cart conversion, no Paystack HTTP/attempt, stable replay, processing event and absence of payment-reconciliation event. Evidence `.build/pandago-delivery-002/order-acceptance.log`.

## Failure history

Initial sandbox HTTP tests could not bind localhost (Operation not permitted); rerun with approved localhost access passed. Integration compilation exposed missing scheduledInput helper in the interrupted changes; repaired. An initial cart edit accidentally referenced cod in uncertainExpired; fixed before successful verification. Two patch attempts used a redundant repository prefix and made no changes; reran from workspace. Initial Angular build aborted with exit 134 under sandbox process restrictions; approved build with two workers passed. No test assertions were weakened to fabricate provider success.

## Contracts and activation

API-037/181/182/195 and private API-119 carry the new option/nullability fields; COD uses existing EVT-003. No database migration or new owner boundary. No real provider request, deployment or credential change was performed. Existing .env modifications predate this work and were preserved.

Automatic shipment booking is currently disabled; no production caller invokes PandagoAdapter.confirm. Booking-result/dispatch also expects a label, which needs a PandaGo-specific no-label workflow. Cash remittance settlement/reconciliation is not implemented. Adapter HTTP tests do not establish live courier booking readiness. Next dependency-ready work: durable PandaGo booking and UNKNOWN reconciliation plus no-label shipment lifecycle acceptance. This remains IN_PROGRESS rather than claiming end-to-end delivery completion.

Last successful backend source commit: cbc716243908dd5745cf8505927c2cbf3de32985 (pre-existing).
Last successful frontend source commit: fe4721ccddde664b70ba81ae5b44939fa3408d89 (pre-existing).
New acceptance commit: NONE.

## Files changed (including resumed edits)

- `IMPLEMENTATION_STATUS.md`
- `contracts/openapi/commerce.yaml`
- `contracts/openapi/integration.yaml`
- `scripts/contracts/fulfillment.py`
- `scripts/contracts/integration.py`
- `scripts/contracts/order.py`
- `services/commerce/src/main/java/com/nigson/commerce/cart/JdbcCartCheckout.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/CarrierRates.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/CarrierShipmentContext.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/DeliveryQuoteService.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/DeliveryQuoteValues.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/HttpCarrierRates.java`
- `services/commerce/src/main/java/com/nigson/commerce/fulfillment/JdbcShipmentRepository.java`
- `services/commerce/src/main/java/com/nigson/commerce/inventory/JdbcStockLedger.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/JdbcOrderRepository.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/OrderController.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/OrderService.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/OrderValues.java`
- `services/commerce/src/test/java/com/nigson/acceptance/carrier/CarrierDeliveryHttpAcceptance.java`
- `services/commerce/src/test/java/com/nigson/acceptance/fulfillment/FulfillmentValuesTest.java`
- `services/commerce/src/test/java/com/nigson/acceptance/order/OrderPersistenceTest.java`
- `services/commerce/src/test/java/com/nigson/acceptance/order/OrderValuesTest.java`
- `services/integration/src/main/java/com/nigson/integration/carrier/CarrierValues.java`
- `services/integration/src/main/java/com/nigson/integration/carrier/PandagoQuoteService.java`
- `services/integration/src/main/java/com/nigson/integration/providers/PandagoAdapter.java`
- `services/integration/src/test/java/com/nigson/integration/carrier/PandagoAutomaticAddressTest.java`
- `services/integration/src/test/java/com/nigson/integration/providers/PandagoAdapterTest.java`
- `tests/contract/test_fulfillment_contract.py`
- `tests/contract/test_order_contract.py`
- `tests/contract/test_provider_contract.py`
- `docs/adr/ADR-028-pandago-delivery-timing-and-cod.md`
- `docs/implementation/2026-11-17-PANDAGO-DELIVERY-002.md`
- `../nigson-micro-frontend/projects/shop/src/app/checkout/checkout.component.spec.ts`
- `../nigson-micro-frontend/projects/shop/src/app/checkout/checkout.component.ts`
- `../nigson-micro-frontend/projects/shop/src/app/checkout/order-success.component.ts`
