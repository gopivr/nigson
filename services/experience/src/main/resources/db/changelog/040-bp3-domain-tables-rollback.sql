DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.distributor_application) THEN RAISE EXCEPTION 'Populated table experience.distributor_application: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.application_document) THEN RAISE EXCEPTION 'Populated table experience.application_document: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.lead_requested_item) THEN RAISE EXCEPTION 'Populated table experience.lead_requested_item: use expand/contract migration'; END IF; END $$;

DROP TABLE experience.lead_requested_item;

DROP TABLE experience.application_document;

DROP TABLE experience.distributor_application;
