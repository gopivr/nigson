# ADR-029: Account-owned wishlist in Commerce

Status: Accepted implementation decision. Date: 17 November 2026.

The user requested completion of the visible but disconnected wishlist. Commerce already owns personal parties, catalogue SKUs and cart. A personal wishlist is a Commerce-owned set of (party, SKU), not a price snapshot, cart reservation or entitlement. No owner boundary changes or external provider calls are introduced.

API-216 GET /api/v1/me/wishlist, API-217 PUT /api/v1/me/wishlist/items/{skuId}, API-218 DELETE on that item path use the existing BFF session and CSRF boundary. Require the current ACTIVE CUSTOMER in personal context; ADMIN does not select another owner, and no OTP is required. No client party id, query string or body is accepted. Reads are no-store and bounded to 100, ordered created_at descending then SKU id. Save validates active NG catalogue eligibility; unavailable existing items remain readable as identifiers/availability and removable without unpublished metadata.

Set mutations serialize on the current Party, use durable actor/API/key idempotency, enforce uniqueness and the 100-item cap, and audit actual membership changes atomically. No If-Match is necessary because each operation explicitly sets one membership rather than replacing an observed list. Replaying an old acknowledgement never resurrects a later removal; clients reload current membership after acknowledgement. Independent keys follow serialized execution order. No cross-owner event is needed.

Additive BP1 migration 027 creates commerce.wishlist_item with UUIDv7 id, standard provenance/version columns, owner and SKU FKs within Commerce, unique (party_id, sku_id), bounded-query/FK indexes and SELECT/INSERT/DELETE grants only for Commerce runtime. Physical party/SKU deletion cascades the association; soft-deleted or unpublished SKUs remain removable. Explicit rollback refuses a populated wishlist table; an empty table may be removed under normal migration rollback governance. No old migration is edited.

The frontend uses a dedicated account-scoped service, cancels in-flight state when accounts change, and adds accessible heart controls to product cards/details. Catalogue details and current pricing are loaded for the exact saved SKU with four-item concurrency, never from saved browser price snapshots. Add to Cart retains the wishlist entry and reports success only after cart acknowledgement. No guest persistence, automatic guest merge or offline fallback is introduced. There is no importable legacy persisted wishlist: the old state was memory-only.
