# Session report: ORDER-RETURNS-001

Date: 24 November 2026. Specification 1.1.0; ADR-034. Task status: IN_PROGRESS for deployment/restart, real Finance refund provider execution and browser acceptance; local implementation and scoped verification complete. Current engineering/product phase: EP05 / BP1.

Summary: implemented the committed customer-return feature end to end. A customer with a proven-delivered order can request one product and an exact scaled quantity per request. Operations reviews the frozen policy revision, then records physical receipt with a required external reference and an explicit restock decision. Finance can separately link a received return to exactly one existing Paystack refund request through the existing two-person approval and worker path. The customer order page and Admin Sales page now drive these owner APIs; no automatic refund, return window, return fee, reverse-pickup/carrier booking or provider mock was added.

Contracts/migrations/events affected: API-237..243 and the closed `OrderReturnsDTO`, `ReturnLineDTO`, `ReturnDTO`, `ReturnRequestInput`, `ReturnReviewInput`, `ReturnReceiptInput` and `ReturnRefundInput` schemas in `contracts/openapi/commerce.yaml`; narrative entries in `docs/API_CONTRACT.md`. Existing Commerce038 `commerce.return_request` and its rollback already existed at HEAD; this change consumes them. Shared audit states gain `RETURN_REQUEST` resource and `RETURN_REQUEST`/`RETURN_REVIEW`/`RETURN_RECEIPT`/`RETURN_REFUND_REQUEST` actions. No new event type, provider dependency or cross-owner access. API numbering keeps ADR-034's reserved 237..243; the unrelated staff-account lookup that had claimed `API-237` was renumbered to `API-244`.

Acceptance criteria: AC-ORDER-RETURNS-001 scoped local evidence below. Native tests cover closed request/review/receipt/refund validation, owner and stale-version denial, current-role recheck, delivered-quantity availability, approval/rejection, idempotent physical receipt with restock, exactly one Finance refund linkage, read visibility, duplicate-receipt rejection and populated rollback refusal. Frontend specs cover customer loading/request/quantity bounds and Admin review/receipt/refund mapping, retry keys and error display.

Commands executed and results:

- `ENV=test python3 scripts/order/verify_returns.py`: native isolated PostgreSQL supply plus scoped Maven tests `ReturnValuesTest,ReturnPersistenceTest`. Evidence `.build/order-returns/verify.log` and `.build/order-returns/database-evidence`. `BuildCompatibilityTest` is intentionally excluded because its module-cycle check already fails at HEAD from the separate cancellation feature (`order` ↔ `payment`); the same failure was reproduced without return changes.
- `python3 -m unittest tests.contract.test_order_contract tests.contract.test_party_contract tests.contract.test_authorization_contract tests.contract.test_database_catalog -q`: PASS9 after regenerating OpenAPI and catalog expectations. The authorization/database counts move 136→137 for the additive return paths/table; party/authorization route sets include `API-244`.
- `python3 scripts/contracts/authorization.py` and `python3 scripts/database/generate.py`: PASS; reviewed artifacts match the generators.
- Frontend `npx vitest run --config vitest.cancellation.config.mjs`: PASS25 across return and existing cancellation specs.
- `npx ng build account` and `npx ng build admin-shop`: PASS.
- `git diff --check` in both repositories: PASS.

Build: PASS scoped backend test compile/run and both affected Angular production builds.
Tests: PASS6 isolated backend, PASS9 selected contracts/database, PASS25 frontend. The entire product suite was not run.

Security/migration/compatibility: owner or current persisted OPERATIONS/FINANCE authority is rechecked inside the transaction; mutations require CSRF, exact `If-Match` and durable idempotency. Quantity reservation happens under the order lock and prior non-rejected requests plus net legacy physical returns cap availability. Restock uses the existing idempotent `StockLedger.PHYSICAL_RETURN`; non-restock receipt leaves saleable stock unchanged. Finance refund linkage creates no money movement and preserves existing approval, stable reference, worker, webhook and UNKNOWN reconciliation. Read surfaces are bounded to 100 and fail closed on overflow. No applied migration, checksum, `.env` content or unrelated work was modified. Populated return rollback refuses before dropping evidence.

Open issues/activation inputs: deployment must apply/rebuild/restart and preserve existing return table state. Real refund execution remains blocked on a real Paystack integration ID in the local TEST account, and local webhook reception is unavailable. PandaGo staging approval expiry and disabled Google geocoding billing can block delivery/return-path acceptance independently. Real staff/customer browser acceptance and a release acceptance commit remain pending; no deployed database, configuration or real provider operation was changed by this session.

Files changed (backend paths relative to `nigson/`):

- `BACKEND_IMPLEMENTATION_MASTER.md`
- `IMPLEMENTATION_STATUS.md`
- `contracts/openapi/commerce.yaml`
- `docs/API_CONTRACT.md`
- `docs/implementation/2026-11-24-ORDER-RETURNS-001.md`
- `scripts/contracts/authorization.py`
- `scripts/contracts/order.py`
- `scripts/order/verify_returns.py`
- `services/commerce/src/main/java/com/nigson/commerce/order/OrderConfiguration.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/ReturnController.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/ReturnService.java`
- `services/commerce/src/test/java/com/nigson/acceptance/order/ReturnPersistenceTest.java`
- `services/commerce/src/test/java/com/nigson/acceptance/order/ReturnValuesTest.java`
- `shared/common-problem/src/main/java/com/nigson/shared/problem/AuditValues.java`
- `tests/contract/test_authorization_contract.py`
- `tests/contract/test_database_catalog.py`
- `tests/contract/test_order_contract.py`
- `tests/contract/test_party_contract.py`

Files changed (frontend paths relative to `nigson-micro-frontend/`):

- `projects/account/src/app/orders/order-detail.component.ts`
- `projects/account/src/app/orders/order-detail.component.spec.ts`
- `projects/admin-shop/src/app/sales/orders.component.ts`
- `projects/admin-shop/src/app/sales/orders.component.spec.ts`
- `projects/shared-ui/src/lib/admin/admin.service.ts`
- `projects/shared-ui/src/lib/order.service.ts`
- `projects/shared-ui/src/lib/order.service.spec.ts`
