-- ADR-029: account-owned wishlist membership; previous migrations preserved.
CREATE TABLE commerce.wishlist_item (
    id uuid NOT NULL DEFAULT uuidv7(),
    party_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT wishlist_item_id_pk PRIMARY KEY (id),
    CONSTRAINT wishlist_item_version_ck CHECK (version >= 0),
    CONSTRAINT wishlist_item_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wishlist_item_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wishlist_item_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wishlist_item_rule_1_ck CHECK (created_by=updated_by)
);

GRANT SELECT, INSERT, DELETE ON commerce.wishlist_item TO nigson_commerce_app;

CREATE UNIQUE INDEX wishlist_item_party_id_sku_id_uq ON commerce.wishlist_item (party_id,sku_id);

CREATE INDEX wishlist_item_party_id_created_at_desc_sku_id_ix ON commerce.wishlist_item (party_id,created_at DESC,sku_id);

CREATE INDEX wishlist_item_sku_id_ix ON commerce.wishlist_item (sku_id);

ALTER TABLE commerce.wishlist_item ADD CONSTRAINT wishlist_item_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE CASCADE;

ALTER TABLE commerce.wishlist_item ADD CONSTRAINT wishlist_item_sku_id_fk FOREIGN KEY (sku_id) REFERENCES commerce.sku (id) ON DELETE CASCADE;
