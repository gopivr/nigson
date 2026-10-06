DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.content_item) THEN RAISE EXCEPTION 'Populated table experience.content_item: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.content_revision) THEN RAISE EXCEPTION 'Populated table experience.content_revision: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.file_asset) THEN RAISE EXCEPTION 'Populated table experience.file_asset: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.lead) THEN RAISE EXCEPTION 'Populated table experience.lead: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.lead_activity) THEN RAISE EXCEPTION 'Populated table experience.lead_activity: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.consent_event) THEN RAISE EXCEPTION 'Populated table experience.consent_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.communication_suppression) THEN RAISE EXCEPTION 'Populated table experience.communication_suppression: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.channel_configuration) THEN RAISE EXCEPTION 'Populated table experience.channel_configuration: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.communication_destination) THEN RAISE EXCEPTION 'Populated table experience.communication_destination: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.destination_verification) THEN RAISE EXCEPTION 'Populated table experience.destination_verification: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.inbound_message) THEN RAISE EXCEPTION 'Populated table experience.inbound_message: use expand/contract migration'; END IF; END $$;

DROP TABLE experience.inbound_message;

DROP TABLE experience.destination_verification;

DROP TABLE experience.communication_destination;

DROP TABLE experience.channel_configuration;

DROP TABLE experience.communication_suppression;

DROP TABLE experience.consent_event;

DROP TABLE experience.lead_activity;

DROP TABLE experience.lead;

DROP TABLE experience.file_asset;

DROP TABLE experience.content_revision;

DROP TABLE experience.content_item;
