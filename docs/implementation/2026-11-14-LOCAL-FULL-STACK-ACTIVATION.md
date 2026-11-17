# Local full-stack activation (catalogue, market, quotes)

Status: DONE locally; provider-backed flows intentionally fail closed. All changes are
uncommitted on top of backend `89c4a78` / frontend `539b26f`.

## Requests addressed

- Fix `GET /api/v1/catalogue/categories` returning `404 NOT_FOUND` for reference
  `976d1d5c-eb42-49cc-9591-ec813f7073cb`.
- Seed every blocking dataset for local only; never invent production approvals or run
  outside local.
- Enable all features that can work locally.
- Make the storefront call the real APIs; remove mock data acting as a provider.
- Surface an error when a service is genuinely unavailable.

## Root causes

1. `JdbcCatalogueQueries` / `RecommendationService` collapsed "unknown market" and
   "configured but disabled market" into `404`. Split into `404 NOT_FOUND` and
   `422 MARKET_RULE_VIOLATION` for the API-013/014/015 and API-073 reads.
2. Local had no catalogue/market rows at all. NG existed only as a disabled,
   policy-empty draft, so the storefront could not legitimately read anything.
3. `SCHEMA_RELEASE=BP3` omitted `commerce.product_recommendation`, so API-073 threw a SQL
   error (`500`) even for a valid product.
4. The shop enriched list items from `DEMO_SKU_MAP`/SKU-prefix heuristics instead of the
   authoritative product read, so real local SKUs/categories never reached the UI.
5. Local Keycloak `nigson-commerce` service-account tokens carried only `profile email`.
   Every Commerce→Experience relay therefore failed with `503 PROVIDER_UNAVAILABLE`
   (e.g. `GET /api/v1/quote-requests`, reference `4765120f-0fc1-4f7d-bb02-ad016753b773`).

## Changes

### Backend

- `services/commerce/.../catalogue/JdbcCatalogueQueries.java`,
  `RecommendationService.java`: 404/422 market split (previous session).
- `services/commerce/.../market/JdbcMarketRepository.java`: 3-arg constructor with an
  explicit `localSynthetic` flag; local activation only when tax policy, returns
  revision, active retail price book, NG delivery destination and address rules are all
  already present and valid.
- `services/commerce/.../market/MarketConfiguration.java`: `NIGSON_LOCAL_SYNTHETIC_SELLING`
  honoured only when `APP_ENV=local`; startup fails otherwise.
- `services/commerce/src/test/.../market/MarketPersistenceTest.java`: regression test
  proving the local gate enables a complete policy, rejects incomplete ones, and still
  fails closed for a production repository.
- `scripts/local/seed_local_catalogue.py`: idempotent, `--dry-run`, guarded to the local
  Docker context + `nigson-local` Compose project + `APP_ENV=local`. Seeds only synthetic
  NG NGN catalogue/policy/price rows, labelled `local-synthetic-seed`.
- `scripts/local/dependencies.py`: run-core now applies canonical local workload scopes
  through the existing guarded `scripts/local/enable_bp3.py` before starting apps, so the
  Commerce→Experience relay cannot regress to `503`.
- `services/experience/src/test/.../fixtures/ExperienceHttpFixtures.java`: moved out of the
  scanned `com.nigson.experience` base package to `com.nigson.fixtures`. This removes a
  pre-existing Spring Modulith test-class cycle (`platform -> root -> platform`) that broke
  `BuildCompatibilityTest`; no production code changed.
- `.env`: `SCHEMA_RELEASE=BP5`, `NIGSON_QUOTES_ENABLED=true`,
  `NIGSON_LOCAL_SYNTHETIC_SELLING=true`. `.env.example` documents the local-only flag.
- `docker-compose.yml`: passes `NIGSON_LOCAL_SYNTHETIC_SELLING` to Commerce.

### Frontend

- `projects/shared-ui/src/lib/catalog.service.ts`: product list now enriches each item from
  `GET /api/v1/catalogue/products/{id}` (real SKUs, category ids/names, assets) via
  `withProductDetails`/`toProduct`; detail reads no longer use `DEMO_SKU_MAP`,
  `extractSku` or `resolveCategoryFromSku`. Remaining `FALLBACK_IMG`/`CAT_IMG` are visual
  fallbacks only, not business data.
- `projects/shop/src/app/app.spec.ts`: replaced the stale Angular scaffold assertion
  (`Hello, shop`) with a router-outlet assertion.

## Synthetic local data (not approvals)

- Seeded: 6 categories, 2 brands, 8 active products/SKUs, 8 retail prices, stock location
  `SYN-LAGOS`, NG market enabled with policy revision 2.
- Placeholder evidence values are clearly synthetic: tax
  `LOCAL-SYNTHETIC-VAT-NOT-A-REAL-APPROVAL`, returns `LOCAL-SYNTHETIC-RETURNS-1`, price
  book `LOCAL-RETAIL-NGN`, actor `local-synthetic-seed`.
- Re-apply with `python3 scripts/local/seed_local_catalogue.py` (it refuses non-local
  contexts/projects/environments).

## Verification

- `./mvnw -B -o -q -DskipTests compile` — pass.
- `ENV=test python3 scripts/catalogue/verify.py` — PASS, commerce 22 tests, 0 skipped;
  Experience 2; Integration 2.
- `ENV=test python3 scripts/market/verify.py` — PASS, commerce 26 tests, 0 skipped
  (includes the new local-activation regression test).
- `ENV=test python3 scripts/lead/verify.py` — PASS, 10 Experience + 3 Commerce +
  2 Integration tests, 0 skipped (quote POST/read HTTP acceptance).
- `ENV=test python3 scripts/experience_lead/verify.py` — PASS, 18 Experience tests,
  0 skipped.
- `npx ng build shop` and `npx ng build shell` — pass (SCSS sub-selector warnings only).
- Frontend suites: `ng test shop` 9/9, distributor 13/13, admin 11/11, checkout 7/7.
- `make run-core` — exit 0; scope configuration PASS, actor key PASS, registration client
  PASS, Commerce/Experience/Integration healthy.
- Live BFF checks: markets `200`, categories `200` (6 real rows), products `200` (8 real
  rows), product detail returns real SKU `NX-PB20-BLK` and category `Power Banks`, SKU
  price `200` (`24999.00` NGN), brands `200`, recommendations `200 []`.
- Live authenticated quote flow through a real local BFF OIDC session:
  `POST /api/v1/quote-requests -> 201 {"leadId":"...","state":"NEW"}`, then
  `GET /api/v1/quote-requests -> 200` with `totalElements=1` (temporary local identity
  removed after the check; log in `.build/local-quote-smoke.log`). The empty-history read
  also returned `200 {"items":[],...}` earlier.
- Frontend dev server `http://localhost:4200/` serves `200` and proxies the real API.

## Intentionally fail-closed (no mocks)

- Delivery quotes were `422 FEATURE_DISABLED` during this session. They are now enabled
  locally with the verified PandaGo stage account; see
  `docs/implementation/2026-11-14-LOCAL-PANDAGO-STAGING-QUOTES.md`.
- Fulfillment/shipments: `PROVIDER_UNAVAILABLE` until a real carrier account, approval and
  fingerprint are configured (`NIGSON_FULFILLMENT_ENABLED=false`).
- Paystack/PandaGo live qualification and checkout remain input-bound; no simulator,
  dummy credential or local success path is used by application runtimes.

## Known limitations

- `make status-check` still fails for pre-existing reasons (stale Terraform paths, tracked
  `.env` and local provider PEM material); unchanged.
- Non-shell remotes (4201–4211) are not running, so the shell logs `remoteEntry.json`
  CORS/init warnings and falls back to the local shop routes. Cosmetic for local.
- The QuoteService surfaces load failures via `loadError`; no silent empty state.

## Evidence

- `/tmp/catalogue-verify.log`, `/tmp/market-verify-2.log`, `/tmp/lead-verify-2.log`,
  `/tmp/exp-lead-verify.log`, `/tmp/run-core-4.log`.
- `docs/implementation/evidence/2026-10-18-EXP-LEAD-002/`,
  `docs/implementation/evidence/2026-10-19-EXP-LEAD-001/` (native PostgreSQL migration and
  HTTP acceptance logs generated by the verify scripts).

## Next dependency-ready task

Genuine provider qualification (Paystack live account, PandaGo account/route approval,
Google billing for address lookup) so delivery quotes, fulfillment and checkout can be
enabled without mocks. Until then those flags stay off and return explicit errors.
