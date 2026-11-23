-- ADR-034: customer delivered-product returns and reviewed receipt/refund linkage.
CREATE TABLE commerce.return_request (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    quantity numeric(18,3) NOT NULL,
    reason varchar(500) NOT NULL,
    policy_revision varchar(80) NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'REQUESTED',
    reviewed_by uuid,
    decision_reason varchar(500),
    instructions varchar(1000),
    received_by uuid,
    received_at timestamptz,
    receipt_reference varchar(120),
    restock boolean,
    inspection_reason varchar(500),
    refund_id uuid,
    CONSTRAINT return_request_id_pk PRIMARY KEY (id),
    CONSTRAINT return_request_version_ck CHECK (version >= 0),
    CONSTRAINT return_request_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_order_line_id_nonnil_ck CHECK (order_line_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_requested_by_nonnil_ck CHECK (requested_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_reviewed_by_nonnil_ck CHECK (reviewed_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_received_by_nonnil_ck CHECK (received_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_refund_id_nonnil_ck CHECK (refund_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT return_request_rule_1_ck CHECK (state IN ('REQUESTED','APPROVED','REJECTED','RECEIVED')),
    CONSTRAINT return_request_rule_2_ck CHECK (quantity>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.return_request TO nigson_commerce_app;

CREATE INDEX return_request_order_id_created_at_ix ON commerce.return_request (order_id,created_at);

CREATE INDEX return_request_order_line_id_state_ix ON commerce.return_request (order_line_id,state);

CREATE UNIQUE INDEX return_request_refund_id_uq ON commerce.return_request (refund_id);

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_order_line_id_fk FOREIGN KEY (order_line_id) REFERENCES commerce.order_line (id) ON DELETE RESTRICT;

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_requested_by_fk FOREIGN KEY (requested_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_reviewed_by_fk FOREIGN KEY (reviewed_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_received_by_fk FOREIGN KEY (received_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.return_request ADD CONSTRAINT return_request_refund_id_fk FOREIGN KEY (refund_id) REFERENCES commerce.refund (id) ON DELETE RESTRICT;
