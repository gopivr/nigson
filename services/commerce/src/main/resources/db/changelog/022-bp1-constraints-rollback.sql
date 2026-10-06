ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_currency_code_fk;

ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_market_id_fk;

ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_cart_id_fk;

ALTER TABLE commerce.shipment_event DROP CONSTRAINT shipment_event_shipment_id_fk;

ALTER TABLE commerce.shipment_line DROP CONSTRAINT shipment_line_balance_id_fk;

ALTER TABLE commerce.shipment_line DROP CONSTRAINT shipment_line_order_line_id_fk;

ALTER TABLE commerce.shipment_line DROP CONSTRAINT shipment_line_shipment_id_fk;

ALTER TABLE commerce.shipment DROP CONSTRAINT shipment_label_asset_id_fk;

ALTER TABLE commerce.shipment DROP CONSTRAINT shipment_order_id_fk;

ALTER TABLE commerce.refund_line DROP CONSTRAINT refund_line_order_line_id_fk;

ALTER TABLE commerce.refund_line DROP CONSTRAINT refund_line_refund_id_fk;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_approved_by_fk;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_requested_by_fk;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_currency_code_fk;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_attempt_id_fk;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_order_id_fk;

ALTER TABLE commerce.payment_transaction DROP CONSTRAINT payment_transaction_currency_code_fk;

ALTER TABLE commerce.payment_transaction DROP CONSTRAINT payment_transaction_attempt_id_fk;

ALTER TABLE commerce.payment_attempt DROP CONSTRAINT payment_attempt_currency_code_fk;

ALTER TABLE commerce.payment_attempt DROP CONSTRAINT payment_attempt_order_id_fk;

ALTER TABLE commerce.inventory_movement DROP CONSTRAINT inventory_movement_order_line_id_fk;

ALTER TABLE commerce.inventory_movement DROP CONSTRAINT inventory_movement_order_id_fk;

ALTER TABLE commerce.inventory_movement DROP CONSTRAINT inventory_movement_balance_id_fk;

ALTER TABLE commerce.inventory_reservation DROP CONSTRAINT inventory_reservation_balance_id_fk;

ALTER TABLE commerce.inventory_reservation DROP CONSTRAINT inventory_reservation_order_id_fk;

ALTER TABLE commerce.inventory_balance DROP CONSTRAINT inventory_balance_location_id_fk;

ALTER TABLE commerce.inventory_balance DROP CONSTRAINT inventory_balance_sku_id_fk;

ALTER TABLE commerce.order_status_history DROP CONSTRAINT order_status_history_order_id_fk;

ALTER TABLE commerce.order_total_component DROP CONSTRAINT order_total_component_order_id_fk;

ALTER TABLE commerce.order_line DROP CONSTRAINT order_line_sku_id_fk;

ALTER TABLE commerce.order_line DROP CONSTRAINT order_line_order_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_carrier_quote_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_currency_code_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_cart_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_market_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_party_id_fk;

ALTER TABLE commerce.checkout_quote DROP CONSTRAINT checkout_quote_carrier_quote_id_fk;

ALTER TABLE commerce.checkout_quote DROP CONSTRAINT checkout_quote_cart_id_fk;

ALTER TABLE commerce.cart_line DROP CONSTRAINT cart_line_sku_id_fk;

ALTER TABLE commerce.cart_line DROP CONSTRAINT cart_line_cart_id_fk;

ALTER TABLE commerce.cart DROP CONSTRAINT cart_converted_order_id_fk;

ALTER TABLE commerce.cart DROP CONSTRAINT cart_market_id_fk;

ALTER TABLE commerce.cart DROP CONSTRAINT cart_party_id_fk;

ALTER TABLE commerce.delivery_rule DROP CONSTRAINT delivery_rule_market_id_fk;

ALTER TABLE commerce.price_entry DROP CONSTRAINT price_entry_sku_id_fk;

ALTER TABLE commerce.price_entry DROP CONSTRAINT price_entry_price_book_id_fk;

ALTER TABLE commerce.price_book DROP CONSTRAINT price_book_currency_code_fk;

ALTER TABLE commerce.product_market_eligibility DROP CONSTRAINT product_market_eligibility_market_id_fk;

ALTER TABLE commerce.product_market_eligibility DROP CONSTRAINT product_market_eligibility_product_id_fk;

ALTER TABLE commerce.market_destination DROP CONSTRAINT market_destination_market_id_fk;

ALTER TABLE commerce.market DROP CONSTRAINT market_retail_price_book_id_fk;

ALTER TABLE commerce.market DROP CONSTRAINT market_currency_code_fk;

ALTER TABLE commerce.asset_reference DROP CONSTRAINT asset_reference_shipment_id_fk;

ALTER TABLE commerce.asset_reference DROP CONSTRAINT asset_reference_sku_id_fk;

ALTER TABLE commerce.asset_reference DROP CONSTRAINT asset_reference_product_id_fk;

ALTER TABLE commerce.product_attribute DROP CONSTRAINT product_attribute_product_id_fk;

ALTER TABLE commerce.product_category DROP CONSTRAINT product_category_category_id_fk;

ALTER TABLE commerce.product_category DROP CONSTRAINT product_category_product_id_fk;

ALTER TABLE commerce.sku DROP CONSTRAINT sku_product_id_fk;

ALTER TABLE commerce.product DROP CONSTRAINT product_brand_id_fk;

ALTER TABLE commerce.category DROP CONSTRAINT category_parent_id_fk;

ALTER TABLE commerce.address DROP CONSTRAINT address_party_id_fk;

ALTER TABLE commerce.staff_role_assignment DROP CONSTRAINT staff_role_assignment_granted_by_fk;

ALTER TABLE commerce.staff_role_assignment DROP CONSTRAINT staff_role_assignment_party_id_fk;

ALTER TABLE commerce.customer_profile DROP CONSTRAINT customer_profile_party_id_fk;
