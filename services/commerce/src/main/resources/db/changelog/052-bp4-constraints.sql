-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.abandoned_cart_candidate ADD CONSTRAINT abandoned_cart_candidate_cart_id_fk FOREIGN KEY (cart_id) REFERENCES commerce.cart (id) ON DELETE RESTRICT;

ALTER TABLE commerce.abandoned_cart_candidate ADD CONSTRAINT abandoned_cart_candidate_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;
