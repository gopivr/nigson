DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.affiliate_payout) THEN RAISE EXCEPTION 'Populated table commerce.affiliate_payout: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.affiliate_payout DROP CONSTRAINT affiliate_payout_resolved_by_fk;

ALTER TABLE commerce.affiliate_payout DROP CONSTRAINT affiliate_payout_bank_detail_id_fk;

ALTER TABLE commerce.affiliate_payout DROP CONSTRAINT affiliate_payout_party_id_fk;

DROP INDEX commerce.affiliate_payout_party_id_created_at_ix;

DROP INDEX commerce.affiliate_payout_transfer_reference_uq;

DROP INDEX commerce.affiliate_payout_party_id_uq;

DROP INDEX commerce.affiliate_payout_party_id_period_uq;

DROP TABLE commerce.affiliate_payout;
