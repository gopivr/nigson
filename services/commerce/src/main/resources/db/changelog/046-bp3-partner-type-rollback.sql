DO $$ BEGIN IF EXISTS(SELECT 1 FROM commerce.account_entitlement WHERE partner_type='WHOLESALE') THEN RAISE EXCEPTION 'Wholesale records exist: preserve partner identity'; END IF; END $$;
ALTER TABLE commerce.account_entitlement DROP COLUMN partner_type;
