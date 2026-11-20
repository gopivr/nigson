DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.affiliate_link) THEN RAISE EXCEPTION 'Populated table commerce.affiliate_link: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.affiliate_link DROP CONSTRAINT affiliate_link_product_id_fk;

ALTER TABLE commerce.affiliate_link DROP CONSTRAINT affiliate_link_party_id_fk;

DROP INDEX commerce.affiliate_link_code_uq;

DROP INDEX commerce.affiliate_link_party_id_product_id_uq;

DROP TABLE commerce.affiliate_link;
