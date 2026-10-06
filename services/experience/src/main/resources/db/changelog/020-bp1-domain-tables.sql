-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE experience.content_item (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    content_type varchar(30) NOT NULL,
    slug varchar(220) NOT NULL,
    locale varchar(16) NOT NULL,
    current_published_revision_id uuid,
    status varchar(20) NOT NULL,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT content_item_id_pk PRIMARY KEY (id),
    CONSTRAINT content_item_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT content_item_version_ck CHECK (version >= 0),
    CONSTRAINT content_item_current_published_revision_id_nonnil_ck CHECK (current_published_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT content_item_rule_1_ck CHECK (content_type IN ('HOME','ABOUT','LEADERSHIP','DIVISION','BLOG','VIDEO','FAQ','CAREERS','CONTACT')),
    CONSTRAINT content_item_rule_2_ck CHECK (status IN ('ACTIVE','ARCHIVED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.content_item TO nigson_experience_app;

CREATE TABLE experience.content_revision (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    content_item_id uuid NOT NULL,
    revision integer NOT NULL,
    title varchar(220) NOT NULL,
    body jsonb NOT NULL,
    metadata jsonb NOT NULL,
    state varchar(20) NOT NULL,
    scheduled_at timestamptz,
    published_at timestamptz,
    review_reason varchar(500),
    approved_by uuid,
    checksum_sha256 char(64) NOT NULL,
    CONSTRAINT content_revision_id_pk PRIMARY KEY (id),
    CONSTRAINT content_revision_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT content_revision_version_ck CHECK (version >= 0),
    CONSTRAINT content_revision_content_item_id_nonnil_ck CHECK (content_item_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT content_revision_approved_by_nonnil_ck CHECK (approved_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT content_revision_rule_1_ck CHECK (revision>0),
    CONSTRAINT content_revision_rule_2_ck CHECK (state IN ('DRAFT','REVIEW','APPROVED','PUBLISHED','WITHDRAWN','ARCHIVED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.content_revision TO nigson_experience_app;

CREATE TABLE experience.file_asset (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    owner_party_id uuid,
    organization_id uuid,
    object_key varchar(500) NOT NULL,
    purpose varchar(30) NOT NULL,
    mime_type varchar(100) NOT NULL,
    byte_size bigint NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    classification varchar(20) NOT NULL,
    scan_status varchar(20) NOT NULL,
    scan_result varchar(300),
    linked_resource_type varchar(40),
    linked_resource_id uuid,
    upload_session_id uuid,
    CONSTRAINT file_asset_id_pk PRIMARY KEY (id),
    CONSTRAINT file_asset_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_asset_version_ck CHECK (version >= 0),
    CONSTRAINT file_asset_owner_party_id_nonnil_ck CHECK (owner_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_asset_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_asset_linked_resource_id_nonnil_ck CHECK (linked_resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_asset_upload_session_id_nonnil_ck CHECK (upload_session_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_asset_rule_1_ck CHECK (byte_size>0),
    CONSTRAINT file_asset_rule_2_ck CHECK (classification IN ('PUBLIC','PRIVATE')),
    CONSTRAINT file_asset_rule_3_ck CHECK (scan_status IN ('UPLOADING','QUARANTINED','CLEAN','REJECTED')),
    CONSTRAINT file_asset_rule_4_ck CHECK (owner_party_id IS NOT NULL OR upload_session_id IS NOT NULL)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.file_asset TO nigson_experience_app;

CREATE TABLE experience.lead (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    type varchar(20) NOT NULL,
    party_id uuid,
    organization_id uuid,
    name varchar(160) NOT NULL,
    email varchar(254) NOT NULL,
    phone varchar(32),
    subject varchar(200) NOT NULL,
    message text NOT NULL,
    status varchar(20) NOT NULL,
    assigned_to uuid,
    priority varchar(10) NOT NULL DEFAULT 'NORMAL',
    follow_up_at timestamptz,
    source varchar(40) NOT NULL,
    related_quote_id uuid,
    CONSTRAINT lead_id_pk PRIMARY KEY (id),
    CONSTRAINT lead_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_version_ck CHECK (version >= 0),
    CONSTRAINT lead_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_assigned_to_nonnil_ck CHECK (assigned_to <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_related_quote_id_nonnil_ck CHECK (related_quote_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_rule_1_ck CHECK (type IN ('CONTACT','COMPLAINT','QUOTE','DISTRIBUTOR')),
    CONSTRAINT lead_rule_2_ck CHECK (status IN ('NEW','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT lead_rule_3_ck CHECK (priority IN ('NORMAL','HIGH'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.lead TO nigson_experience_app;

CREATE TABLE experience.lead_activity (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    lead_id uuid NOT NULL,
    activity_type varchar(30) NOT NULL,
    note text NOT NULL,
    from_status varchar(20),
    to_status varchar(20),
    actor_party_id uuid,
    CONSTRAINT lead_activity_id_pk PRIMARY KEY (id),
    CONSTRAINT lead_activity_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_activity_lead_id_nonnil_ck CHECK (lead_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_activity_actor_party_id_nonnil_ck CHECK (actor_party_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT ON experience.lead_activity TO nigson_experience_app;

CREATE TABLE experience.consent_event (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    party_id uuid,
    email_hash char(64),
    channel varchar(20) NOT NULL,
    purpose varchar(30) NOT NULL,
    granted boolean NOT NULL,
    policy_version varchar(40) NOT NULL,
    source varchar(80) NOT NULL,
    occurred_at timestamptz NOT NULL,
    destination_hash char(64) NOT NULL,
    destination_kind varchar(10) NOT NULL,
    hash_key_version integer NOT NULL,
    CONSTRAINT consent_event_id_pk PRIMARY KEY (id),
    CONSTRAINT consent_event_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT consent_event_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT consent_event_rule_1_ck CHECK (channel IN ('EMAIL','SMS','WHATSAPP')),
    CONSTRAINT consent_event_rule_2_ck CHECK (purpose IN ('NEWSLETTER','MARKETING','CART_RECOVERY','ORDER_UPDATES')),
    CONSTRAINT consent_event_rule_3_ck CHECK (destination_kind IN ('EMAIL','PHONE')),
    CONSTRAINT consent_event_rule_4_ck CHECK (hash_key_version>0)
);

GRANT SELECT, INSERT ON experience.consent_event TO nigson_experience_app;

CREATE TABLE experience.communication_suppression (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    email_hash char(64),
    channel varchar(20) NOT NULL,
    reason varchar(40) NOT NULL,
    suppressed_at timestamptz NOT NULL,
    destination_hash char(64) NOT NULL,
    destination_kind varchar(10) NOT NULL,
    hash_key_version integer NOT NULL,
    CONSTRAINT communication_suppression_id_pk PRIMARY KEY (id),
    CONSTRAINT communication_suppression_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT communication_suppression_version_ck CHECK (version >= 0),
    CONSTRAINT communication_suppression_rule_1_ck CHECK (destination_kind IN ('EMAIL','PHONE')),
    CONSTRAINT communication_suppression_rule_2_ck CHECK (hash_key_version>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.communication_suppression TO nigson_experience_app;

CREATE TABLE experience.channel_configuration (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    kind varchar(30) NOT NULL,
    key varchar(80) NOT NULL,
    public_value jsonb NOT NULL,
    active boolean NOT NULL DEFAULT false,
    CONSTRAINT channel_configuration_id_pk PRIMARY KEY (id),
    CONSTRAINT channel_configuration_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT channel_configuration_version_ck CHECK (version >= 0),
    CONSTRAINT channel_configuration_rule_1_ck CHECK (kind IN ('WHATSAPP','SOCIAL','MAP','LEAD_RECIPIENT_GROUP','SEO'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.channel_configuration TO nigson_experience_app;

CREATE TABLE experience.communication_destination (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    channel varchar(20) NOT NULL,
    address_ciphertext bytea NOT NULL,
    destination_hash char(64) NOT NULL,
    hash_key_version integer NOT NULL,
    status varchar(20) NOT NULL,
    verified_at timestamptz,
    policy_version varchar(40) NOT NULL,
    consented_purposes jsonb NOT NULL,
    CONSTRAINT communication_destination_id_pk PRIMARY KEY (id),
    CONSTRAINT communication_destination_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT communication_destination_version_ck CHECK (version >= 0),
    CONSTRAINT communication_destination_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT communication_destination_rule_1_ck CHECK (channel IN ('SMS','WHATSAPP')),
    CONSTRAINT communication_destination_rule_2_ck CHECK (status IN ('PENDING','ACTIVE','UNSUBSCRIBED','SUPPRESSED')),
    CONSTRAINT communication_destination_rule_3_ck CHECK (hash_key_version>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.communication_destination TO nigson_experience_app;

CREATE TABLE experience.destination_verification (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    destination_id uuid NOT NULL,
    code_hmac char(64) NOT NULL,
    code_ciphertext bytea,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL,
    sent_job_key varchar(180) NOT NULL,
    CONSTRAINT destination_verification_id_pk PRIMARY KEY (id),
    CONSTRAINT destination_verification_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT destination_verification_version_ck CHECK (version >= 0),
    CONSTRAINT destination_verification_destination_id_nonnil_ck CHECK (destination_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT destination_verification_rule_1_ck CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT destination_verification_rule_2_ck CHECK (status IN ('PENDING','CONFIRMED','EXPIRED','LOCKED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.destination_verification TO nigson_experience_app;

CREATE TABLE experience.inbound_message (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    provider varchar(30) NOT NULL,
    provider_message_id varchar(160) NOT NULL,
    channel varchar(20) NOT NULL,
    sender_hash char(64) NOT NULL,
    sender_ciphertext bytea NOT NULL,
    body_ciphertext bytea,
    lead_id uuid,
    received_at timestamptz NOT NULL,
    retention_until timestamptz NOT NULL,
    disposition varchar(20) NOT NULL,
    CONSTRAINT inbound_message_id_pk PRIMARY KEY (id),
    CONSTRAINT inbound_message_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inbound_message_version_ck CHECK (version >= 0),
    CONSTRAINT inbound_message_lead_id_nonnil_ck CHECK (lead_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inbound_message_rule_1_ck CHECK (channel='WHATSAPP'),
    CONSTRAINT inbound_message_rule_2_ck CHECK (disposition IN ('RECEIVED','LEAD_CREATED','OPT_OUT','HELP','IGNORED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.inbound_message TO nigson_experience_app;
