DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.wishlist_item) THEN RAISE EXCEPTION 'Populated table commerce.wishlist_item: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.wishlist_item DROP CONSTRAINT wishlist_item_sku_id_fk;

ALTER TABLE commerce.wishlist_item DROP CONSTRAINT wishlist_item_party_id_fk;

DROP INDEX commerce.wishlist_item_sku_id_ix;

DROP INDEX commerce.wishlist_item_party_id_created_at_desc_sku_id_ix;

DROP INDEX commerce.wishlist_item_party_id_sku_id_uq;

DROP TABLE commerce.wishlist_item;
