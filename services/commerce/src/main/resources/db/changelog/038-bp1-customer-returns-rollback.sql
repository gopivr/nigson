DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.return_request) THEN RAISE EXCEPTION 'Populated table commerce.return_request: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_refund_id_fk;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_received_by_fk;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_reviewed_by_fk;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_requested_by_fk;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_order_line_id_fk;

ALTER TABLE commerce.return_request DROP CONSTRAINT return_request_order_id_fk;

DROP INDEX commerce.return_request_refund_id_uq;

DROP INDEX commerce.return_request_order_line_id_state_ix;

DROP INDEX commerce.return_request_order_id_created_at_ix;

DROP TABLE commerce.return_request;
