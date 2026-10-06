ALTER TABLE ai.model_rate DROP CONSTRAINT model_rate_provider_model_id_revision_uq;

ALTER TABLE ai.prompt_revision DROP CONSTRAINT prompt_revision_code_revision_uq;

DROP INDEX ai.model_policy_prompt_revision_id_fk_ix;

DROP INDEX ai.model_policy_evaluation_result_id_fk_ix;

ALTER TABLE ai.model_policy DROP CONSTRAINT model_policy_code_revision_uq;

DROP INDEX ai.ingestion_job_embedding_revision_id_fk_ix;

DROP INDEX ai.ingestion_job_document_id_fk_ix;

DROP INDEX ai.ingestion_job_status_next_attempt_at_ix;

ALTER TABLE ai.ingestion_job DROP CONSTRAINT ingestion_job_business_key_uq;

ALTER TABLE ai.feature_configuration DROP CONSTRAINT feature_configuration_key_revision_uq;

DROP INDEX ai.audit_record_actor_subject_occurred_at_ix;

DROP INDEX ai.audit_record_resource_type_resource_id_occurred_at_ix;

DROP INDEX ai.idempotency_record_expires_at_ix;

ALTER TABLE ai.idempotency_record DROP CONSTRAINT idempotency_record_principal_key_operation_request_key_uq;

ALTER TABLE ai.processed_event DROP CONSTRAINT processed_event_consumer_event_id_uq;

DROP INDEX ai.outbox_event_state_next_attempt_at_ix;

ALTER TABLE ai.outbox_event DROP CONSTRAINT outbox_event_event_id_uq;

DROP INDEX ai.evaluation_result_suite_id_fk_ix;

DROP INDEX ai.evaluation_result_suite_id_created_at_ix;

ALTER TABLE ai.evaluation_suite DROP CONSTRAINT evaluation_suite_code_revision_uq;

DROP INDEX ai.ai_feedback_run_id_fk_ix;

ALTER TABLE ai.ai_feedback DROP CONSTRAINT ai_feedback_run_id_uq;

DROP INDEX ai.tool_invocation_run_id_fk_ix;

ALTER TABLE ai.tool_invocation DROP CONSTRAINT tool_invocation_run_id_invocation_number_uq;

DROP INDEX ai.ai_checkpoint_parent_checkpoint_id_fk_ix;

DROP INDEX ai.ai_checkpoint_run_id_fk_ix;

DROP INDEX ai.ai_checkpoint_conversation_id_fk_ix;

DROP INDEX ai.ai_checkpoint_expires_at_ix;

ALTER TABLE ai.ai_checkpoint DROP CONSTRAINT ai_checkpoint_run_id_checkpoint_number_uq;

DROP INDEX ai.ai_run_conversation_id_fk_ix;

DROP INDEX ai.ai_run_status_created_at_ix;

ALTER TABLE ai.ai_run DROP CONSTRAINT ai_run_conversation_id_request_key_uq;

DROP INDEX ai.conversation_expires_at_ix;

DROP INDEX ai.conversation_party_id_created_at_ix;

ALTER TABLE ai.embedding_revision DROP CONSTRAINT embedding_revision_provider_model_id_dimensions_chunke_5634b895;

DROP INDEX ai.knowledge_chunk_revision_id_fk_ix;

DROP INDEX ai.knowledge_chunk_search_vector_ix;

DROP INDEX ai.knowledge_chunk_embedding_vector_cosine_ops_ix;

ALTER TABLE ai.knowledge_chunk DROP CONSTRAINT knowledge_chunk_revision_id_ordinal_uq;

ALTER TABLE ai.knowledge_chunk DROP COLUMN search_vector;

DROP INDEX ai.knowledge_revision_embedding_revision_id_fk_ix;

DROP INDEX ai.knowledge_revision_document_id_fk_ix;

ALTER TABLE ai.knowledge_revision DROP CONSTRAINT knowledge_revision_document_id_source_revision_embeddi_71ee9250;

DROP INDEX ai.knowledge_document_active_revision_id_fk_ix;

ALTER TABLE ai.knowledge_document DROP CONSTRAINT knowledge_document_source_service_source_resource_id_l_4ae210f5;
