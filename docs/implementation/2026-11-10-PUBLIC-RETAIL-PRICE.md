# Public retail price

API-026 retail reads now ignore stale browser sessions, matching public catalogue behavior. Explicit organization pricing retains session validation and membership checks; private routes and mutations remain protected. OpenAPI clarified; no schema changes.

Reproduced before fix: anonymous retail GET 200, same GET with stale session 401. Targeted PublicRetailPriceTest and SecurityProtocolTest: 11 passed (.build/public-retail-price-tests.log). Docker Commerce build passed (.build/public-retail-price-docker.log); local Commerce recreated. Deployed HTTP acceptance: anonymous, expired-cookie and malformed-cookie retail reads 200 with unitAmount; anonymous organization price and private cart 401. No migration needed; git diff --check passed. Base commit 55976a6; no commit created.
