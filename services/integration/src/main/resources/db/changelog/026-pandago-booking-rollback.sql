DO $$ BEGIN IF EXISTS(SELECT 1 FROM integration.carrier_booking WHERE prepared_at IS NOT NULL) THEN RAISE EXCEPTION 'Prepared PandaGo bookings require expand/contract rollback'; END IF; END $$;
ALTER TABLE integration.carrier_booking DROP CONSTRAINT carrier_booking_prepared_ck, DROP CONSTRAINT carrier_booking_account_hash_ck;
ALTER TABLE integration.carrier_booking DROP COLUMN result_body, DROP COLUMN account_hash, DROP COLUMN prepared_at, DROP COLUMN source_occurred_at, DROP COLUMN creation_generation, DROP COLUMN booking_reference;
