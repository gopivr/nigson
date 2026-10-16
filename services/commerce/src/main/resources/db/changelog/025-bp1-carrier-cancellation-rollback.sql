DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.carrier_cancellation) THEN RAISE EXCEPTION 'Populated table commerce.carrier_cancellation: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.carrier_cancellation DROP CONSTRAINT carrier_cancellation_shipment_id_fk;

DROP INDEX commerce.carrier_cancellation_shipment_id_created_at_ix;

DROP INDEX commerce.carrier_cancellation_state_next_attempt_at_ix;

DROP INDEX commerce.carrier_cancellation_shipment_id_uq;

DROP TABLE commerce.carrier_cancellation;
