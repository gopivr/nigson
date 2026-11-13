BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='15s';
DO $$ BEGIN
IF NOT EXISTS(SELECT 1 FROM commerce.market WHERE code='NG' AND enabled
 AND (retail_price_book_id IS NULL OR retail_price_book_id='18156ba1-334a-53e9-9373-493a9df4b677'::uuid)
 AND (tax_policy='{}'::jsonb OR tax_policy='{"schemaVersion": 1, "mode": "NONE", "rate": "0", "rounding": "HALF_UP", "approvalReference": "SYNTHETIC_LOCAL_TEST_ONLY"}'::jsonb)
 AND (returns_policy_revision IS NULL OR returns_policy_revision='SYNTHETIC-LOCAL-RETURNS-V1'))
THEN RAISE EXCEPTION 'REFUSING_TO_REPLACE_NONDEMO_MARKET_POLICY'; END IF;
END $$;
INSERT INTO commerce.price_book(id,created_by,updated_by,code,currency_code,segment,revision,effective_from,active)
VALUES ('18156ba1-334a-53e9-9373-493a9df4b677','local-demo-pricing','local-demo-pricing','SYNTHETIC-LOCAL-ELECTRONICS-NGN','NGN','RETAIL',1,'2026-01-01T00:00:00Z',true)
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '538c6417-76a4-5abd-b92c-0df721ea90c4','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,450000
FROM commerce.sku WHERE id='8dd03db8-c840-5da9-a005-67174d5272fc' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '3c35f77f-c96d-505e-8ad7-5c5197246c8d','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,451000
FROM commerce.sku WHERE id='dd3573ba-5a3a-5c20-be9c-c3265f48e7dc' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '5cee73b8-dd0d-540a-90e8-1d1b27b0dd84','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,452000
FROM commerce.sku WHERE id='024bcad4-81c6-5379-97d2-947e77a9a8c4' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'e4bf80e8-ec5e-5eae-b0e9-60da7351b051','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,180000
FROM commerce.sku WHERE id='c9122607-2f48-5329-b497-70725334813b' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'c0b66b9e-be12-5ae5-a1fa-36679f668b20','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,181000
FROM commerce.sku WHERE id='c59b5e73-0da1-5ba9-98cd-7f183ffb59ef' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '8acbec87-0e6c-5503-8c5a-bfbdd10ff41a','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,182000
FROM commerce.sku WHERE id='b546d4f3-4605-5016-90dd-36a0168a3452' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '27d53f80-3e11-5d7f-8ad0-10a86ac889e4','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,95000
FROM commerce.sku WHERE id='13b3215c-6a3f-538e-afb7-530d96a5ac4a' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '41313321-a3a6-5a87-ab08-30c349d3da25','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,96000
FROM commerce.sku WHERE id='ac5217b8-d5af-58ab-bff5-0b6462adb152' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'ac19c513-167a-5968-8216-ac1bd500cafb','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,97000
FROM commerce.sku WHERE id='613216bb-4795-5039-b0f2-0df2a01528f8' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'a7e441fb-581d-5045-98f7-34064a56e586','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,25000
FROM commerce.sku WHERE id='b38607c1-f008-5923-8e29-0de559560329' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '0a1716d7-58dc-56a9-b1d5-32bc1660fe2b','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,26000
FROM commerce.sku WHERE id='03538c8a-5dcb-5d1a-a5e1-9d1670ba9620' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'ab9c0284-067f-5134-b3c3-5941a0532a66','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,27000
FROM commerce.sku WHERE id='877d2857-7d5a-5472-bced-8c938c564905' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '597b4b7f-9d8d-577c-8a84-cffbf6bf394f','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,35000
FROM commerce.sku WHERE id='a4240469-5751-5ee3-b11e-423b5bcd1df1' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'c0333a8e-74c0-5168-bf9c-cd20a8950c2f','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,36000
FROM commerce.sku WHERE id='0677c163-e73d-5c80-bfa4-93c0a255e882' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'b73ecb18-ca9e-549e-9bb9-06de3ec4ed0a','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,37000
FROM commerce.sku WHERE id='b4a11381-8921-50b8-b979-aa303945a1eb' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '4b33d4a5-66b9-5dab-91f9-6362ddcf7533','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,30000
FROM commerce.sku WHERE id='29f666d2-5b32-5564-a5cf-f095215c81b3' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'de81a4ef-6c40-5f7f-b246-502c0b73bfb8','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,31000
FROM commerce.sku WHERE id='d7d48669-6d1d-5b96-bc0b-d146de217738' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '8cd48aa0-9c0f-5e6c-ab85-87cfd1e0a51d','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,32000
FROM commerce.sku WHERE id='b0c7bd40-d1b8-546f-8bdc-c2194894d605' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'c29158ee-f615-5988-bcc5-90a72e4cfe9f','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,20000
FROM commerce.sku WHERE id='3ac8e4e4-49c7-549c-958f-9051347f33d2' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '796e184c-063a-555f-aec6-2bc827dee13a','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,21000
FROM commerce.sku WHERE id='e96bcd85-3620-5858-8dda-acfda62ff3b6' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'c5a3cd80-086b-53e2-9366-e5240d103232','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,22000
FROM commerce.sku WHERE id='4fc83b37-fd00-547d-8c67-129976973011' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT 'aa173796-51b8-5978-9da4-c7ee509ceb20','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,15000
FROM commerce.sku WHERE id='7ddfd7c5-0e32-5482-80d6-05f656854a11' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '9f091d41-62b6-525c-bb17-b6471ed9b811','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,16000
FROM commerce.sku WHERE id='d9a0ad9d-c004-572b-af94-86cb6618222d' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
INSERT INTO commerce.price_entry(id,created_by,updated_by,price_book_id,sku_id,minimum_quantity,unit_amount)
SELECT '507e01e3-48ad-5d05-a65f-6cf59277b785','local-demo-pricing','local-demo-pricing','18156ba1-334a-53e9-9373-493a9df4b677',id,1,17000
FROM commerce.sku WHERE id='e286c2ac-8c4a-5838-bc0a-dbbdccd1a68a' AND created_by='demo-electronics-v1'
ON CONFLICT(id) DO NOTHING;
UPDATE commerce.market SET retail_price_book_id='18156ba1-334a-53e9-9373-493a9df4b677',tax_policy='{"schemaVersion": 1, "mode": "NONE", "rate": "0", "rounding": "HALF_UP", "approvalReference": "SYNTHETIC_LOCAL_TEST_ONLY"}'::jsonb,
returns_policy_revision='SYNTHETIC-LOCAL-RETURNS-V1',policy_revision=policy_revision+1,version=version+1,
updated_at=now(),updated_by='local-demo-pricing'
WHERE code='NG' AND (retail_price_book_id IS DISTINCT FROM '18156ba1-334a-53e9-9373-493a9df4b677'::uuid
OR tax_policy IS DISTINCT FROM '{"schemaVersion": 1, "mode": "NONE", "rate": "0", "rounding": "HALF_UP", "approvalReference": "SYNTHETIC_LOCAL_TEST_ONLY"}'::jsonb OR returns_policy_revision IS DISTINCT FROM 'SYNTHETIC-LOCAL-RETURNS-V1');
DO $$ BEGIN
IF (SELECT count(*) FROM commerce.price_entry WHERE price_book_id='18156ba1-334a-53e9-9373-493a9df4b677')<>24
THEN RAISE EXCEPTION 'DEMO_ELECTRONICS_PRICE_COVERAGE_REQUIRED'; END IF;
END $$;
DO $$ BEGIN
IF EXISTS(SELECT 1 FROM commerce.market_destination d JOIN commerce.market m ON m.id=d.market_id
 WHERE m.code='NG' AND d.country_code='NG' AND NOT
 ((d.created_by='approved-launch:ADR-008' AND NOT d.delivery_enabled AND d.address_rules='{}'::jsonb)
 OR d.updated_by='local-demo-pricing'))
THEN RAISE EXCEPTION 'REFUSING_TO_REPLACE_NONDEMO_DESTINATION'; END IF;
END $$;
INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled)
SELECT '2e1f2942-d0e0-5726-aa2d-2ce8bfd6ada9','local-demo-pricing','local-demo-pricing',id,'NG','{"schemaVersion": 1, "requiredFields": ["RECIPIENT", "LINE1", "CITY"], "postalPattern": null, "regionCodes": []}'::jsonb,true
FROM commerce.market m WHERE code='NG' AND NOT EXISTS(SELECT 1 FROM commerce.market_destination d WHERE d.market_id=m.id AND d.country_code='NG');
WITH changed AS (
 UPDATE commerce.market_destination d SET address_rules='{"schemaVersion": 1, "requiredFields": ["RECIPIENT", "LINE1", "CITY"], "postalPattern": null, "regionCodes": []}'::jsonb,delivery_enabled=true,
 updated_by='local-demo-pricing',updated_at=now(),version=d.version+1 FROM commerce.market m
 WHERE d.market_id=m.id AND m.code='NG' AND d.country_code='NG'
 AND (NOT d.delivery_enabled OR d.address_rules IS DISTINCT FROM '{"schemaVersion": 1, "requiredFields": ["RECIPIENT", "LINE1", "CITY"], "postalPattern": null, "regionCodes": []}'::jsonb) RETURNING d.market_id
) UPDATE commerce.market SET version=version+1,policy_revision=policy_revision+1,
 updated_by='local-demo-pricing',updated_at=now() WHERE id IN(SELECT market_id FROM changed);

-- Staff roles for admin accounts
INSERT INTO commerce.staff_role_assignment (id, created_at, created_by, updated_at, updated_by, version, party_id, role, granted_by)
SELECT gen_random_uuid(), NOW(), 'local-admin-bootstrap', NOW(), 'local-admin-bootstrap', 0, p.party_id, r.role, p.party_id
FROM (VALUES 
  ('43c829a8-5ddc-5d8a-be64-557f8998c1a9'::uuid),
  ('9bff1df7-906c-4be8-a76d-0884dc9f6005'::uuid)
) AS p(party_id)
CROSS JOIN (VALUES 
  ('ADMIN'), ('CONTENT_EDITOR'), ('OPERATIONS'), ('FINANCE'), ('SALES'), ('CONTENT_PUBLISHER'), ('ANALYST')
) AS r(role)
WHERE NOT EXISTS (
  SELECT 1 FROM commerce.staff_role_assignment existing
  WHERE existing.party_id = p.party_id AND existing.role = r.role AND existing.revoked_at IS NULL
);

COMMIT;