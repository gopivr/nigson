-- ADR-033: durable order cancellation and refund linkage.
CREATE TABLE commerce.order_cancellation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    requested_by uuid NOT NULL,
    reason varchar(500) NOT NULL,
    state varchar(24) NOT NULL DEFAULT 'REQUESTED',
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    refund_id uuid,
    error_code varchar(60),
    CONSTRAINT order_cancellation_id_pk PRIMARY KEY (id),
    CONSTRAINT order_cancellation_version_ck CHECK (version >= 0),
    CONSTRAINT order_cancellation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_cancellation_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_cancellation_requested_by_nonnil_ck CHECK (requested_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_cancellation_refund_id_nonnil_ck CHECK (refund_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_cancellation_rule_1_ck CHECK (state IN ('REQUESTED','WAITING_PAYMENT','WAITING_CARRIER','CANCELLED','REJECTED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.order_cancellation TO nigson_commerce_app;

CREATE UNIQUE INDEX order_cancellation_order_id_uq ON commerce.order_cancellation (order_id);

CREATE INDEX order_cancellation_next_attempt_at_ix ON commerce.order_cancellation (next_attempt_at);

ALTER TABLE commerce.order_cancellation ADD CONSTRAINT order_cancellation_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_cancellation ADD CONSTRAINT order_cancellation_requested_by_fk FOREIGN KEY (requested_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.order_cancellation ADD CONSTRAINT order_cancellation_refund_id_fk FOREIGN KEY (refund_id) REFERENCES commerce.refund (id) ON DELETE RESTRICT;
