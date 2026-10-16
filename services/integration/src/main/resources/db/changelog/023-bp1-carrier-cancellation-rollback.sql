DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.carrier_cancellation) THEN RAISE EXCEPTION 'Populated table integration.carrier_cancellation: use expand/contract migration'; END IF; END $$;

ALTER TABLE integration.carrier_cancellation DROP CONSTRAINT carrier_cancellation_booking_id_fk;

DROP INDEX integration.carrier_cancellation_booking_id_ix;

DROP INDEX integration.carrier_cancellation_state_next_attempt_at_ix;

DROP INDEX integration.carrier_cancellation_booking_id_uq;

DROP INDEX integration.carrier_cancellation_source_request_id_uq;

DROP TABLE integration.carrier_cancellation;
