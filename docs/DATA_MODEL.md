# Nigson Data Model

Specification1.1.0; synchronized view; master is authoritative.


# Master section8: Domain Model

An aggregate is the consistency boundary, not necessarily a deployable. The fields, types, nullable values, constraints and indexes for every entity appear in section 10; that catalog is the normative field specification. Mutable aggregates require expected version checks. Append-only records are evidence/ledger entries with no mutation API.

| Aggregate root | Service | Children and invariants |
|---|---|---|
| Party / Organization | commerce | Profile, addresses, memberships and entitlements. Multi-organization actor context must be explicit. |
| Product | commerce | SKU, categories, attributes and asset references. Published catalogue is market-filtered. |
| PriceBook | commerce | Revisioned tier entries and organization assignments. Currency and revision frozen at order. |
| Cart | commerce | Lines and checkout quote. Cannot modify after conversion; no stock promise before reserve. |
| SalesOrder | commerce | Line and total snapshots, status history, payment attempt, reservations, refunds and shipments. Atomic lock order governs financial/stock transitions. |
| InventoryBalance | commerce | Reservation/movement evidence; available never negative. |
| Quote | commerce | Approved immutable revisions and lines; acceptance has exactly one order. |
| PromotionRule / WheelCampaign | commerce | Immutable policy revisions, coupons/draws and budget; no client selected rewards. |
| RewardAccount logical view | commerce | Append-only reward_entry per party/organization and currency, no independently editable points balance. |
| ContentItem | experience | Reviewed content revisions and active publication pointer. Withdrawal suppresses knowledge immediately. |
| Lead / DistributorApplication | experience | Follow-up activity, requested items/documents and acknowledged entitlement decision. |
| Review / Subscription / Opportunity | experience | Moderation, append-only consent/suppression and private applications. |
| NotificationJob / IntegrationJob | integration | Persisted retries, attempts and external uncertainty; no foreign domain FK. |
| KnowledgeDocument / Conversation | ai | Atomic knowledge revision activation and owner-scoped runs/checkpoints. |

States are case-sensitive wire strings. Map SQL names to Java/Python names explicitly; public JSON uses camelCase, event envelopes snake_case. Money is a decimal string, Quantity a decimal string with SKU scale, and Instant an RFC3339 UTC string. Do not accept JSON floating-point money. Each entity creation/modification is actor-attributed; sensitive transitions write audit_record in the same transaction.

### Entity commerce.party

- **Owner/module:** commerce/party; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** The identity issuer and subject pair is the immutable identity mapping; do not use email as an authentication key.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.customer_profile

- **Owner/module:** commerce/party; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** At most one profile per party; marketing consent is owned by experience.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.staff_role_assignment

- **Owner/module:** commerce/authorization; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Administrative assignment is audited; a role change invalidates affected BFF sessions.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.organization

- **Owner/module:** commerce/party; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Organization lifecycle is distinct from identity account lifecycle.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.organization_membership

- **Owner/module:** commerce/party; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Every organizational operation checks both active membership and current organization entitlement.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.address

- **Owner/module:** commerce/party; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Soft deletion affects future checkout only; orders preserve an immutable address snapshot.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.account_entitlement

- **Owner/module:** commerce/authorization; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** No external FK; repeat approval command returns the existing acknowledged result.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.brand

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Referenced brands are deactivated, not cascade deleted.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.category

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Reject ancestor cycles and depth above 5.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.product

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Publishing requires at least one active SKU, category and approved image. search_vector is generated from name and description; see search section.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.sku

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Stock, pricing and ordering quantities use this SKU unit and scale. Carrier-ready SKU requires an approved server-owned packaging rule and positive weight/dimensions; browser values cannot reduce shipping weight.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.product_category

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Removing this link does not delete the category or product.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.product_attribute

- **Owner/module:** commerce/catalogue; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Validate each attribute against its configured type and length; no arbitrary HTML.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.asset_reference

- **Owner/module:** commerce/files; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Only clean assets may be published; public URLs use immutable object keys. SHIPPING_LABEL is generated by trusted carrier result only, always PRIVATE and staff OPERATIONS-readable; excluded from product/CDN APIs and browser upload purposes.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.currency

- **Owner/module:** commerce/market; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Seed supported ISO currency metadata; activate only approved currencies.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.market

- **Owner/module:** commerce/market; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Production baseline NG/Nigeria, currency NGN, destination NG; international markets remain disabled until BP5 approval. An approved retail book, tax/returns and carrier service policy are required before live selling. Market approval itself is complete.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.market_destination

- **Owner/module:** commerce/market; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** The approved destination and address rule are checked before any reservation.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.product_market_eligibility

- **Owner/module:** commerce/market; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Missing eligibility means disallowed; no permissive default.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.price_book

- **Owner/module:** commerce/pricing; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Published price book revisions are immutable; changes produce a new revision.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.price_entry

- **Owner/module:** commerce/pricing; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Resolve the greatest qualifying quantity threshold; amounts must conform to currency minor units.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.price_assignment

- **Owner/module:** commerce/pricing; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Book currency must match market and segment DISTRIBUTOR; enforce in application transaction.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.wholesale_rule

- **Owner/module:** commerce/pricing; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Specific organization rule overrides the market default; quantity rules and order minimums are separately evaluated.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.delivery_rule

- **Owner/module:** commerce/fulfillment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Match most specific area then priority; reject ambiguous matching rules at activation.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.cart

- **Owner/module:** commerce/cart; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** One active cart per party and organization and market; enforce a COALESCE expression unique index with documented UUID nil sentinel.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.cart_line

- **Owner/module:** commerce/cart; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Quantity scale and pack multiples must conform to the SKU and current wholesale rules.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.checkout_quote

- **Owner/module:** commerce/pricing; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Signed by server state, immutable; expires after 5 minutes; reservation creation verifies current rule and cart versions.
- **Audit:** Append-only evidence.

### Entity commerce.sales_order

- **Owner/module:** commerce/order; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Use sales_order to avoid the reserved SQL word order. State transitions in section 17 are exhaustive.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.order_line

- **Owner/module:** commerce/order; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Commercial values are immutable snapshots; track refund and fulfillment quantities in separate records.
- **Audit:** Append-only evidence.

### Entity commerce.order_total_component

- **Owner/module:** commerce/order; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Components reconcile exactly to the persisted grand total.
- **Audit:** Append-only evidence.

### Entity commerce.order_status_history

- **Owner/module:** commerce/order; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** History is inserted in the same transaction as the state change.
- **Audit:** Append-only evidence.

### Entity commerce.stock_location

- **Owner/module:** commerce/inventory; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** One launch location; schema supports more. Default fulfillment selects configured priority, never arbitrary row order.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.inventory_balance

- **Owner/module:** commerce/inventory; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** available=on_hand-reserved-allocated; no cached value authorizes a sale.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.inventory_reservation

- **Owner/module:** commerce/inventory; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Transitions lock order first and balance rows in sorted ID order; quantity may split through shipment lines.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.inventory_movement

- **Owner/module:** commerce/inventory; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Reconcile sum of deltas to balance; operation keys make mutations exactly once at the business level. Return business_key is stable return command + line; cumulative returned quantity cannot exceed dispatched or still-allocated eligible quantity.
- **Audit:** Append-only evidence.

### Entity commerce.payment_attempt

- **Owner/module:** commerce/payment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Never create a second payable attempt while provider outcome is uncertain. Mode is recorded at creation; provider callback/verify must match configured environment/account and saved mode. No mock/test financial state may be reused for live settlement.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.payment_transaction

- **Owner/module:** commerce/payment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Only independently verified successful settlement may create this immutable record. Mode is recorded at creation; provider callback/verify must match configured environment/account and saved mode. No mock/test financial state may be reused for live settlement.
- **Audit:** Append-only evidence.

### Entity commerce.webhook_receipt

- **Owner/module:** commerce/payment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Authenticate raw bytes before parsing; event_key includes event type and stable provider object ID; fallback keyed canonical body digest.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.refund

- **Owner/module:** commerce/payment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Under order lock, successful plus in-flight refunds cannot exceed settlement. Provider uncertainty retains the reserved refund amount.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.refund_line

- **Owner/module:** commerce/payment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Cumulative refunded quantity cannot exceed purchased quantity; shipping-only refunds can omit lines.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.shipment

- **Owner/module:** commerce/fulfillment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Dispatch requires paid, unheld order and sufficient allocated stock; verified return is a separate stock adjustment.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.shipment_line

- **Owner/module:** commerce/fulfillment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Cumulative dispatched quantity cannot exceed allocated noncancelled line quantity.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.shipment_event

- **Owner/module:** commerce/fulfillment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Ignore stale event regressions; persist event evidence while keeping the current lifecycle monotonic.
- **Audit:** Append-only evidence.

### Entity commerce.quote

- **Owner/module:** commerce/quote; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Lead and quote synchronization uses acknowledged commands; sales approval is explicit.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.quote_revision

- **Owner/module:** commerce/quote; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Offering freezes this revision; modifications produce a new revision.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.quote_line

- **Owner/module:** commerce/quote; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Only approved quote prices override normal repricing; eligibility and inventory still revalidate.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.promotion_rule

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Validate JSON against versioned rule schemas. Published commercial rule revisions do not mutate.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.coupon

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Reserve usage under lock with order; nonzero defaults are safety limits, not approved reward policy.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.coupon_redemption

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Per-party usage counts RESERVED and CONSUMED; refund reversal follows configured campaign policy.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.reward_entry

- **Owner/module:** commerce/reward; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Represent value changes as new entries. Unspent balance is the signed ledger sum, never an editable profile counter. Multiple partial-refund reversals may reference one earning. Lock earning/order and enforce cumulative reverse amount<=earned; business_key includes refund_id. Round cumulative entitlement then subtract prior reversals.
- **Audit:** Append-only evidence.

### Entity commerce.referral_code

- **Owner/module:** commerce/reward; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Never reveal inviter personal details publicly.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.referral_attribution

- **Owner/module:** commerce/reward; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Default first attribution wins; exactly one qualifying purchase reward, with a configured hold period.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.wheel_campaign

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Weighted outcome is sampled securely server side while campaign row protects budget.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.wheel_outcome

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Benefit JSON must reference a valid active rule; sum of active weights is strictly positive.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.wheel_draw

- **Owner/module:** commerce/promotion; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** UTC day boundary default; draw number cannot exceed campaign daily limit; immutable result replay.
- **Audit:** Append-only evidence.

### Entity commerce.product_recommendation

- **Owner/module:** commerce/catalogue; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Curated rules precede learning-based recommendations; filter all results by active market eligibility.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.abandoned_cart_candidate

- **Owner/module:** commerce/cart; BP4.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** After 24 hours inactivity by default; recheck active cart and consent before send.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.content_item

- **Owner/module:** experience/content; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Public API serves only the currently published approved revision.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.content_revision

- **Owner/module:** experience/content; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Published text is immutable; withdrawal changes status and current pointer, and immediately affects revision validation APIs.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.file_asset

- **Owner/module:** experience/files; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Linked owner and purpose determine download access; no public URI for applications or business proofs.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.lead

- **Owner/module:** experience/lead; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Notification recipient group is configuration; complaint details never enter shared events.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.lead_activity

- **Owner/module:** experience/lead; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Notes are staff-only and audit-preserved.
- **Audit:** Append-only evidence.

### Entity experience.distributor_application

- **Owner/module:** experience/distributor; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Distinguish commercial decision from acknowledged entitlement; retries reuse command ID.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.application_document

- **Owner/module:** experience/distributor; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** All linked assets belong to the applicant and are CLEAN before review approval.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.review

- **Owner/module:** experience/engagement; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Verified purchase comes from commerce proof API, never a client boolean.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.consent_event

- **Owner/module:** experience/consent; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Append withdrawal and grant events. Transactional service notices are classified separately.
- **Audit:** Append-only evidence.

### Entity experience.newsletter_subscription

- **Owner/module:** experience/consent; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Default double opt-in; unsubscribe succeeds even if provider sync is temporarily unavailable.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.communication_suppression

- **Owner/module:** experience/consent; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Local suppression overrides a historical grant; sending checks this authoritative service.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.career_opportunity

- **Owner/module:** experience/careers; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Application form schema version is captured with submissions.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.career_application

- **Owner/module:** experience/careers; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Applicant data is private, excluded from AI ingestion and marketing.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.campaign

- **Owner/module:** experience/engagement; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Campaign UI cannot create a commercial entitlement; commerce owns qualification.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.channel_configuration

- **Owner/module:** experience/content; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Public API excludes recipient addresses; secrets remain in Secrets Manager.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.notification_template

- **Owner/module:** integration/notification; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Templates use an allowlisted field schema; sanitize substitutions and prohibit executable template code.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.notification_job

- **Owner/module:** integration/notification; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Fetch minimum authorized delivery context just before send; fail closed if consent cannot be checked. Unique business_key includes channel and destination. Never fallback between channels without fresh consent and explicitly configured routing.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.notification_attempt

- **Owner/module:** integration/notification; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Do not store rendered personal message bodies in general telemetry.
- **Audit:** Append-only evidence.

### Entity integration.integration_job

- **Owner/module:** integration/provider; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist execution state before remote side effects; preserve provider request identity across retries.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.provider_cursor

- **Owner/module:** integration/provider; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Checkpoint advancement occurs only after all items in the page have durable outcomes.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.sheet_sync_record

- **Owner/module:** integration/provider; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Write/update by stable application ID column rather than blindly appending on retry.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.knowledge_document

- **Owner/module:** ai/knowledge; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Only approved public content enters knowledge; private documents are never indexed.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.knowledge_revision

- **Owner/module:** ai/knowledge; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Build then atomically switch active pointer; validate owner publication before any cited answer.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.knowledge_chunk

- **Owner/module:** ai/knowledge; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Use normalized 1024-dimensional embeddings; model dimension changes require a new column/index migration.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.embedding_revision

- **Owner/module:** ai/knowledge; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Reference adapter is Bedrock Titan v2; provider/model revisions do not mix in one active vector space.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.conversation

- **Owner/module:** ai/assistant; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Conversation ownership is verified for every message and feedback command.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.ai_run

- **Owner/module:** ai/assistant; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** No raw sensitive prompts in this audit record; token usage and price-table revision make spend estimation explainable.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.ai_checkpoint

- **Owner/module:** ai/assistant; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Implement a typed application-controlled LangGraph checkpointer; do not create framework tables outside Liquibase.
- **Audit:** Append-only evidence.

### Entity ai.tool_invocation

- **Owner/module:** ai/assistant; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Record argument digest, not order details or identity documents.
- **Audit:** Append-only evidence.

### Entity ai.ai_feedback

- **Owner/module:** ai/assistant; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Only the conversation owner may rate a run; redact comments before aggregation.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.evaluation_suite

- **Owner/module:** ai/evaluation; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** At least 200 synthetic or approved cases; no production PII.
- **Audit:** Append-only evidence.

### Entity ai.evaluation_result

- **Owner/module:** ai/evaluation; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Activation requires passing access, fallback and grounded correctness gates.
- **Audit:** Append-only evidence.

### Entity commerce.outbox_event

- **Owner/module:** commerce/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.processed_event

- **Owner/module:** commerce/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Insert only when local handling or durable external job creation commits successfully.
- **Audit:** Append-only evidence.

### Entity commerce.idempotency_record

- **Owner/module:** commerce/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.audit_record

- **Owner/module:** commerce/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.
- **Audit:** Append-only evidence.

### Entity commerce.feature_configuration

- **Owner/module:** commerce/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.outbox_event

- **Owner/module:** experience/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.processed_event

- **Owner/module:** experience/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Insert only when local handling or durable external job creation commits successfully.
- **Audit:** Append-only evidence.

### Entity experience.idempotency_record

- **Owner/module:** experience/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.audit_record

- **Owner/module:** experience/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.
- **Audit:** Append-only evidence.

### Entity experience.feature_configuration

- **Owner/module:** experience/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.outbox_event

- **Owner/module:** integration/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.processed_event

- **Owner/module:** integration/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Insert only when local handling or durable external job creation commits successfully.
- **Audit:** Append-only evidence.

### Entity integration.idempotency_record

- **Owner/module:** integration/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.audit_record

- **Owner/module:** integration/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.
- **Audit:** Append-only evidence.

### Entity integration.feature_configuration

- **Owner/module:** integration/platform; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.outbox_event

- **Owner/module:** ai/platform; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.processed_event

- **Owner/module:** ai/platform; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Insert only when local handling or durable external job creation commits successfully.
- **Audit:** Append-only evidence.

### Entity ai.idempotency_record

- **Owner/module:** ai/platform; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.audit_record

- **Owner/module:** ai/platform; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.
- **Audit:** Append-only evidence.

### Entity ai.feature_configuration

- **Owner/module:** ai/platform; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.spring_session

- **Owner/module:** commerce/session; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Framework-owned physical shape; configure table name commerce.spring_session and initialize only through Liquibase.
- **Audit:** Versioned actor-attributed changes.

### Entity commerce.spring_session_attributes

- **Owner/module:** commerce/session; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Serialize an allowlisted typed session record; encrypt provider tokens and reject arbitrary deserialization types.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.lead_requested_item

- **Owner/module:** experience/lead; BP3.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist normalized quote-request products; validate through commerce before acceptance.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.upload_session

- **Owner/module:** experience/files; BP2.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Anonymous upload capability is short-lived and purpose-scoped; linking claims it once.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.ingestion_job

- **Owner/module:** ai/knowledge; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Owned queue/reindex work state; retries cannot activate partial embeddings.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.model_policy

- **Owner/module:** ai/gateway; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Activation requires compatible provider model, region and passing evaluated policy; no implicit provider fallback.
- **Audit:** Versioned actor-attributed changes.

### Entity ai.prompt_revision

- **Owner/module:** ai/gateway; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Immutable templates; runtime selects explicitly versioned approved policy.
- **Audit:** Append-only evidence.

### Entity ai.model_rate

- **Owner/module:** ai/gateway; BP5.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Rates are audited approved config; estimates are not billed-cost claims.
- **Audit:** Append-only evidence.

### Entity commerce.carrier_quote

- **Owner/module:** commerce/fulfillment; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Server-computed NGN fee with bound address/package/cart revision; valid5m. No mock quote can be used in live checkout. Carrier can return another rate currency: reject unless a separate approved FX policy exists, never treat it as NGN.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.carrier_booking

- **Owner/module:** integration/provider; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Stable shipment business reference; UNKNOWN creates are reconciled by reference or operator before retry. Booking/label creation does not dispatch or debit stock.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.communication_destination

- **Owner/module:** experience/consent; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** A phone in an address/profile is not messaging consent. OTP proves control; separate channel/purpose grants required, latest withdrawal/suppression authoritative. No cross-owner party FK. At most one active phone per actor/channel; a second activation returns409 until the actor withdraws the old destination. Party-only eligibility resolves that one destination, never arbitrarily selects among phones.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.destination_verification

- **Owner/module:** experience/consent; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Six-digit CSPRNG proof code, expiry10m, max5 tries, max3 requests/hour/party+destination. Erase ciphertext on confirm/expiry; no plaintext code/events/logs; not a Keycloak login/MFA replacement.
- **Audit:** Versioned actor-attributed changes.

### Entity experience.inbound_message

- **Owner/module:** experience/lead; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Signed WhatsApp inbound is a contact/lead, never authentication or authority to inspect orders; STOP suppresses matching phone/channel immediately. No autonomous financial/AI commands.
- **Audit:** Versioned actor-attributed changes.

### Entity integration.message_receipt

- **Owner/module:** integration/notification; BP1.
- **Fields/relations/keys:** section10 exact table; EXTERNAL references are never SQL FKs.
- **Invariant:** Persist verified callback mapping before HTTP acknowledgment; duplicate and out-of-order statuses cannot regress DELIVERED. SENT/QUEUED is not delivery proof.
- **Audit:** Append-only evidence.


# Master section9: Database Architecture

Topology: one `nigson` PostgreSQL 18 database, four owning schemas. Runtime roles `nigson_<service>_app` receive schema USAGE and only owned-object CRUD, with immutable ledgers/audit INSERT and SELECT only. Migration roles `nigson_<service>_migrator` own that schema and Liquibase tracking objects. Bootstrap superuser is infrastructure-only, never an application secret. Revoke CREATE from public schema and cross-service USAGE; set explicit search_path. An acceptance test attempts cross-owner reads/writes and must fail.

Use application UUIDv7 for business identifiers, generated before insert so event/correlation IDs exist in the transaction. PostgreSQL's uuid type stores them; do not rely on monotonically contiguous identifiers. Framework session keys use the framework's required char(36) shape. Entity versions are bigint >=0 incremented once per aggregate mutation. Timestamps are timestamptz UTC, not local timestamp.

Naming: snake_case tables/columns; singular names, explicit constraints `<table>_<fields>_{pk,fk,uq,ck,ix}`. Use `sales_order` for reserved SQL keyword avoidance. All FKs are same-owner schema. No cross-schema join, trigger, FK, view or security-definer function. Service projections ingest minimal events and can rebuild via scoped snapshot APIs. `ON DELETE RESTRICT` is the default; CASCADE only for framework sessions and nonhistorical draft children after explicit validated deletion.

Mutable business rows normally contain `id uuid PK`, `created_at`, `created_by`, `updated_at`, `updated_by`, `version`; full column types below. Append-only evidence rows contain id/creation actor/time and omit update/version columns. Framework tracking tables follow upstream schema and are explicitly exempt. Soft deletion is only where marked; historical orders/payment/stock evidence are never hidden by a deleted flag. Slugs remain unique after archive unless an explicit redirect policy is added.

Validate JSONB against versioned schemas at the application boundary; size cap 64KiB for specifications/rules and 1MiB approved content. No JSONB blobs for money/stock ledgers. Index FKs and primary lookup/filter paths; specify GIN full-text and HNSW vectors. No automatic partitioning at launch. Introduce monthly time partitioning only after table size/query/retention evidence, with Liquibase and a migration plan preserving uniqueness semantics.

Encrypt RDS/S3/backups via KMS. Envelope-encrypt refresh tokens, private webhook payloads, sensitive application emails and checkpoint state. Deterministic HMAC-SHA256 lookup hashes use a dedicated rotating keyed secret; ordinary SHA256 is insufficient for enumerable emails. Hash columns named email_hash use this keyed definition. Keys/nonce/ciphertext/tag/version are packaged in a validated envelope; no encryption key in a row.

Archival/retention policy is configuration and actor audited. Jobs process bounded batches, honor active legal holds and provider uncertainty, and never purge evidence before policy approval. Outbox/dedup retention must exceed queue and replay windows. Reconcile balance/movement and payment/order totals nightly. Point-in-time restore, immutable backups and access-separated audit export are tested. All schema changes including framework session/checkpointer/index definitions go through Liquibase.


Keycloak is a platform-owned separate logical database and identity role. Prefer the same managed PostgreSQL instance only if the pinned Keycloak supported matrix includes that engine version; otherwise provision a compatible separate managed identity instance. Domain runtime/migration roles have no identity-table access. Include its pool/restore/upgrade budget; domain owner rules unchanged.


# Master section10: Complete Database Schema

Column notation: NN = NOT NULL; NULL = nullable; PK = primary key; UQ = unique constraint/index; IX = B-tree index unless an operator class is stated; FK = same-owner foreign key with RESTRICT unless explicit; EXTERNAL = UUID reference to another service, no database FK. JSON default `{}`/`[]` means a typed JSONB literal. `currency.code` is a same-schema unique target. Every varchar and enum length must be enforced. CHECK expressions below are normative predicates; cross-row checks must use locked application commands, not illegal SQL CHECK subqueries.

Create all tables first, then cyclic FKs in a dedicated constraints changeset. For NULL-aware cart uniqueness use `COALESCE(organization_id, '00000000-0000-0000-0000-000000000000'::uuid)` with a constraint prohibiting nil business UUIDs. Lifecycle CHECK predicates and domain enums are detailed in section 17. Add expression/default search_vector columns via the search changesets.

### commerce.party

Owner: `commerce`; module: `party`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
identity_subject varchar(200) NN
identity_issuer varchar(500) NN
email varchar(254) NN
display_name varchar(160) NN
status varchar(20) NN DEFAULT ACTIVE
email_verified boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(identity_issuer,identity_subject); IX(lower(email)).
- **Checks:** status IN (ACTIVE,SUSPENDED,ANONYMIZED).
- **Business invariant:** The identity issuer and subject pair is the immutable identity mapping; do not use email as an authentication key.

### commerce.customer_profile

Owner: `commerce`; module: `party`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
phone varchar(32) NULL
locale varchar(16) NN DEFAULT en
preferred_currency char(3) NULL
```

- **Keys and indexes:** UQ(party_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** At most one profile per party; marketing consent is owned by experience.

### commerce.staff_role_assignment

Owner: `commerce`; module: `authorization`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
role varchar(30) NN
granted_by uuid NN FK party
revoked_at timestamptz NULL
```

- **Keys and indexes:** UQ(party_id,role) WHERE revoked_at IS NULL.
- **Checks:** role IN (ADMIN,CONTENT_EDITOR,CONTENT_PUBLISHER,SALES,OPERATIONS,FINANCE,ANALYST).
- **Business invariant:** Administrative assignment is audited; a role change invalidates affected BFF sessions.

### commerce.organization

Owner: `commerce`; module: `party`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
name varchar(200) NN
business_reference varchar(100) NULL
country_code char(2) NN
status varchar(20) NN DEFAULT PENDING
```

- **Keys and indexes:** UQ(country_code,business_reference) WHERE business_reference IS NOT NULL.
- **Checks:** status IN (PENDING,ACTIVE,SUSPENDED).
- **Business invariant:** Organization lifecycle is distinct from identity account lifecycle.

### commerce.organization_membership

Owner: `commerce`; module: `party`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
organization_id uuid NN FK organization
party_id uuid NN FK party
role varchar(20) NN
status varchar(20) NN
```

- **Keys and indexes:** UQ(organization_id,party_id).
- **Checks:** role IN (OWNER,BUYER); status IN (ACTIVE,SUSPENDED,REVOKED).
- **Business invariant:** Every organizational operation checks both active membership and current organization entitlement.

### commerce.address

Owner: `commerce`; module: `party`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
organization_id uuid NULL FK organization
label varchar(60) NULL
recipient varchar(160) NN
phone varchar(32) NN
line1 varchar(200) NN
line2 varchar(200) NULL
city varchar(100) NN
region varchar(100) NULL
postal_code varchar(24) NULL
country_code char(2) NN
is_default boolean NN DEFAULT false
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** IX(party_id); IX(organization_id); UQ(party_id) WHERE is_default AND deleted_at IS NULL.
- **Checks:** No additional table-level predicate..
- **Business invariant:** Soft deletion affects future checkout only; orders preserve an immutable address snapshot.

### commerce.account_entitlement

Owner: `commerce`; module: `authorization`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
organization_id uuid NN FK organization
entitlement varchar(40) NN
status varchar(20) NN
source_application_id uuid NN EXTERNAL experience.distributor_application
approval_command_id uuid NN
activated_at timestamptz NULL
suspended_at timestamptz NULL
```

- **Keys and indexes:** UQ(organization_id,entitlement); UQ(approval_command_id).
- **Checks:** status IN (PENDING,ACTIVE,SUSPENDED,REVOKED).
- **Business invariant:** No external FK; repeat approval command returns the existing acknowledged result.

### commerce.brand

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
name varchar(120) NN
slug varchar(140) NN
active boolean NN DEFAULT true
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(slug).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Referenced brands are deactivated, not cascade deleted.

### commerce.category

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
parent_id uuid NULL FK category
name varchar(120) NN
slug varchar(140) NN
sort_order integer NN DEFAULT 0
active boolean NN DEFAULT true
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(slug); IX(parent_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Reject ancestor cycles and depth above 5.

### commerce.product

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
brand_id uuid NULL FK brand
division_code varchar(40) NULL
name varchar(200) NN
slug varchar(220) NN
description text NN
specifications jsonb NN DEFAULT {}
status varchar(20) NN DEFAULT DRAFT
revision bigint NN DEFAULT 1
published_at timestamptz NULL
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(slug); GIN(search_vector); IX(status,published_at).
- **Checks:** status IN (DRAFT,ACTIVE,ARCHIVED); jsonb_typeof(specifications)=object; revision>0.
- **Business invariant:** Publishing requires at least one active SKU, category and approved image. search_vector is generated from name and description; see search section.

### commerce.sku

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NN FK product
code varchar(80) NN
variant_name varchar(160) NN
unit varchar(20) NN
quantity_scale smallint NN DEFAULT 0
pack_size numeric(20,3) NN DEFAULT 1
weight_kg numeric(12,3) NULL
dimensions jsonb NULL
active boolean NN DEFAULT true
carrier_packaging jsonb NULL
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(code); IX(product_id).
- **Checks:** quantity_scale BETWEEN 0 AND 3; pack_size>0; weight_kg IS NULL OR weight_kg>=0.
- **Business invariant:** Stock, pricing and ordering quantities use this SKU unit and scale. Carrier-ready SKU requires an approved server-owned packaging rule and positive weight/dimensions; browser values cannot reduce shipping weight.

### commerce.product_category

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NN FK product
category_id uuid NN FK category
```

- **Keys and indexes:** UQ(product_id,category_id); IX(category_id,product_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Removing this link does not delete the category or product.

### commerce.product_attribute

Owner: `commerce`; module: `catalogue`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NN FK product
attribute_key varchar(80) NN
value jsonb NN
schema_version integer NN DEFAULT 1
```

- **Keys and indexes:** UQ(product_id,attribute_key).
- **Checks:** schema_version>0.
- **Business invariant:** Validate each attribute against its configured type and length; no arbitrary HTML.

### commerce.asset_reference

Owner: `commerce`; module: `files`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NULL FK product
sku_id uuid NULL FK sku
object_key varchar(500) NN
purpose varchar(20) NN
mime_type varchar(100) NN
byte_size bigint NN
checksum_sha256 char(64) NN
status varchar(20) NN
sort_order integer NN DEFAULT 0
classification varchar(20) NN DEFAULT PUBLIC
shipment_id uuid NULL FK shipment
```

- **Keys and indexes:** UQ(object_key); IX(product_id,purpose).
- **Checks:** byte_size>0; purpose IN (IMAGE,BROCHURE,SHIPPING_LABEL); status IN (UPLOADING,QUARANTINED,CLEAN,PUBLISHED,REJECTED); classification IN (PUBLIC,PRIVATE).
- **Business invariant:** Only clean assets may be published; public URLs use immutable object keys. SHIPPING_LABEL is generated by trusted carrier result only, always PRIVATE and staff OPERATIONS-readable; excluded from product/CDN APIs and browser upload purposes.

### commerce.file_transfer

Owner: `commerce`; module: `files`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK FK asset_reference
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
owner_party_id uuid NN FK party
source_key varchar(500) NN
final_key varchar(500) NN
upload_expires_at timestamptz NN
scan_command_id uuid NULL
source_version varchar(200) NULL
source_etag varchar(200) NULL
state varchar(20) NN DEFAULT UPLOADING
copy_lease_until timestamptz NULL
scan_request_hash char(64) NULL
scan_result varchar(8) NULL
scanner_version varchar(100) NULL
```

- **Keys and indexes:** UQ(scan_command_id); IX(owner_party_id); IX(state,updated_at).
- **Checks:** state IN (UPLOADING,QUARANTINED,COPYING,UNKNOWN,CLEAN,REJECTED); scan_result IN (CLEAN,REJECTED).
- **Business invariant:** ADR-009 additive owned transfer intent; verified source version/ETag and emitted scan event ID bind callbacks. Provider HTTP occurs outside transactions; expired copy lease becomes UNKNOWN and final-object reconciliation precedes any further decision. No blind copy retry or terminal scan reversal. Immutable source/final keys never include original filenames; owner-party and purpose/resource checks govern detached uploads.

### commerce.currency

Owner: `commerce`; module: `market`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code char(3) NN
minor_unit smallint NN
active boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(code).
- **Checks:** minor_unit BETWEEN 0 AND 4.
- **Business invariant:** Seed supported ISO currency metadata; activate only approved currencies.

### commerce.market

Owner: `commerce`; module: `market`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(32) NN
name varchar(100) NN
currency_code char(3) NN FK currency.code
enabled boolean NN DEFAULT false
policy_revision bigint NN DEFAULT 1
tax_policy jsonb NN DEFAULT {}
returns_policy_revision varchar(80) NULL
retail_price_book_id uuid NULL FK price_book
```

- **Keys and indexes:** UQ(code).
- **Checks:** policy_revision>0.
- **Business invariant:** Production baseline NG/Nigeria, currency NGN, destination NG; international markets remain disabled until BP5 approval. An approved retail book, tax/returns and carrier service policy are required before live selling. Market approval itself is complete.

### commerce.market_destination

Owner: `commerce`; module: `market`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
market_id uuid NN FK market
country_code char(2) NN
address_rules jsonb NN
delivery_enabled boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(market_id,country_code).
- **Checks:** jsonb_typeof(address_rules)=object.
- **Business invariant:** The approved destination and address rule are checked before any reservation.

### commerce.product_market_eligibility

Owner: `commerce`; module: `market`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NN FK product
market_id uuid NN FK market
eligible boolean NN DEFAULT false
reason varchar(300) NULL
```

- **Keys and indexes:** UQ(product_id,market_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Missing eligibility means disallowed; no permissive default.

### commerce.price_book

Owner: `commerce`; module: `pricing`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(60) NN
currency_code char(3) NN FK currency.code
segment varchar(20) NN
revision bigint NN
effective_from timestamptz NN
effective_until timestamptz NULL
active boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(code,revision); IX(segment,currency_code,effective_from).
- **Checks:** segment IN (RETAIL,DISTRIBUTOR); revision>0; effective_until IS NULL OR effective_until>effective_from.
- **Business invariant:** Published price book revisions are immutable; changes produce a new revision.

### commerce.price_entry

Owner: `commerce`; module: `pricing`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
price_book_id uuid NN FK price_book
sku_id uuid NN FK sku
minimum_quantity numeric(20,3) NN DEFAULT 1
unit_amount numeric(19,4) NN
```

- **Keys and indexes:** UQ(price_book_id,sku_id,minimum_quantity); IX(sku_id,price_book_id).
- **Checks:** minimum_quantity>0; unit_amount>=0.
- **Business invariant:** Resolve the greatest qualifying quantity threshold; amounts must conform to currency minor units.

### commerce.price_assignment

Owner: `commerce`; module: `pricing`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
organization_id uuid NN FK organization
market_id uuid NN FK market
price_book_id uuid NN FK price_book
active boolean NN DEFAULT true
```

- **Keys and indexes:** UQ(organization_id,market_id) WHERE active.
- **Checks:** No additional table-level predicate..
- **Business invariant:** Book currency must match market and segment DISTRIBUTOR; enforce in application transaction.

### commerce.wholesale_rule

Owner: `commerce`; module: `pricing`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
organization_id uuid NULL FK organization
sku_id uuid NULL FK sku
market_id uuid NN FK market
minimum_quantity numeric(20,3) NULL
pack_multiple numeric(20,3) NULL
minimum_order_amount numeric(19,4) NULL
rule_revision bigint NN
```

- **Keys and indexes:** IX(organization_id,market_id,sku_id).
- **Checks:** minimum_quantity IS NULL OR minimum_quantity>0; pack_multiple IS NULL OR pack_multiple>0; minimum_order_amount IS NULL OR minimum_order_amount>=0.
- **Business invariant:** Specific organization rule overrides the market default; quantity rules and order minimums are separately evaluated.

### commerce.delivery_rule

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
market_id uuid NN FK market
country_code char(2) NN
region_code varchar(80) NULL
postal_prefix varchar(24) NULL
fee numeric(19,4) NN
free_above numeric(19,4) NULL
priority integer NN DEFAULT 0
active boolean NN DEFAULT true
```

- **Keys and indexes:** IX(market_id,country_code,priority).
- **Checks:** fee>=0; free_above IS NULL OR free_above>=0.
- **Business invariant:** Match most specific area then priority; reject ambiguous matching rules at activation.

### commerce.cart

Owner: `commerce`; module: `cart`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
organization_id uuid NULL FK organization
market_id uuid NN FK market
status varchar(20) NN DEFAULT ACTIVE
last_activity_at timestamptz NN
converted_order_id uuid NULL FK sales_order
```

- **Keys and indexes:** IX(party_id,status); IX(status,last_activity_at).
- **Checks:** status IN (ACTIVE,CHECKOUT_PENDING,CONVERTED,EXPIRED).
- **Business invariant:** One active cart per party and organization and market; enforce a COALESCE expression unique index with documented UUID nil sentinel.

### commerce.cart_line

Owner: `commerce`; module: `cart`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
cart_id uuid NN FK cart
sku_id uuid NN FK sku
quantity numeric(20,3) NN
```

- **Keys and indexes:** UQ(cart_id,sku_id).
- **Checks:** quantity>0.
- **Business invariant:** Quantity scale and pack multiples must conform to the SKU and current wholesale rules.

### commerce.checkout_quote

Owner: `commerce`; module: `pricing`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
cart_id uuid NN FK cart
cart_version bigint NN
market_revision bigint NN
address_snapshot jsonb NN
price_snapshot jsonb NN
quote_hash char(64) NN
expires_at timestamptz NN
carrier_quote_id uuid NN FK carrier_quote
```

- **Keys and indexes:** IX(cart_id,expires_at).
- **Checks:** expires_at>created_at.
- **Business invariant:** Signed by server state, immutable; expires after 5 minutes; reservation creation verifies current rule and cart versions.

### commerce.sales_order

Owner: `commerce`; module: `order`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
order_number varchar(40) NN
party_id uuid NN FK party
organization_id uuid NULL FK organization
market_id uuid NN FK market
cart_id uuid NULL FK cart
status varchar(30) NN
payment_status varchar(30) NN
fulfillment_status varchar(30) NN
currency_code char(3) NN FK currency.code
subtotal numeric(19,4) NN
discount_total numeric(19,4) NN
tax_total numeric(19,4) NN
delivery_total numeric(19,4) NN
grand_total numeric(19,4) NN
refunded_total numeric(19,4) NN DEFAULT 0
address_snapshot jsonb NN
commercial_snapshot jsonb NN
reservation_expires_at timestamptz NULL
accepted_quote_revision_id uuid NULL FK quote_revision
hold_reason varchar(300) NULL
carrier_quote_id uuid NN FK carrier_quote
carrier_snapshot jsonb NN
```

- **Keys and indexes:** UQ(order_number); UQ(cart_id) WHERE cart_id IS NOT NULL; IX(party_id,created_at); IX(organization_id,created_at); IX(status,created_at).
- **Checks:** grand_total=subtotal-discount_total+tax_total+delivery_total; subtotal>=0; discount_total>=0; tax_total>=0; delivery_total>=0; grand_total>=0; refunded_total BETWEEN 0 AND grand_total; status IN (PENDING,PAID,PROCESSING,SHIPPED,DELIVERED,CANCELLED,PAYMENT_HOLD,REFUNDING,REFUNDED); payment_status IN (UNPAID,PENDING,PAID,PARTIALLY_REFUNDED,REFUNDED,FAILED); fulfillment_status IN (UNFULFILLED,PROCESSING,PARTIALLY_SHIPPED,SHIPPED,DELIVERED,CANCELLED,HOLD).
- **Business invariant:** Use sales_order to avoid the reserved SQL word order. State transitions in section 17 are exhaustive.

### commerce.order_line

Owner: `commerce`; module: `order`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
order_id uuid NN FK sales_order
sku_id uuid NN FK sku
sku_code varchar(80) NN
product_name varchar(200) NN
unit varchar(20) NN
quantity numeric(20,3) NN
unit_amount numeric(19,4) NN
discount_amount numeric(19,4) NN
tax_amount numeric(19,4) NN
line_total numeric(19,4) NN
price_book_revision bigint NN
```

- **Keys and indexes:** IX(order_id).
- **Checks:** quantity>0; unit_amount>=0; discount_amount>=0; tax_amount>=0; line_total>=0.
- **Business invariant:** Commercial values are immutable snapshots; track refund and fulfillment quantities in separate records.

### commerce.order_total_component

Owner: `commerce`; module: `order`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
order_id uuid NN FK sales_order
component_type varchar(20) NN
amount numeric(19,4) NN
source_rule_id uuid NULL
source_revision bigint NULL
metadata jsonb NN DEFAULT {}
```

- **Keys and indexes:** IX(order_id).
- **Checks:** component_type IN (SUBTOTAL,DISCOUNT,TAX,DELIVERY,REWARD).
- **Business invariant:** Components reconcile exactly to the persisted grand total.

### commerce.order_status_history

Owner: `commerce`; module: `order`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
order_id uuid NN FK sales_order
previous_status varchar(30) NULL
new_status varchar(30) NN
reason varchar(500) NN
source_event_id uuid NULL
```

- **Keys and indexes:** IX(order_id,created_at).
- **Checks:** No additional table-level predicate..
- **Business invariant:** History is inserted in the same transaction as the state change.

### commerce.stock_location

Owner: `commerce`; module: `inventory`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(40) NN
name varchar(120) NN
active boolean NN DEFAULT true
```

- **Keys and indexes:** UQ(code).
- **Checks:** No additional table-level predicate..
- **Business invariant:** One launch location; schema supports more. Default fulfillment selects configured priority, never arbitrary row order.

### commerce.inventory_balance

Owner: `commerce`; module: `inventory`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
sku_id uuid NN FK sku
location_id uuid NN FK stock_location
on_hand numeric(20,3) NN DEFAULT 0
reserved numeric(20,3) NN DEFAULT 0
allocated numeric(20,3) NN DEFAULT 0
```

- **Keys and indexes:** UQ(sku_id,location_id).
- **Checks:** on_hand>=0; reserved>=0; allocated>=0; on_hand>=reserved+allocated.
- **Business invariant:** available=on_hand-reserved-allocated; no cached value authorizes a sale.

### commerce.inventory_reservation

Owner: `commerce`; module: `inventory`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
order_id uuid NN FK sales_order
balance_id uuid NN FK inventory_balance
quantity numeric(20,3) NN
status varchar(20) NN
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(order_id,balance_id); IX(status,expires_at).
- **Checks:** quantity>0; status IN (RESERVED,ALLOCATED,RELEASED,DISPATCHED).
- **Business invariant:** Transitions lock order first and balance rows in sorted ID order; quantity may split through shipment lines.

### commerce.inventory_movement

Owner: `commerce`; module: `inventory`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
balance_id uuid NN FK inventory_balance
order_id uuid NULL FK sales_order
operation_key varchar(150) NN
movement_type varchar(30) NN
delta_on_hand numeric(20,3) NN
delta_reserved numeric(20,3) NN
delta_allocated numeric(20,3) NN
reason varchar(500) NN
order_line_id uuid NULL FK order_line
return_kind varchar(30) NULL
receipt_reference varchar(120) NULL
```

- **Keys and indexes:** UQ(operation_key); IX(balance_id,created_at).
- **Checks:** ; return_kind IS NULL OR return_kind IN (PHYSICAL_RETURN,UNSHIPPED_CANCEL).
- **Business invariant:** Reconcile sum of deltas to balance; operation keys make mutations exactly once at the business level. Return business_key is stable return command + line; cumulative returned quantity cannot exceed dispatched or still-allocated eligible quantity.

### commerce.payment_attempt

Owner: `commerce`; module: `payment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
order_id uuid NN FK sales_order
provider varchar(40) NN
reference varchar(100) NN
merchant_account varchar(100) NN
expected_amount numeric(19,4) NN
currency_code char(3) NN FK currency.code
state varchar(25) NN
checkout_url varchar(1000) NULL
initiating_until timestamptz NULL
verified_at timestamptz NULL
next_reconcile_at timestamptz NULL
provider_mode varchar(10) NN DEFAULT MOCK
```

- **Keys and indexes:** UQ(provider,merchant_account,reference); IX(state,next_reconcile_at); UQ(order_id) WHERE state IN (CREATED,INITIATING,PENDING,UNKNOWN).
- **Checks:** expected_amount>=0; state IN (CREATED,INITIATING,PENDING,UNKNOWN,SUCCEEDED,FAILED,CANCELLED); provider_mode IN (MOCK,TEST,LIVE).
- **Business invariant:** Never create a second payable attempt while provider outcome is uncertain. Mode is recorded at creation; provider callback/verify must match configured environment/account and saved mode. No mock/test financial state may be reused for live settlement.

### commerce.payment_transaction

Owner: `commerce`; module: `payment`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
attempt_id uuid NN FK payment_attempt
provider_transaction_id varchar(120) NN
settled_amount numeric(19,4) NN
currency_code char(3) NN FK currency.code
provider_status varchar(40) NN
settled_at timestamptz NN
verification_hash char(64) NN
provider_mode varchar(10) NN DEFAULT MOCK
```

- **Keys and indexes:** UQ(attempt_id); UQ(provider_transaction_id).
- **Checks:** settled_amount>=0; provider_mode IN (MOCK,TEST,LIVE).
- **Business invariant:** Only independently verified successful settlement may create this immutable record. Mode is recorded at creation; provider callback/verify must match configured environment/account and saved mode. No mock/test financial state may be reused for live settlement.

### commerce.webhook_receipt

Owner: `commerce`; module: `payment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
provider varchar(40) NN
merchant_account varchar(100) NN
event_key varchar(200) NN
body_hash char(64) NN
payload_ciphertext bytea NN
verified_signature boolean NN
received_at timestamptz NN
state varchar(20) NN
rejection_code varchar(60) NULL
```

- **Keys and indexes:** UQ(provider,merchant_account,event_key); IX(state,received_at).
- **Checks:** state IN (RECEIVED,APPLIED,REJECTED,RETRY).
- **Business invariant:** Authenticate raw bytes before parsing; event_key includes event type and stable provider object ID; fallback keyed canonical body digest.

### commerce.refund

Owner: `commerce`; module: `payment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
order_id uuid NN FK sales_order
attempt_id uuid NN FK payment_attempt
request_key varchar(100) NN
provider_refund_id varchar(120) NULL
amount numeric(19,4) NN
currency_code char(3) NN FK currency.code
state varchar(20) NN
reason varchar(500) NN
requested_by uuid NN FK party
approved_by uuid NULL FK party
provider_reference varchar(120) NN
```

- **Keys and indexes:** UQ(request_key); UQ(provider_reference); UQ(provider_refund_id) WHERE provider_refund_id IS NOT NULL; IX(state,created_at).
- **Checks:** amount>0; state IN (REQUESTED,APPROVED,SUBMITTING,PENDING,UNKNOWN,SUCCEEDED,FAILED,REJECTED).
- **Business invariant:** Under order lock, successful plus in-flight refunds cannot exceed settlement. Provider uncertainty retains the reserved refund amount.

### commerce.refund_line

Owner: `commerce`; module: `payment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
refund_id uuid NN FK refund
order_line_id uuid NN FK order_line
quantity numeric(20,3) NN
amount numeric(19,4) NN
```

- **Keys and indexes:** UQ(refund_id,order_line_id).
- **Checks:** quantity>0; amount>=0.
- **Business invariant:** Cumulative refunded quantity cannot exceed purchased quantity; shipping-only refunds can omit lines.

### commerce.shipment

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
order_id uuid NN FK sales_order
provider varchar(40) NN DEFAULT MANUAL
provider_reference varchar(120) NULL
tracking_url varchar(1000) NULL
status varchar(20) NN
dispatched_at timestamptz NULL
delivered_at timestamptz NULL
booking_request_id uuid NULL
package_snapshot jsonb NULL
label_asset_id uuid NULL FK asset_reference
```

- **Keys and indexes:** IX(order_id); UQ(provider,provider_reference) WHERE provider_reference IS NOT NULL.
- **Checks:** status IN (CREATED,READY,SHIPPED,DELIVERED,CANCELLED,EXCEPTION).
- **Business invariant:** Dispatch requires paid, unheld order and sufficient allocated stock; verified return is a separate stock adjustment.

### commerce.shipment_line

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
shipment_id uuid NN FK shipment
order_line_id uuid NN FK order_line
balance_id uuid NN FK inventory_balance
quantity numeric(20,3) NN
```

- **Keys and indexes:** UQ(shipment_id,order_line_id,balance_id).
- **Checks:** quantity>0.
- **Business invariant:** Cumulative dispatched quantity cannot exceed allocated noncancelled line quantity.

### commerce.shipment_event

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
shipment_id uuid NN FK shipment
source varchar(40) NN
event_key varchar(180) NN
status varchar(20) NN
occurred_at timestamptz NN
note varchar(500) NULL
```

- **Keys and indexes:** UQ(source,event_key); IX(shipment_id,occurred_at).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Ignore stale event regressions; persist event evidence while keeping the current lifecycle monotonic.

### commerce.quote

Owner: `commerce`; module: `quote`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
organization_id uuid NULL FK organization
market_id uuid NN FK market
source_lead_id uuid NN EXTERNAL experience.lead
status varchar(20) NN
current_revision_id uuid NULL FK quote_revision
accepted_order_id uuid NULL FK sales_order
```

- **Keys and indexes:** IX(party_id,created_at); UQ(source_lead_id).
- **Checks:** status IN (DRAFT,OFFERED,ACCEPTED,REJECTED,EXPIRED,CANCELLED).
- **Business invariant:** Lead and quote synchronization uses acknowledged commands; sales approval is explicit.

### commerce.quote_revision

Owner: `commerce`; module: `quote`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
quote_id uuid NN FK quote
revision integer NN
currency_code char(3) NN FK currency.code
valid_until timestamptz NN
terms text NN
approval_status varchar(20) NN
approved_by uuid NULL FK party
total numeric(19,4) NN
```

- **Keys and indexes:** UQ(quote_id,revision).
- **Checks:** revision>0; total>=0; approval_status IN (DRAFT,APPROVED,REJECTED).
- **Business invariant:** Offering freezes this revision; modifications produce a new revision.

### commerce.quote_line

Owner: `commerce`; module: `quote`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
revision_id uuid NN FK quote_revision
sku_id uuid NN FK sku
quantity numeric(20,3) NN
agreed_unit_amount numeric(19,4) NN
discount_amount numeric(19,4) NN DEFAULT 0
tax_amount numeric(19,4) NN DEFAULT 0
```

- **Keys and indexes:** IX(revision_id).
- **Checks:** quantity>0; agreed_unit_amount>=0; discount_amount>=0; tax_amount>=0.
- **Business invariant:** Only approved quote prices override normal repricing; eligibility and inventory still revalidate.

### commerce.promotion_rule

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(80) NN
revision bigint NN
kind varchar(30) NN
market_id uuid NN FK market
starts_at timestamptz NN
ends_at timestamptz NN
parameters jsonb NN
eligibility jsonb NN
stacking_policy varchar(20) NN
enabled boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(code,revision); IX(market_id,enabled,starts_at).
- **Checks:** revision>0; ends_at>starts_at; kind IN (PERCENT,FIXED,FREE_DELIVERY,SPEND_REWARD,REFERRAL,DEALER); stacking_policy IN (EXCLUSIVE,STACKABLE).
- **Business invariant:** Validate JSON against versioned rule schemas. Published commercial rule revisions do not mutate.

### commerce.coupon

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
rule_id uuid NN FK promotion_rule
code varchar(80) NN
party_id uuid NULL FK party
maximum_uses integer NN DEFAULT 1
maximum_uses_per_party integer NN DEFAULT 1
reserved_uses integer NN DEFAULT 0
consumed_uses integer NN DEFAULT 0
expires_at timestamptz NN
active boolean NN DEFAULT true
```

- **Keys and indexes:** UQ(lower(code)); IX(party_id,active).
- **Checks:** maximum_uses>0; maximum_uses_per_party>0; reserved_uses>=0; consumed_uses>=0; reserved_uses+consumed_uses<=maximum_uses.
- **Business invariant:** Reserve usage under lock with order; nonzero defaults are safety limits, not approved reward policy.

### commerce.coupon_redemption

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
coupon_id uuid NN FK coupon
party_id uuid NN FK party
order_id uuid NN FK sales_order
state varchar(20) NN
amount numeric(19,4) NN
expires_at timestamptz NULL
```

- **Keys and indexes:** UQ(coupon_id,order_id); IX(coupon_id,party_id).
- **Checks:** state IN (RESERVED,CONSUMED,RELEASED,REVERSED); amount>=0.
- **Business invariant:** Per-party usage counts RESERVED and CONSUMED; refund reversal follows configured campaign policy.

### commerce.reward_entry

Owner: `commerce`; module: `reward`; introduced: BP2; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
party_id uuid NN FK party
organization_id uuid NULL FK organization
order_id uuid NULL FK sales_order
rule_id uuid NN FK promotion_rule
kind varchar(20) NN
amount numeric(19,4) NN
currency_code char(3) NN FK currency.code
business_key varchar(180) NN
reverses_id uuid NULL FK reward_entry
expires_at timestamptz NULL
coupon_id uuid NULL FK coupon
```

- **Keys and indexes:** UQ(business_key); IX(reverses_id); IX(party_id,created_at).
- **Checks:** kind IN (EARN,REDEEM,EXPIRE,REVERSE,DEALER_ISSUE); amount>=0.
- **Business invariant:** Represent value changes as new entries. Unspent balance is the signed ledger sum, never an editable profile counter. Multiple partial-refund reversals may reference one earning. Lock earning/order and enforce cumulative reverse amount<=earned; business_key includes refund_id. Round cumulative entitlement then subtract prior reversals.

### commerce.referral_code

Owner: `commerce`; module: `reward`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN FK party
code varchar(60) NN
active boolean NN DEFAULT true
```

- **Keys and indexes:** UQ(code); UQ(party_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Never reveal inviter personal details publicly.

### commerce.referral_attribution

Owner: `commerce`; module: `reward`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
referrer_party_id uuid NN FK party
referred_party_id uuid NN FK party
qualifying_order_id uuid NULL FK sales_order
rule_id uuid NN FK promotion_rule
state varchar(20) NN
qualify_after timestamptz NULL
```

- **Keys and indexes:** UQ(referred_party_id).
- **Checks:** referrer_party_id<>referred_party_id; state IN (ATTRIBUTED,PENDING,QUALIFIED,REVERSED,INELIGIBLE).
- **Business invariant:** Default first attribution wins; exactly one qualifying purchase reward, with a configured hold period.

### commerce.wheel_campaign

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
rule_id uuid NN FK promotion_rule
market_id uuid NN FK market
code varchar(80) NN
daily_limit integer NN DEFAULT 1
reward_budget numeric(19,4) NN
reserved_budget numeric(19,4) NN DEFAULT 0
spent_budget numeric(19,4) NN DEFAULT 0
enabled boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(code).
- **Checks:** daily_limit>0; reward_budget>=0; reserved_budget>=0; spent_budget>=0; reserved_budget+spent_budget<=reward_budget.
- **Business invariant:** Weighted outcome is sampled securely server side while campaign row protects budget.

### commerce.wheel_outcome

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
campaign_id uuid NN FK wheel_campaign
label varchar(100) NN
kind varchar(20) NN
weight integer NN
benefit jsonb NN
```

- **Keys and indexes:** IX(campaign_id).
- **Checks:** weight>0; kind IN (COUPON,FREE_DELIVERY,RETRY,NONE).
- **Business invariant:** Benefit JSON must reference a valid active rule; sum of active weights is strictly positive.

### commerce.wheel_draw

Owner: `commerce`; module: `promotion`; introduced: BP2; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
campaign_id uuid NN FK wheel_campaign
party_id uuid NN FK party
outcome_id uuid NN FK wheel_outcome
window_key date NN
draw_number integer NN
coupon_id uuid NULL FK coupon
request_key varchar(100) NN
```

- **Keys and indexes:** UQ(request_key); UQ(campaign_id,party_id,window_key,draw_number).
- **Checks:** draw_number>0.
- **Business invariant:** UTC day boundary default; draw number cannot exceed campaign daily limit; immutable result replay.

### commerce.product_recommendation

Owner: `commerce`; module: `catalogue`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NN FK product
related_product_id uuid NN FK product
kind varchar(20) NN
priority integer NN DEFAULT 0
active boolean NN DEFAULT true
```

- **Keys and indexes:** UQ(product_id,related_product_id,kind).
- **Checks:** product_id<>related_product_id; kind IN (RELATED,FEATURED).
- **Business invariant:** Curated rules precede learning-based recommendations; filter all results by active market eligibility.

### commerce.abandoned_cart_candidate

Owner: `commerce`; module: `cart`; introduced: BP4; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
cart_id uuid NN FK cart
party_id uuid NN FK party
scheduled_at timestamptz NN
status varchar(20) NN
notification_key varchar(160) NN
completed_at timestamptz NULL
```

- **Keys and indexes:** UQ(cart_id,notification_key); IX(status,scheduled_at).
- **Checks:** status IN (ELIGIBLE,REQUESTED,SUPPRESSED,COMPLETED).
- **Business invariant:** After 24 hours inactivity by default; recheck active cart and consent before send.

### experience.content_item

Owner: `experience`; module: `content`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
content_type varchar(30) NN
slug varchar(220) NN
locale varchar(16) NN
current_published_revision_id uuid NULL FK content_revision
status varchar(20) NN
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(content_type,slug,locale).
- **Checks:** content_type IN (HOME,ABOUT,LEADERSHIP,DIVISION,BLOG,VIDEO,FAQ,CAREERS,CONTACT); status IN (ACTIVE,ARCHIVED).
- **Business invariant:** Public API serves only the currently published approved revision.

### experience.content_revision

Owner: `experience`; module: `content`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
content_item_id uuid NN FK content_item
revision integer NN
title varchar(220) NN
body jsonb NN
metadata jsonb NN
state varchar(20) NN
scheduled_at timestamptz NULL
published_at timestamptz NULL
review_reason varchar(500) NULL
approved_by uuid NULL EXTERNAL commerce.party
checksum_sha256 char(64) NN
```

- **Keys and indexes:** UQ(content_item_id,revision); IX(state,scheduled_at).
- **Checks:** revision>0; state IN (DRAFT,REVIEW,APPROVED,PUBLISHED,WITHDRAWN,ARCHIVED).
- **Business invariant:** Published text is immutable; withdrawal changes status and current pointer, and immediately affects revision validation APIs.

### experience.file_asset

Owner: `experience`; module: `files`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
owner_party_id uuid NULL EXTERNAL commerce.party
organization_id uuid NULL EXTERNAL commerce.organization
object_key varchar(500) NN
purpose varchar(30) NN
mime_type varchar(100) NN
byte_size bigint NN
checksum_sha256 char(64) NN
classification varchar(20) NN
scan_status varchar(20) NN
scan_result varchar(300) NULL
linked_resource_type varchar(40) NULL
linked_resource_id uuid NULL
upload_session_id uuid NULL FK upload_session
```

- **Keys and indexes:** UQ(object_key); IX(owner_party_id); IX(scan_status,created_at).
- **Checks:** byte_size>0; classification IN (PUBLIC,PRIVATE); scan_status IN (UPLOADING,QUARANTINED,CLEAN,REJECTED); owner_party_id IS NOT NULL OR upload_session_id IS NOT NULL.
- **Business invariant:** Linked owner and purpose determine download access; no public URI for applications or business proofs.

### experience.lead

Owner: `experience`; module: `lead`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
type varchar(20) NN
party_id uuid NULL EXTERNAL commerce.party
organization_id uuid NULL EXTERNAL commerce.organization
name varchar(160) NN
email varchar(254) NN
phone varchar(32) NULL
subject varchar(200) NN
message text NN
status varchar(20) NN
assigned_to uuid NULL EXTERNAL commerce.party
priority varchar(10) NN DEFAULT NORMAL
follow_up_at timestamptz NULL
source varchar(40) NN
related_quote_id uuid NULL EXTERNAL commerce.quote
```

- **Keys and indexes:** IX(status,priority,created_at); IX(assigned_to,follow_up_at).
- **Checks:** type IN (CONTACT,COMPLAINT,QUOTE,DISTRIBUTOR); status IN (NEW,ASSIGNED,IN_PROGRESS,RESOLVED,CLOSED); priority IN (NORMAL,HIGH).
- **Business invariant:** Notification recipient group is configuration; complaint details never enter shared events.

### experience.lead_activity

Owner: `experience`; module: `lead`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
lead_id uuid NN FK lead
activity_type varchar(30) NN
note text NN
from_status varchar(20) NULL
to_status varchar(20) NULL
actor_party_id uuid NULL EXTERNAL commerce.party
```

- **Keys and indexes:** IX(lead_id,created_at).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Notes are staff-only and audit-preserved.

### experience.distributor_application

Owner: `experience`; module: `distributor`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN EXTERNAL commerce.party
organization_name varchar(200) NN
country_code char(2) NN
business_reference varchar(100) NULL
status varchar(30) NN
reviewer_party_id uuid NULL EXTERNAL commerce.party
review_reason varchar(500) NULL
decision_command_id uuid NULL
commerce_organization_id uuid NULL EXTERNAL commerce.organization
acknowledgment_revision bigint NULL
submitted_at timestamptz NN
decision_payload jsonb NN DEFAULT {}
```

- **Keys and indexes:** IX(party_id,submitted_at); UQ(decision_command_id) WHERE decision_command_id IS NOT NULL.
- **Checks:** status IN (SUBMITTED,UNDER_REVIEW,ACTIVATION_PENDING,APPROVED,REJECTED,SUSPENSION_PENDING,SUSPENDED).
- **Business invariant:** Distinguish commercial decision from acknowledged entitlement; retries reuse command ID.

### experience.application_document

Owner: `experience`; module: `distributor`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
application_id uuid NN FK distributor_application
file_asset_id uuid NN FK file_asset
document_type varchar(40) NN
```

- **Keys and indexes:** UQ(application_id,file_asset_id).
- **Checks:** No additional table-level predicate..
- **Business invariant:** All linked assets belong to the applicant and are CLEAN before review approval.

### experience.review

Owner: `experience`; module: `engagement`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
product_id uuid NULL EXTERNAL commerce.product
order_line_id uuid NULL EXTERNAL commerce.order_line
party_id uuid NN EXTERNAL commerce.party
kind varchar(20) NN
rating smallint NULL
title varchar(160) NULL
body text NN
state varchar(20) NN
verified_purchase boolean NN DEFAULT false
moderation_reason varchar(500) NULL
```

- **Keys and indexes:** IX(product_id,state,created_at); UQ(party_id,order_line_id) WHERE order_line_id IS NOT NULL.
- **Checks:** kind IN (PRODUCT,TESTIMONIAL); rating IS NULL OR rating BETWEEN 1 AND 5; state IN (PENDING,PUBLISHED,REJECTED,WITHDRAWN).
- **Business invariant:** Verified purchase comes from commerce proof API, never a client boolean.

### experience.consent_event

Owner: `experience`; module: `consent`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
party_id uuid NULL EXTERNAL commerce.party
email_hash char(64) NULL
channel varchar(20) NN
purpose varchar(30) NN
granted boolean NN
policy_version varchar(40) NN
source varchar(80) NN
occurred_at timestamptz NN
destination_hash char(64) NN
destination_kind varchar(10) NN
hash_key_version integer NN
```

- **Keys and indexes:** IX(email_hash,channel,purpose,occurred_at).
- **Checks:** channel IN (EMAIL,SMS,WHATSAPP); purpose IN (NEWSLETTER,MARKETING,CART_RECOVERY,ORDER_UPDATES); destination_kind IN (EMAIL,PHONE); hash_key_version>0.
- **Business invariant:** Append withdrawal and grant events. Transactional service notices are classified separately.

### experience.newsletter_subscription

Owner: `experience`; module: `consent`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NULL EXTERNAL commerce.party
email_ciphertext bytea NN
email_hash char(64) NN
state varchar(20) NN
verified_at timestamptz NULL
unsubscribe_token_hash char(64) NN
provider_contact_id varchar(120) NULL
confirmation_token_hash char(64) NULL
confirmation_expires_at timestamptz NULL
encrypted_confirmation_context bytea NULL
confirmation_context_expires_at timestamptz NULL
```

- **Keys and indexes:** UQ(email_hash); UQ(unsubscribe_token_hash).
- **Checks:** state IN (PENDING,ACTIVE,UNSUBSCRIBED,SUPPRESSED).
- **Business invariant:** Default double opt-in; unsubscribe succeeds even if provider sync is temporarily unavailable.

### experience.communication_suppression

Owner: `experience`; module: `consent`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
email_hash char(64) NULL
channel varchar(20) NN
reason varchar(40) NN
suppressed_at timestamptz NN
destination_hash char(64) NN
destination_kind varchar(10) NN
hash_key_version integer NN
```

- **Keys and indexes:** UQ(destination_hash,channel); IX(email_hash,channel).
- **Checks:** ; destination_kind IN (EMAIL,PHONE); hash_key_version>0.
- **Business invariant:** Local suppression overrides a historical grant; sending checks this authoritative service.

### experience.career_opportunity

Owner: `experience`; module: `careers`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
title varchar(180) NN
slug varchar(200) NN
kind varchar(30) NN
description text NN
fields_schema jsonb NN
state varchar(20) NN
closes_at timestamptz NULL
deleted_at timestamptz NULL
deleted_by varchar(200) NULL
```

- **Keys and indexes:** UQ(slug).
- **Checks:** kind IN (JOB,INTERNSHIP,INFLUENCER,AFFILIATE); state IN (DRAFT,PUBLISHED,CLOSED).
- **Business invariant:** Application form schema version is captured with submissions.

### experience.career_application

Owner: `experience`; module: `careers`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
opportunity_id uuid NN FK career_opportunity
name varchar(160) NN
email_ciphertext bytea NN
email_hash char(64) NN
answers jsonb NN
form_revision integer NN
cv_file_id uuid NULL FK file_asset
state varchar(20) NN
retention_until timestamptz NN
```

- **Keys and indexes:** IX(opportunity_id,state); IX(retention_until).
- **Checks:** form_revision>0; state IN (SUBMITTED,REVIEWED,SHORTLISTED,REJECTED,CLOSED).
- **Business invariant:** Applicant data is private, excluded from AI ingestion and marketing.

### experience.campaign

Owner: `experience`; module: `engagement`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(80) NN
title varchar(180) NN
market_id uuid NN EXTERNAL commerce.market
commercial_rule_id uuid NULL EXTERNAL commerce.promotion_rule
display_config jsonb NN
starts_at timestamptz NN
ends_at timestamptz NN
published boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(code).
- **Checks:** ends_at>starts_at.
- **Business invariant:** Campaign UI cannot create a commercial entitlement; commerce owns qualification.

### experience.channel_configuration

Owner: `experience`; module: `content`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
kind varchar(30) NN
key varchar(80) NN
public_value jsonb NN
active boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(kind,key).
- **Checks:** kind IN (WHATSAPP,SOCIAL,MAP,LEAD_RECIPIENT_GROUP,SEO).
- **Business invariant:** Public API excludes recipient addresses; secrets remain in Secrets Manager.

### integration.notification_template

Owner: `integration`; module: `notification`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(80) NN
revision integer NN
channel varchar(20) NN
locale varchar(16) NN
subject varchar(200) NULL
body text NN
classification varchar(20) NN
active boolean NN DEFAULT false
provider_template_id varchar(160) NULL
variables_schema jsonb NN DEFAULT {}
```

- **Keys and indexes:** UQ(code,revision,locale,channel).
- **Checks:** revision>0; channel IN (EMAIL,SMS,WHATSAPP); classification IN (TRANSACTIONAL,MARKETING).
- **Business invariant:** Templates use an allowlisted field schema; sanitize substitutions and prohibit executable template code.

### integration.notification_job

Owner: `integration`; module: `notification`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
business_key varchar(180) NN
source_event_id uuid NULL
owner_service varchar(30) NN
resource_id uuid NN
recipient_party_id uuid NULL
recipient_group varchar(80) NULL
template_code varchar(80) NN
template_revision integer NN
classification varchar(20) NN
state varchar(20) NN
next_attempt_at timestamptz NN
lease_until timestamptz NULL
attempt_count integer NN DEFAULT 0
provider_reference varchar(160) NULL
channel varchar(20) NN DEFAULT EMAIL
destination_id uuid NULL EXTERNAL experience.communication_destination
destination_hash char(64) NULL
provider_mode varchar(10) NN DEFAULT MOCK
```

- **Keys and indexes:** UQ(business_key); IX(state,next_attempt_at).
- **Checks:** state IN (PENDING,CLAIMED,SENT,DELIVERED,SUPPRESSED,FAILED,UNKNOWN); attempt_count>=0; channel IN (EMAIL,SMS,WHATSAPP); provider_mode IN (MOCK,SANDBOX,LIVE).
- **Business invariant:** Fetch minimum authorized delivery context just before send; fail closed if consent cannot be checked. Unique business_key includes channel and destination. Never fallback between channels without fresh consent and explicitly configured routing.

### integration.notification_attempt

Owner: `integration`; module: `notification`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
job_id uuid NN FK notification_job
attempt_number integer NN
provider varchar(40) NN
provider_message_id varchar(160) NULL
result varchar(20) NN
error_code varchar(60) NULL
duration_ms integer NN
channel varchar(20) NN DEFAULT EMAIL
```

- **Keys and indexes:** UQ(job_id,attempt_number); IX(provider_message_id).
- **Checks:** attempt_number>0; duration_ms>=0.
- **Business invariant:** Do not store rendered personal message bodies in general telemetry.

### integration.integration_job

Owner: `integration`; module: `provider`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
kind varchar(30) NN
business_key varchar(180) NN
owner_service varchar(30) NN
resource_id uuid NN
state varchar(20) NN
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN
lease_until timestamptz NULL
cursor jsonb NN DEFAULT {}
error_code varchar(60) NULL
```

- **Keys and indexes:** UQ(business_key); IX(kind,state,next_attempt_at).
- **Checks:** kind IN (SHEETS_SYNC,LOGISTICS_CREATE,PAYMENT_RECONCILE,ASSET_SCAN,REPORT_EXPORT); state IN (PENDING,CLAIMED,SUCCEEDED,FAILED,UNKNOWN).
- **Business invariant:** Persist execution state before remote side effects; preserve provider request identity across retries.

### integration.provider_cursor

Owner: `integration`; module: `provider`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
provider varchar(40) NN
job_kind varchar(40) NN
checkpoint jsonb NN
last_success_at timestamptz NULL
```

- **Keys and indexes:** UQ(provider,job_kind).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Checkpoint advancement occurs only after all items in the page have durable outcomes.

### integration.sheet_sync_record

Owner: `integration`; module: `provider`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
application_id uuid NN EXTERNAL experience.career_application
spreadsheet_key varchar(120) NN
row_key varchar(120) NN
content_hash char(64) NN
state varchar(20) NN
```

- **Keys and indexes:** UQ(application_id,spreadsheet_key); UQ(spreadsheet_key,row_key).
- **Checks:** state IN (PENDING,SYNCED,FAILED).
- **Business invariant:** Write/update by stable application ID column rather than blindly appending on retry.

### ai.knowledge_document

Owner: `ai`; module: `knowledge`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
source_service varchar(30) NN
source_resource_id uuid NN
source_type varchar(30) NN
locale varchar(16) NN
market_code varchar(32) NN
visibility varchar(20) NN
active_revision_id uuid NULL FK knowledge_revision
withdrawn_at timestamptz NULL
```

- **Keys and indexes:** UQ(source_service,source_resource_id,locale,market_code).
- **Checks:** source_type IN (CONTENT,PRODUCT,FAQ,BROCHURE); visibility IN (PUBLIC).
- **Business invariant:** Only approved public content enters knowledge; private documents are never indexed.

### ai.knowledge_revision

Owner: `ai`; module: `knowledge`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
document_id uuid NN FK knowledge_document
source_revision bigint NN
checksum_sha256 char(64) NN
source_url varchar(1000) NN
embedding_revision_id uuid NN FK embedding_revision
status varchar(20) NN
activated_at timestamptz NULL
```

- **Keys and indexes:** UQ(document_id,source_revision,embedding_revision_id).
- **Checks:** status IN (BUILDING,VALIDATED,ACTIVE,SUPERSEDED,WITHDRAWN,FAILED).
- **Business invariant:** Build then atomically switch active pointer; validate owner publication before any cited answer.

### ai.knowledge_chunk

Owner: `ai`; module: `knowledge`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
revision_id uuid NN FK knowledge_revision
ordinal integer NN
section_title varchar(200) NN
body text NN
token_count integer NN
embedding vector(1024) NN
metadata jsonb NN
source_hash char(64) NN
```

- **Keys and indexes:** UQ(revision_id,ordinal); HNSW(embedding vector_cosine_ops); GIN(search_vector).
- **Checks:** ordinal>=0; token_count BETWEEN 1 AND 1200.
- **Business invariant:** Use normalized 1024-dimensional embeddings; model dimension changes require a new column/index migration.

### ai.embedding_revision

Owner: `ai`; module: `knowledge`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
provider varchar(40) NN
model_id varchar(200) NN
dimensions integer NN
normalization boolean NN
chunker_version varchar(40) NN
enabled boolean NN DEFAULT false
```

- **Keys and indexes:** UQ(provider,model_id,dimensions,chunker_version).
- **Checks:** dimensions=1024.
- **Business invariant:** Reference adapter is Bedrock Titan v2; provider/model revisions do not mix in one active vector space.

### ai.conversation

Owner: `ai`; module: `assistant`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NULL
anonymous_session_hash char(64) NULL
market_code varchar(32) NN
locale varchar(16) NN
state varchar(20) NN
expires_at timestamptz NN
```

- **Keys and indexes:** IX(party_id,created_at); IX(expires_at).
- **Checks:** state IN (ACTIVE,CLOSED,EXPIRED); num_nonnulls(party_id,anonymous_session_hash)=1.
- **Business invariant:** Conversation ownership is verified for every message and feedback command.

### ai.ai_run

Owner: `ai`; module: `assistant`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
conversation_id uuid NN FK conversation
request_key varchar(100) NN
workflow_version varchar(40) NN
prompt_version varchar(40) NN
provider varchar(40) NN
model_id varchar(200) NN
status varchar(20) NN
input_tokens integer NN DEFAULT 0
output_tokens integer NN DEFAULT 0
cost_estimate numeric(19,6) NN DEFAULT 0
step_count integer NN DEFAULT 0
failure_code varchar(60) NULL
source_revision_ids jsonb NN DEFAULT []
rate_revision integer NN
```

- **Keys and indexes:** UQ(conversation_id,request_key); IX(status,created_at).
- **Checks:** status IN (RUNNING,SUCCEEDED,ESCALATED,FAILED,CANCELLED); input_tokens>=0; output_tokens>=0; step_count BETWEEN 0 AND 8.
- **Business invariant:** No raw sensitive prompts in this audit record; token usage and price-table revision make spend estimation explainable.

### ai.ai_checkpoint

Owner: `ai`; module: `assistant`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
conversation_id uuid NN FK conversation
run_id uuid NN FK ai_run
checkpoint_number integer NN
state_ciphertext bytea NN
parent_checkpoint_id uuid NULL FK ai_checkpoint
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(run_id,checkpoint_number); IX(expires_at).
- **Checks:** checkpoint_number>=0.
- **Business invariant:** Implement a typed application-controlled LangGraph checkpointer; do not create framework tables outside Liquibase.

### ai.tool_invocation

Owner: `ai`; module: `assistant`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
run_id uuid NN FK ai_run
tool_name varchar(60) NN
invocation_number integer NN
argument_hash char(64) NN
authorization_scope varchar(100) NN
outcome varchar(20) NN
duration_ms integer NN
```

- **Keys and indexes:** UQ(run_id,invocation_number).
- **Checks:** invocation_number BETWEEN 1 AND 4; outcome IN (SUCCEEDED,DENIED,FAILED); duration_ms>=0.
- **Business invariant:** Record argument digest, not order details or identity documents.

### ai.ai_feedback

Owner: `ai`; module: `assistant`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
run_id uuid NN FK ai_run
party_id uuid NULL
anonymous_session_hash char(64) NULL
rating smallint NN
category varchar(30) NULL
comment varchar(1000) NULL
```

- **Keys and indexes:** UQ(run_id).
- **Checks:** rating IN (-1,1).
- **Business invariant:** Only the conversation owner may rate a run; redact comments before aggregation.

### ai.evaluation_suite

Owner: `ai`; module: `evaluation`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
code varchar(80) NN
revision integer NN
dataset_checksum char(64) NN
cases jsonb NN
scoring_policy jsonb NN
```

- **Keys and indexes:** UQ(code,revision).
- **Checks:** revision>0; jsonb_typeof(cases)=array.
- **Business invariant:** At least 200 synthetic or approved cases; no production PII.

### ai.evaluation_result

Owner: `ai`; module: `evaluation`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
suite_id uuid NN FK evaluation_suite
model_policy_revision varchar(80) NN
prompt_revision varchar(40) NN
retrieval_revision varchar(40) NN
scores jsonb NN
passed boolean NN
report_object_key varchar(500) NN
```

- **Keys and indexes:** IX(suite_id,created_at).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Activation requires passing access, fallback and grounded correctness gates.

### commerce.outbox_event

Owner: `commerce`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
event_id uuid NN
event_type varchar(100) NN
schema_version integer NN
aggregate_id uuid NN
aggregate_version bigint NN
payload jsonb NN
occurred_at timestamptz NN
state varchar(20) NN DEFAULT PENDING
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN
lease_until timestamptz NULL
```

- **Keys and indexes:** UQ(event_id); IX(state,next_attempt_at).
- **Checks:** schema_version>0; aggregate_version>=0; state IN (PENDING,CLAIMED,PUBLISHED,FAILED).
- **Business invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.

### commerce.processed_event

Owner: `commerce`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
consumer varchar(80) NN
event_id uuid NN
event_type varchar(100) NN
outcome varchar(20) NN
processed_at timestamptz NN
```

- **Keys and indexes:** UQ(consumer,event_id).
- **Checks:** outcome IN (APPLIED,IGNORED).
- **Business invariant:** Insert only when local handling or durable external job creation commits successfully.

### commerce.idempotency_record

Owner: `commerce`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
principal_key varchar(200) NN
operation varchar(120) NN
request_key varchar(100) NN
request_hash char(64) NN
state varchar(20) NN
response_status integer NULL
response_body jsonb NULL
resource_id uuid NULL
lease_until timestamptz NULL
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(principal_key,operation,request_key); IX(expires_at).
- **Checks:** state IN (RUNNING,SUCCEEDED,FAILED_RETRYABLE); response_status IS NULL OR response_status BETWEEN 100 AND 599.
- **Business invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.

### commerce.audit_record

Owner: `commerce`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
actor_subject varchar(200) NN
actor_kind varchar(20) NN
action varchar(100) NN
resource_type varchar(80) NN
resource_id uuid NN
occurred_at timestamptz NN
correlation_id uuid NN
ip_hash char(64) NULL
reason varchar(500) NULL
old_value jsonb NULL
new_value jsonb NULL
```

- **Keys and indexes:** IX(resource_type,resource_id,occurred_at); IX(actor_subject,occurred_at).
- **Checks:** actor_kind IN (USER,SERVICE,SYSTEM).
- **Business invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.

### commerce.feature_configuration

Owner: `commerce`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
key varchar(120) NN
revision bigint NN
value jsonb NN
active boolean NN DEFAULT false
approved_by varchar(200) NULL
```

- **Keys and indexes:** UQ(key,revision).
- **Checks:** revision>0.
- **Business invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.

### experience.outbox_event

Owner: `experience`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
event_id uuid NN
event_type varchar(100) NN
schema_version integer NN
aggregate_id uuid NN
aggregate_version bigint NN
payload jsonb NN
occurred_at timestamptz NN
state varchar(20) NN DEFAULT PENDING
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN
lease_until timestamptz NULL
```

- **Keys and indexes:** UQ(event_id); IX(state,next_attempt_at).
- **Checks:** schema_version>0; aggregate_version>=0; state IN (PENDING,CLAIMED,PUBLISHED,FAILED).
- **Business invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.

### experience.processed_event

Owner: `experience`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
consumer varchar(80) NN
event_id uuid NN
event_type varchar(100) NN
outcome varchar(20) NN
processed_at timestamptz NN
```

- **Keys and indexes:** UQ(consumer,event_id).
- **Checks:** outcome IN (APPLIED,IGNORED).
- **Business invariant:** Insert only when local handling or durable external job creation commits successfully.

### experience.idempotency_record

Owner: `experience`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
principal_key varchar(200) NN
operation varchar(120) NN
request_key varchar(100) NN
request_hash char(64) NN
state varchar(20) NN
response_status integer NULL
response_body jsonb NULL
resource_id uuid NULL
lease_until timestamptz NULL
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(principal_key,operation,request_key); IX(expires_at).
- **Checks:** state IN (RUNNING,SUCCEEDED,FAILED_RETRYABLE); response_status IS NULL OR response_status BETWEEN 100 AND 599.
- **Business invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.

### experience.audit_record

Owner: `experience`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
actor_subject varchar(200) NN
actor_kind varchar(20) NN
action varchar(100) NN
resource_type varchar(80) NN
resource_id uuid NN
occurred_at timestamptz NN
correlation_id uuid NN
ip_hash char(64) NULL
reason varchar(500) NULL
old_value jsonb NULL
new_value jsonb NULL
```

- **Keys and indexes:** IX(resource_type,resource_id,occurred_at); IX(actor_subject,occurred_at).
- **Checks:** actor_kind IN (USER,SERVICE,SYSTEM).
- **Business invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.

### experience.feature_configuration

Owner: `experience`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
key varchar(120) NN
revision bigint NN
value jsonb NN
active boolean NN DEFAULT false
approved_by varchar(200) NULL
```

- **Keys and indexes:** UQ(key,revision).
- **Checks:** revision>0.
- **Business invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.

### integration.outbox_event

Owner: `integration`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
event_id uuid NN
event_type varchar(100) NN
schema_version integer NN
aggregate_id uuid NN
aggregate_version bigint NN
payload jsonb NN
occurred_at timestamptz NN
state varchar(20) NN DEFAULT PENDING
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN
lease_until timestamptz NULL
```

- **Keys and indexes:** UQ(event_id); IX(state,next_attempt_at).
- **Checks:** schema_version>0; aggregate_version>=0; state IN (PENDING,CLAIMED,PUBLISHED,FAILED).
- **Business invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.

### integration.processed_event

Owner: `integration`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
consumer varchar(80) NN
event_id uuid NN
event_type varchar(100) NN
outcome varchar(20) NN
processed_at timestamptz NN
```

- **Keys and indexes:** UQ(consumer,event_id).
- **Checks:** outcome IN (APPLIED,IGNORED).
- **Business invariant:** Insert only when local handling or durable external job creation commits successfully.

### integration.idempotency_record

Owner: `integration`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
principal_key varchar(200) NN
operation varchar(120) NN
request_key varchar(100) NN
request_hash char(64) NN
state varchar(20) NN
response_status integer NULL
response_body jsonb NULL
resource_id uuid NULL
lease_until timestamptz NULL
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(principal_key,operation,request_key); IX(expires_at).
- **Checks:** state IN (RUNNING,SUCCEEDED,FAILED_RETRYABLE); response_status IS NULL OR response_status BETWEEN 100 AND 599.
- **Business invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.

### integration.audit_record

Owner: `integration`; module: `platform`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
actor_subject varchar(200) NN
actor_kind varchar(20) NN
action varchar(100) NN
resource_type varchar(80) NN
resource_id uuid NN
occurred_at timestamptz NN
correlation_id uuid NN
ip_hash char(64) NULL
reason varchar(500) NULL
old_value jsonb NULL
new_value jsonb NULL
```

- **Keys and indexes:** IX(resource_type,resource_id,occurred_at); IX(actor_subject,occurred_at).
- **Checks:** actor_kind IN (USER,SERVICE,SYSTEM).
- **Business invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.

### integration.feature_configuration

Owner: `integration`; module: `platform`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
key varchar(120) NN
revision bigint NN
value jsonb NN
active boolean NN DEFAULT false
approved_by varchar(200) NULL
```

- **Keys and indexes:** UQ(key,revision).
- **Checks:** revision>0.
- **Business invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.

### ai.outbox_event

Owner: `ai`; module: `platform`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
event_id uuid NN
event_type varchar(100) NN
schema_version integer NN
aggregate_id uuid NN
aggregate_version bigint NN
payload jsonb NN
occurred_at timestamptz NN
state varchar(20) NN DEFAULT PENDING
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN
lease_until timestamptz NULL
```

- **Keys and indexes:** UQ(event_id); IX(state,next_attempt_at).
- **Checks:** schema_version>0; aggregate_version>=0; state IN (PENDING,CLAIMED,PUBLISHED,FAILED).
- **Business invariant:** Owner relay claims rows using short leases, publishes after commit, then acknowledges; duplicate publish is safe.

### ai.processed_event

Owner: `ai`; module: `platform`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
consumer varchar(80) NN
event_id uuid NN
event_type varchar(100) NN
outcome varchar(20) NN
processed_at timestamptz NN
```

- **Keys and indexes:** UQ(consumer,event_id).
- **Checks:** outcome IN (APPLIED,IGNORED).
- **Business invariant:** Insert only when local handling or durable external job creation commits successfully.

### ai.idempotency_record

Owner: `ai`; module: `platform`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
principal_key varchar(200) NN
operation varchar(120) NN
request_key varchar(100) NN
request_hash char(64) NN
state varchar(20) NN
response_status integer NULL
response_body jsonb NULL
resource_id uuid NULL
lease_until timestamptz NULL
expires_at timestamptz NN
```

- **Keys and indexes:** UQ(principal_key,operation,request_key); IX(expires_at).
- **Checks:** state IN (RUNNING,SUCCEEDED,FAILED_RETRYABLE); response_status IS NULL OR response_status BETWEEN 100 AND 599.
- **Business invariant:** Persist minimal response; a same key/different request hash returns 409. Financial business keys outlive response cache.

### ai.audit_record

Owner: `ai`; module: `platform`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
actor_subject varchar(200) NN
actor_kind varchar(20) NN
action varchar(100) NN
resource_type varchar(80) NN
resource_id uuid NN
occurred_at timestamptz NN
correlation_id uuid NN
ip_hash char(64) NULL
reason varchar(500) NULL
old_value jsonb NULL
new_value jsonb NULL
```

- **Keys and indexes:** IX(resource_type,resource_id,occurred_at); IX(actor_subject,occurred_at).
- **Checks:** actor_kind IN (USER,SERVICE,SYSTEM).
- **Business invariant:** Allowlisted redacted fields only; append-only role; sensitive field plaintext is excluded.

### ai.feature_configuration

Owner: `ai`; module: `platform`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
key varchar(120) NN
revision bigint NN
value jsonb NN
active boolean NN DEFAULT false
approved_by varchar(200) NULL
```

- **Keys and indexes:** UQ(key,revision).
- **Checks:** revision>0.
- **Business invariant:** Feature exposure is not permission; invalid enabled provider policy makes activation fail validation.

### commerce.spring_session

Owner: `commerce`; module: `session`; introduced: BP1; mutation: versioned.

```text
primary_id char(36) NN PK
session_id char(36) NN
creation_time bigint NN
last_access_time bigint NN
max_inactive_interval integer NN
expiry_time bigint NN
principal_name varchar(100) NULL
```

- **Keys and indexes:** UQ(session_id); IX(expiry_time); IX(principal_name).
- **Checks:** max_inactive_interval>0.
- **Business invariant:** Framework-owned physical shape; configure table name commerce.spring_session and initialize only through Liquibase.

### commerce.spring_session_attributes

Owner: `commerce`; module: `session`; introduced: BP1; mutation: versioned.

```text
session_primary_id char(36) NN FK spring_session.primary_id ON DELETE CASCADE
attribute_name varchar(200) NN
attribute_bytes bytea NN
```

- **Keys and indexes:** PK(session_primary_id,attribute_name).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Serialize an allowlisted typed session record; encrypt provider tokens and reject arbitrary deserialization types.

### experience.lead_requested_item

Owner: `experience`; module: `lead`; introduced: BP3; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
lead_id uuid NN FK lead
sku_id uuid NN EXTERNAL commerce.sku
quantity numeric(20,3) NN
```

- **Keys and indexes:** UQ(lead_id,sku_id).
- **Checks:** quantity>0.
- **Business invariant:** Persist normalized quote-request products; validate through commerce before acceptance.

### experience.upload_session

Owner: `experience`; module: `files`; introduced: BP2; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
owner_party_id uuid NULL EXTERNAL commerce.party
token_hash char(64) NN
purpose varchar(30) NN
expires_at timestamptz NN
maximum_bytes bigint NN
claimed_application_id uuid NULL FK career_application
```

- **Keys and indexes:** UQ(token_hash); IX(expires_at).
- **Checks:** maximum_bytes>0; purpose IN (CAREER_CV).
- **Business invariant:** Anonymous upload capability is short-lived and purpose-scoped; linking claims it once.

### ai.ingestion_job

Owner: `ai`; module: `knowledge`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
business_key varchar(180) NN
document_id uuid NULL FK knowledge_document
source_service varchar(30) NN
source_resource_id uuid NULL
embedding_revision_id uuid NN FK embedding_revision
status varchar(20) NN
next_attempt_at timestamptz NN
lease_until timestamptz NULL
attempt_count integer NN DEFAULT 0
```

- **Keys and indexes:** UQ(business_key); IX(status,next_attempt_at).
- **Checks:** status IN (PENDING,CLAIMED,SUCCEEDED,FAILED).
- **Business invariant:** Owned queue/reindex work state; retries cannot activate partial embeddings.

### ai.model_policy

Owner: `ai`; module: `gateway`; introduced: BP5; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
code varchar(80) NN
revision integer NN
provider varchar(40) NN
chat_model_id varchar(200) NN
fallback_model_id varchar(200) NULL
parameters jsonb NN
approved boolean NN DEFAULT false
evaluation_result_id uuid NULL FK evaluation_result
prompt_revision_id uuid NN FK prompt_revision
```

- **Keys and indexes:** UQ(code,revision).
- **Checks:** revision>0.
- **Business invariant:** Activation requires compatible provider model, region and passing evaluated policy; no implicit provider fallback.

### ai.prompt_revision

Owner: `ai`; module: `gateway`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
code varchar(80) NN
revision integer NN
body text NN
output_schema jsonb NN
checksum_sha256 char(64) NN
tool_schema_revision varchar(80) NN
```

- **Keys and indexes:** UQ(code,revision).
- **Checks:** revision>0.
- **Business invariant:** Immutable templates; runtime selects explicitly versioned approved policy.

### ai.model_rate

Owner: `ai`; module: `gateway`; introduced: BP5; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
provider varchar(40) NN
model_id varchar(200) NN
revision integer NN
input_per_million numeric(19,6) NN
output_per_million numeric(19,6) NN
currency_code char(3) NN
effective_from timestamptz NN
```

- **Keys and indexes:** UQ(provider,model_id,revision).
- **Checks:** revision>0; input_per_million>=0; output_per_million>=0.
- **Business invariant:** Rates are audited approved config; estimates are not billed-cost claims.

### commerce.carrier_quote

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
cart_id uuid NN FK cart
cart_version bigint NN
market_id uuid NN FK market
provider varchar(30) NN
mode varchar(10) NN
provider_quote_reference varchar(160) NULL
service_code varchar(80) NN
currency_code char(3) NN FK currency.code
delivery_amount numeric(19,4) NN
address_hash char(64) NN
package_hash char(64) NN
package_snapshot jsonb NN
provider_snapshot jsonb NN
expires_at timestamptz NN
status varchar(20) NN
source_quote_revision_id uuid NULL FK quote_revision
```

- **Keys and indexes:** IX(cart_id,expires_at); UQ(cart_id,cart_version,address_hash,package_hash,service_code,mode).
- **Checks:** provider=FEDEX; mode IN (MOCK,SANDBOX,LIVE); status IN (QUOTED,EXPIRED,CONSUMED); delivery_amount>=0.
- **Business invariant:** Server-computed NGN fee with bound address/package/cart revision; valid5m. No mock quote can be used in live checkout. Carrier can return another rate currency: reject unless a separate approved FX policy exists, never treat it as NGN.

### commerce.carrier_cancellation

Owner: `commerce`; module: `fulfillment`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
shipment_id uuid NN FK shipment
booking_request_id uuid NN
reference varchar(40) NN
mode varchar(10) NN
reason varchar(500) NN
request_hash char(64) NN
requested_at timestamptz NN
state varchar(20) NN DEFAULT REQUESTED
accepted_at timestamptz NULL
next_attempt_at timestamptz NN DEFAULT now()
lease_until timestamptz NULL
attempt_count integer NN DEFAULT 0
last_result_command_id uuid NULL
error_code varchar(60) NULL
```

- **Keys and indexes:** UQ(shipment_id) WHERE state IN (REQUESTED,QUEUED,UNKNOWN); IX(state,next_attempt_at); IX(shipment_id,created_at).
- **Checks:** state IN (REQUESTED,QUEUED,UNKNOWN,CANCELLED,REJECTED); mode IN (MOCK,SANDBOX,LIVE); attempt_count>=0.
- **Business invariant:** ADR-014. Active cancellation is a durable dispatch exclusion serialized under order/shipment locks. Original request UUID/reference/mode/time survives relay uncertainty; current staff authorization precedes intent. Provider cancellation never refunds money or returns stock. Source callbacks bind the original approved intent and cannot cancel an already physically shipped/delivered shipment.

### integration.carrier_booking

Owner: `integration`; module: `provider`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
shipment_id uuid NN EXTERNAL commerce.shipment
business_key varchar(180) NN
provider varchar(30) NN
mode varchar(10) NN
state varchar(20) NN
request_hash char(64) NN
tracking_number varchar(160) NULL
provider_shipment_id varchar(160) NULL
label_object_key varchar(500) NULL
next_poll_at timestamptz NULL
lease_until timestamptz NULL
last_provider_status varchar(80) NULL
error_code varchar(80) NULL
```

- **Keys and indexes:** UQ(shipment_id,provider); UQ(business_key); IX(state,next_poll_at).
- **Checks:** state IN (PENDING,CLAIMED,BOOKED,UNKNOWN,FAILED,CANCELLED); mode IN (MOCK,SANDBOX,LIVE).
- **Business invariant:** Stable shipment business reference; UNKNOWN creates are reconciled by reference or operator before retry. Booking/label creation does not dispatch or debit stock.

### integration.carrier_cancellation

Owner: `integration`; module: `provider`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
source_request_id uuid NN EXTERNAL commerce.carrier_cancellation
booking_id uuid NN FK carrier_booking
shipment_id uuid NN EXTERNAL commerce.shipment
reference varchar(40) NN
mode varchar(10) NN
account_hash char(64) NN
request_hash char(64) NN
generation uuid NN
requested_at timestamptz NN
piece_numbers jsonb NN
state varchar(20) NN DEFAULT QUEUED
attempt_count integer NN DEFAULT 0
next_attempt_at timestamptz NN DEFAULT now()
lease_until timestamptz NULL
submitted_at timestamptz NULL
outcome_state varchar(20) NULL
occurred_at timestamptz NULL
callback_id uuid NULL
callback_body jsonb NULL
notified_state varchar(20) NULL
error_code varchar(60) NULL
```

- **Keys and indexes:** UQ(source_request_id); UQ(booking_id) WHERE state IN (QUEUED,SUBMITTING,UNKNOWN,ACK_PENDING); IX(state,next_attempt_at); IX(booking_id).
- **Checks:** state IN (QUEUED,SUBMITTING,UNKNOWN,ACK_PENDING,CANCELLED,REJECTED); outcome_state IN (UNKNOWN,CANCELLED,REJECTED); notified_state IN (UNKNOWN,CANCELLED,REJECTED); mode IN (MOCK,SANDBOX,LIVE); attempt_count>=0; jsonb_typeof(piece_numbers)=array.
- **Business invariant:** ADR-014. Source request dedup and fenced SUBMITTING intent precede a single provider cancellation. Submitted/expired/restored/uncertain records reconcile by saved physical-piece tracking; no blind repeat or new shipment. Account/mode/generation/source-time guards fail closed. Callback body/UUID persists before source HTTP and UNKNOWN remains durable until authoritative evidence.

### experience.communication_destination

Owner: `experience`; module: `consent`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
party_id uuid NN EXTERNAL commerce.party
channel varchar(20) NN
address_ciphertext bytea NN
destination_hash char(64) NN
hash_key_version integer NN
status varchar(20) NN
verified_at timestamptz NULL
policy_version varchar(40) NN
consented_purposes jsonb NN
```

- **Keys and indexes:** UQ(party_id,channel,destination_hash); IX(destination_hash,channel); UQ(party_id,channel) WHERE status=ACTIVE.
- **Checks:** channel IN (SMS,WHATSAPP); status IN (PENDING,ACTIVE,UNSUBSCRIBED,SUPPRESSED); hash_key_version>0.
- **Business invariant:** A phone in an address/profile is not messaging consent. OTP proves control; separate channel/purpose grants required, latest withdrawal/suppression authoritative. No cross-owner party FK. At most one active phone per actor/channel; a second activation returns409 until the actor withdraws the old destination. Party-only eligibility resolves that one destination, never arbitrarily selects among phones.

### experience.destination_verification

Owner: `experience`; module: `consent`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
destination_id uuid NN FK communication_destination
code_hmac char(64) NN
code_ciphertext bytea NULL
expires_at timestamptz NN
attempts integer NN DEFAULT 0
status varchar(20) NN
sent_job_key varchar(180) NN
```

- **Keys and indexes:** UQ(sent_job_key); IX(destination_id,status); UQ(destination_id) WHERE status=PENDING.
- **Checks:** attempts BETWEEN 0 AND 5; status IN (PENDING,CONFIRMED,EXPIRED,LOCKED).
- **Business invariant:** Six-digit CSPRNG proof code, expiry10m, max5 tries, max3 requests/hour/party+destination. Erase ciphertext on confirm/expiry; no plaintext code/events/logs; not a Keycloak login/MFA replacement.

### experience.inbound_message

Owner: `experience`; module: `lead`; introduced: BP1; mutation: versioned.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
updated_at timestamptz NN DEFAULT now()
updated_by varchar(200) NN
version bigint NN DEFAULT 0
provider varchar(30) NN
provider_message_id varchar(160) NN
channel varchar(20) NN
sender_hash char(64) NN
sender_ciphertext bytea NN
body_ciphertext bytea NULL
lead_id uuid NULL FK lead
received_at timestamptz NN
retention_until timestamptz NN
disposition varchar(20) NN
```

- **Keys and indexes:** UQ(provider,provider_message_id); IX(retention_until).
- **Checks:** channel=WHATSAPP; disposition IN (RECEIVED,LEAD_CREATED,OPT_OUT,HELP,IGNORED).
- **Business invariant:** Signed WhatsApp inbound is a contact/lead, never authentication or authority to inspect orders; STOP suppresses matching phone/channel immediately. No autonomous financial/AI commands.

### integration.message_receipt

Owner: `integration`; module: `notification`; introduced: BP1; mutation: append-only.

```text
id uuid NN PK
created_at timestamptz NN DEFAULT now()
created_by varchar(200) NN
job_id uuid NN FK notification_job
provider varchar(30) NN
provider_message_id varchar(160) NN
event_hash char(64) NN
provider_status varchar(40) NN
occurred_at timestamptz NULL
received_at timestamptz NN
error_code varchar(80) NULL
```

- **Keys and indexes:** UQ(provider,provider_message_id,event_hash); IX(job_id,received_at).
- **Checks:** No additional table-level predicate..
- **Business invariant:** Persist verified callback mapping before HTTP acknowledgment; duplicate and out-of-order statuses cannot regress DELIVERED. SENT/QUEUED is not delivery proof.

## Versioned JSONB / object schemas

Implement these as closed JSON Schema2020-12 documents under contracts/rules/content/ai and equivalent Bean Validation/Pydantic types. Unknown keys rejected. All approval references refer to reviewed configuration records; schemas do not supply actual commercial rates.

| Type / storage | Required fields and constraints |
|---|---|
| TaxPolicy / market.tax_policy | `{schemaVersion:1,mode:NONE\|EXCLUSIVE\|INCLUSIVE,rate:decimal-string 0..1,rounding:HALF_UP,approvalReference:string<=120}`; NONE requires approved explicit zero policy. Flat-rate launch policy applies all SKU lines; jurisdiction-dependent exemptions or multiple rates require a new approved schema/ADR before activation. |
| AddressRules / destination.address_rules | `{schemaVersion:1,requiredFields:[RECIPIENT,PHONE,LINE1,CITY,REGION,POSTAL_CODE],postalPattern:string\|null,regionCodes:[string<=100]}`; fixed required recipient/phone/line1/city/country; regex <=120 compiled with safe bounded engine, input postal <=24. |
| PromotionEligibility / promotion_rule.eligibility | `{schemaVersion:1,segment:RETAIL\|DISTRIBUTOR\|ANY,productIds:[UUID],categoryIds:[UUID],minimumSpend:Money\|null,minimumQuantity:Quantity\|null,firstSettledPurchaseOnly:boolean}`; empty lists mean no product restriction; conjunctive constraints; scope price/currency/market authoritative. |
| PERCENT parameters | `{schemaVersion:1,percentage:decimal-string >0..1,maximumDiscount:Money\|null}`; calculate eligible rounded line-net discount, never above eligible amount. |
| FIXED parameters | `{schemaVersion:1,amount:Money>0}`; allocate discount proportionally to eligible line net using largest remainder in currency minor units and SKU-ID tie-break. |
| FREE_DELIVERY parameters | `{schemaVersion:1,maximumDeliveryDiscount:Money\|null}`; no negative delivery charge. |
| SPEND_REWARD parameters | `{schemaVersion:1,threshold:Money>0,rewardAmount:Money>0,maximumRewardPerOrder:Money>0,holdDays:int0..365,expiresDays:int1..3650,refundMode:PROPORTIONAL\|FULL_ON_ANY_REFUND}`; one reward per order, no multiples unless future schema approved. |
| REFERRAL parameters | `{schemaVersion:1,minimumSettledSpend:Money>=0,referrerReward:Money>0,referredReward:Money>=0,holdDays:int0..365,expiresDays:int1..3650,maximumRewardsPerReferrer:int>0,refundMode:PROPORTIONAL\|FULL_ON_ANY_REFUND}`; reject self/same-party and reuse; fraud flags may block qualification. |
| DEALER parameters | `{schemaVersion:1,minimumSettledSpend:Money>=0,maximumIssue:Money>0,expiresDays:int1..3650,requiresSecondActor:boolean}`; only active org and authorized staff issue; amount within approved remaining budget. |
| WheelBenefit / wheel_outcome.benefit | COUPON `{schemaVersion:1,ruleId:UUID,expiresHours:int1..8760,maximumDiscount:Money>0}`; FREE_DELIVERY same active rule/expiry/cap; RETRY `{schemaVersion:1,extraDraws:1}` consumes daily slot and budget cap; NONE `{schemaVersion:1}`. Campaign daily limit always absolute, RETRY cannot create an unlimited loop. |
| Product specifications / product_attribute.value | Object <=50 keys; key `[a-z][a-z0-9_]{0,79}`; value string<=1000, decimal string, boolean or primitive array<=20; depth<=2; no HTML/executable references. Division-specific registered attribute definitions enforce expected type/unit. |
| ContentBody / content_revision.body | `{schemaVersion:1,blocks:[Block]}` <=200; Block discriminated type TEXT `{text:string<=20000}` sanitized allowed markup; IMAGE `{assetId:UUID,alt:string<=300}` clean public file; VIDEO `{provider:YOUTUBE\|VIMEO,videoId:string<=100,caption:string<=500}` validated ID/no arbitrary embed; PERSON `{name:string<=160,title:string<=160,bio:string<=10000,assetId:UUID\|null}`; LINK `{label:string<=160,url:HTTPS}` allowlisted; MAP `{channelKey:string<=80}`; PRODUCT_LIST `{productIds:[UUID]<=30}`; FAQ `{question:string<=500,answer:string<=5000}`. Require appropriate blocks for content type; no arbitrary scripts/iframes. |
| Career fields_schema | `{schemaVersion:int>=1,fields:[{key:string<=80,type:TEXT\|LONG_TEXT\|SELECT\|BOOLEAN,required:boolean,maximumLength:int1..5000,options:[string<=100]}]}` <=30; reserved name/email/CV fields cannot override privacy limits; answers match types/required keys and captured schemaVersion. |
| Channel public_value | WHATSAPP `{label:string<=120,phone:E164}`; SOCIAL `{label:string<=120,url:HTTPS}` allowlisted host; MAP `{label:string<=120,latitude:decimal -90..90,longitude:decimal -180..180}`; LEAD_RECIPIENT_GROUP `{groupKey:string<=80}` no public email; SEO uses SeoDTO. |
| CampaignDisplay / display_config | `{schemaVersion:1,summary:string<=1000,assetId:UUID\|null,cta:{label:string<=80,url:relative-approved-path},commercialRuleId:UUID\|null}`; no override discount/eligibility values. |
| ModelParameters / model_policy.parameters | `{schemaVersion:1,temperature:decimal0..1,maxOutputTokens:int128..2000,requestDeadlineSeconds:int1..20,maximumSteps:int1..8,maximumTools:int0..4,maximumInputTokens:int1..12000,concurrency:int1..10,dailyBudget:decimal-string>0,rateRevision:int>=1}`; provider capability validates exact supported model parameters, never blindly passes these internal budget fields to Bedrock. |

Tax computation: choose eligible current book tier, derive quantity*unitAmount; round line net HALF_UP to currency minor scale. Allocate discounts in minor units, capped at net. EXCLUSIVE tax=round(discountedNet*rate); INCLUSIVE: let grossBefore be rounded quantity*unitAmount and grossDiscount allocated discount; grossAfter=grossBefore-grossDiscount, baseBefore=round(grossBefore/(1+rate)), baseAfter=round(grossAfter/(1+rate)), line discount=baseBefore-baseAfter, line tax=grossAfter-baseAfter. Header subtotal=sum(baseBefore), discount=sum(line discount), tax=sum(line tax), so subtotal-discount+tax equals final inclusive merchandise gross exactly. EXCLUSIVE header subtotal/discount/tax sum their rounded line components; NONE tax=0. Delivery tax is zero under this provisional flat launch schema; activation requires approval of that treatment or a schema extension. No generic configured tax engine without an approved calculation schema.

Rule priority: nonexpired approved quote revision supplies agreed prices; otherwise active org-assigned wholesale book; otherwise market's explicit retail book. Reject missing/ineligible assignments. Apply one EXCLUSIVE qualifying discount by maximum benefit then rule UUID; STACKABLE rules apply in startsAt/UUID order to remaining eligible net, never negative. Reward qualification uses settled net merchandise after refunds, excluding tax/delivery; spend/hold/budget values are business inputs. Refund proportional reversals = round(originalEarn*cumulativeEligibleRefund/originalEligibleSpend) minus prior reversal amount; cap at originalEarn and distinguish refund IDs in business keys. Reject zero original denominator. This supersedes any shorthand suggesting only one reversal per original earning.


Distributor decision_payload is closed `{schemaVersion:1,decision:ACTIVATE|SUSPEND|REJECT,marketId:UUID|null,priceBookId:UUID|null,reason:string<=500,decisionActor:UUID}`. Persist in the same experience transaction as decision command/outbox; retries reconstruct the identical request and never lose selected assignment. Catalog/price/market knowledge revisions are validated through authoritative owner APIs at answer time; withdrawn or no-longer-eligible facts are filtered even if a cache/queue update is late.


## Confirmed launch schema extensions

CarrierPackaging is closed `{schemaVersion:1,maximumUnitsPerPackage:Quantity>0,unitWeightKg:Quantity>0,tareWeightKg:Quantity>=0,lengthCm:decimal>0,widthCm:decimal>0,heightCm:decimal>0}`. Use one SKU per package: full cartons plus one remainder carton, fixed approved carton dimensions and actual units*unit weight+tare; calculate on server, validate totals/quantity scale. Missing rule makes shipping quote ineligible, not weight0. CarrierAddress is `{recipient:string,phone:E164,line1:string,line2:string|null,city:string,region:string,postalCode:string|null,countryCode:NG}`; CarrierPackage is `{skuId:UUID,quantity:Quantity,weightKg:Quantity,lengthCm:decimal,widthCm:decimal,heightCm:decimal}`. Shipper comes from approved origin config, never browser input.

Phone hashes are HMAC-SHA256 over canonical E164, keyed/versioned separately from encryption. `destination_hash` is the common email/phone suppression key; email legacy hash may be null for phones. Keycloak owns a separate logical PostgreSQL database `keycloak` and its vendor tables; none of the four business services can query it. Its vendor migrations are the explicit exception to business Liquibase ownership, coordinated with pinned Keycloak upgrade/backup/runbook; never attempt to invent or maintain Keycloak's vendor columns in commerce Liquibase.


# Master section11: Liquibase Architecture

Each owner has `db/changelog/master.yaml` in its Java service resources; AI changesets live in `database/changelog/ai/master.yaml`. A standalone migration CLI/container uses Liquibase JDBC against each owner. Root `database/master-all-local.yaml` is convenience only for local integration tests and must never grant one runtime service access to all schemas.

Structure per owner: `000-schema-and-roles.yaml`, `010-platform-tables.yaml`, `020-bp1-domain-tables.yaml`, `021-bp1-indexes.yaml`, `022-bp1-constraints.yaml`, `030-bp2-*`, `040-bp3-*`, `050-bp4-*`, `060-bp5-*`, `090-reporting-views.yaml`, `seed/local-only.yaml`, `migrations/<date>-<purpose>.yaml`. AI platform tables start in BP5. A schemas manifest maps every section-10 table to exactly one creation changeset and its phase.

ID format `<owner>-<yyyyMMdd>-<sequence>`; author `nigson-platform`; stable logicalFilePath. Define schemaName on every operation. Never edit an applied changeset or clear checksums to hide drift. Run validate, status and updateSQL before update. Use preconditions to check supported PostgreSQL/extension versions and fail on conflicting objects. Liquibase DATABASECHANGELOG and LOCK tables are owner-local. Schema/role bootstrap belongs to an explicit infrastructure changeset with elevated provisioning identity; normal migrations do not reuse it.

Migrations run once as an ECS task per owner before new application deployment. Runtime settings: Hibernate ddl-auto=validate, Liquibase auto-run=false, Spring Session initialize-schema=never, and no LangGraph setup() unmanaged SQL. Tests invoke production Liquibase masters on a real PostgreSQL container. Seed only deterministic dev/test data under local/test contexts; production seeds only approved static currency/unit metadata and deny-by-default settings.

Rollback: additive tables/columns/indexes have explicit rollback where non-destructive. Mark backfills and financial changes irreversible when downgrade would lose truth. Production release uses expand/backfill/switch/contract across at least two releases; container rollback assumes backward compatible schema, not automatic database downgrade. Concurrent index creation uses runInTransaction=false with pre/post validation; transactional DDL otherwise remains atomic. Backfill batches have checkpoint and cancellation controls.

The framework Spring Session schema must be copied from the version actually locked by Boot, mapped to commerce schema/lowercase names, and versioned through Liquibase. The AI custom checkpointer tables above avoid unstable upstream auto-created shapes. HNSW index creation requires pgvector compatible with the selected RDS engine and 1024 dimensions.


Specification1.1.0 changesets add carrier/messaging records and extend existing columns/checks/uniques. Fresh DB uses the revised schema; upgrade uses expand/backfill/validate/set-not-null. Existing email consent/suppression rows backfill destination_hash=email_hash, destination_kind=EMAIL and the known existing HMAC key version. Phone rows use new E164 hashes. Update event/API pre-release contracts consistently; no deployed backend was generated by this task. Keycloak vendor migrations run separately under controlled identity upgrade, never commerce Liquibase or runtime auto-DDL.


## DATA-001 migration implementation (specification 1.1.0)

`database/schema-manifest.json` maps all 122 section10 tables to their owner,
first product phase and exactly one creation changeset. Versioned SQL is the
runtime source; `scripts/database/generate.py --check` detects drift from the
reviewed catalog. The existing Spring Session changeset is retained unchanged.

Owner masters select the cumulative `releases/BP1.yaml` through `BP5.yaml` using
`schema.release`. The local migration launcher supplies this from
`SCHEMA_RELEASE` (default BP1); it rejects unknown gates and a release below one
already applied. These are deployment gates, with no contexts or labels hiding
changesets. A schema gate does not activate a product feature or provider.
AI platform and domain tables first appear in BP5. Tables precede indexes and
owner-local foreign keys. Nullable references to later-phase tables gain their
foreign key when the target phase is installed: address/organization and
sales_order/quote_revision in BP3, file_asset/upload_session in BP2. Earlier
applications must leave these future references null until that release.

All business UUIDs prohibit nil, versions prohibit negative values, and each
normative predicate, key, index, column length, default and nullability is
implemented. Product and knowledge-chunk full-text columns are generated;
knowledge embeddings use 1024-dimensional pgvector HNSW cosine indexes with
m=16 and ef_construction=64. Application query ef_search=80 belongs to AI-001.
External owner references remain UUID values, never SQL foreign keys.

The printed inventory_movement predicate starts with an empty item; the six
movement types in section17 supply its explicit enum check. The similarly empty
leading item in communication_suppression supplies no enum definition: the
remaining section10 predicates are enforced without inventing an extra policy.
Cross-row state transitions, JSON schemas/size caps, encryption and UUIDv7
creation belong to their application tasks; database UUID storage does not
pretend to enforce UUID generation or lifecycle commands.

Migrators own only their schema. Runtime roles have owned CRUD, with append-only
records limited to SELECT/INSERT; audit export roles are separate NOLOGIN roles
with only own audit SELECT and no application membership. Privileged bootstrap
SQL is infrastructure-owned; the Terraform module renders the production
provisioning SQL without embedding credentials. Local bootstrap retains existing
role OIDs/passwords and does not replace running local volumes.

Explicit additive rollback SQL removes constraints/indexes and empty tables;
all table-emptiness checks precede drops and populated evidence causes a failure.
The CLI exposes forward migration only. Production container rollback still
requires a backward-compatible schema and an approved expand/contract plan.
Run `make database-check` for actual isolated PostgreSQL catalog, upgrade,
constraint and ownership qualification; `make schema-check` alone is not
runtime acceptance.

COM-INVEN-001 implements application-scale stock values, append-only movement conservation and caller-owned transaction transitions over the existing four inventory tables. A full unshipped allocation cancellation marks its reservation RELEASED without claiming dispatch; mixed dispatch/cancellation completes DISPATCHED only when dispatched units exist. A released pre-payment reservation may be reused for eligible late settlement, but prior settlement/dispatch/return evidence forbids reusing an allocated-and-cancelled reservation. Stable return business key is command UUID plus immutable order line. No inventory schema changed.

## COM-MARKE-001 owned policy implementation —10 October2026

Existing currency/market/destination/eligibility/book tables are reused without DDL. NGN scale2 is verified, not editable by market API; NGN-001 owns its approved seed. Explicit eligibility increments Product version/public revision plus its own row version, with reason retained only in owned state. Flat TaxPolicy is closed and requires explicit approvalReference (NONE requires decimal zero); empty draft{} fails selling. AddressRules reuses the existing Party/RE2-J contract. Current market/destination/tax/returns/retail book and supplied trusted provider qualification proof are rechecked inside checkout before reservation; no network under locks. No commercial policy values or actual provider approvals are seeded.

## NGN-001 additive approved metadata —10 October2026

Changeset commerce-20261010-ngn-001 seeds approved currencyNGN2/marketNG-Nigeria/destinationNG into the existing owned tables, with selling/delivery disabled and unresolved policies empty/null. No DDL/FK/entity added; older SQL checksums preserved. UUIDv5 metadata IDs deterministic; exact actual IDs and previous policy-preservation/rollback evidence recorded by the task. An existing compatible inactive NGN row retains ID and is supported by the prior explicit approval, version increment once. Wrong NGN scale or NG currency fails atomically. Rollback cannot delete modified/referenced evidence or alter preexisting policy.

ADR-016 account metadata uses existing commerce JSON columns without DDL: carrier_quote.provider_snapshot.accountHash is immutable across refreshes; checkout_quote.price_snapshot.carrierAccountHash participates in the commercial hash; sales_order.carrier_snapshot.accountHash and its original providerSnapshot.accountHash are frozen; shipment.package_snapshot.accountHash binds the creation context. Missing historical values are not backfilled from current configuration. Integration's existing carrier checkpoint accountHash must equal private API-119 before preparing a new provider create. There are no cross-owner SQL/FK/entity changes.
