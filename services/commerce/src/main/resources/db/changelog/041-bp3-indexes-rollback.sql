DROP INDEX commerce.quote_line_sku_id_fk_ix;

DROP INDEX commerce.quote_line_revision_id_fk_ix;

DROP INDEX commerce.quote_line_revision_id_ix;

DROP INDEX commerce.quote_revision_approved_by_fk_ix;

DROP INDEX commerce.quote_revision_currency_code_fk_ix;

DROP INDEX commerce.quote_revision_quote_id_fk_ix;

ALTER TABLE commerce.quote_revision DROP CONSTRAINT quote_revision_quote_id_revision_uq;

DROP INDEX commerce.quote_accepted_order_id_fk_ix;

DROP INDEX commerce.quote_current_revision_id_fk_ix;

DROP INDEX commerce.quote_market_id_fk_ix;

DROP INDEX commerce.quote_organization_id_fk_ix;

DROP INDEX commerce.quote_party_id_fk_ix;

ALTER TABLE commerce.quote DROP CONSTRAINT quote_source_lead_id_uq;

DROP INDEX commerce.quote_party_id_created_at_ix;

DROP INDEX commerce.wholesale_rule_market_id_fk_ix;

DROP INDEX commerce.wholesale_rule_sku_id_fk_ix;

DROP INDEX commerce.wholesale_rule_organization_id_fk_ix;

DROP INDEX commerce.wholesale_rule_organization_id_market_id_sku_id_ix;

DROP INDEX commerce.price_assignment_price_book_id_fk_ix;

DROP INDEX commerce.price_assignment_market_id_fk_ix;

DROP INDEX commerce.price_assignment_organization_id_fk_ix;

DROP INDEX commerce.price_assignment_organization_id_market_id_uq;

DROP INDEX commerce.account_entitlement_organization_id_fk_ix;

ALTER TABLE commerce.account_entitlement DROP CONSTRAINT account_entitlement_approval_command_id_uq;

ALTER TABLE commerce.account_entitlement DROP CONSTRAINT account_entitlement_organization_id_entitlement_uq;

DROP INDEX commerce.organization_membership_party_id_fk_ix;

DROP INDEX commerce.organization_membership_organization_id_fk_ix;

ALTER TABLE commerce.organization_membership DROP CONSTRAINT organization_membership_organization_id_party_id_uq;

DROP INDEX commerce.organization_country_code_business_reference_uq;
