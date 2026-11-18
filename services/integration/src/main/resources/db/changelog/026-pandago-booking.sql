ALTER TABLE integration.carrier_booking
 ADD COLUMN booking_reference uuid,
 ADD COLUMN creation_generation uuid,
 ADD COLUMN source_occurred_at timestamptz,
 ADD COLUMN prepared_at timestamptz,
 ADD COLUMN account_hash varchar(64),
 ADD COLUMN result_body jsonb;
ALTER TABLE integration.carrier_booking ADD CONSTRAINT carrier_booking_account_hash_ck CHECK (account_hash IS NULL OR account_hash ~ '^[a-f0-9]{64}$');
ALTER TABLE integration.carrier_booking ADD CONSTRAINT carrier_booking_prepared_ck CHECK (prepared_at IS NULL OR (booking_reference IS NOT NULL AND creation_generation IS NOT NULL AND account_hash IS NOT NULL));
