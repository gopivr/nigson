-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE INDEX review_product_id_state_created_at_ix ON experience.review (product_id,state,created_at);

CREATE UNIQUE INDEX review_party_id_order_line_id_uq ON experience.review (party_id,order_line_id) WHERE order_line_id IS NOT NULL;

ALTER TABLE experience.newsletter_subscription ADD CONSTRAINT newsletter_subscription_email_hash_uq UNIQUE (email_hash);

ALTER TABLE experience.newsletter_subscription ADD CONSTRAINT newsletter_subscription_unsubscribe_token_hash_uq UNIQUE (unsubscribe_token_hash);

ALTER TABLE experience.career_opportunity ADD CONSTRAINT career_opportunity_slug_uq UNIQUE (slug);

CREATE INDEX career_application_opportunity_id_state_ix ON experience.career_application (opportunity_id,state);

CREATE INDEX career_application_retention_until_ix ON experience.career_application (retention_until);

CREATE INDEX career_application_opportunity_id_fk_ix ON experience.career_application (opportunity_id);

CREATE INDEX career_application_cv_file_id_fk_ix ON experience.career_application (cv_file_id);

ALTER TABLE experience.campaign ADD CONSTRAINT campaign_code_uq UNIQUE (code);

ALTER TABLE experience.upload_session ADD CONSTRAINT upload_session_token_hash_uq UNIQUE (token_hash);

CREATE INDEX upload_session_expires_at_ix ON experience.upload_session (expires_at);

CREATE INDEX upload_session_claimed_application_id_fk_ix ON experience.upload_session (claimed_application_id);
