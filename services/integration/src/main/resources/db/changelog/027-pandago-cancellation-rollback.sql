DO $$ BEGIN IF EXISTS(SELECT 1 FROM integration.carrier_cancellation) THEN RAISE EXCEPTION 'Populated carrier cancellation: roll forward'; END IF; END $$;
ALTER TABLE integration.carrier_cancellation ALTER COLUMN reference TYPE varchar(40);
