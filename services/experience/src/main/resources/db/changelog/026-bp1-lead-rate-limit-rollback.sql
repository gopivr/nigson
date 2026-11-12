DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.lead_rate_limit) THEN RAISE EXCEPTION 'Populated table experience.lead_rate_limit: use expand/contract migration'; END IF; END $$;

DROP INDEX experience.lead_rate_limit_window_start_ix;

DROP INDEX experience.lead_rate_limit_source_hash_uq;

DROP TABLE experience.lead_rate_limit;
