# LOCAL-DISTRIBUTOR-001 — local BP3 workflow

Result: local primary flow verified. Existing source baseline `97bee90`; no new
commit created. User explicitly chose local BP3 testing rather than keeping BP1.

The browser originally posted distributor requests directly to Experience, which
requires Commerce workload identity and a body-bound signed actor assertion.
Added six distributor APIs to the Commerce engagement relay/controller and routed
frontend distributor requests to Commerce. Current session authority, CUSTOMER
or SALES role, CSRF, idempotency, If-Match and BP3 phase checks remain enforced;
no browser cookie/token is forwarded to Experience. Workload token/JWKS calls use
the private Keycloak backchannel while validating the configured public issuer.

Applied BP3 owner migrations and canonical phase-three workload scopes/mappers
using local-only setup. Existing identities were retained; temporary recovery
administrators were removed. Configured only the actual enabled NG catalogue
market and a clearly synthetic DEMO_DISTRIBUTOR entitlement. No tax, retail or
wholesale prices, delivery policy, live scanner or provider approval was invented.
Created synthetic PDF bytes in LocalStack with owner-local private file records,
explicit local fixture scan labels, checksum and immutable linked documents.

Frontend now submits an actual owner fixture document ID, identifies its sample
nature and allows preview. Staff queue uses owner detail/version APIs, requires
human document-review confirmation and a distinct approver, and waits for the
real acknowledged response before showing approval. Errors are displayed rather
than optimistic fabricated updates. Local sample metadata is localhost-only.

Executed actual browser-protocol Keycloak/BFF sessions through localhost:4200:
customer staff access403; wrong CSRF401; empty document list400; customer
submission201 and stable replay; own list and cross-customer isolation; foreign
owner document422; staff queue/detail/review200; same reviewer422 on a fresh
application; separate validator approval202 with an actual Commerce organization
and ACTIVE entitlement acknowledgement. Original pending command also reconciled
successfully. Logout204 ran after each session. Evidence:
`.build/local-bp3-http-final.log`, `.build/local-bp3-http-new-owner.log`,
`.build/local-bp3-acceptance.json`.

Preserved failures: initial empty query string caused400, corrected to absent
query; null optional business/price-book fields caused downstream400 mapped503,
corrected to omission and tested. Initial cross-owner test expected the wrong
status; verified actual rule422. A replayed reviewer request correctly returned
412 VERSION_MISMATCH because the application was already pending, so a fresh
application additionally proved reviewer separation422. A further test reused
an already-active synthetic business reference and Commerce correctly rejected
a duplicate entitlement (INVALID_TRANSITION); Experience returned503 and retained
ACTIVATION_PENDING. This fixture remains visible for investigation; it was not
silently deleted or marked approved. A distinct synthetic business reference
then completed a fresh full flow. Do not reuse business registration references
when intending to create different organizations.

Docker hung and later reported full-host-disk I/O errors; user explicitly approved
restart and cached updater cleanup. Recovery is recorded in
`2026-11-02-LOCAL-DOCKER-STORAGE-001.md`. Local actor signing keys and sample PDF
objects were restored after emulator data loss; sessions/database volumes remain.

Validation: focused relay/controller6 tests, entitlement HTTP adapter2 tests;
frontend mapping/workflow10 tests and shell/admin-shop builds passed. Broader
isolated PostgreSQL/HTTP phase acceptance is recorded in the phase audit report.
Guide: `docs/DEMO_VALIDATOR_GUIDE.md`. Production/provider qualification remains
NOT_RUN. Next work: complete fresh BP2–BP5 evidence and reviewed release gates.
