-- NGN-001 / ADR-008: country/currency facts approved 4 October2026; no selling policy.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM commerce.currency WHERE code='NGN' AND minor_unit<>2) THEN RAISE EXCEPTION 'NGN_METADATA_CONFLICT'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.market WHERE code='NG' AND currency_code<>'NGN') THEN RAISE EXCEPTION 'NG_MARKET_METADATA_CONFLICT'; END IF;
 INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit,active)
 VALUES ('99ac5083-bb88-5dab-a5cd-64492a9353da','approved-launch:ADR-008','approved-launch:ADR-008','NGN',2,true)
 ON CONFLICT(code) DO UPDATE SET active=true,version=commerce.currency.version+1,updated_by='approved-launch:ADR-008',updated_at=now() WHERE NOT commerce.currency.active;
 INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled,policy_revision,tax_policy)
 VALUES ('29b78d62-ec0d-5ff9-8319-b485ecb093d6','approved-launch:ADR-008','approved-launch:ADR-008','NG','Nigeria','NGN',false,1,'{}'::jsonb)
 ON CONFLICT(code) DO NOTHING;
 INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled)
 SELECT 'fa95263f-cc97-5907-8948-c913030ecfa8','approved-launch:ADR-008','approved-launch:ADR-008',id,'NG','{}'::jsonb,false FROM commerce.market WHERE code='NG'
 ON CONFLICT(market_id,country_code) DO NOTHING;
END $$;
