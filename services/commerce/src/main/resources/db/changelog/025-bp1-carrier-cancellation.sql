-- ADR-014: additive owned cancellation/dispatch exclusion; prior migrations preserved.
CREATE TABLE commerce.carrier_cancellation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    shipment_id uuid NOT NULL,
    booking_request_id uuid NOT NULL,
    reference varchar(40) NOT NULL,
    mode varchar(10) NOT NULL,
    reason varchar(500) NOT NULL,
    request_hash char(64) NOT NULL,
    requested_at timestamptz NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'REQUESTED',
    accepted_at timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    last_result_command_id uuid,
    error_code varchar(60),
    CONSTRAINT carrier_cancellation_id_pk PRIMARY KEY (id),
    CONSTRAINT carrier_cancellation_version_ck CHECK (version >= 0),
    CONSTRAINT carrier_cancellation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_booking_request_id_nonnil_ck CHECK (booking_request_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_last_result_command_id_nonnil_ck CHECK (last_result_command_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_rule_1_ck CHECK (state IN ('REQUESTED','QUEUED','UNKNOWN','CANCELLED','REJECTED')),
    CONSTRAINT carrier_cancellation_rule_2_ck CHECK (mode IN ('MOCK','SANDBOX','LIVE')),
    CONSTRAINT carrier_cancellation_rule_3_ck CHECK (attempt_count>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.carrier_cancellation TO nigson_commerce_app;

CREATE UNIQUE INDEX carrier_cancellation_shipment_id_uq ON commerce.carrier_cancellation (shipment_id) WHERE state IN ('REQUESTED','QUEUED','UNKNOWN');

CREATE INDEX carrier_cancellation_state_next_attempt_at_ix ON commerce.carrier_cancellation (state,next_attempt_at);

CREATE INDEX carrier_cancellation_shipment_id_created_at_ix ON commerce.carrier_cancellation (shipment_id,created_at);

ALTER TABLE commerce.carrier_cancellation ADD CONSTRAINT carrier_cancellation_shipment_id_fk FOREIGN KEY (shipment_id) REFERENCES commerce.shipment (id) ON DELETE RESTRICT;
