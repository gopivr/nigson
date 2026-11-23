DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.order_cancellation) THEN RAISE EXCEPTION 'Populated table commerce.order_cancellation: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.order_cancellation DROP CONSTRAINT order_cancellation_refund_id_fk;

ALTER TABLE commerce.order_cancellation DROP CONSTRAINT order_cancellation_requested_by_fk;

ALTER TABLE commerce.order_cancellation DROP CONSTRAINT order_cancellation_order_id_fk;

DROP INDEX commerce.order_cancellation_next_attempt_at_ix;

DROP INDEX commerce.order_cancellation_order_id_uq;

DROP TABLE commerce.order_cancellation;
