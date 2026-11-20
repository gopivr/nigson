DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.affiliate_bank_detail) THEN RAISE EXCEPTION 'Populated table commerce.affiliate_bank_detail: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.affiliate_bank_detail DROP CONSTRAINT affiliate_bank_detail_reviewed_by_fk;

ALTER TABLE commerce.affiliate_bank_detail DROP CONSTRAINT affiliate_bank_detail_previous_id_fk;

ALTER TABLE commerce.affiliate_bank_detail DROP CONSTRAINT affiliate_bank_detail_party_id_fk;

DROP INDEX commerce.affiliate_bank_detail_party_id_revision_uq;

DROP TABLE commerce.affiliate_bank_detail;
