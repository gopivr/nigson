DO $$ BEGIN IF EXISTS(SELECT 1 FROM commerce.affiliate_bank_detail) OR EXISTS(SELECT 1 FROM commerce.affiliate_payout) THEN RAISE EXCEPTION 'Affiliate financial records exist: use expand/contract migration'; END IF; END $$;
DROP TRIGGER affiliate_payout_guard ON commerce.affiliate_payout;
DROP TRIGGER affiliate_bank_guard ON commerce.affiliate_bank_detail;
DROP FUNCTION commerce.guard_affiliate_payout();
DROP FUNCTION commerce.guard_affiliate_bank();
