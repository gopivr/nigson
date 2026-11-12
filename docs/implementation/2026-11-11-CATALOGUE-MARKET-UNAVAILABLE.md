# Session report: CATALOGUE-MARKET-UNAVAILABLE
Date/time: 2026-11-11 21:30 UTC
Specification/architecture version: 1.1.0 / architecture 1.0 + ADR-008, ADR-026
Task status: DONE for the scoped error-semantics and UI-state repair
Current engineering/product phase: EP10 infrastructure continues independently; this report covers a local catalogue read/UX correction

## Summary

`GET /api/v1/catalogue/categories?market=NG` returned HTTP 404 `NOT_FOUND` whenever the
requested market row existed but had `enabled=false`. The endpoint and controller were
present; the market guard in the read repository collapsed "market configured but not yet
activated" into "resource missing". That produced a misleading storefront/admin error and a
generic "No products match your search" state.

The guard now distinguishes the two cases:

- unknown market code → 404 `NOT_FOUND` (resource does not exist)
- configured market with `enabled=false` → 422 `MARKET_RULE_VIOLATION`
  ("The market operation does not meet approved selling rules.")

The same split applies to public recommendations (API-073). Brands remain 200 with an empty
list because they are not market-scoped. No market, tax, price, category, product or
eligibility data was enabled or invented; the NG market remains disabled until approved
configuration is supplied.

Frontend behaviour is now explicit rather than silent:

- `CatalogService` tracks `catalogueUnavailable` when a catalogue read returns
  `422 MARKET_RULE_VIOLATION` or `404 NOT_FOUND`, and the shop product list renders a
  "Catalogue is not available yet" notice instead of a misleading empty result.
- `ApiErrorsService` and the admin service render an actionable market-activation message
  instead of the generic 404 detail.

## Files changed

- `services/commerce/src/main/java/com/nigson/commerce/catalogue/JdbcCatalogueQueries.java`
- `services/commerce/src/main/java/com/nigson/commerce/catalogue/RecommendationService.java`
- `services/commerce/src/test/java/com/nigson/acceptance/catalogue/CatalogueHttpAcceptance.java`
- `contracts/openapi/commerce.yaml` (API-013/014/015 descriptions)
- `docs/API_CONTRACT.md` (API-013/014/015 rules and errors)
- `docs/runbooks/catalogue.md`
- `nigson-micro-frontend/projects/shared-ui/src/lib/catalog.service.ts`
- `nigson-micro-frontend/projects/shared-ui/src/lib/api-errors.service.ts`
- `nigson-micro-frontend/projects/shared-ui/src/lib/api-errors.service.spec.ts`
- `nigson-micro-frontend/projects/shared-ui/src/lib/admin/admin.service.ts`
- `nigson-micro-frontend/projects/shop/src/app/products/product-list.component.ts`
- `IMPLEMENTATION_STATUS.md`
- `docs/implementation/2026-11-11-CATALOGUE-MARKET-UNAVAILABLE.md`

## Contracts/migrations/events affected

- API-013, API-014, API-015, API-073: documented 422 `MARKET_RULE_VIOLATION` for a
  configured but disabled market and 404 `NOT_FOUND` for an unknown market.
- No schema migration, event catalogue change or new API.

## Acceptance criteria and evidence

- `GET /api/v1/catalogue/categories?market=NG` → 422 `MARKET_RULE_VIOLATION` on the rebuilt
  local Commerce container.
- `GET /api/v1/catalogue/categories?market=ZZ9` → 404 `NOT_FOUND`.
- `GET /api/v1/catalogue/products?market=NG` → 422 `MARKET_RULE_VIOLATION`.
- `GET /api/v1/catalogue/brands` → 200 `[]`.
- `GET /api/v1/markets` → 200 `[]` (market remains disabled).
- Acceptance harness now asserts the disabled-market 422 and unknown-market 404 paths.

## Commands executed

- `./mvnw -B -o -q -DskipTests compile` → PASS (exit 0)
- `./mvnw -B -o -q test-compile` → PASS (exit 0)
- `make run-core` → PASS; three owner images rebuilt, migrations applied, Commerce/Experience/
  Integration healthy, Keycloak discovery ready
- `curl` checks against `http://localhost:8081` → 422/404/422/200 as listed above
- `npx ng build shop` → PASS (bundle generated)
- `npx ng build shell` → PASS (bundle generated)
- `npx vitest run --config vitest.admin.config.mjs` → 11 passed
- `npx vitest run --config vitest.distributor.config.mjs` → 13 passed
- `npx vitest run --config vitest.checkout.config.mjs` → 7 passed
- `ENV=test python3 scripts/catalogue/verify.py` → PASS; commerce 22 tests, zero failures/
  errors/skips, including `SecurityAssemblyTest` (1 run) which executes
  `CatalogueHttpAcceptance` with the new disabled-market 422 and unknown-market 404 assertions.
  Evidence: `.build/catalogue001/database-evidence/`

## Build

PASS for the scoped modules and both affected Angular applications.

## Tests

- Java compile, test-compile and native PostgreSQL catalogue acceptance: PASS.
- `CatalogueHttpAcceptance` now verifies, over actual HTTP against isolated PostgreSQL:
  disabled market → 422 `MARKET_RULE_VIOLATION`, unknown market → 404 `NOT_FOUND`, plus the
  existing enabled-market, ETag, role, CSRF, replay and ownership cases.
- Frontend unit tests: PASS (31 total across the three configured suites).

## Security/migration/compatibility checks

- No schema, credential, authorization or owner-boundary change.
- Disabled markets still cannot be enabled through API-030 (`MARKET_RULE_VIOLATION`), so this
  change does not relax selling policy.
- Public error bodies remain the closed safe vocabulary; no SQL or provider detail is exposed.

## Last successful commit

Backend `5ef973b`; frontend `b460a57`. These changes remain uncommitted.

## Open issues/activation inputs

- The NG market remains `enabled=false` with no tax policy, returns revision, retail price book,
  enabled destination or catalogue rows. Activating it still requires approved business inputs
  (ADR-008/ADR-026); the storefront intentionally shows the "not available yet" state.
- Existing unrelated repository-check failures (stale Terraform paths, tracked `.env`/PEM
  files) are unchanged and outside this scope.

## Next dependency-ready task

Continue ADMIN-FUNCTIONAL-AUDIT; the remaining blocker for catalogue UI qualification is real
approved market and catalogue configuration.
