DO $$ BEGIN IF EXISTS(SELECT 1 FROM commerce.account_entitlement WHERE partner_type='AFFILIATE') THEN RAISE EXCEPTION 'Affiliate records exist: use expand/contract migration'; END IF; END $$;
ALTER TABLE commerce.account_entitlement DROP CONSTRAINT account_entitlement_partner_type_ck;
ALTER TABLE commerce.account_entitlement ADD CONSTRAINT account_entitlement_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE'));
