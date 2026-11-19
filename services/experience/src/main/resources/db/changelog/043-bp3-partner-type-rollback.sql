DO $$ BEGIN IF EXISTS(SELECT 1 FROM experience.distributor_application WHERE partner_type='WHOLESALE' OR business_details IS NOT NULL) THEN RAISE EXCEPTION 'Wholesale records exist: preserve partner identity'; END IF; END $$;
ALTER TABLE experience.distributor_application DROP COLUMN partner_type;
ALTER TABLE experience.distributor_application DROP COLUMN business_details;
