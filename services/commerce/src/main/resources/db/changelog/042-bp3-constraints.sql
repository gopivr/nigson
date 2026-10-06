-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.organization_membership ADD CONSTRAINT organization_membership_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.organization_membership ADD CONSTRAINT organization_membership_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.address ADD CONSTRAINT address_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.account_entitlement ADD CONSTRAINT account_entitlement_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.price_assignment ADD CONSTRAINT price_assignment_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.price_assignment ADD CONSTRAINT price_assignment_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.price_assignment ADD CONSTRAINT price_assignment_price_book_id_fk FOREIGN KEY (price_book_id) REFERENCES commerce.price_book (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wholesale_rule ADD CONSTRAINT wholesale_rule_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wholesale_rule ADD CONSTRAINT wholesale_rule_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wholesale_rule ADD CONSTRAINT wholesale_rule_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.cart ADD CONSTRAINT cart_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_accepted_quote_revision_id_fk FOREIGN KEY (accepted_quote_revision_id) REFERENCES commerce.quote_revision (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote ADD CONSTRAINT quote_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote ADD CONSTRAINT quote_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote ADD CONSTRAINT quote_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote ADD CONSTRAINT quote_current_revision_id_fk FOREIGN KEY (current_revision_id) REFERENCES commerce.quote_revision (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote ADD CONSTRAINT quote_accepted_order_id_fk FOREIGN KEY (accepted_order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote_revision ADD CONSTRAINT quote_revision_quote_id_fk FOREIGN KEY (quote_id) REFERENCES commerce.quote (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote_revision ADD CONSTRAINT quote_revision_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.quote_revision ADD CONSTRAINT quote_revision_approved_by_fk FOREIGN KEY (approved_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote_line ADD CONSTRAINT quote_line_revision_id_fk FOREIGN KEY (revision_id) REFERENCES commerce.quote_revision (id) ON DELETE RESTRICT;

ALTER TABLE commerce.quote_line ADD CONSTRAINT quote_line_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_organization_id_fk FOREIGN KEY (organization_id) REFERENCES commerce.organization (id) ON DELETE RESTRICT;

ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_source_quote_revision_id_fk FOREIGN KEY (source_quote_revision_id) REFERENCES commerce.quote_revision (id) ON DELETE RESTRICT;
