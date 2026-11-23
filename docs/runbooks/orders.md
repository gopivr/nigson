# Checkout and owned orders

API-037 uses the current CUSTOMER and explicit organization buying authority. T1 locks the owned cart, validates the stored quote/hash/expiry and recalculates through the commerce-local CheckoutPricing port. Current market policy, price revision/tiers, quantity/pack/wholesale minima, address, server packages and carrier quote/proof must match. Recalculation writes no extra quote and performs no carrier/provider HTTP. Changed commercial inputs require a new quote; an expired cached quote cannot create an order.

T1 freezes owned order, immutable lines/components/address/commercial/carrier snapshots, stock reservations, cart CHECKOUT_PENDING, one Paystack attempt and history/audit/outbox together. Select one configured location per SKU by explicit priority; globally sorted balance locks and StockLedger enforce conservation. No location/account/tax/delivery fee is guessed. Repeating the same command returns its existing resource, and a different HTTP key with the original cart/quote/hash cannot create a second order or payable attempt. A different quote for an already checked-out cart is rejected. New Paystack references use supported alphanumeric/hyphen characters.

The public commerce-local OrderCheckout.pending port joins a caller transaction and commits the same complete T1 behavior. It performs no provider calls and supports future owning offered-quote acceptance. API-037 claims initialization only after T1 commits, persists INITIATING with a30s lease, releases locks, calls the real Paystack adapter and then persists PENDING or UNKNOWN. Crashes/expired leases and uncertain errors never trigger a second initialization.202 returns the existing order/attempt when queued or uncertain. MOCK's actual HTTP hosted URL remains private and checkoutUrl is null, preserving the wire HTTPS|null contract; qualified TEST/LIVE uses the adapter-validated HTTPS checkout URL.

Checkout is disabled by default. Explicit activation requires NIGSON_ORDER_CHECKOUT_ENABLED, APP_ENV, NIGSON_ORDER_PAYMENT_MODE, NIGSON_ORDER_MERCHANT_ACCOUNT and NIGSON_ORDER_STOCK_LOCATION_PRIORITY (one to ten ordered UUIDs). NIGSON_ORDER_RESERVATION_SECONDS defaults900 and validates60..3600. The actual NIGSON_PROVIDERS_ENABLED Paystack adapter/configuration must be present and its mode must match the order mode. Pricing provider approvals must also be current; TEST payment mode binds SANDBOX carrier mode, LIVE binds LIVE, and MOCK is restricted to local/test. Production cannot fall back to mocks. Real account/key association and provider/carrier routes remain release qualification inputs.

Independent verification occurs outside DB transactions against the stored reference and exact NGN kobo amount/mode/account. A verified successful provider record supplies a stable transaction ID and paid_at, then payment transaction/attempt and OrderLifecycle commit stock allocation, cart conversion, paid history/audit/outbox together. Duplicate verification cannot insert another transaction or allocate twice. Immutable financial truth is not inferred from a browser callback. Released stock is re-reserved only on the first independently verified late settlement when all original balances/current references have capacity. Otherwise the order persists paymentStatus PAID/status PAYMENT_HOLD/hold_reason STOCK_UNAVAILABLE, without fulfillment. Repeated settlement does not auto-resolve a payment hold when stock changes; the owning finance/customer-approved resolution workflow remains required.

API-038/039 returns frozen private values to the retail owner or current active organization members; revoked membership and unauthorized resources return404. Historical reads do not require a fresh wholesale buying entitlement. The immutable address excludes mutable address/organization identifiers and versions. API-043 requires current persisted OPERATIONS or FINANCE (recent MFA for FINANCE), bounded stable page/filter queries and no shared private cache. API-044 requires OPERATIONS, cookie CSRF/key/If-Match and actual verified paid unheld truth, then updates only operational state with history/audit/outbox. Money and stock remain unchanged.

API-040 requires current owned order/key/CSRF/If-Match. It inspects uncertain payment outside DB transactions; pending/UNKNOWN/unverified outcomes cannot cancel or release stock. Independently failed/cancelled unpaid PENDING may atomically cancel, release reservation, expire the pending cart and append history/audit/outbox. Verification can change a financial version; refresh on412 and use a new command key for a changed intended version. A paid cancellation belongs to the authorized refund workflow. No refund is implied by cancellation, and monetary refunds never restore stock.

API-121 is private exact nigson-experience/purchase-proof.read. The real workload decoder enforces issuer, client, audience, scope and private ingress; browser cookies/actors cannot substitute. The response contains only eligible, reasonCode and revision after party/line/product association, current active party, independent successful settlement and nonfully-refunded quantity checks. It exposes no account/order details and creates no mutable proof record.

Run `ENV=test JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home DOCKER_CONFIG=/private/tmp/nigson-public-image-fetch DOCKER_HOST=unix:///Users/gopiravi/.docker/run/docker.sock python3 scripts/order/verify.py` (or order-check with the same environment). The harness uses isolated native PostgreSQL/all-owner migrations and the pinned previously qualified stateful WireMock Paystack server/dummy credentials. Fixtures explicitly mark prices/tax/location/provider metadata synthetic. Owned API-041/042/052/053/054/118, signed durable callbacks/refund reconciliation/grace expiry and polling are covered by the payment increment and docs/runbooks/payments.md. The complete PAYSTACK-001/CARRIER-001 release qualification remains separate. Passing this harness does not activate actual business policies or qualify genuine provider accounts.


## Customer cancellation: PandaGo and Paystack (ADR-033)

API-040 now accepts an unshipped order cancellation and normally returns 202.
GET order and command replay return the current `cancellation` and
`cancellationAllowed` values. A request excludes preparation, booking, hosted
payment initialization and physical dispatch under the Commerce order lock.
SHIPPED/DELIVERED orders require the separate returns workflow.

Deploy Commerce036/037 and Integration027 migrations before the new artifacts.
Every Commerce instance must honor the new exclusion before requests are served.
The Commerce saga worker runs when NIGSON_ORDER_CHECKOUT_ENABLED=true; keep that
existing setting enabled while servicing these cancellation requests.
Enable `NIGSON_CARRIER_CANCELLATION_ENABLED=true` on Commerce and Integration
(together, including docker-compose.pandago.yml); the flag defaults off. Existing
PandaGo approval, mode/account binding, private workload credentials, externally
managed creation generation/cutoff and the real Paystack integration ID remain
required. Do not replace them with guessed values. Restart the applications and
rebuild/restart the customer and Admin frontends. This implementation does not
change an existing deployment or its .env automatically.

- REQUESTED: durable request accepted; worker checks current payment/delivery facts.
- WAITING_PAYMENT: verify/listen for Paystack outcome; no assumption that an expired
  checkout URL means failed payment. Late success records money without allocating
  cancelled stock or restarting fulfillment.
- WAITING_CARRIER: the original shipment cancellation is queued or unknown. Check
  Commerce carrier_cancellation and the matching Integration source_request_id.
  Unprepared bookings can be suppressed locally. A prepared booking with no known
  PandaGo order ID needs reference reconciliation through the provider/operator;
  do not create another delivery or infer that a missing local ID proves absence.
- REJECTED: authoritative refusal or physical handover prevents automatic cancellation.
  Stock and money remain intact; contact the carrier/support for the appropriate
  exception or returns workflow. Do not clear a hold to force dispatch of an
  uncertain cancellation.
- CANCELLED: every unshipped delivery is cancelled; reserved/allocated stock has
  been released exactly once without adding physical on_hand. A captured payment
  remains PAID until independently verified refund success.

Cancellation requests the remaining captured amount, including the frozen delivery
amount, less successful and active refund reservations. It does not deduct invented
carrier/payment fees. COD without collection creates no Paystack refund. Existing
Finance and two-person approval rules remain in force. Finance can open Admin
Orders, refresh the list, view the cancelled order and choose Review and approve
refund. The dialog confirms the current refund amount fetched from API-054 before
API-053 approval. Recent MFA and a separate approver (when configured) are enforced
by the backend. The order exposes only the opaque refund ID and safe status, not
provider/account details.

Refund REQUESTED needs Finance review. APPROVED/SUBMITTING/PENDING are processed by
the existing Commerce payment worker. UNKNOWN (including Paystack needs-attention)
requires reconciliation/customer assistance; it is not refund success. FAILED or
REJECTED requires Finance attention; fulfillment stays CANCELLED and no automatic
replacement refund is created. Paystack integration/account configuration must be
present for execution. Payment workers/webhooks independently verify the provider
outcome. Successful full refund changes paymentStatus to REFUNDED while order and
fulfillment remain CANCELLED.

PandaGo DELETE is authorized only once after a committed SUBMITTING checkpoint
and live fenced lease. 204 proves cancellation; legacy 203 requires GET proof.
409 is a known refusal; 404/timeout/malformed/unavailable responses remain UNKNOWN.
Repeated unknown reads replay the same persisted receipt; later cancellation proof
gets a new deterministic receipt ID. A known booking outcome arriving later can
permit the first cancellation DELETE only when no cancellation was ever submitted
and current generation/cutoff/account/approval checks still pass. Submitted, expired
or restored write authority cannot be recreated by retries.

Customer order pages poll cancellation/refund progress for up to ten minutes;
reload later for long bank processing. Admin Orders has an explicit Refresh action.
Logs use ORDER_CANCELLATION_RECOVERY_REQUIRED/REQUIRES_REVIEW and
PANDAGO_CANCELLATION_RECOVERY_REQUIRED/REQUIRES_REVIEW without personal/provider
payloads. Durable rows, audit records and original receipt IDs provide recovery
and operational evidence. Never manually set CANCELLED/REFUNDED to hide uncertainty.

Qualification: `python3 scripts/carrier/verify_order_cancellation.py` uses disposable
PostgreSQL and isolated provider simulators; `verify_cancellation_migrations.py`
checks previous-release upgrades, checksums, retained booking evidence and rollback.
Neither command establishes genuine PandaGo/Paystack or browser acceptance.
