-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE ai.knowledge_document ADD CONSTRAINT knowledge_document_active_revision_id_fk FOREIGN KEY (active_revision_id) REFERENCES ai.knowledge_revision (id) ON DELETE RESTRICT;

ALTER TABLE ai.knowledge_revision ADD CONSTRAINT knowledge_revision_document_id_fk FOREIGN KEY (document_id) REFERENCES ai.knowledge_document (id) ON DELETE RESTRICT;

ALTER TABLE ai.knowledge_revision ADD CONSTRAINT knowledge_revision_embedding_revision_id_fk FOREIGN KEY (embedding_revision_id) REFERENCES ai.embedding_revision (id) ON DELETE RESTRICT;

ALTER TABLE ai.knowledge_chunk ADD CONSTRAINT knowledge_chunk_revision_id_fk FOREIGN KEY (revision_id) REFERENCES ai.knowledge_revision (id) ON DELETE RESTRICT;

ALTER TABLE ai.ai_run ADD CONSTRAINT ai_run_conversation_id_fk FOREIGN KEY (conversation_id) REFERENCES ai.conversation (id) ON DELETE RESTRICT;

ALTER TABLE ai.ai_checkpoint ADD CONSTRAINT ai_checkpoint_conversation_id_fk FOREIGN KEY (conversation_id) REFERENCES ai.conversation (id) ON DELETE RESTRICT;

ALTER TABLE ai.ai_checkpoint ADD CONSTRAINT ai_checkpoint_run_id_fk FOREIGN KEY (run_id) REFERENCES ai.ai_run (id) ON DELETE RESTRICT;

ALTER TABLE ai.ai_checkpoint ADD CONSTRAINT ai_checkpoint_parent_checkpoint_id_fk FOREIGN KEY (parent_checkpoint_id) REFERENCES ai.ai_checkpoint (id) ON DELETE RESTRICT;

ALTER TABLE ai.tool_invocation ADD CONSTRAINT tool_invocation_run_id_fk FOREIGN KEY (run_id) REFERENCES ai.ai_run (id) ON DELETE RESTRICT;

ALTER TABLE ai.ai_feedback ADD CONSTRAINT ai_feedback_run_id_fk FOREIGN KEY (run_id) REFERENCES ai.ai_run (id) ON DELETE RESTRICT;

ALTER TABLE ai.evaluation_result ADD CONSTRAINT evaluation_result_suite_id_fk FOREIGN KEY (suite_id) REFERENCES ai.evaluation_suite (id) ON DELETE RESTRICT;

ALTER TABLE ai.ingestion_job ADD CONSTRAINT ingestion_job_document_id_fk FOREIGN KEY (document_id) REFERENCES ai.knowledge_document (id) ON DELETE RESTRICT;

ALTER TABLE ai.ingestion_job ADD CONSTRAINT ingestion_job_embedding_revision_id_fk FOREIGN KEY (embedding_revision_id) REFERENCES ai.embedding_revision (id) ON DELETE RESTRICT;

ALTER TABLE ai.model_policy ADD CONSTRAINT model_policy_evaluation_result_id_fk FOREIGN KEY (evaluation_result_id) REFERENCES ai.evaluation_result (id) ON DELETE RESTRICT;

ALTER TABLE ai.model_policy ADD CONSTRAINT model_policy_prompt_revision_id_fk FOREIGN KEY (prompt_revision_id) REFERENCES ai.prompt_revision (id) ON DELETE RESTRICT;
