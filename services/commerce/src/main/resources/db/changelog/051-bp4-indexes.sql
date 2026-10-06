-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.abandoned_cart_candidate ADD CONSTRAINT abandoned_cart_candidate_cart_id_notification_key_uq UNIQUE (cart_id,notification_key);

CREATE INDEX abandoned_cart_candidate_status_scheduled_at_ix ON commerce.abandoned_cart_candidate (status,scheduled_at);

CREATE INDEX abandoned_cart_candidate_cart_id_fk_ix ON commerce.abandoned_cart_candidate (cart_id);

CREATE INDEX abandoned_cart_candidate_party_id_fk_ix ON commerce.abandoned_cart_candidate (party_id);
