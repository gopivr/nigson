DROP INDEX commerce.carrier_quote_source_quote_revision_id_fk_ix;

DROP INDEX commerce.carrier_quote_currency_code_fk_ix;

DROP INDEX commerce.carrier_quote_market_id_fk_ix;

DROP INDEX commerce.carrier_quote_cart_id_fk_ix;

ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_cart_id_cart_version_address_hash_packag_b991cb26;

DROP INDEX commerce.carrier_quote_cart_id_expires_at_ix;

ALTER TABLE commerce.feature_configuration DROP CONSTRAINT feature_configuration_key_revision_uq;

DROP INDEX commerce.audit_record_actor_subject_occurred_at_ix;

DROP INDEX commerce.audit_record_resource_type_resource_id_occurred_at_ix;

DROP INDEX commerce.idempotency_record_expires_at_ix;

ALTER TABLE commerce.idempotency_record DROP CONSTRAINT idempotency_record_principal_key_operation_request_key_uq;

ALTER TABLE commerce.processed_event DROP CONSTRAINT processed_event_consumer_event_id_uq;

DROP INDEX commerce.outbox_event_state_next_attempt_at_ix;

ALTER TABLE commerce.outbox_event DROP CONSTRAINT outbox_event_event_id_uq;

DROP INDEX commerce.shipment_event_shipment_id_fk_ix;

DROP INDEX commerce.shipment_event_shipment_id_occurred_at_ix;

ALTER TABLE commerce.shipment_event DROP CONSTRAINT shipment_event_source_event_key_uq;

DROP INDEX commerce.shipment_line_balance_id_fk_ix;

DROP INDEX commerce.shipment_line_order_line_id_fk_ix;

DROP INDEX commerce.shipment_line_shipment_id_fk_ix;

ALTER TABLE commerce.shipment_line DROP CONSTRAINT shipment_line_shipment_id_order_line_id_balance_id_uq;

DROP INDEX commerce.shipment_label_asset_id_fk_ix;

DROP INDEX commerce.shipment_order_id_fk_ix;

DROP INDEX commerce.shipment_provider_provider_reference_uq;

DROP INDEX commerce.shipment_order_id_ix;

DROP INDEX commerce.refund_line_order_line_id_fk_ix;

DROP INDEX commerce.refund_line_refund_id_fk_ix;

ALTER TABLE commerce.refund_line DROP CONSTRAINT refund_line_refund_id_order_line_id_uq;

DROP INDEX commerce.refund_approved_by_fk_ix;

DROP INDEX commerce.refund_requested_by_fk_ix;

DROP INDEX commerce.refund_currency_code_fk_ix;

DROP INDEX commerce.refund_attempt_id_fk_ix;

DROP INDEX commerce.refund_order_id_fk_ix;

DROP INDEX commerce.refund_state_created_at_ix;

DROP INDEX commerce.refund_provider_refund_id_uq;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_provider_reference_uq;

ALTER TABLE commerce.refund DROP CONSTRAINT refund_request_key_uq;

DROP INDEX commerce.webhook_receipt_state_received_at_ix;

ALTER TABLE commerce.webhook_receipt DROP CONSTRAINT webhook_receipt_provider_merchant_account_event_key_uq;

DROP INDEX commerce.payment_transaction_currency_code_fk_ix;

DROP INDEX commerce.payment_transaction_attempt_id_fk_ix;

ALTER TABLE commerce.payment_transaction DROP CONSTRAINT payment_transaction_provider_transaction_id_uq;

ALTER TABLE commerce.payment_transaction DROP CONSTRAINT payment_transaction_attempt_id_uq;

DROP INDEX commerce.payment_attempt_currency_code_fk_ix;

DROP INDEX commerce.payment_attempt_order_id_fk_ix;

DROP INDEX commerce.payment_attempt_order_id_uq;

DROP INDEX commerce.payment_attempt_state_next_reconcile_at_ix;

ALTER TABLE commerce.payment_attempt DROP CONSTRAINT payment_attempt_provider_merchant_account_reference_uq;

DROP INDEX commerce.inventory_movement_order_line_id_fk_ix;

DROP INDEX commerce.inventory_movement_order_id_fk_ix;

DROP INDEX commerce.inventory_movement_balance_id_fk_ix;

DROP INDEX commerce.inventory_movement_balance_id_created_at_ix;

ALTER TABLE commerce.inventory_movement DROP CONSTRAINT inventory_movement_operation_key_uq;

DROP INDEX commerce.inventory_reservation_balance_id_fk_ix;

DROP INDEX commerce.inventory_reservation_order_id_fk_ix;

DROP INDEX commerce.inventory_reservation_status_expires_at_ix;

ALTER TABLE commerce.inventory_reservation DROP CONSTRAINT inventory_reservation_order_id_balance_id_uq;

DROP INDEX commerce.inventory_balance_location_id_fk_ix;

DROP INDEX commerce.inventory_balance_sku_id_fk_ix;

ALTER TABLE commerce.inventory_balance DROP CONSTRAINT inventory_balance_sku_id_location_id_uq;

ALTER TABLE commerce.stock_location DROP CONSTRAINT stock_location_code_uq;

DROP INDEX commerce.order_status_history_order_id_fk_ix;

DROP INDEX commerce.order_status_history_order_id_created_at_ix;

DROP INDEX commerce.order_total_component_order_id_fk_ix;

DROP INDEX commerce.order_total_component_order_id_ix;

DROP INDEX commerce.order_line_sku_id_fk_ix;

DROP INDEX commerce.order_line_order_id_fk_ix;

DROP INDEX commerce.order_line_order_id_ix;

DROP INDEX commerce.sales_order_carrier_quote_id_fk_ix;

DROP INDEX commerce.sales_order_accepted_quote_revision_id_fk_ix;

DROP INDEX commerce.sales_order_currency_code_fk_ix;

DROP INDEX commerce.sales_order_cart_id_fk_ix;

DROP INDEX commerce.sales_order_market_id_fk_ix;

DROP INDEX commerce.sales_order_organization_id_fk_ix;

DROP INDEX commerce.sales_order_party_id_fk_ix;

DROP INDEX commerce.sales_order_status_created_at_ix;

DROP INDEX commerce.sales_order_organization_id_created_at_ix;

DROP INDEX commerce.sales_order_party_id_created_at_ix;

DROP INDEX commerce.sales_order_cart_id_uq;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_order_number_uq;

DROP INDEX commerce.checkout_quote_carrier_quote_id_fk_ix;

DROP INDEX commerce.checkout_quote_cart_id_fk_ix;

DROP INDEX commerce.checkout_quote_cart_id_expires_at_ix;

DROP INDEX commerce.cart_line_sku_id_fk_ix;

DROP INDEX commerce.cart_line_cart_id_fk_ix;

ALTER TABLE commerce.cart_line DROP CONSTRAINT cart_line_cart_id_sku_id_uq;

DROP INDEX commerce.cart_active_context_uq;

DROP INDEX commerce.cart_converted_order_id_fk_ix;

DROP INDEX commerce.cart_market_id_fk_ix;

DROP INDEX commerce.cart_organization_id_fk_ix;

DROP INDEX commerce.cart_party_id_fk_ix;

DROP INDEX commerce.cart_status_last_activity_at_ix;

DROP INDEX commerce.cart_party_id_status_ix;

DROP INDEX commerce.delivery_rule_market_id_fk_ix;

DROP INDEX commerce.delivery_rule_market_id_country_code_priority_ix;

DROP INDEX commerce.price_entry_sku_id_fk_ix;

DROP INDEX commerce.price_entry_price_book_id_fk_ix;

DROP INDEX commerce.price_entry_sku_id_price_book_id_ix;

ALTER TABLE commerce.price_entry DROP CONSTRAINT price_entry_price_book_id_sku_id_minimum_quantity_uq;

DROP INDEX commerce.price_book_currency_code_fk_ix;

DROP INDEX commerce.price_book_segment_currency_code_effective_from_ix;

ALTER TABLE commerce.price_book DROP CONSTRAINT price_book_code_revision_uq;

DROP INDEX commerce.product_market_eligibility_market_id_product_id_ix;

DROP INDEX commerce.product_market_eligibility_market_id_fk_ix;

DROP INDEX commerce.product_market_eligibility_product_id_fk_ix;

ALTER TABLE commerce.product_market_eligibility DROP CONSTRAINT product_market_eligibility_product_id_market_id_uq;

DROP INDEX commerce.market_destination_market_id_fk_ix;

ALTER TABLE commerce.market_destination DROP CONSTRAINT market_destination_market_id_country_code_uq;

DROP INDEX commerce.market_retail_price_book_id_fk_ix;

DROP INDEX commerce.market_currency_code_fk_ix;

ALTER TABLE commerce.market DROP CONSTRAINT market_code_uq;

ALTER TABLE commerce.currency DROP CONSTRAINT currency_code_uq;

DROP INDEX commerce.asset_reference_shipment_id_fk_ix;

DROP INDEX commerce.asset_reference_sku_id_fk_ix;

DROP INDEX commerce.asset_reference_product_id_fk_ix;

DROP INDEX commerce.asset_reference_product_id_purpose_ix;

ALTER TABLE commerce.asset_reference DROP CONSTRAINT asset_reference_object_key_uq;

DROP INDEX commerce.product_attribute_product_id_fk_ix;

ALTER TABLE commerce.product_attribute DROP CONSTRAINT product_attribute_product_id_attribute_key_uq;

DROP INDEX commerce.product_category_category_id_fk_ix;

DROP INDEX commerce.product_category_product_id_fk_ix;

DROP INDEX commerce.product_category_category_id_product_id_ix;

ALTER TABLE commerce.product_category DROP CONSTRAINT product_category_product_id_category_id_uq;

DROP INDEX commerce.sku_product_id_active_ix;

DROP INDEX commerce.sku_product_id_fk_ix;

DROP INDEX commerce.sku_product_id_ix;

ALTER TABLE commerce.sku DROP CONSTRAINT sku_code_uq;

DROP INDEX commerce.product_brand_id_fk_ix;

DROP INDEX commerce.product_status_published_at_ix;

DROP INDEX commerce.product_search_vector_ix;

ALTER TABLE commerce.product DROP CONSTRAINT product_slug_uq;

ALTER TABLE commerce.product DROP COLUMN search_vector;

DROP INDEX commerce.category_parent_id_fk_ix;

DROP INDEX commerce.category_parent_id_ix;

ALTER TABLE commerce.category DROP CONSTRAINT category_slug_uq;

ALTER TABLE commerce.brand DROP CONSTRAINT brand_slug_uq;

DROP INDEX commerce.address_organization_id_fk_ix;

DROP INDEX commerce.address_party_id_fk_ix;

DROP INDEX commerce.address_party_id_uq;

DROP INDEX commerce.address_organization_id_ix;

DROP INDEX commerce.address_party_id_ix;

DROP INDEX commerce.staff_role_assignment_granted_by_fk_ix;

DROP INDEX commerce.staff_role_assignment_party_id_fk_ix;

DROP INDEX commerce.staff_role_assignment_party_id_role_uq;

DROP INDEX commerce.customer_profile_party_id_fk_ix;

ALTER TABLE commerce.customer_profile DROP CONSTRAINT customer_profile_party_id_uq;

DROP INDEX commerce.party_lower_email_ix;

ALTER TABLE commerce.party DROP CONSTRAINT party_identity_issuer_identity_subject_uq;
