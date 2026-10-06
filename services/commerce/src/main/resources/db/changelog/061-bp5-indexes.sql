-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.product_recommendation ADD CONSTRAINT product_recommendation_product_id_related_product_id_kind_uq UNIQUE (product_id,related_product_id,kind);

CREATE INDEX product_recommendation_product_id_fk_ix ON commerce.product_recommendation (product_id);

CREATE INDEX product_recommendation_related_product_id_fk_ix ON commerce.product_recommendation (related_product_id);
