-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.customer_profile ADD CONSTRAINT customer_profile_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.staff_role_assignment ADD CONSTRAINT staff_role_assignment_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.staff_role_assignment ADD CONSTRAINT staff_role_assignment_granted_by_fk FOREIGN KEY (granted_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.address ADD CONSTRAINT address_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.category ADD CONSTRAINT category_parent_id_fk FOREIGN KEY (parent_id) REFERENCES commerce.category (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product ADD CONSTRAINT product_brand_id_fk FOREIGN KEY (brand_id) REFERENCES commerce.brand (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sku ADD CONSTRAINT sku_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_category ADD CONSTRAINT product_category_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_category ADD CONSTRAINT product_category_category_id_fk FOREIGN KEY (category_id) REFERENCES commerce.category (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_attribute ADD CONSTRAINT product_attribute_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.asset_reference ADD CONSTRAINT asset_reference_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.asset_reference ADD CONSTRAINT asset_reference_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.asset_reference ADD CONSTRAINT asset_reference_shipment_id_fk FOREIGN KEY (shipment_id) REFERENCES commerce.shipment (id) ON DELETE RESTRICT;

ALTER TABLE commerce.market ADD CONSTRAINT market_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.market ADD CONSTRAINT market_retail_price_book_id_fk FOREIGN KEY (retail_price_book_id) REFERENCES commerce.price_book (id) ON DELETE RESTRICT;

ALTER TABLE commerce.market_destination ADD CONSTRAINT market_destination_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_market_eligibility ADD CONSTRAINT product_market_eligibility_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_market_eligibility ADD CONSTRAINT product_market_eligibility_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.price_book ADD CONSTRAINT price_book_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.price_entry ADD CONSTRAINT price_entry_price_book_id_fk FOREIGN KEY (price_book_id) REFERENCES commerce.price_book (id) ON DELETE RESTRICT;

ALTER TABLE commerce.price_entry ADD CONSTRAINT price_entry_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.delivery_rule ADD CONSTRAINT delivery_rule_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart ADD CONSTRAINT cart_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart ADD CONSTRAINT cart_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart ADD CONSTRAINT cart_converted_order_id_fk FOREIGN KEY (converted_order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart_line ADD CONSTRAINT cart_line_cart_id_fk FOREIGN KEY (cart_id) REFERENCES commerce.cart (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart_line ADD CONSTRAINT cart_line_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.checkout_quote ADD CONSTRAINT checkout_quote_cart_id_fk FOREIGN KEY (cart_id) REFERENCES commerce.cart (id) ON DELETE RESTRICT;

ALTER TABLE commerce.checkout_quote ADD CONSTRAINT checkout_quote_carrier_quote_id_fk FOREIGN KEY (carrier_quote_id) REFERENCES commerce.carrier_quote (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_cart_id_fk FOREIGN KEY (cart_id) REFERENCES commerce.cart (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_carrier_quote_id_fk FOREIGN KEY (carrier_quote_id) REFERENCES commerce.carrier_quote (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_line ADD CONSTRAINT order_line_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_line ADD CONSTRAINT order_line_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_total_component ADD CONSTRAINT order_total_component_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_status_history ADD CONSTRAINT order_status_history_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_balance ADD CONSTRAINT inventory_balance_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_balance ADD CONSTRAINT inventory_balance_location_id_fk FOREIGN KEY (location_id) REFERENCES commerce.stock_location (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_reservation ADD CONSTRAINT inventory_reservation_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_reservation ADD CONSTRAINT inventory_reservation_balance_id_fk FOREIGN KEY (balance_id) REFERENCES commerce.inventory_balance (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_movement ADD CONSTRAINT inventory_movement_balance_id_fk FOREIGN KEY (balance_id) REFERENCES commerce.inventory_balance (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_movement ADD CONSTRAINT inventory_movement_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.inventory_movement ADD CONSTRAINT inventory_movement_order_line_id_fk FOREIGN KEY (order_line_id) REFERENCES commerce.order_line (id) ON DELETE RESTRICT;

ALTER TABLE commerce.payment_attempt ADD CONSTRAINT payment_attempt_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.payment_attempt ADD CONSTRAINT payment_attempt_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.payment_transaction ADD CONSTRAINT payment_transaction_attempt_id_fk FOREIGN KEY (attempt_id) REFERENCES commerce.payment_attempt (id) ON DELETE RESTRICT;

ALTER TABLE commerce.payment_transaction ADD CONSTRAINT payment_transaction_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.refund ADD CONSTRAINT refund_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.refund ADD CONSTRAINT refund_attempt_id_fk FOREIGN KEY (attempt_id) REFERENCES commerce.payment_attempt (id) ON DELETE RESTRICT;

ALTER TABLE commerce.refund ADD CONSTRAINT refund_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.refund ADD CONSTRAINT refund_requested_by_fk FOREIGN KEY (requested_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.refund ADD CONSTRAINT refund_approved_by_fk FOREIGN KEY (approved_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.refund_line ADD CONSTRAINT refund_line_refund_id_fk FOREIGN KEY (refund_id) REFERENCES commerce.refund (id) ON DELETE RESTRICT;

ALTER TABLE commerce.refund_line ADD CONSTRAINT refund_line_order_line_id_fk FOREIGN KEY (order_line_id) REFERENCES commerce.order_line (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment ADD CONSTRAINT shipment_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment ADD CONSTRAINT shipment_label_asset_id_fk FOREIGN KEY (label_asset_id) REFERENCES commerce.asset_reference (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment_line ADD CONSTRAINT shipment_line_shipment_id_fk FOREIGN KEY (shipment_id) REFERENCES commerce.shipment (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment_line ADD CONSTRAINT shipment_line_order_line_id_fk FOREIGN KEY (order_line_id) REFERENCES commerce.order_line (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment_line ADD CONSTRAINT shipment_line_balance_id_fk FOREIGN KEY (balance_id) REFERENCES commerce.inventory_balance (id) ON DELETE RESTRICT;

ALTER TABLE commerce.shipment_event ADD CONSTRAINT shipment_event_shipment_id_fk FOREIGN KEY (shipment_id) REFERENCES commerce.shipment (id) ON DELETE RESTRICT;

ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_cart_id_fk FOREIGN KEY (cart_id) REFERENCES commerce.cart (id) ON DELETE RESTRICT;

ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;
