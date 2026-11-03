# Session report: LOCAL-CATALOGUE-VISIBILITY-001

Date: 1 November 2026 (America/New_York)
Specification/architecture: 1.1.0 / 1.0 plus ADR-008
Task status: DONE locally
Phase: local public demo catalogue testing

The user requested the new electronics products be visible to other users. They were drafts and the NG market was disabled; public catalogue reads intentionally excluded them. Added an explicit local-only `--publish` option to the existing electronics fixture script and applied it. The 24 synthetic electronics are now ACTIVE/published, eligible for NG, and NG is enabled for catalogue visibility. Categories and SKUs already satisfy publication readiness. Other products, staff grants and customer records were preserved; the older solar fixture remains a draft.

No price book, tax, returns, delivery or checkout configuration was added. NG is still backed by NGN with the existing empty/unconfigured commercial policies. No fabricated price/tax/provider approval is claimed. The local bootstrap records this fixture publication with a deterministic audit entry. Inserts/publication/readiness checks commit in one transaction. Repeat execution preserves balances and avoids duplicate rows, audit entries and repeated product version changes. Archived/deleted products are not silently republished; the all24 eligibility assertion would fail instead.

Files changed:

- `scripts/local/seed_electronics.py`
- `docs/DEMO_VALIDATOR_GUIDE.md`
- `IMPLEMENTATION_STATUS.md`
- This report

Contracts/migrations/events affected: no runtime source, API contract or DDL changes. Local fixture publication only; no provider requests or queued event fixtures.
Acceptance: all24 products are visible without staff permissions, across all8 categories and public detail pages.

Actual commands/results:

- `python3 -m py_compile scripts/local/seed_electronics.py`: PASS.
- `python3 scripts/local/seed_electronics.py --publish`: PASS; repeated PASS with 24 products,8 categories,24 balances. SQL asserts every synthetic electronics product is publicly eligible before commit.
- `python3 /private/tmp/nigson-public-electronics-probe.py`: PASS. Anonymous HTTP200 for all24 public products, all24 detail pages, all8 categories, category filters with3 products each, and pagination20+4. Availability correctly reports22 in-stock and2 out-of-stock products.
- `python3 /private/tmp/nigson-retail-visibility-probe.py --username retailA`: PASS; real Keycloak/BFF login with CUSTOMER-only role sees all24 products. Probe's own session logged out and invalidated; next login requires credentials.
- Same probe with `--username retailB`: PASS with the same results. No new role grant or other user's browser session was used.
- `git diff --check`: PASS.

Build: NOT_RUN, no runtime code changed.
Tests: actual PostgreSQL/public HTTP and two independent retail session checks PASS.
Security/migration/compatibility: local project/environment/Unix endpoint guards retained; no authorization/ownership checks weakened, no data reset or schema migration.
Last successful commit: NONE created.
Open issues: unconfigured commercial rules still prevent this being a qualified checkout journey; publication visibility is not a provider/market launch qualification. Full browser click automation NOT_RUN.
Next task: INFRA-001 continues independently.
