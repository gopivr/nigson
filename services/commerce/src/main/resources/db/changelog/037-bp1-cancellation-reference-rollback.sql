DO $$ BEGIN IF EXISTS(SELECT 1 FROM commerce.carrier_cancellation) OR EXISTS(SELECT 1 FROM commerce.order_cancellation) THEN RAISE EXCEPTION 'Populated cancellation evidence requires roll forward'; END IF; END $$;
ALTER TABLE commerce.carrier_cancellation ALTER COLUMN reference TYPE varchar(40);
