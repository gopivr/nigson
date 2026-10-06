-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE integration.outbox_event (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    event_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    schema_version integer NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    lease_until timestamptz,
    CONSTRAINT outbox_event_id_pk PRIMARY KEY (id),
    CONSTRAINT outbox_event_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT outbox_event_version_ck CHECK (version >= 0),
    CONSTRAINT outbox_event_event_id_nonnil_ck CHECK (event_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT outbox_event_aggregate_id_nonnil_ck CHECK (aggregate_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT outbox_event_rule_1_ck CHECK (schema_version>0),
    CONSTRAINT outbox_event_rule_2_ck CHECK (aggregate_version>=0),
    CONSTRAINT outbox_event_rule_3_ck CHECK (state IN ('PENDING','CLAIMED','PUBLISHED','FAILED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.outbox_event TO nigson_integration_app;

CREATE TABLE integration.processed_event (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    consumer varchar(80) NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(100) NOT NULL,
    outcome varchar(20) NOT NULL,
    processed_at timestamptz NOT NULL,
    CONSTRAINT processed_event_id_pk PRIMARY KEY (id),
    CONSTRAINT processed_event_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT processed_event_event_id_nonnil_ck CHECK (event_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT processed_event_rule_1_ck CHECK (outcome IN ('APPLIED','IGNORED'))
);

GRANT SELECT, INSERT ON integration.processed_event TO nigson_integration_app;

CREATE TABLE integration.idempotency_record (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    principal_key varchar(200) NOT NULL,
    operation varchar(120) NOT NULL,
    request_key varchar(100) NOT NULL,
    request_hash char(64) NOT NULL,
    state varchar(20) NOT NULL,
    response_status integer,
    response_body jsonb,
    resource_id uuid,
    lease_until timestamptz,
    expires_at timestamptz NOT NULL,
    CONSTRAINT idempotency_record_id_pk PRIMARY KEY (id),
    CONSTRAINT idempotency_record_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT idempotency_record_version_ck CHECK (version >= 0),
    CONSTRAINT idempotency_record_resource_id_nonnil_ck CHECK (resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT idempotency_record_rule_1_ck CHECK (state IN ('RUNNING','SUCCEEDED','FAILED_RETRYABLE')),
    CONSTRAINT idempotency_record_rule_2_ck CHECK (response_status IS NULL OR response_status BETWEEN 100 AND 599)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.idempotency_record TO nigson_integration_app;

CREATE TABLE integration.audit_record (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    actor_subject varchar(200) NOT NULL,
    actor_kind varchar(20) NOT NULL,
    action varchar(100) NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id uuid NOT NULL,
    occurred_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    ip_hash char(64),
    reason varchar(500),
    old_value jsonb,
    new_value jsonb,
    CONSTRAINT audit_record_id_pk PRIMARY KEY (id),
    CONSTRAINT audit_record_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT audit_record_resource_id_nonnil_ck CHECK (resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT audit_record_correlation_id_nonnil_ck CHECK (correlation_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT audit_record_rule_1_ck CHECK (actor_kind IN ('USER','SERVICE','SYSTEM'))
);

GRANT SELECT, INSERT ON integration.audit_record TO nigson_integration_app;

GRANT SELECT ON integration.audit_record TO nigson_integration_audit;

CREATE TABLE integration.feature_configuration (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    key varchar(120) NOT NULL,
    revision bigint NOT NULL,
    value jsonb NOT NULL,
    active boolean NOT NULL DEFAULT false,
    approved_by varchar(200),
    CONSTRAINT feature_configuration_id_pk PRIMARY KEY (id),
    CONSTRAINT feature_configuration_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT feature_configuration_version_ck CHECK (version >= 0),
    CONSTRAINT feature_configuration_rule_1_ck CHECK (revision>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.feature_configuration TO nigson_integration_app;
