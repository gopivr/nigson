-- ADR-030: explicit standard per-SKU partner prices; preserve legacy books.
CREATE TABLE commerce.standard_sku_price (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    market_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    segment varchar(11) NOT NULL,
    price_book_id uuid,
    CONSTRAINT standard_sku_price_id_pk PRIMARY KEY (id),
    CONSTRAINT standard_sku_price_version_ck CHECK (version >= 0),
    CONSTRAINT standard_sku_price_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT standard_sku_price_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT standard_sku_price_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT standard_sku_price_price_book_id_nonnil_ck CHECK (price_book_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT standard_sku_price_rule_1_ck CHECK (segment IN ('WHOLESALE','DISTRIBUTOR'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.standard_sku_price TO nigson_commerce_app;

CREATE UNIQUE INDEX standard_sku_price_market_id_sku_id_segment_uq ON commerce.standard_sku_price (market_id,sku_id,segment);

ALTER TABLE commerce.standard_sku_price ADD CONSTRAINT standard_sku_price_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.standard_sku_price ADD CONSTRAINT standard_sku_price_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE RESTRICT;

ALTER TABLE commerce.standard_sku_price ADD CONSTRAINT standard_sku_price_price_book_id_fk FOREIGN KEY (price_book_id) REFERENCES commerce.price_book (id) ON DELETE RESTRICT;
