-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE integration.notification_template (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(80) NOT NULL,
    revision integer NOT NULL,
    channel varchar(20) NOT NULL,
    locale varchar(16) NOT NULL,
    subject varchar(200),
    body text NOT NULL,
    classification varchar(20) NOT NULL,
    active boolean NOT NULL DEFAULT false,
    provider_template_id varchar(160),
    variables_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT notification_template_id_pk PRIMARY KEY (id),
    CONSTRAINT notification_template_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_template_version_ck CHECK (version >= 0),
    CONSTRAINT notification_template_rule_1_ck CHECK (revision>0),
    CONSTRAINT notification_template_rule_2_ck CHECK (channel IN ('EMAIL','SMS','WHATSAPP')),
    CONSTRAINT notification_template_rule_3_ck CHECK (classification IN ('TRANSACTIONAL','MARKETING'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.notification_template TO nigson_integration_app;

CREATE TABLE integration.notification_job (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    business_key varchar(180) NOT NULL,
    source_event_id uuid,
    owner_service varchar(30) NOT NULL,
    resource_id uuid NOT NULL,
    recipient_party_id uuid,
    recipient_group varchar(80),
    template_code varchar(80) NOT NULL,
    template_revision integer NOT NULL,
    classification varchar(20) NOT NULL,
    state varchar(20) NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    lease_until timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    provider_reference varchar(160),
    channel varchar(20) NOT NULL DEFAULT 'EMAIL',
    destination_id uuid,
    destination_hash char(64),
    provider_mode varchar(10) NOT NULL DEFAULT 'MOCK',
    CONSTRAINT notification_job_id_pk PRIMARY KEY (id),
    CONSTRAINT notification_job_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_job_version_ck CHECK (version >= 0),
    CONSTRAINT notification_job_source_event_id_nonnil_ck CHECK (source_event_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_job_resource_id_nonnil_ck CHECK (resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_job_recipient_party_id_nonnil_ck CHECK (recipient_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_job_destination_id_nonnil_ck CHECK (destination_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_job_rule_1_ck CHECK (state IN ('PENDING','CLAIMED','SENT','DELIVERED','SUPPRESSED','FAILED','UNKNOWN')),
    CONSTRAINT notification_job_rule_2_ck CHECK (attempt_count>=0),
    CONSTRAINT notification_job_rule_3_ck CHECK (channel IN ('EMAIL','SMS','WHATSAPP')),
    CONSTRAINT notification_job_rule_4_ck CHECK (provider_mode IN ('MOCK','SANDBOX','LIVE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.notification_job TO nigson_integration_app;

CREATE TABLE integration.notification_attempt (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    job_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    provider varchar(40) NOT NULL,
    provider_message_id varchar(160),
    result varchar(20) NOT NULL,
    error_code varchar(60),
    duration_ms integer NOT NULL,
    channel varchar(20) NOT NULL DEFAULT 'EMAIL',
    CONSTRAINT notification_attempt_id_pk PRIMARY KEY (id),
    CONSTRAINT notification_attempt_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_attempt_job_id_nonnil_ck CHECK (job_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_attempt_rule_1_ck CHECK (attempt_number>0),
    CONSTRAINT notification_attempt_rule_2_ck CHECK (duration_ms>=0)
);

GRANT SELECT, INSERT ON integration.notification_attempt TO nigson_integration_app;

CREATE TABLE integration.integration_job (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    kind varchar(30) NOT NULL,
    business_key varchar(180) NOT NULL,
    owner_service varchar(30) NOT NULL,
    resource_id uuid NOT NULL,
    state varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    lease_until timestamptz,
    cursor jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_code varchar(60),
    CONSTRAINT integration_job_id_pk PRIMARY KEY (id),
    CONSTRAINT integration_job_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT integration_job_version_ck CHECK (version >= 0),
    CONSTRAINT integration_job_resource_id_nonnil_ck CHECK (resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT integration_job_rule_1_ck CHECK (kind IN ('SHEETS_SYNC','LOGISTICS_CREATE','PAYMENT_RECONCILE','ASSET_SCAN','REPORT_EXPORT')),
    CONSTRAINT integration_job_rule_2_ck CHECK (state IN ('PENDING','CLAIMED','SUCCEEDED','FAILED','UNKNOWN'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.integration_job TO nigson_integration_app;

CREATE TABLE integration.provider_cursor (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    provider varchar(40) NOT NULL,
    job_kind varchar(40) NOT NULL,
    checkpoint jsonb NOT NULL,
    last_success_at timestamptz,
    CONSTRAINT provider_cursor_id_pk PRIMARY KEY (id),
    CONSTRAINT provider_cursor_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT provider_cursor_version_ck CHECK (version >= 0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.provider_cursor TO nigson_integration_app;

CREATE TABLE integration.carrier_booking (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    shipment_id uuid NOT NULL,
    business_key varchar(180) NOT NULL,
    provider varchar(30) NOT NULL,
    mode varchar(10) NOT NULL,
    state varchar(20) NOT NULL,
    request_hash char(64) NOT NULL,
    tracking_number varchar(160),
    provider_shipment_id varchar(160),
    label_object_key varchar(500),
    next_poll_at timestamptz,
    lease_until timestamptz,
    last_provider_status varchar(80),
    error_code varchar(80),
    CONSTRAINT carrier_booking_id_pk PRIMARY KEY (id),
    CONSTRAINT carrier_booking_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_booking_version_ck CHECK (version >= 0),
    CONSTRAINT carrier_booking_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_booking_rule_1_ck CHECK (state IN ('PENDING','CLAIMED','BOOKED','UNKNOWN','FAILED','CANCELLED')),
    CONSTRAINT carrier_booking_rule_2_ck CHECK (mode IN ('MOCK','SANDBOX','LIVE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.carrier_booking TO nigson_integration_app;

CREATE TABLE integration.message_receipt (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    job_id uuid NOT NULL,
    provider varchar(30) NOT NULL,
    provider_message_id varchar(160) NOT NULL,
    event_hash char(64) NOT NULL,
    provider_status varchar(40) NOT NULL,
    occurred_at timestamptz,
    received_at timestamptz NOT NULL,
    error_code varchar(80),
    CONSTRAINT message_receipt_id_pk PRIMARY KEY (id),
    CONSTRAINT message_receipt_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT message_receipt_job_id_nonnil_ck CHECK (job_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT ON integration.message_receipt TO nigson_integration_app;
