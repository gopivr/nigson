-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.product_recommendation ADD CONSTRAINT product_recommendation_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;

ALTER TABLE commerce.product_recommendation ADD CONSTRAINT product_recommendation_related_product_id_fk FOREIGN KEY (related_product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;
