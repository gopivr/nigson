-- Remove only this migration's untouched metadata. Preserve preexisting approved facts and all commercial evidence.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM commerce.market WHERE id='29b78d62-ec0d-5ff9-8319-b485ecb093d6' AND (created_by<>'approved-launch:ADR-008' OR version<>0 OR enabled OR policy_revision<>1 OR tax_policy<>'{}'::jsonb OR returns_policy_revision IS NOT NULL OR retail_price_book_id IS NOT NULL)) THEN RAISE EXCEPTION 'POPULATED_NG_METADATA_USE_FORWARD_CORRECTION'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.market_destination WHERE id='fa95263f-cc97-5907-8948-c913030ecfa8' AND (created_by<>'approved-launch:ADR-008' OR version<>0 OR delivery_enabled OR address_rules<>'{}'::jsonb)) THEN RAISE EXCEPTION 'POPULATED_NG_DESTINATION_USE_FORWARD_CORRECTION'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.currency WHERE id='99ac5083-bb88-5dab-a5cd-64492a9353da' AND (created_by<>'approved-launch:ADR-008' OR version<>0 OR code<>'NGN' OR minor_unit<>2)) THEN RAISE EXCEPTION 'POPULATED_NGN_METADATA_USE_FORWARD_CORRECTION'; END IF;
 DELETE FROM commerce.market_destination WHERE id='fa95263f-cc97-5907-8948-c913030ecfa8' AND created_by='approved-launch:ADR-008';
 DELETE FROM commerce.market WHERE id='29b78d62-ec0d-5ff9-8319-b485ecb093d6' AND created_by='approved-launch:ADR-008';
 DELETE FROM commerce.currency WHERE id='99ac5083-bb88-5dab-a5cd-64492a9353da' AND created_by='approved-launch:ADR-008';
END $$;
