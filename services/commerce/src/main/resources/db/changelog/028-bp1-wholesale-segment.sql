-- ADR-030: distinct wholesale books; no prices or entitlements are provisioned.
ALTER TABLE commerce.price_book DROP CONSTRAINT price_book_rule_1_ck;
ALTER TABLE commerce.price_book ADD CONSTRAINT price_book_rule_1_ck CHECK (segment IN ('RETAIL','WHOLESALE','DISTRIBUTOR'));
