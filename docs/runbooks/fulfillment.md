# Delivery rules, shipments and physical returns






API-050 accepts only SHIPPED and actual deliveredAt between dispatch and current time. Partial deliveries keep the order incomplete; all eligible quantities must be delivered before aggregate DELIVERED. Payment status and totals remain financial-owner truth. Customer/staff tracking callbacks and out-of-order provider projection are separate owning tasks; these commands do not infer carrier HTTP success.

API-174 requires current OPERATIONS and a durable key. PHYSICAL_RETURN needs an explicit verified receipt and cumulative dispatched-quantity guard. The canonical SHA256 identity binds order/kind/receipt, so a different HTTP key for the same receipt cannot add stock again; changed contents conflict. UNSHIPPED_CANCEL requires an authorized cancelled/refunding/refunded order and releases allocation without increasing on_hand. If its reference is omitted, the server derives a stable command reference; no physical evidence is invented. Monetary refund and physical stock receipt are separate actions. A return changes no payment status/amount and does not require a fabricated refund success.

All mutations audit and commit acknowledgments atomically. Locks/statement waits are bounded; conflicts409 can retry the same intended key, stale412 needs a fresh state/version, and invalid state/rules409/422 need correction. Matching replay returns the original acknowledgment; current Party/role checks precede it. No provider HTTP occurs in these order/stock transactions. Tracking URLs must be HTTPS, without credentials/fragment/nonstandard port, and use an exact configured host in `NIGSON_FULFILLMENT_TRACKING_HOSTS`.

Run `ENV=test JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home DOCKER_CONFIG=/private/tmp/nigson-public-image-fetch DOCKER_HOST=unix:///Users/gopiravi/.docker/run/docker.sock python3 scripts/fulfillment/verify.py` or the equivalent `make fulfillment-check` environment. The harness uses isolated native PostgreSQL, actual encrypted JDBC BFF sessions and explicitly synthetic policies/prices/settlement/receipt/label fixtures. It checks local state/volumes are preserved. This demonstrates engineering behavior; it does not qualify real carrier accounts/routes/labels, actual business fees, production approvals or the required order/payment/private carrier release composition.
