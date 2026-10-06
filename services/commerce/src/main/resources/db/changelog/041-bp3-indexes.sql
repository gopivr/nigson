-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE UNIQUE INDEX organization_country_code_business_reference_uq ON commerce.organization (country_code,business_reference) WHERE business_reference IS NOT NULL;

ALTER TABLE commerce.organization_membership ADD CONSTRAINT organization_membership_organization_id_party_id_uq UNIQUE (organization_id,party_id);

CREATE INDEX organization_membership_organization_id_fk_ix ON commerce.organization_membership (organization_id);

CREATE INDEX organization_membership_party_id_fk_ix ON commerce.organization_membership (party_id);

ALTER TABLE commerce.account_entitlement ADD CONSTRAINT account_entitlement_organization_id_entitlement_uq UNIQUE (organization_id,entitlement);

ALTER TABLE commerce.account_entitlement ADD CONSTRAINT account_entitlement_approval_command_id_uq UNIQUE (approval_command_id);

CREATE INDEX account_entitlement_organization_id_fk_ix ON commerce.account_entitlement (organization_id);

CREATE UNIQUE INDEX price_assignment_organization_id_market_id_uq ON commerce.price_assignment (organization_id,market_id) WHERE active;

CREATE INDEX price_assignment_organization_id_fk_ix ON commerce.price_assignment (organization_id);

CREATE INDEX price_assignment_market_id_fk_ix ON commerce.price_assignment (market_id);

CREATE INDEX price_assignment_price_book_id_fk_ix ON commerce.price_assignment (price_book_id);

CREATE INDEX wholesale_rule_organization_id_market_id_sku_id_ix ON commerce.wholesale_rule (organization_id,market_id,sku_id);

CREATE INDEX wholesale_rule_organization_id_fk_ix ON commerce.wholesale_rule (organization_id);

CREATE INDEX wholesale_rule_sku_id_fk_ix ON commerce.wholesale_rule (sku_id);

CREATE INDEX wholesale_rule_market_id_fk_ix ON commerce.wholesale_rule (market_id);

CREATE INDEX quote_party_id_created_at_ix ON commerce.quote (party_id,created_at);

ALTER TABLE commerce.quote ADD CONSTRAINT quote_source_lead_id_uq UNIQUE (source_lead_id);

CREATE INDEX quote_party_id_fk_ix ON commerce.quote (party_id);

CREATE INDEX quote_organization_id_fk_ix ON commerce.quote (organization_id);

CREATE INDEX quote_market_id_fk_ix ON commerce.quote (market_id);

CREATE INDEX quote_current_revision_id_fk_ix ON commerce.quote (current_revision_id);

CREATE INDEX quote_accepted_order_id_fk_ix ON commerce.quote (accepted_order_id);

ALTER TABLE commerce.quote_revision ADD CONSTRAINT quote_revision_quote_id_revision_uq UNIQUE (quote_id,revision);

CREATE INDEX quote_revision_quote_id_fk_ix ON commerce.quote_revision (quote_id);

CREATE INDEX quote_revision_currency_code_fk_ix ON commerce.quote_revision (currency_code);

CREATE INDEX quote_revision_approved_by_fk_ix ON commerce.quote_revision (approved_by);

CREATE INDEX quote_line_revision_id_ix ON commerce.quote_line (revision_id);

CREATE INDEX quote_line_revision_id_fk_ix ON commerce.quote_line (revision_id);

CREATE INDEX quote_line_sku_id_fk_ix ON commerce.quote_line (sku_id);
