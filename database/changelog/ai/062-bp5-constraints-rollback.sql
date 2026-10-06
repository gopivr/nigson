ALTER TABLE ai.model_policy DROP CONSTRAINT model_policy_prompt_revision_id_fk;

ALTER TABLE ai.model_policy DROP CONSTRAINT model_policy_evaluation_result_id_fk;

ALTER TABLE ai.ingestion_job DROP CONSTRAINT ingestion_job_embedding_revision_id_fk;

ALTER TABLE ai.ingestion_job DROP CONSTRAINT ingestion_job_document_id_fk;

ALTER TABLE ai.evaluation_result DROP CONSTRAINT evaluation_result_suite_id_fk;

ALTER TABLE ai.ai_feedback DROP CONSTRAINT ai_feedback_run_id_fk;

ALTER TABLE ai.tool_invocation DROP CONSTRAINT tool_invocation_run_id_fk;

ALTER TABLE ai.ai_checkpoint DROP CONSTRAINT ai_checkpoint_parent_checkpoint_id_fk;

ALTER TABLE ai.ai_checkpoint DROP CONSTRAINT ai_checkpoint_run_id_fk;

ALTER TABLE ai.ai_checkpoint DROP CONSTRAINT ai_checkpoint_conversation_id_fk;

ALTER TABLE ai.ai_run DROP CONSTRAINT ai_run_conversation_id_fk;

ALTER TABLE ai.knowledge_chunk DROP CONSTRAINT knowledge_chunk_revision_id_fk;

ALTER TABLE ai.knowledge_revision DROP CONSTRAINT knowledge_revision_embedding_revision_id_fk;

ALTER TABLE ai.knowledge_revision DROP CONSTRAINT knowledge_revision_document_id_fk;

ALTER TABLE ai.knowledge_document DROP CONSTRAINT knowledge_document_active_revision_id_fk;
