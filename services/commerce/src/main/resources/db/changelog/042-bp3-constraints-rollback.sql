ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_source_quote_revision_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_organization_id_fk;

ALTER TABLE commerce.quote_line DROP CONSTRAINT quote_line_sku_id_fk;

ALTER TABLE commerce.quote_line DROP CONSTRAINT quote_line_revision_id_fk;

ALTER TABLE commerce.quote_revision DROP CONSTRAINT quote_revision_approved_by_fk;

ALTER TABLE commerce.quote_revision DROP CONSTRAINT quote_revision_currency_code_fk;

ALTER TABLE commerce.quote_revision DROP CONSTRAINT quote_revision_quote_id_fk;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_accepted_order_id_fk;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_current_revision_id_fk;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_market_id_fk;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_organization_id_fk;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_party_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_accepted_quote_revision_id_fk;

ALTER TABLE commerce.sales_order DROP CONSTRAINT sales_order_organization_id_fk;

ALTER TABLE commerce.cart DROP CONSTRAINT cart_organization_id_fk;

ALTER TABLE commerce.wholesale_rule DROP CONSTRAINT wholesale_rule_market_id_fk;

ALTER TABLE commerce.wholesale_rule DROP CONSTRAINT wholesale_rule_sku_id_fk;

ALTER TABLE commerce.wholesale_rule DROP CONSTRAINT wholesale_rule_organization_id_fk;

ALTER TABLE commerce.price_assignment DROP CONSTRAINT price_assignment_price_book_id_fk;

ALTER TABLE commerce.price_assignment DROP CONSTRAINT price_assignment_market_id_fk;

ALTER TABLE commerce.price_assignment DROP CONSTRAINT price_assignment_organization_id_fk;

ALTER TABLE commerce.account_entitlement DROP CONSTRAINT account_entitlement_organization_id_fk;

ALTER TABLE commerce.address DROP CONSTRAINT address_organization_id_fk;

ALTER TABLE commerce.organization_membership DROP CONSTRAINT organization_membership_party_id_fk;

ALTER TABLE commerce.organization_membership DROP CONSTRAINT organization_membership_organization_id_fk;
