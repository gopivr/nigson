# Nigson Event Catalog

Specification1.1.0; synchronized view; master is authoritative.


# Master section16: Event-Driven Architecture

Transport topology: `nigson-<env>-domain-events` SNS standard topic, encrypted, publisher role limited per service. Dedicated encrypted SQS standard subscriptions: `integration-work`, `experience-projections`, `commerce-domain` (rewards/refund listeners), `ai-ingestion` at BP5; each has corresponding `<queue>-dlq`. Filter subscription event_type and owning producer allowlist, not string substring matching. Local LocalStack supplies the same topic/queue policy shape.

Envelope: `{event_id:UUID,event_type:string,schema_version:1,producer:service,occurred_at:Instant,aggregate_id:UUID,aggregate_version:int64,organization_id:UUID|null,correlation_id:UUID,traceparent:string|null,payload:object}`. Payload must match the registered JSON schema; additionalProperties=false in v1, optional additive evolution requires deployed consumer tolerance or v2 compatibility plan. SNS raw message delivery sends the envelope as SQS body. Do not place unneeded personal details, email addresses, CVs or wholesale prices on the shared topic.

SQS long poll20s, batch<=10, visibility120s, heartbeat every30s for work still executing, max concurrent messages10 per worker initially, maxReceiveCount5, retention4d, DLQ retention14d. Set processing deadline <=90s except explicit checkpointed jobs. Transient backoff schedules per delivery 5,20,60,120,300s bounded to visibility limits; workers stop retrying permanent validation/security failures and route them to classified dead-letter review. Unknown external outcomes reconcile, not blind replay. Delete SQS message only after local transaction commits durable effect/job+processed_event. Dedup retention90d exceeds supported14d replay horizon. Replay service checks original event_id, reason and authorized operator; never change payload to bypass dedup.

Partition key means logical `aggregate_id` for ordering checks; standard SQS has no Kafka partition or per-key order guarantee. An older aggregate_version may be recorded ignored only if a refreshed projection/authoritative owner confirms the current state. For financial effects the unique business key, not merely event ordering, prevents double earning or reversing. The ledger itself is authoritative. Queue lag = ApproximateAgeOfOldestMessage / oldest outbox age, not Kafka offsets.

### EVT-001 order.pending.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `order_id:UUID,party_id:UUID,market_code:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-002 order.paid.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; commerce reward listener; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `order_id:UUID,party_id:UUID,organization_id:UUID|null,currency:ISO4217,total:Money,payment_transaction_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-003 order.processing.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `order_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-004 order.cancelled.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; commerce reward listener; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `order_id:UUID,reason_code:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-005 inventory.availability.changed.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration cache/invalidation worker; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `sku_id:UUID,location_id:UUID,available:Quantity`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-006 catalogue.product.changed.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; ai at P5; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `product_id:UUID,revision:int64,publication_state:string,market_codes:[string]`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-007 pricing.changed.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration cache/invalidation worker; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `entity_type:PRICE_BOOK|PROMOTION_RULE|ASSIGNMENT,entity_id:UUID,revision:int64,market_id:UUID|null`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-008 market.changed.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; ai at P5; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `market_id:UUID,policy_revision:int64,enabled:boolean`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-009 shipment.changed.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `shipment_id:UUID,order_id:UUID,status:string,occurred_at:Instant`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-010 refund.requested.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration staff notifications; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `refund_id:UUID,order_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-011 refund.approved.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** commerce durable refund dispatcher; integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `refund_id:UUID,order_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-012 refund.succeeded.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; commerce reward listener; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `refund_id:UUID,order_id:UUID,amount:Money,currency:ISO4217`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-013 quote.offered.v1

- **Producer / phase:** commerce / BP3.
- **Consumers:** integration; experience; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `quote_id:UUID,revision_id:UUID,lead_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-014 quote.accepted.v1

- **Producer / phase:** commerce / BP3.
- **Consumers:** integration; experience; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `quote_id:UUID,revision_id:UUID,order_id:UUID,lead_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-015 distributor.entitlement.changed.v1

- **Producer / phase:** commerce / BP3.
- **Consumers:** experience; integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `command_id:UUID,application_id:UUID,organization_id:UUID,state:string,revision:int64`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-016 lead.created.v1

- **Producer / phase:** experience / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `lead_id:UUID,type:string,priority:string,recipient_group:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-017 content.published.v1

- **Producer / phase:** experience / BP1.
- **Consumers:** integration; ai at P5; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `content_item_id:UUID,revision:int,locale:string,market_codes:[string]`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-018 content.withdrawn.v1

- **Producer / phase:** experience / BP1.
- **Consumers:** integration; ai at P5; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `content_item_id:UUID,revision:int,locale:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-019 distributor.application.created.v1

- **Producer / phase:** experience / BP3.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `application_id:UUID,recipient_group:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-020 distributor.decision.requested.v1

- **Producer / phase:** experience / BP3.
- **Consumers:** experience owning durable command relay; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `application_id:UUID,command_id:UUID,decision:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-021 review.published.v1

- **Producer / phase:** experience / BP2.
- **Consumers:** integration cache invalidation; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `review_id:UUID,product_id:UUID|null`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-022 newsletter.verification.requested.v1

- **Producer / phase:** experience / BP2.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `subscription_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-023 consent.changed.v1

- **Producer / phase:** experience / BP2.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `party_id:UUID|null,destination_hash:string,destination_kind:EMAIL|PHONE,email_hash:string|null,channel:EMAIL|SMS|WHATSAPP,purpose:string,granted:boolean,revision:int64`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-024 career.application.created.v1

- **Producer / phase:** experience / BP2.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `application_id:UUID,opportunity_id:UUID,recipient_group:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-025 file.scan.requested.v1

- **Producer / phase:** commerce or experience / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `owner_service:string,asset_id:UUID,checksum_sha256:string`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-026 cart.recovery.requested.v1

- **Producer / phase:** commerce / BP4.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `candidate_id:UUID,cart_id:UUID,party_id:UUID`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-027 shipment.booking.requested.v1

- **Producer / phase:** commerce / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `shipment_id:UUID,booking_request_id:UUID,provider:FEDEX`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.

### EVT-028 communication.verification.requested.v1

- **Producer / phase:** experience / BP1.
- **Consumers:** integration; exact event filter and owning queue.
- **Topic:** nigson-<env>-domain-events.
- **Payload:** `verification_id:UUID,destination_id:UUID,channel:SMS|WHATSAPP`; section16 envelope and closed JSON schema.
- **Logical ordering key:** aggregate_id/aggregate_version; SQS standard is unordered.
- **Delivery/retry/DLQ:** at least once; existing bounded lease/visibility/retry/maxReceiveCount5/DLQ policies.
- **Idempotency:** consumer processed_event plus business key; commit durable job/local effect before delete.


# Master section17: Transaction Strategy

## Checkout and inventory

Use READ COMMITTED with explicit row locks and conditional updates, not distributed two-phase commit. Transaction T1 locks cart and compares cart/version/quote hash, then locks SKU inventory_balance rows sorted by balance UUID, promotion usage rows sorted by UUID, and creates pending sales_order, immutable order lines/totals/address/commercial snapshot, inventory reservations/movements, coupon reservation, payment_attempt and outbox. Recalculate under lock; reject if quote/rule revision changed with CHECKOUT_REPRICE_REQUIRED. Commit before any external payment HTTP call. All lines succeed or none; deadlock retry <=2 uses same operation key and no external call inside the retry.

`available=on_hand-reserved-allocated`. RESERVE increases reserved; SETTLE decreases reserved and increases allocated; RELEASE decreases reserved; DISPATCH decreases allocated and on_hand together; RETURN increases on_hand only after verified physical return/authorized unshipped reversal. ADJUST changes on_hand subject to balance check. Insert movement and update balance in the same transaction. Allocate one launch location per line using configured priority; sort locks globally. Never reserve through a read-then-write without locking/conditional update.

T2 payment initialization: claim persisted attempt INITIATING with short lease, then release DB transaction, call provider with stable reference, then persist PENDING and hosted URL. Timeout/crash means UNKNOWN; independent verify before any retry. A second attempt for the order is forbidden while any attempt is CREATED/INITIATING/PENDING/UNKNOWN. Same idempotency response must progress to or reveal the existing resource state, never create another order.

T3 settlement: authenticate and store webhook receipt; asynchronously or promptly verify provider state. Lock sales_order, attempt, reservations and balances in the same ordering convention. Validate provider merchant/account identity from configured credentials, transaction reference, amount in exact minor units, currency and terminal success. Insert one payment_transaction; convert reservations; consume coupon; set Paid and write outbox/history atomically. Duplicate successful event performs no additional transition and returns acknowledged result. A failed verification is retained/reconciled, not ignored as a paid order.

Expiry task locks order and attempt. Never assume an expired URL is a payment failure. If provider is uncertain/unavailable mark reconciliation needed and apply configured grace up to60m; after that release reservation but retain payment UNKNOWN and potential late-settlement monitoring. Expiry and T3 cannot concurrently allocate/release the same stock. On late settlement, atomically re-reserve and allocate if stock exists; otherwise record settled financial truth, set PAYMENT_HOLD and hold_reason=STOCK_UNAVAILABLE, do not fulfill. Finance initiates an authorized refund or customer-approved stock resolution. No silent oversell and no discarding real settlement.

OrderState: PENDING -> PAID -> PROCESSING -> SHIPPED -> DELIVERED; PENDING -> CANCELLED; verified late success may enter PAYMENT_HOLD then PAID after stock resolution or REFUNDING after approval. PAID/PROCESSING may enter REFUNDING only with authorized cancellation/refund. REFUNDING -> REFUNDED after confirmed full refund. Partial refunds preserve operational status while paymentStatus=PARTIALLY_REFUNDED. Delivered returns do not revert shipment status. Do not combine cancellation, payment settlement and fulfillment into one enum.

PaymentAttemptState: CREATED -> INITIATING -> PENDING or UNKNOWN or FAILED; PENDING/UNKNOWN -> SUCCEEDED or FAILED/CANCELLED only after provider verification. SUCCEEDED is terminal; refunds have separate records. Order paymentStatus UNPAID|PENDING|PAID|PARTIALLY_REFUNDED|REFUNDED|FAILED; fulfillmentStatus UNFULFILLED|PROCESSING|PARTIALLY_SHIPPED|SHIPPED|DELIVERED|CANCELLED|HOLD. SQL lifecycle columns enforce these sets. CartState ACTIVE|CHECKOUT_PENDING|CONVERTED|EXPIRED. QuoteState and other enum sets match table CHECK constraints.

## Refunds and rewards

Refund reserve: under order lock, `successful_refunds + pending/unknown_refund_reservations + requested_amount <= verified_settlement`. Stable provider_reference and request_key are required. REQUESTED -> APPROVED/REJECTED; APPROVED -> SUBMITTING -> PENDING/UNKNOWN -> SUCCEEDED/FAILED; only authoritative provider outcome finalizes success. Paystack refund is dispatched by commerce-owned scheduler/outbox handler, never integration worker credentials. Crash after provider call requires verify/list-by-reference reconciliation; never send a second refund solely because a timeout occurred. Inventory reversal is a separate validated unshipped cancellation/physical return operation. Monetary refund does not automatically add stock.

Reward/referral listener locks qualification context and inserts a unique business-key earn entry exactly once after configured hold/eligibility. Refund reversal references original earning and unique refund business key; cumulative reversal is capped at original earning; no negative editable counters. Dealer staff issue checks settled purchase thresholds and entitlement. Coupons reserve and consume usage atomically with order, including per-party ceiling. Wheel draw uses campaign/party-day lock (pg_advisory_xact_lock over stable scoped hash or campaign row plus unique draw slot) and campaign budget reservation; cryptographic random sample, one immutable result per request key.

## Distributor, quote and publication consistency

Experience decision transaction records ACTIVATION_PENDING or SUSPENSION_PENDING and a durable command ID/outbox. The owning command relay calls commerce's scoped entitlement API after commit. Commerce command is idempotent and returns revision/status. Experience applies ACK under application lock; at-least-once event projection is a recovery path. Do not display APPROVED/SUSPENDED until commerce ACK; during suspension pending, the source staff workflow reports retry/unknown. Commerce entitlement checks are immediately authoritative once command succeeds.

Quote acceptance locks quote/revision and actor context, validates offered approved revision and expiry, then calls the same **local** checkout application port with frozen quote terms. Record one accepted order and quote.accepted outbox in the same commerce transaction. Catalog price changes do not replace accepted terms; market/eligibility/stock still revalidate. Lead association is acknowledged experience command after commit.

Publication transaction sets approved current revision pointer and outbox; withdrawal immediately clears/suppresses source. AI checks each selected source revision with owner before using it; missing owner response fails closed for that evidence. Newsletter withdrawal commits consent_event and local suppression before sending provider list-sync work. Marketing sender checks current eligibility just before the provider call; cancellation race cannot retract a send already accepted by provider and this residual window is recorded, not described as a global distributed guarantee.

## Outbox relay and external side effects

Claim <=100 outbox rows with FOR UPDATE SKIP LOCKED, set CLAIMED and lease_until, commit, then publish. A successful SNS publish followed by process crash may publish again; consumer dedup handles it. Reclaim expired leases. Never hold DB locks during network calls or delete evidence before publication is confirmed. External email send has no universal exactly-once guarantee: a timeout after acceptance becomes UNKNOWN, inspect provider outcome/events if possible, otherwise staff policy chooses delayed resend or no resend. Business order/reward effects remain idempotent even if a provider notification duplicates.

No distributed locks or cross-service database transactions. Cross-service workflows use local transactions, outbox/durable command state, acknowledged APIs and compensating business actions. All uncertain outcomes have visible statuses and operator runbooks.


## Required carrier and multichannel launch transactions

Carrier rate lookup executes outside DB transactions. Commerce persists a5m bound carrier_quote after checking cart revision, server packing, destination and NGN/mode; checkout price quote references it, T1 revalidates/fixes fee and creates frozen carrier_snapshot with order. Stock/payment consistency never depends on a live carrier HTTP call inside T1/T3. Carrier API failure prevents a new delivery quote; an existing valid bound quote may proceed under configured expiry, never an invented fee.

Paid order fulfillment creates shipment/booking_request and outbox in commerce transaction; integration persists carrier_booking/job on event consumption, calls FedEx after commit, stores booking/label/tracking and posts idempotent commerce result. UNKNOWN keeps order unshipped; staff reconciliation does not blindly create another label. Carrier label creation never dispatches stock; physical handover/authoritative accepted scan must invoke once-only dispatch guard. Monetary refund and physical stock return remain separate. Verified tracking can update fulfillment but never rewrite paid/refund truth. Carrier exception/failure is visible; manual contingency requires audited explicit operator action.

Consent/destination proof transaction activates phone destination and appends only requested purpose grants; withdrawal/STOP writes suppression before asynchronous cleanup. Receipt application is local under job/receipt lock, matched account/SID/mode only, terminal delivery cannot regress from an older callback. Marketing eligibility is rechecked immediately before side effect; no blanket transactional override for unverified phone or unconsented channel.

