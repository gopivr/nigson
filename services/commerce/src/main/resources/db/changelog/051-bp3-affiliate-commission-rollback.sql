DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.affiliate_commission_entry) THEN RAISE EXCEPTION 'Populated table commerce.affiliate_commission_entry: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.affiliate_commission_entry DROP CONSTRAINT affiliate_commission_entry_refund_id_fk;

ALTER TABLE commerce.affiliate_commission_entry DROP CONSTRAINT affiliate_commission_entry_reverses_id_fk;

ALTER TABLE commerce.affiliate_commission_entry DROP CONSTRAINT affiliate_commission_entry_order_id_fk;

ALTER TABLE commerce.affiliate_commission_entry DROP CONSTRAINT affiliate_commission_entry_party_id_fk;

DROP INDEX commerce.affiliate_commission_entry_order_id_ix;

DROP INDEX commerce.affiliate_commission_entry_party_id_created_at_ix;

DROP INDEX commerce.affiliate_commission_entry_business_key_uq;

DROP TABLE commerce.affiliate_commission_entry;
