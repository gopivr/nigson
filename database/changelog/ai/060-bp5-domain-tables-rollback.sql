DO $$ BEGIN IF EXISTS (SELECT 1 FROM ai.knowledge_document) THEN RAISE EXCEPTION 'Populated table ai.knowledge_document: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.knowledge_revision) THEN RAISE EXCEPTION 'Populated table ai.knowledge_revision: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.knowledge_chunk) THEN RAISE EXCEPTION 'Populated table ai.knowledge_chunk: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.embedding_revision) THEN RAISE EXCEPTION 'Populated table ai.embedding_revision: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.conversation) THEN RAISE EXCEPTION 'Populated table ai.conversation: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.ai_run) THEN RAISE EXCEPTION 'Populated table ai.ai_run: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.ai_checkpoint) THEN RAISE EXCEPTION 'Populated table ai.ai_checkpoint: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.tool_invocation) THEN RAISE EXCEPTION 'Populated table ai.tool_invocation: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.ai_feedback) THEN RAISE EXCEPTION 'Populated table ai.ai_feedback: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.evaluation_suite) THEN RAISE EXCEPTION 'Populated table ai.evaluation_suite: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.evaluation_result) THEN RAISE EXCEPTION 'Populated table ai.evaluation_result: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.ingestion_job) THEN RAISE EXCEPTION 'Populated table ai.ingestion_job: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.model_policy) THEN RAISE EXCEPTION 'Populated table ai.model_policy: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.prompt_revision) THEN RAISE EXCEPTION 'Populated table ai.prompt_revision: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.model_rate) THEN RAISE EXCEPTION 'Populated table ai.model_rate: use expand/contract migration'; END IF; END $$;

DROP TABLE ai.model_rate;

DROP TABLE ai.prompt_revision;

DROP TABLE ai.model_policy;

DROP TABLE ai.ingestion_job;

DROP TABLE ai.evaluation_result;

DROP TABLE ai.evaluation_suite;

DROP TABLE ai.ai_feedback;

DROP TABLE ai.tool_invocation;

DROP TABLE ai.ai_checkpoint;

DROP TABLE ai.ai_run;

DROP TABLE ai.conversation;

DROP TABLE ai.embedding_revision;

DROP TABLE ai.knowledge_chunk;

DROP TABLE ai.knowledge_revision;

DROP TABLE ai.knowledge_document;
