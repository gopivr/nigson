-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE ai.knowledge_document (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    source_service varchar(30) NOT NULL,
    source_resource_id uuid NOT NULL,
    source_type varchar(30) NOT NULL,
    locale varchar(16) NOT NULL,
    market_code varchar(32) NOT NULL,
    visibility varchar(20) NOT NULL,
    active_revision_id uuid,
    withdrawn_at timestamptz,
    CONSTRAINT knowledge_document_id_pk PRIMARY KEY (id),
    CONSTRAINT knowledge_document_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_document_version_ck CHECK (version >= 0),
    CONSTRAINT knowledge_document_source_resource_id_nonnil_ck CHECK (source_resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_document_active_revision_id_nonnil_ck CHECK (active_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_document_rule_1_ck CHECK (source_type IN ('CONTENT','PRODUCT','FAQ','BROCHURE')),
    CONSTRAINT knowledge_document_rule_2_ck CHECK (visibility IN ('PUBLIC'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.knowledge_document TO nigson_ai_app;

CREATE TABLE ai.knowledge_revision (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    document_id uuid NOT NULL,
    source_revision bigint NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    source_url varchar(1000) NOT NULL,
    embedding_revision_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    activated_at timestamptz,
    CONSTRAINT knowledge_revision_id_pk PRIMARY KEY (id),
    CONSTRAINT knowledge_revision_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_revision_version_ck CHECK (version >= 0),
    CONSTRAINT knowledge_revision_document_id_nonnil_ck CHECK (document_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_revision_embedding_revision_id_nonnil_ck CHECK (embedding_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_revision_rule_1_ck CHECK (status IN ('BUILDING','VALIDATED','ACTIVE','SUPERSEDED','WITHDRAWN','FAILED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.knowledge_revision TO nigson_ai_app;

CREATE TABLE ai.knowledge_chunk (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    revision_id uuid NOT NULL,
    ordinal integer NOT NULL,
    section_title varchar(200) NOT NULL,
    body text NOT NULL,
    token_count integer NOT NULL,
    embedding ai.vector(1024) NOT NULL,
    metadata jsonb NOT NULL,
    source_hash char(64) NOT NULL,
    CONSTRAINT knowledge_chunk_id_pk PRIMARY KEY (id),
    CONSTRAINT knowledge_chunk_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_chunk_version_ck CHECK (version >= 0),
    CONSTRAINT knowledge_chunk_revision_id_nonnil_ck CHECK (revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT knowledge_chunk_rule_1_ck CHECK (ordinal>=0),
    CONSTRAINT knowledge_chunk_rule_2_ck CHECK (token_count BETWEEN 1 AND 1200)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.knowledge_chunk TO nigson_ai_app;

CREATE TABLE ai.embedding_revision (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    provider varchar(40) NOT NULL,
    model_id varchar(200) NOT NULL,
    dimensions integer NOT NULL,
    normalization boolean NOT NULL,
    chunker_version varchar(40) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    CONSTRAINT embedding_revision_id_pk PRIMARY KEY (id),
    CONSTRAINT embedding_revision_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT embedding_revision_version_ck CHECK (version >= 0),
    CONSTRAINT embedding_revision_rule_1_ck CHECK (dimensions=1024)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.embedding_revision TO nigson_ai_app;

CREATE TABLE ai.conversation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid,
    anonymous_session_hash char(64),
    market_code varchar(32) NOT NULL,
    locale varchar(16) NOT NULL,
    state varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT conversation_id_pk PRIMARY KEY (id),
    CONSTRAINT conversation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT conversation_version_ck CHECK (version >= 0),
    CONSTRAINT conversation_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT conversation_rule_1_ck CHECK (state IN ('ACTIVE','CLOSED','EXPIRED')),
    CONSTRAINT conversation_rule_2_ck CHECK (num_nonnulls(party_id,anonymous_session_hash)=1)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.conversation TO nigson_ai_app;

CREATE TABLE ai.ai_run (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    conversation_id uuid NOT NULL,
    request_key varchar(100) NOT NULL,
    workflow_version varchar(40) NOT NULL,
    prompt_version varchar(40) NOT NULL,
    provider varchar(40) NOT NULL,
    model_id varchar(200) NOT NULL,
    status varchar(20) NOT NULL,
    input_tokens integer NOT NULL DEFAULT 0,
    output_tokens integer NOT NULL DEFAULT 0,
    cost_estimate numeric(19,6) NOT NULL DEFAULT 0,
    step_count integer NOT NULL DEFAULT 0,
    failure_code varchar(60),
    source_revision_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    rate_revision integer NOT NULL,
    CONSTRAINT ai_run_id_pk PRIMARY KEY (id),
    CONSTRAINT ai_run_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_run_version_ck CHECK (version >= 0),
    CONSTRAINT ai_run_conversation_id_nonnil_ck CHECK (conversation_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_run_rule_1_ck CHECK (status IN ('RUNNING','SUCCEEDED','ESCALATED','FAILED','CANCELLED')),
    CONSTRAINT ai_run_rule_2_ck CHECK (input_tokens>=0),
    CONSTRAINT ai_run_rule_3_ck CHECK (output_tokens>=0),
    CONSTRAINT ai_run_rule_4_ck CHECK (step_count BETWEEN 0 AND 8)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.ai_run TO nigson_ai_app;

CREATE TABLE ai.ai_checkpoint (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    conversation_id uuid NOT NULL,
    run_id uuid NOT NULL,
    checkpoint_number integer NOT NULL,
    state_ciphertext bytea NOT NULL,
    parent_checkpoint_id uuid,
    expires_at timestamptz NOT NULL,
    CONSTRAINT ai_checkpoint_id_pk PRIMARY KEY (id),
    CONSTRAINT ai_checkpoint_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_checkpoint_conversation_id_nonnil_ck CHECK (conversation_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_checkpoint_run_id_nonnil_ck CHECK (run_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_checkpoint_parent_checkpoint_id_nonnil_ck CHECK (parent_checkpoint_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_checkpoint_rule_1_ck CHECK (checkpoint_number>=0)
);

GRANT SELECT, INSERT ON ai.ai_checkpoint TO nigson_ai_app;

CREATE TABLE ai.tool_invocation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    run_id uuid NOT NULL,
    tool_name varchar(60) NOT NULL,
    invocation_number integer NOT NULL,
    argument_hash char(64) NOT NULL,
    authorization_scope varchar(100) NOT NULL,
    outcome varchar(20) NOT NULL,
    duration_ms integer NOT NULL,
    CONSTRAINT tool_invocation_id_pk PRIMARY KEY (id),
    CONSTRAINT tool_invocation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT tool_invocation_run_id_nonnil_ck CHECK (run_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT tool_invocation_rule_1_ck CHECK (invocation_number BETWEEN 1 AND 4),
    CONSTRAINT tool_invocation_rule_2_ck CHECK (outcome IN ('SUCCEEDED','DENIED','FAILED')),
    CONSTRAINT tool_invocation_rule_3_ck CHECK (duration_ms>=0)
);

GRANT SELECT, INSERT ON ai.tool_invocation TO nigson_ai_app;

CREATE TABLE ai.ai_feedback (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    run_id uuid NOT NULL,
    party_id uuid,
    anonymous_session_hash char(64),
    rating smallint NOT NULL,
    category varchar(30),
    comment varchar(1000),
    CONSTRAINT ai_feedback_id_pk PRIMARY KEY (id),
    CONSTRAINT ai_feedback_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_feedback_version_ck CHECK (version >= 0),
    CONSTRAINT ai_feedback_run_id_nonnil_ck CHECK (run_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_feedback_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ai_feedback_rule_1_ck CHECK (rating IN (-1,1))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.ai_feedback TO nigson_ai_app;

CREATE TABLE ai.evaluation_suite (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    code varchar(80) NOT NULL,
    revision integer NOT NULL,
    dataset_checksum char(64) NOT NULL,
    cases jsonb NOT NULL,
    scoring_policy jsonb NOT NULL,
    CONSTRAINT evaluation_suite_id_pk PRIMARY KEY (id),
    CONSTRAINT evaluation_suite_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT evaluation_suite_rule_1_ck CHECK (revision>0),
    CONSTRAINT evaluation_suite_rule_2_ck CHECK (jsonb_typeof(cases)='array')
);

GRANT SELECT, INSERT ON ai.evaluation_suite TO nigson_ai_app;

CREATE TABLE ai.evaluation_result (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    suite_id uuid NOT NULL,
    model_policy_revision varchar(80) NOT NULL,
    prompt_revision varchar(40) NOT NULL,
    retrieval_revision varchar(40) NOT NULL,
    scores jsonb NOT NULL,
    passed boolean NOT NULL,
    report_object_key varchar(500) NOT NULL,
    CONSTRAINT evaluation_result_id_pk PRIMARY KEY (id),
    CONSTRAINT evaluation_result_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT evaluation_result_suite_id_nonnil_ck CHECK (suite_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT ON ai.evaluation_result TO nigson_ai_app;

CREATE TABLE ai.ingestion_job (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    business_key varchar(180) NOT NULL,
    document_id uuid,
    source_service varchar(30) NOT NULL,
    source_resource_id uuid,
    embedding_revision_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    lease_until timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    CONSTRAINT ingestion_job_id_pk PRIMARY KEY (id),
    CONSTRAINT ingestion_job_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ingestion_job_version_ck CHECK (version >= 0),
    CONSTRAINT ingestion_job_document_id_nonnil_ck CHECK (document_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ingestion_job_source_resource_id_nonnil_ck CHECK (source_resource_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ingestion_job_embedding_revision_id_nonnil_ck CHECK (embedding_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT ingestion_job_rule_1_ck CHECK (status IN ('PENDING','CLAIMED','SUCCEEDED','FAILED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.ingestion_job TO nigson_ai_app;

CREATE TABLE ai.model_policy (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(80) NOT NULL,
    revision integer NOT NULL,
    provider varchar(40) NOT NULL,
    chat_model_id varchar(200) NOT NULL,
    fallback_model_id varchar(200),
    parameters jsonb NOT NULL,
    approved boolean NOT NULL DEFAULT false,
    evaluation_result_id uuid,
    prompt_revision_id uuid NOT NULL,
    CONSTRAINT model_policy_id_pk PRIMARY KEY (id),
    CONSTRAINT model_policy_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT model_policy_version_ck CHECK (version >= 0),
    CONSTRAINT model_policy_evaluation_result_id_nonnil_ck CHECK (evaluation_result_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT model_policy_prompt_revision_id_nonnil_ck CHECK (prompt_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT model_policy_rule_1_ck CHECK (revision>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ai.model_policy TO nigson_ai_app;

CREATE TABLE ai.prompt_revision (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    code varchar(80) NOT NULL,
    revision integer NOT NULL,
    body text NOT NULL,
    output_schema jsonb NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    tool_schema_revision varchar(80) NOT NULL,
    CONSTRAINT prompt_revision_id_pk PRIMARY KEY (id),
    CONSTRAINT prompt_revision_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT prompt_revision_rule_1_ck CHECK (revision>0)
);

GRANT SELECT, INSERT ON ai.prompt_revision TO nigson_ai_app;

CREATE TABLE ai.model_rate (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    provider varchar(40) NOT NULL,
    model_id varchar(200) NOT NULL,
    revision integer NOT NULL,
    input_per_million numeric(19,6) NOT NULL,
    output_per_million numeric(19,6) NOT NULL,
    currency_code char(3) NOT NULL,
    effective_from timestamptz NOT NULL,
    CONSTRAINT model_rate_id_pk PRIMARY KEY (id),
    CONSTRAINT model_rate_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT model_rate_rule_1_ck CHECK (revision>0),
    CONSTRAINT model_rate_rule_2_ck CHECK (input_per_million>=0),
    CONSTRAINT model_rate_rule_3_ck CHECK (output_per_million>=0)
);

GRANT SELECT, INSERT ON ai.model_rate TO nigson_ai_app;
