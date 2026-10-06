-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE experience.distributor_application (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    organization_name varchar(200) NOT NULL,
    country_code char(2) NOT NULL,
    business_reference varchar(100),
    status varchar(30) NOT NULL,
    reviewer_party_id uuid,
    review_reason varchar(500),
    decision_command_id uuid,
    commerce_organization_id uuid,
    acknowledgment_revision bigint,
    submitted_at timestamptz NOT NULL,
    decision_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT distributor_application_id_pk PRIMARY KEY (id),
    CONSTRAINT distributor_application_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT distributor_application_version_ck CHECK (version >= 0),
    CONSTRAINT distributor_application_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT distributor_application_reviewer_party_id_nonnil_ck CHECK (reviewer_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT distributor_application_decision_command_id_nonnil_ck CHECK (decision_command_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT distributor_application_commerce_organization_id_nonnil_ck CHECK (commerce_organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT distributor_application_rule_1_ck CHECK (status IN ('SUBMITTED','UNDER_REVIEW','ACTIVATION_PENDING','APPROVED','REJECTED','SUSPENSION_PENDING','SUSPENDED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.distributor_application TO nigson_experience_app;

CREATE TABLE experience.application_document (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    application_id uuid NOT NULL,
    file_asset_id uuid NOT NULL,
    document_type varchar(40) NOT NULL,
    CONSTRAINT application_document_id_pk PRIMARY KEY (id),
    CONSTRAINT application_document_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT application_document_version_ck CHECK (version >= 0),
    CONSTRAINT application_document_application_id_nonnil_ck CHECK (application_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT application_document_file_asset_id_nonnil_ck CHECK (file_asset_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.application_document TO nigson_experience_app;

CREATE TABLE experience.lead_requested_item (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    lead_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    CONSTRAINT lead_requested_item_id_pk PRIMARY KEY (id),
    CONSTRAINT lead_requested_item_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_requested_item_version_ck CHECK (version >= 0),
    CONSTRAINT lead_requested_item_lead_id_nonnil_ck CHECK (lead_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_requested_item_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_requested_item_rule_1_ck CHECK (quantity>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.lead_requested_item TO nigson_experience_app;
