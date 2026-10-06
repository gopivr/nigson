-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE ai.knowledge_document ADD CONSTRAINT knowledge_document_source_service_source_resource_id_l_4ae210f5 UNIQUE (source_service,source_resource_id,locale,market_code);

CREATE INDEX knowledge_document_active_revision_id_fk_ix ON ai.knowledge_document (active_revision_id);

ALTER TABLE ai.knowledge_revision ADD CONSTRAINT knowledge_revision_document_id_source_revision_embeddi_71ee9250 UNIQUE (document_id,source_revision,embedding_revision_id);

CREATE INDEX knowledge_revision_document_id_fk_ix ON ai.knowledge_revision (document_id);

CREATE INDEX knowledge_revision_embedding_revision_id_fk_ix ON ai.knowledge_revision (embedding_revision_id);

ALTER TABLE ai.knowledge_chunk ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(section_title,'') || ' ' || coalesce(body,''))) STORED;

ALTER TABLE ai.knowledge_chunk ADD CONSTRAINT knowledge_chunk_revision_id_ordinal_uq UNIQUE (revision_id,ordinal);

CREATE INDEX knowledge_chunk_embedding_vector_cosine_ops_ix ON ai.knowledge_chunk USING hnsw (embedding ai.vector_cosine_ops) WITH (m=16, ef_construction=64);

CREATE INDEX knowledge_chunk_search_vector_ix ON ai.knowledge_chunk USING gin (search_vector);

CREATE INDEX knowledge_chunk_revision_id_fk_ix ON ai.knowledge_chunk (revision_id);

ALTER TABLE ai.embedding_revision ADD CONSTRAINT embedding_revision_provider_model_id_dimensions_chunke_5634b895 UNIQUE (provider,model_id,dimensions,chunker_version);

CREATE INDEX conversation_party_id_created_at_ix ON ai.conversation (party_id,created_at);

CREATE INDEX conversation_expires_at_ix ON ai.conversation (expires_at);

ALTER TABLE ai.ai_run ADD CONSTRAINT ai_run_conversation_id_request_key_uq UNIQUE (conversation_id,request_key);

CREATE INDEX ai_run_status_created_at_ix ON ai.ai_run (status,created_at);

CREATE INDEX ai_run_conversation_id_fk_ix ON ai.ai_run (conversation_id);

ALTER TABLE ai.ai_checkpoint ADD CONSTRAINT ai_checkpoint_run_id_checkpoint_number_uq UNIQUE (run_id,checkpoint_number);

CREATE INDEX ai_checkpoint_expires_at_ix ON ai.ai_checkpoint (expires_at);

CREATE INDEX ai_checkpoint_conversation_id_fk_ix ON ai.ai_checkpoint (conversation_id);

CREATE INDEX ai_checkpoint_run_id_fk_ix ON ai.ai_checkpoint (run_id);

CREATE INDEX ai_checkpoint_parent_checkpoint_id_fk_ix ON ai.ai_checkpoint (parent_checkpoint_id);

ALTER TABLE ai.tool_invocation ADD CONSTRAINT tool_invocation_run_id_invocation_number_uq UNIQUE (run_id,invocation_number);

CREATE INDEX tool_invocation_run_id_fk_ix ON ai.tool_invocation (run_id);

ALTER TABLE ai.ai_feedback ADD CONSTRAINT ai_feedback_run_id_uq UNIQUE (run_id);

CREATE INDEX ai_feedback_run_id_fk_ix ON ai.ai_feedback (run_id);

ALTER TABLE ai.evaluation_suite ADD CONSTRAINT evaluation_suite_code_revision_uq UNIQUE (code,revision);

CREATE INDEX evaluation_result_suite_id_created_at_ix ON ai.evaluation_result (suite_id,created_at);

CREATE INDEX evaluation_result_suite_id_fk_ix ON ai.evaluation_result (suite_id);

ALTER TABLE ai.outbox_event ADD CONSTRAINT outbox_event_event_id_uq UNIQUE (event_id);

CREATE INDEX outbox_event_state_next_attempt_at_ix ON ai.outbox_event (state,next_attempt_at);

ALTER TABLE ai.processed_event ADD CONSTRAINT processed_event_consumer_event_id_uq UNIQUE (consumer,event_id);

ALTER TABLE ai.idempotency_record ADD CONSTRAINT idempotency_record_principal_key_operation_request_key_uq UNIQUE (principal_key,operation,request_key);

CREATE INDEX idempotency_record_expires_at_ix ON ai.idempotency_record (expires_at);

CREATE INDEX audit_record_resource_type_resource_id_occurred_at_ix ON ai.audit_record (resource_type,resource_id,occurred_at);

CREATE INDEX audit_record_actor_subject_occurred_at_ix ON ai.audit_record (actor_subject,occurred_at);

ALTER TABLE ai.feature_configuration ADD CONSTRAINT feature_configuration_key_revision_uq UNIQUE (key,revision);

ALTER TABLE ai.ingestion_job ADD CONSTRAINT ingestion_job_business_key_uq UNIQUE (business_key);

CREATE INDEX ingestion_job_status_next_attempt_at_ix ON ai.ingestion_job (status,next_attempt_at);

CREATE INDEX ingestion_job_document_id_fk_ix ON ai.ingestion_job (document_id);

CREATE INDEX ingestion_job_embedding_revision_id_fk_ix ON ai.ingestion_job (embedding_revision_id);

ALTER TABLE ai.model_policy ADD CONSTRAINT model_policy_code_revision_uq UNIQUE (code,revision);

CREATE INDEX model_policy_evaluation_result_id_fk_ix ON ai.model_policy (evaluation_result_id);

CREATE INDEX model_policy_prompt_revision_id_fk_ix ON ai.model_policy (prompt_revision_id);

ALTER TABLE ai.prompt_revision ADD CONSTRAINT prompt_revision_code_revision_uq UNIQUE (code,revision);

ALTER TABLE ai.model_rate ADD CONSTRAINT model_rate_provider_model_id_revision_uq UNIQUE (provider,model_id,revision);
