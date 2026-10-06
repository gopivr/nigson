DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.party) THEN RAISE EXCEPTION 'Populated table commerce.party: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.customer_profile) THEN RAISE EXCEPTION 'Populated table commerce.customer_profile: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.staff_role_assignment) THEN RAISE EXCEPTION 'Populated table commerce.staff_role_assignment: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.address) THEN RAISE EXCEPTION 'Populated table commerce.address: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.brand) THEN RAISE EXCEPTION 'Populated table commerce.brand: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.category) THEN RAISE EXCEPTION 'Populated table commerce.category: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.product) THEN RAISE EXCEPTION 'Populated table commerce.product: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.sku) THEN RAISE EXCEPTION 'Populated table commerce.sku: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.product_category) THEN RAISE EXCEPTION 'Populated table commerce.product_category: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.product_attribute) THEN RAISE EXCEPTION 'Populated table commerce.product_attribute: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.asset_reference) THEN RAISE EXCEPTION 'Populated table commerce.asset_reference: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.currency) THEN RAISE EXCEPTION 'Populated table commerce.currency: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.market) THEN RAISE EXCEPTION 'Populated table commerce.market: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.market_destination) THEN RAISE EXCEPTION 'Populated table commerce.market_destination: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.product_market_eligibility) THEN RAISE EXCEPTION 'Populated table commerce.product_market_eligibility: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.price_book) THEN RAISE EXCEPTION 'Populated table commerce.price_book: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.price_entry) THEN RAISE EXCEPTION 'Populated table commerce.price_entry: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.delivery_rule) THEN RAISE EXCEPTION 'Populated table commerce.delivery_rule: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.cart) THEN RAISE EXCEPTION 'Populated table commerce.cart: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.cart_line) THEN RAISE EXCEPTION 'Populated table commerce.cart_line: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.checkout_quote) THEN RAISE EXCEPTION 'Populated table commerce.checkout_quote: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.sales_order) THEN RAISE EXCEPTION 'Populated table commerce.sales_order: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.order_line) THEN RAISE EXCEPTION 'Populated table commerce.order_line: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.order_total_component) THEN RAISE EXCEPTION 'Populated table commerce.order_total_component: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.order_status_history) THEN RAISE EXCEPTION 'Populated table commerce.order_status_history: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.stock_location) THEN RAISE EXCEPTION 'Populated table commerce.stock_location: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.inventory_balance) THEN RAISE EXCEPTION 'Populated table commerce.inventory_balance: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.inventory_reservation) THEN RAISE EXCEPTION 'Populated table commerce.inventory_reservation: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.inventory_movement) THEN RAISE EXCEPTION 'Populated table commerce.inventory_movement: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.payment_attempt) THEN RAISE EXCEPTION 'Populated table commerce.payment_attempt: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.payment_transaction) THEN RAISE EXCEPTION 'Populated table commerce.payment_transaction: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.webhook_receipt) THEN RAISE EXCEPTION 'Populated table commerce.webhook_receipt: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.refund) THEN RAISE EXCEPTION 'Populated table commerce.refund: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.refund_line) THEN RAISE EXCEPTION 'Populated table commerce.refund_line: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.shipment) THEN RAISE EXCEPTION 'Populated table commerce.shipment: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.shipment_line) THEN RAISE EXCEPTION 'Populated table commerce.shipment_line: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.shipment_event) THEN RAISE EXCEPTION 'Populated table commerce.shipment_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.carrier_quote) THEN RAISE EXCEPTION 'Populated table commerce.carrier_quote: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.carrier_quote;

DROP TABLE commerce.shipment_event;

DROP TABLE commerce.shipment_line;

DROP TABLE commerce.shipment;

DROP TABLE commerce.refund_line;

DROP TABLE commerce.refund;

DROP TABLE commerce.webhook_receipt;

DROP TABLE commerce.payment_transaction;

DROP TABLE commerce.payment_attempt;

DROP TABLE commerce.inventory_movement;

DROP TABLE commerce.inventory_reservation;

DROP TABLE commerce.inventory_balance;

DROP TABLE commerce.stock_location;

DROP TABLE commerce.order_status_history;

DROP TABLE commerce.order_total_component;

DROP TABLE commerce.order_line;

DROP TABLE commerce.sales_order;

DROP TABLE commerce.checkout_quote;

DROP TABLE commerce.cart_line;

DROP TABLE commerce.cart;

DROP TABLE commerce.delivery_rule;

DROP TABLE commerce.price_entry;

DROP TABLE commerce.price_book;

DROP TABLE commerce.product_market_eligibility;

DROP TABLE commerce.market_destination;

DROP TABLE commerce.market;

DROP TABLE commerce.currency;

DROP TABLE commerce.asset_reference;

DROP TABLE commerce.product_attribute;

DROP TABLE commerce.product_category;

DROP TABLE commerce.sku;

DROP TABLE commerce.product;

DROP TABLE commerce.category;

DROP TABLE commerce.brand;

DROP TABLE commerce.address;

DROP TABLE commerce.staff_role_assignment;

DROP TABLE commerce.customer_profile;

DROP TABLE commerce.party;
