-- ADR-018: additive owned immutable notification dispatch identity; prior migrations preserved.
CREATE TABLE integration.notification_dispatch (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    template_id uuid NOT NULL,
    locale varchar(16) NOT NULL,
    template_snapshot jsonb NOT NULL,
    template_hash char(64) NOT NULL,
    account_sid varchar(34) NOT NULL,
    messaging_service_sid varchar(34) NOT NULL,
    account_hash char(64) NOT NULL,
    generation uuid NOT NULL,
    source_occurred_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    stage varchar(20) NOT NULL DEFAULT 'INTENT',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    submitted_at timestamptz,
    provider_message_id varchar(160),
    request_hash char(64),
    error_code varchar(60),
    CONSTRAINT notification_dispatch_id_pk PRIMARY KEY (id),
    CONSTRAINT notification_dispatch_version_ck CHECK (version >= 0),
    CONSTRAINT notification_dispatch_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_dispatch_template_id_nonnil_ck CHECK (template_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_dispatch_generation_nonnil_ck CHECK (generation <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_dispatch_correlation_id_nonnil_ck CHECK (correlation_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT notification_dispatch_rule_1_ck CHECK (stage IN ('INTENT','SUBMITTING','ACCEPTED','UNKNOWN','SUPPRESSED','FAILED')),
    CONSTRAINT notification_dispatch_rule_2_ck CHECK (attempt_count>=0),
    CONSTRAINT notification_dispatch_rule_3_ck CHECK (jsonb_typeof(template_snapshot)='object'),
    CONSTRAINT notification_dispatch_rule_4_ck CHECK (account_sid ~ '^AC[a-fA-F0-9]{32}$'),
    CONSTRAINT notification_dispatch_rule_5_ck CHECK (messaging_service_sid ~ '^MG[a-fA-F0-9]{32}$'),
    CONSTRAINT notification_dispatch_rule_6_ck CHECK (account_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT notification_dispatch_rule_7_ck CHECK (template_hash ~ '^[a-f0-9]{64}$')
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.notification_dispatch TO nigson_integration_app;

CREATE UNIQUE INDEX notification_dispatch_account_sid_provider_message_id_uq ON integration.notification_dispatch (account_sid,provider_message_id);

CREATE INDEX notification_dispatch_stage_next_attempt_at_ix ON integration.notification_dispatch (stage,next_attempt_at);

ALTER TABLE integration.notification_dispatch ADD CONSTRAINT notification_dispatch_id_fk FOREIGN KEY (id) REFERENCES integration.notification_job (id) ON DELETE RESTRICT;

ALTER TABLE integration.notification_dispatch ADD CONSTRAINT notification_dispatch_template_id_fk FOREIGN KEY (template_id) REFERENCES integration.notification_template (id) ON DELETE RESTRICT;
