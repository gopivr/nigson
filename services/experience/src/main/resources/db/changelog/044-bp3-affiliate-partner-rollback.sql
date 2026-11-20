DO $$ BEGIN IF EXISTS(SELECT 1 FROM experience.distributor_application WHERE partner_type='AFFILIATE') THEN RAISE EXCEPTION 'Affiliate records exist: use expand/contract migration'; END IF; END $$;
ALTER TABLE experience.distributor_application DROP CONSTRAINT distributor_application_partner_type_ck;
ALTER TABLE experience.distributor_application ADD CONSTRAINT distributor_application_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE'));
