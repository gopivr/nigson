# Session report: PANDAGO-QUALIFICATION-004

Date: 25 November 2026 (America/New_York). Specification1.1.0. Status: IN_PROGRESS for real stage qualification and deployment acceptance. Phase: BP1 provider acceptance.

## Result

Provider requested new examples with only floor/door and extra delivery instructions in notes, plus a mandatory familiar pickup code. Inspection found that committed backend code already mapped frozen address line2 to recipient.notes, omitted sender/internal notes, and sent the last10 uppercase alphanumeric order-number characters as pickup_tasks.pickup_code. The missing customer/operator behavior was in the frontend: new checkout addresses always used line2:null, and operators could not see the short code.

Checkout now captures optional floor/door/instructions, preserves them as existing line2, shows saved instructions, resets them for a new address and enforces the existing200-character limit. Changes invalidate the address/quote. Saved-address editor uses matching guidance. Admin Orders list/detail displays the exact deterministic backend pickup code and list searching includes it. No generated new code, provider config change, migration, API/event contract or transaction-boundary change. Existing notes/code backend safeguards remain unchanged.

## Exact changed files

Backend repository:
- BACKEND_IMPLEMENTATION_MASTER.md
- IMPLEMENTATION_STATUS.md
- services/integration/src/test/java/com/nigson/integration/carrier/PandagoBookingPersistenceTest.java
- scripts/carrier/verify_pandago_qualification.py
- docs/runbooks/pandago-qualification.md
- docs/implementation/2026-11-25-PANDAGO-QUALIFICATION-004.md

Frontend repository:
- projects/shop/src/app/checkout/checkout.component.ts
- projects/shop/src/app/checkout/checkout.component.spec.ts
- projects/account/src/app/addresses/addresses.component.ts
- projects/admin-shop/src/app/sales/orders.component.ts
- projects/admin-shop/src/app/sales/orders.component.spec.ts
- docs/pandago-qualification.md

## Acceptance evidence and commands

- `python3 scripts/carrier/verify_pandago_qualification.py`: PASS39 native Java tests, zero failures/errors/skips (Commerce2 pickup derivation; Integration23 HTTP adapter,12 booking persistence,2 private commerce client). Disposable PostgreSQL/LocalStack plus loopback provider simulator, never real PandaGo. Existing local application data preserved by the harness. Evidence `.build/pandago-qualification004/native-approved.log` and its database-evidence directory.
- Initial sandbox invocation failed to access the Docker socket before creating test containers; retained `.build/pandago-qualification004/native.log`. Approved isolated execution succeeded. A diagnostic process listing was also denied by the sandbox; unnecessary for acceptance and not retried.
- Frontend: `npx vitest run --config vitest.checkout.config.mjs projects/shop/src/app/checkout/checkout.component.spec.ts`: PASS26.
- Frontend: `npx vitest run --config vitest.cancellation.config.mjs projects/admin-shop/src/app/sales/orders.component.spec.ts`: PASS9. (Checkout config does not include Admin Orders, so separate configuration used.)
- Frontend: `npx ngc -p projects/shop/tsconfig.app.json --noEmit`, corresponding admin-shop and account commands: PASS.
- `python3 -m unittest discover -s tests/contract -p test_fulfillment_contract.py`: PASS8.
- `git diff --check` in both repositories: PASS.

Tests verify blank notes never fall back to customer/order information, bad/missing codes prevent confirmation, receiver-only exact notes, real wire pickup_tasks placement, duplicate/timeout uncertainty safeguards, captured checkout instructions, saved/new address behavior and operator-code parity. No browser or deployed acceptance claimed.

Build: Java reactor PASS via scoped tests; three Angular template/compiler checks PASS. Full production asset build NOT_RUN (scoped template/compiler checks cover this edit). Security/compatibility: no new authorization/API/data access; existing owner/private context and no-HTTP-under-lock assertions passed. Migrations: unchanged. No external booking/payment performed.

Last successful backend commit: `301aecad87da070eb715724f0cbe5d337bcb78a3`; frontend: `4f26acd3445aab8df3b101f19185e5c9f24978ef`. No acceptance commit created.

## Remaining inputs and next task

Deploy/restart the reviewed sources and create a NEW real PandaGo stage order using approved Nigson test customer/product/stock/delivery details and registered stage configuration. The user was asked which approved order/details should be used; no answer or stage booking is recorded in this session. No new PandaGo order IDs can be supplied from offline fixtures. Follow docs/runbooks/pandago-qualification.md to capture the real provider order ID, exact notes/code and provider review. Existing submitted orders are not retroactively changed. Genuine provider qualification, browser acceptance and deployed commit acceptance remain NOT_RUN. Next dependency-ready task: PANDAGO-STAGE-ACCEPTANCE, after deployment and approved test inputs.
