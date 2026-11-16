# Session report: LOCAL-TEST-CHECKOUT

Date/time: 2026-11-15T17:50Z
Specification/architecture version: 1.1.0
Task status: DONE locally for checkout and local TEST settlement (production
payment activation, webhooks and refund execution remain BLOCKED; see the
follow-up section below)
Current engineering/product phase: BP1 commerce order/payment, bootstrap payments disabled
Summary: The local storefront can now complete `POST /api/v1/checkout/attempts`
against real local services. Both a located address and the storefront's
postal-only payload (resolved by server-side Google geocoding) get a real
PandaGo SANDBOX delivery quote and a real checkout quote; the checkout quote
replays without `CHECKOUT_REPRICE_REQUIRED`; the order is created PENDING with
stock reserved and a Paystack **TEST** hosted checkout URL is returned. Hosted
checkout now returns to the storefront and local TEST settlement verifies the
payment. Webhooks, refund execution, delivery booking and production activation
remain intentionally off or blocked. The initial order was cancelled through
API-040 with the documented If-Match refresh, releasing stock and expiring the
cart.

## Request addressed

Drive the local checkout end to end with real providers, seeding only local
data and never fabricating production approvals. Local checkout may use Paystack
TEST only; no live money moves and no PandaGo booking is created.

## Design decisions

- Checkout activation is `NIGSON_ORDER_CHECKOUT_ENABLED=true` with
  `NIGSON_ORDER_PAYMENT_MODE=TEST`. `NIGSON_PAYMENT_ENABLED` stays unset, so no
  webhook receiver, refund worker, receipt processor or payment polling bean is
  registered. (Superseded for local settlement by the follow-up below.)
- Approval reference `user-approved-local-test-checkout-20261115` records the
  explicit user approval of 2026-11-15. It replaces the previous local
  "quote-only, no payment authorized" reference for local provider activation.
- Merchant binding `nigson-local-paystack-test` is a **local label only** for the
  TEST key. It must be replaced with the real Paystack merchant/account reference
  before payments or webhooks are enabled.
- Stock is reserved at the seeded local location
  `dcbce681-f763-5536-90ab-5835f8f1ae7f` (`SYN-LAGOS`).
- Cancellation keeps the documented optimistic-concurrency contract: If-Match is
  validated against the version the client read, payment inspection may change
  the financial version, and the client refreshes on 412 with a new command key
  (`docs/runbooks/orders.md`). The smoke follows that contract instead of
  weakening the version guard.

## Root causes fixed

1. **Checkout quote replay rejected every located address.** `commerce.checkout_quote`
   stores `address_snapshot` as jsonb; reading it back turns JSON decimals into
   floating-point nodes, so raw node equality
   (`EventCodec.JSON.valueToTree(address).equals(row)`) was type-sensitive and
   failed even when the address was unchanged. Comparison now uses the canonical
   `EventCodec.hash` on both sides.
2. **Paystack verification rejected a valid TEST transaction.** This TEST
   account passes processing fees to the payer, so Paystack reports
   `amount > requested_amount` (for example requested `1302500` → amount
   `1332488`). `verify` compared only `amount` to the order's exact kobo and threw
   `PAYSTACK_VERIFICATION_MISMATCH`, leaving the attempt UNKNOWN and blocking
   cancellation. `verify` now compares the base `requested_amount` (falling back
   to `amount`) and still requires `amount >= requested`.
3. **Checkout was closed by configuration.** `NIGSON_ORDER_CHECKOUT_ENABLED` was
   false and the overlay had no order payment mode, merchant binding or stock
   location, so checkout failed closed before any provider call.
4. **Cancellation 412 after inspection.** `payments.inspect` records an
   independently verified failure on the order (`payment_status`, version +1)
   before API-040 validates If-Match, so the first cancellation of an abandoned
   attempt returns 412 by design. Refreshing the order version and retrying with
   a new idempotency key cancels successfully; the second inspection is a no-op
   because the attempt is already FAILED and verified.

## Out-of-range delivery mapping (explicit user decision)

PandaGo answers an undeliverable destination with provider `422` ("order is
outside deliverable range") for `/orders/fee`, `/orders/time` and `/quotes`.
Previously every provider `4xx` became `503 PROVIDER_UNAVAILABLE`, which invited
a pointless retry. Per the explicit user decision of 2026-11-15, the read-only
quote paths now classify a non-uncertain provider `422` as
`422 PROVIDER_RULE_VIOLATION`; side-effecting calls and provider `5xx`/timeouts
keep their existing unavailable/UNKNOWN handling. `commerce` already forwards an
integration `422 PROVIDER_RULE_VIOLATION`, so the storefront receives the rule
outcome. `docs/API_CONTRACT.md` API-181 now lists the code; the supplied master's
API-181 error enumeration does not, which is recorded here as the governing
user decision delta.

## Files changed

- `nigson/.env` (local runtime activation only; **intentionally left uncommitted**
  so another live Google Maps key revision is not added to history)
- `nigson/.env.example`
- `nigson/docker-compose.pandago.yml`
- `nigson/services/commerce/src/main/java/com/nigson/commerce/payment/internal/infrastructure/external/paystack/PaystackAdapter.java`
- `nigson/services/commerce/src/main/java/com/nigson/commerce/pricing/JdbcCheckoutQuoteRepository.java`
- `nigson/services/commerce/src/test/java/com/nigson/acceptance/nigeria/NigeriaKoboTest.java`
- `nigson/services/commerce/src/test/java/com/nigson/acceptance/order/OrderFixture.java`
- `nigson/services/commerce/src/test/java/com/nigson/acceptance/order/OrderPersistenceTest.java`
- `nigson/services/integration/src/main/java/com/nigson/integration/carrier/PandagoQuoteService.java`
- `nigson/services/integration/src/test/java/com/nigson/integration/carrier/PandagoAutomaticAddressTest.java`
- `nigson/docs/API_CONTRACT.md` (API-181 error list gains `PROVIDER_RULE_VIOLATION`)
- `nigson/scripts/local/checkout_smoke.py` (new local-only smoke harness)
- `nigson/scripts/local/delivery_quote_smoke.py` (new located + postal + out-of-range delivery-quote smoke)

## Contracts/migrations/events affected

- APIs: API-037 (checkout attempts), API-040 (cancellation), API-026 pricing,
  delivery-quote and checkout-quote reads. No request/response shape changed.
  API-181 now documents `PROVIDER_RULE_VIOLATION` for a provider refused route
  (error vocabulary only; no OpenAPI shape change, so no regeneration needed).
- Tables: `commerce.checkout_quote`, `commerce.sales_order`,
  `commerce.inventory_reservation`, `commerce.inventory_balance`,
  `commerce.payment_attempt`, `commerce.order_status_history`. No migration.
- Events observed in the outbox: `order.pending.v1` (aggregate version 0) and
  `order.cancelled.v1` (aggregate version 3).
- No contract regeneration was required.

## Acceptance criteria and evidence

- `AC-COM-ORDER-001` / `AC-COM-PAYME-001` (local scope): checkout creates one
  PENDING order with a frozen quote, one reservation and one payment attempt;
  cancellation releases the reservation and expires the cart.
- Deterministic tests PASS (25 tests, 0 failures, 0 errors):
  `OrderValuesTest`, `OrderPersistenceTest`, `SecurityAssemblyTest`,
  `BuildCompatibilityTest`. New regressions:
  `verificationUsesBaseRequestedAmountWhenProviderAddsFees` and
  `locatedAddressCheckoutPreservesCanonicalSnapshotAcrossReplay`.
- Integration mapping tests PASS (6 tests, 0 failures, 0 errors) in
  `PandagoAutomaticAddressTest`: automatic address lookup, saved-coordinate
  retention, failed lookup isolation, provider `422` on `/orders/fee`,
  provider `422` on `/quotes`, and a `503` outage that must **not** be
  reclassified as a rule outcome.
- Real local authenticated BFF smoke PASS (exit 0):
  - delivery quote `201 PANDAGO / SANDBOX / NGN 26.00`
  - checkout quote `201`, grand total `13025.00`, quote hash present
  - checkout attempt `201`, `PENDING` / `PENDING`, order version 1,
    payment state `PENDING`, hosted URL host `checkout.paystack.com`
  - order cleanup first attempt `412` (documented inspect/version change), then
    `200 CANCELLED`, payment status `FAILED`, order version 3
  - temporary local identity, address and cart removed
- Postal-only address smoke PASS (exit 0), matching the storefront payload which
  sends no coordinates: located-address quote `201 PANDAGO / SANDBOX / NGN 26.00`
  and postal-only quote `201` after server-side Google geocoding. This closes the
  2026-11-14 `GEOCODING_ACCESS_DENIED` blocker; no geocoding mock is in use.
- Out-of-range smoke PASS (exit 0): an Ibadan address returns
  `422 PROVIDER_RULE_VIOLATION` through the real BFF and integration service, so
  the storefront can tell the customer the address is not deliverable instead of
  showing a retryable outage.
- Database state after the smoke:
  - order `01a1e058-4032-7782-ad0d-7eb00ecaa285`: `CANCELLED / FAILED / CANCELLED`,
    version 3
  - cart `EXPIRED`; reservation `RELEASED`; attempt `FAILED`, `TEST`,
    expected `13025.0000`, hosted URL present, verified
  - `inventory_balance`: `on_hand=250.000, reserved=0.000, allocated=0.000`
  - history `CANCELLED` row present; outbox `order.pending.v1` then
    `order.cancelled.v1`
  - no other `PENDING` order and no active reservation remains after the run
- Active container configuration (values only, secret redacted):
  `NIGSON_ORDER_CHECKOUT_ENABLED=true`, `NIGSON_ORDER_PAYMENT_MODE=TEST`,
  `NIGSON_ORDER_MERCHANT_ACCOUNT=nigson-local-paystack-test`,
  `NIGSON_PRICING_PROVIDER_MODE=TEST`, and `NIGSON_PAYMENT_ENABLED` absent.

## Commands executed

```text
python3 scripts/local/dependencies.py validate                       # PASS (configuration names only)
make run-core                                                        # PASS: commerce/experience/integration healthy
docker compose --project-name nigson-local --env-file .env \
  --env-file .local/pandago/account.env --file docker-compose.yml \
  --file docker-compose.pandago.yml --profile app build commerce      # PASS: BUILD SUCCESS
docker compose ... --profile app up --detach --wait --wait-timeout 180 commerce  # PASS: healthy
ENV=test JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  DOCKER_CONFIG=/private/tmp/nigson-public-image-fetch \
  DOCKER_HOST=unix:///Users/gopiravi/.docker/run/docker.sock \
  python3 scripts/order/verify.py                                     # PASS: 25 tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./mvnw -B -pl services/integration -am -Dtest=PandagoAutomaticAddressTest \
  -Dsurefire.failIfNoSpecifiedTests=false test                        # PASS: 6 tests
docker compose ... --profile app build integration                    # PASS: BUILD SUCCESS
docker compose ... --profile app up --detach --wait --wait-timeout 180 integration  # PASS: healthy
python3 scripts/local/checkout_smoke.py                               # PASS: exit 0
python3 scripts/local/delivery_quote_smoke.py                        # PASS: exit 0
```

Evidence:

- `.build/local-test-checkout/smoke.log`
- `.build/local-test-checkout/postal-quote.log`
- `.build/local-test-checkout/database.txt`
- `.build/local-test-checkout/order-verify.log`
- `.build/local-test-checkout/commerce-build.log`
- `.build/local-test-checkout/pandago-mapping-test.log`
- `.build/local-test-checkout/integration-build.log`

## Build

PASS. Commerce and integration images built and started healthy; the Java `order`
verification build and the integration mapping test build both reported
`BUILD SUCCESS`.

## Tests

PASS. 25 isolated order/payment/security tests and 6 integration carrier mapping
tests, 0 failures/errors. The smoke also exercises real Keycloak, BFF, Google
geocoding, PandaGo SANDBOX and Paystack TEST; it is local acceptance evidence,
not a provider qualification.

## Security/migration/compatibility checks

- `NIGSON_PAYMENT_ENABLED` is off: no webhook, refund, receipt or polling beans;
  no live charge is possible from the local stack.
- Paystack key mode is enforced by `PaystackAdapter` (`sk_test_` in TEST,
  `sk_live_` in LIVE); synthetic keys are rejected outside MOCK.
- Hosted checkout URLs are restricted to `https://checkout.paystack.com`
  (TEST) / `paystack-mock` (MOCK) with no fragment or user-info.
- No fictional production approval was added; the local TEST reference and the
  merchant label are clearly local and must be replaced before activation.
- No migration; no schema change. Cancellation kept the documented If-Match
  contract instead of weakening optimistic concurrency.
- Known risk: `.env` is tracked and contains a Google Maps API key; it should be
  rotated and moved to an ignored secret source. `python3 scripts/check_repository.py`
  reports 25 pre-existing repository-state failures (tracked `.env`/PandaGo key
  material, a missing Terraform storage module and credential-pattern hits); none
  are introduced by the new smoke script, and they are not worked around here.

## Last successful commit

`d8a2619` (`feat(checkout): enable local Paystack TEST checkout and provider rule
mapping`), on top of `4684591` (`enabling carrier`). The local `.env` runtime
values stay uncommitted on purpose (see the security note above).

## Open issues/activation inputs

- Paid order/settlement: requires `NIGSON_PAYMENT_ENABLED=true`, the real
  Paystack integration/merchant reference, a registered webhook callback and a
  completion of the hosted TEST payment. Owner: product/finance + user.
- PandaGo staging occasionally returns `503 PROVIDER_UNAVAILABLE` for valid Lagos
  addresses; an immediate retry succeeds. Both smokes retry delivery quotes at
  most three times and only when the status is `>= 500`.
- Merchant binding `nigson-local-paystack-test` is a local label only.
- Tracked `.env` secret (Google Maps key) needs rotation.
- PandaGo local approval expires `2026-11-17T23:59:59Z`.

## Next dependency-ready task

Enable the Paystack webhook/settlement path (API-038/API-039) with a real
integration/merchant reference, a registered callback and a completed hosted TEST
payment when the user supplies those activation inputs. The out-of-range mapping
is now approved and implemented, so it no longer blocks the storefront.

# Follow-up 2026-11-15: hosted checkout return and local TEST settlement

Task status: DONE locally (refund execution and production activation remain
BLOCKED)

## Request addressed

The customer completed a real Paystack TEST payment but the browser stayed on
`checkout.paystack.com` instead of returning to the storefront, and the order
stayed `PENDING` because no settlement path was active. The user chose
"Redirect + local TEST settlement": return to the storefront and settle a
completed TEST payment locally, still without live money or a PandaGo booking.

## Root causes

1. `PaystackAdapter.initialize` never sent `callback_url`, so the hosted page had
   no return target at all. Paystack only redirects when that field is supplied.
2. `NIGSON_PAYMENT_ENABLED` was off, so no receipt processor, reconciler or
   polling worker existed; even a correct return page could only poll an attempt
   that would never leave `PENDING`.

## Changes

- `PaystackAdapter.initialize(..., callbackUrl)` sends `callback_url`. The value
  must be an absolute `https` URL without query, fragment or user-info;
  `http` is accepted only for `localhost`/`127.0.0.1` in `local`/`test`.
  Invalid values fail with `PAYSTACK_CALLBACK_INVALID` before any HTTP call.
- `OrderPolicy.checkoutReturnUrl` requires `NIGSON_CHECKOUT_RETURN_URL` whenever
  checkout is enabled outside `MOCK` and applies the same rules; missing or
  unsafe values fail closed with `CHECKOUT_RETURN_URL_REQUIRED`.
  `CheckoutPaymentCoordinator` passes the validated URL to `initialize`.
- Local `.env` sets
  `NIGSON_CHECKOUT_RETURN_URL=http://localhost:4200/checkout/success`. Compose
  and `.env.example` document the variable, worker delay, receipt-key inputs and
  the integration id. The return route already existed but only read `id`; the
  storefront did not persist the order id before leaving the app.
- `checkout.component` stores `nigson.checkout.payment.<reference>` and
  `nigson.checkout.last` in `sessionStorage` before redirecting.
- `order-success.component` reads `reference`/`trxref`, resolves the order from
  the query or `sessionStorage`, polls API-041 every 3s for up to 60s and shows
  distinct CHECKING/PAID/PENDING/FAILED/UNVERIFIED states. It never claims
  "paid" before the API reports a verified or settled attempt.
- `shared-ui` adds `OrderPayment` and `OrderService.getPayment(orderId)` for
  API-041.
- Local TEST settlement is enabled with `NIGSON_PAYMENT_ENABLED=true` so the
  payment worker polls `INITIATING/PENDING/UNKNOWN` attempts. `RefundCoordinator`
  still requires the real Paystack integration id; locally `refunds` is replaced
  by `SettlementOnlyRefunds`, which fails closed with
  `PAYSTACK_REFUND_ACCOUNT_REQUIRED` for submit/inspect. Outside `local`/`test`
  the missing integration id still refuses startup. No account identifier is
  invented and no provider mock acts as Paystack.

## Evidence

- Targeted Java tests PASS, 12 tests / 0 failures / 0 errors:
  `CheckoutPaymentReturnUrlTest` (6), `NigeriaKoboTest` (4, including
  `callback_url` sent and unsafe URL rejection), `SettlementOnlyRefundsTest` (2).
  `./mvnw -B -pl services/commerce -am
  -Dtest=SettlementOnlyRefundsTest,NigeriaKoboTest,CheckoutPaymentReturnUrlTest
  -Dsurefire.failIfNoSpecifiedTests=false test` → `BUILD SUCCESS`.
- Isolated order verification PASS, 25 tests / 0 failures / 0 errors:
  `ENV=test python3 scripts/order/verify.py` → `BUILD SUCCESS`,
  `PASS: native atomic checkout/frozen orders/current authority/verified stock
  lifecycle and actual stateful Paystack HTTP`.
- Storefront `ng build shop` PASS; `npx vitest run --config
  vitest.checkout.config.mjs` 7/7 PASS.
- Authenticated BFF checkout smoke PASS after enabling settlement:
  delivery quote `201 PANDAGO / SANDBOX / NGN 26.00`, checkout quote `201`
  `13025.00`, attempt `201 PENDING`, hosted host `checkout.paystack.com`,
  cleanup `200 CANCELLED`. This attempt used the new `callback_url`.
- Real previously paid TEST transaction settled without another provider call
  beyond verification. Reference `nigson-01a1e1bb25387b419e4fb2fe406bf6fd`
  (provider amount `1333198`, requested `1303200`, paid
  `2026-11-16T00:22:03Z`):
  - attempt `SUCCEEDED`, `verified_at=2026-11-16T00:40:35Z`
  - transaction `6768137602`, settled `13032.0000`, provider status `success`
  - order `01a1e1bb-2538-71dc-80df-8849ce77a42a` `PAID / PAID / UNFULFILLED`,
    version 2
  - reservation `ALLOCATED`; outbox `order.paid.v1` aggregate version 2
- Angular return route `GET http://localhost:4200/checkout/success?...` → `200`.
- Evidence: `.build/local-test-checkout-redirect/` (`targeted-tests.log`,
  `order-verify.log`, `checkout-smoke.log`, `shop-build.log`, `shop-vitest.log`,
  `database-settlement.txt`, `container-env.txt`).

## Remaining limits

- Paystack does not expose this account's integration id on
  `transaction/verify`, `transaction/{id}` or the list response, and
  `GET /integration` returns 500 for the TEST key. Refund execution therefore
  stays fail-closed locally until the real id is supplied.
- Local webhooks cannot be delivered because `localhost` is not public. Local
  settlement depends on worker polling; webhook qualification remains
  NOT_RUN.
- The settlement worker verifies with the real Paystack TEST API; no live money
  moved and no PandaGo booking was created. Local TEST only.
- The merchant label `nigson-local-paystack-test` and
  `NIGSON_CHECKOUT_RETURN_URL=http://localhost:4200/...` are local values and
  must be replaced before production activation.
- `.env` remains intentionally uncommitted (live Google Maps key).

## Commits

- `7a4437f` — backend: callback URL, return validation, local TEST settlement
  and fail-closed local refunds.
- `504571a` — storefront repository: remember the order and verify it on
  `/checkout/success` (separate Git repository under `nigson-micro-frontend/`).
