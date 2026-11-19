DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.standard_sku_price) THEN RAISE EXCEPTION 'Populated table commerce.standard_sku_price: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.standard_sku_price DROP CONSTRAINT standard_sku_price_price_book_id_fk;

ALTER TABLE commerce.standard_sku_price DROP CONSTRAINT standard_sku_price_sku_id_fk;

ALTER TABLE commerce.standard_sku_price DROP CONSTRAINT standard_sku_price_market_id_fk;

DROP INDEX commerce.standard_sku_price_market_id_sku_id_segment_uq;

DROP TABLE commerce.standard_sku_price;
