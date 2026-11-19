DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.price_book WHERE segment='WHOLESALE') THEN RAISE EXCEPTION 'Wholesale books exist: retain segment support'; END IF; END $$;
ALTER TABLE commerce.price_book DROP CONSTRAINT price_book_rule_1_ck;
ALTER TABLE commerce.price_book ADD CONSTRAINT price_book_rule_1_ck CHECK (segment IN ('RETAIL','DISTRIBUTOR'));
