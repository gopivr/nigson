# Local PandaGo staging delivery quotes

Status: DONE locally. The PandaGo stage delivery provider is enabled for local
delivery quotes using the existing verified staging account. No booking, order,
payment or production approval was created.

## Request addressed

`POST /api/v1/carts/{id}/delivery-quotes` returned
`422 FEATURE_DISABLED` ("The delivery provider must be enabled before an address
can be quoted"). Enable the delivery provider with the staging credentials.

## Root causes

1. `NIGSON_CARRIER_QUOTES_ENABLED`, `NIGSON_FULFILLMENT_ENABLED` and
   `NIGSON_PRICING_CHECKOUT_ENABLED` were only switched on by
   `docker-compose.pandago.yml`, and `NIGSON_PANDAGO_ENABLED=false` meant
   `run-core` never selected that overlay. `PricingProviders.require` therefore
   threw `FEATURE_DISABLED` before any provider call.
2. The verified staging credentials live in the ignored, mode-600
   `.local/pandago/account.env`; the tracked `.env` still held older/stale
   `PANDAGO_CLIENT_ID`/`PANDAGO_KEY_ID` values. Loading only `.env` would have
   produced a different account fingerprint and `PROVIDER_MODE_MISMATCH`.
3. The new local catalogue SKUs had `carrier_packaging = NULL`. Carrier packing
   is closed and a missing rule makes the cart ineligible for a quote, so
   enabling the provider alone was not sufficient.

## Changes

- `.env`: `NIGSON_PANDAGO_ENABLED=true`. Base delivery flags stay off; the
  overlay enables commerce quotes, fulfilment and pricing for the local stack.
- `scripts/local/dependencies.py`:
  - when the PandaGo overlay is selected, also loads `--env-file
    .local/pandago/account.env` and gives those values precedence over `.env`;
  - refuses non-local `APP_ENV`, missing or group/world-readable credentials,
    `PANDAGO_MODE` other than `TEST`, non-staging endpoints, non-SANDBOX
    fulfilment/TEST pricing modes, or an account fingerprint that does not match
    the verified staging account.
- `scripts/local/seed_local_catalogue.py`: adds clearly synthetic carton
  packaging (`maximumUnitsPerPackage=1`, product unit weight, 0.1 kg tare,
  20x15x10 cm) and an audit row. Applied: `packaging=8`.
- Existing approval values are reused, not invented: PandaGo
  `user-approved-local-sandbox-quotes-20261110` valid through
  `2026-11-17T23:59:59Z`, and the existing local Paystack TEST approval.

## Verification

- `python3 scripts/local/verify_pandago.py` — PASS: Delivery Hero TEST OAuth
  authenticated; registered NGN outlet verified; no quote/booking/cancellation
  submitted. Evidence: `.build/local-pandago-staging-account.log`.
- `python3 scripts/local/dependencies.py validate` — PASS.
- `make run-core` — exit 0; Commerce, Experience and Integration healthy.
  Containers confirmed with `NIGSON_CARRIER_QUOTES_ENABLED=true`,
  `NIGSON_FULFILLMENT_ENABLED=true`, `NIGSON_PRICING_CHECKOUT_ENABLED=true`,
  `NIGSON_PANDAGO_ENABLED=true`, `PANDAGO_MODE=TEST`, and the mode-600 private
  key mounted at `/run/secrets/pandago_private_key`.
- Real authenticated BFF session (temporary local CUSTOMER, removed afterwards):
  `POST /api/v1/carts/{id}/delivery-quotes` with a located address returned
  `201 PANDAGO / SANDBOX / NGN 26.00`. Evidence:
  `.build/local-pandago-staging-quote.log`.
- Same cart with a postal-only address (the checkout UI path) returned
  `503 GEOCODING_ACCESS_DENIED`. This is the Google geocoding blocker below,
  not a delivery-provider failure.

## Remaining blocker (not a mock workaround)

The checkout UI intentionally saves postal addresses without coordinates and
relies on server-side Google geocoding. The configured Google project still has
billing disabled (`REQUEST_DENIED: You must enable Billing on the Google Cloud
Project`), so postal-only UI addresses cannot reach PandaGo yet. Enabling Google
Cloud billing, or supplying a billing-enabled staging key in
`GOOGLE_MAP_API_KEY`, is required. A located address (latitude/longitude already
present) quotes successfully today. No local geocoding mock was introduced.

## Intentionally not enabled

- `NIGSON_ORDER_CHECKOUT_ENABLED=false` in the overlay: no order confirmation,
  booking, shipment or payment is created by a delivery quote.
- Booking/cancellation and end-to-end checkout remain gated by their own
  approvals and workflows.
- The PandaGo approval expires `2026-11-17T23:59:59Z`; after that, quotes fail
  closed with `PROVIDER_UNAVAILABLE` until a new approval is recorded.

## Files

- `nigson/.env`
- `nigson/scripts/local/dependencies.py`
- `nigson/scripts/local/seed_local_catalogue.py`
- `nigson/docker-compose.pandago.yml` (existing overlay, now selected by run-core)
- `nigson/.local/pandago/account.env` (ignored, mode 600; not committed)

## Next dependency-ready task

Enable Google Cloud billing for the configured Maps key (or provide a
billing-enabled staging key) and rerun the checkout postal-address flow. Then
continue ADMIN-FUNCTIONAL-AUDIT; delivery booking and payment remain separately
gated.
