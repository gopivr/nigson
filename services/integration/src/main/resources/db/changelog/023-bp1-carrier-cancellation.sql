-- ADR-014: additive owned cancellation checkpoint; prior migrations preserved.
CREATE TABLE integration.carrier_cancellation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    source_request_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    shipment_id uuid NOT NULL,
    reference varchar(40) NOT NULL,
    mode varchar(10) NOT NULL,
    account_hash char(64) NOT NULL,
    request_hash char(64) NOT NULL,
    generation uuid NOT NULL,
    requested_at timestamptz NOT NULL,
    piece_numbers jsonb NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'QUEUED',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    submitted_at timestamptz,
    outcome_state varchar(20),
    occurred_at timestamptz,
    callback_id uuid,
    callback_body jsonb,
    notified_state varchar(20),
    error_code varchar(60),
    CONSTRAINT carrier_cancellation_id_pk PRIMARY KEY (id),
    CONSTRAINT carrier_cancellation_version_ck CHECK (version >= 0),
    CONSTRAINT carrier_cancellation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_source_request_id_nonnil_ck CHECK (source_request_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_booking_id_nonnil_ck CHECK (booking_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_generation_nonnil_ck CHECK (generation <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_callback_id_nonnil_ck CHECK (callback_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_cancellation_rule_1_ck CHECK (state IN ('QUEUED','SUBMITTING','UNKNOWN','ACK_PENDING','CANCELLED','REJECTED')),
    CONSTRAINT carrier_cancellation_rule_2_ck CHECK (outcome_state IN ('UNKNOWN','CANCELLED','REJECTED')),
    CONSTRAINT carrier_cancellation_rule_3_ck CHECK (notified_state IN ('UNKNOWN','CANCELLED','REJECTED')),
    CONSTRAINT carrier_cancellation_rule_4_ck CHECK (mode IN ('MOCK','SANDBOX','LIVE')),
    CONSTRAINT carrier_cancellation_rule_5_ck CHECK (attempt_count>=0),
    CONSTRAINT carrier_cancellation_rule_6_ck CHECK (jsonb_typeof(piece_numbers)='array')
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.carrier_cancellation TO nigson_integration_app;

CREATE UNIQUE INDEX carrier_cancellation_source_request_id_uq ON integration.carrier_cancellation (source_request_id);

CREATE UNIQUE INDEX carrier_cancellation_booking_id_uq ON integration.carrier_cancellation (booking_id) WHERE state IN ('QUEUED','SUBMITTING','UNKNOWN','ACK_PENDING');

CREATE INDEX carrier_cancellation_state_next_attempt_at_ix ON integration.carrier_cancellation (state,next_attempt_at);

CREATE INDEX carrier_cancellation_booking_id_ix ON integration.carrier_cancellation (booking_id);

ALTER TABLE integration.carrier_cancellation ADD CONSTRAINT carrier_cancellation_booking_id_fk FOREIGN KEY (booking_id) REFERENCES integration.carrier_booking (id) ON DELETE RESTRICT;
