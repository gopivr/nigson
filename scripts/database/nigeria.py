"""Approved ADR-008 metadata only. Existing commercial policy is never overwritten."""
import json
from pathlib import Path
import uuid
BASE=Path('services/commerce/src/main/resources/db/changelog')
STEM='024-bp1-nigeria-metadata'
IDS={kind:str(uuid.uuid5(uuid.NAMESPACE_URL,'https://nigson.invalid/approved/ADR-008/'+kind)) for kind in ['currency-NGN','market-NG','destination-NG']}
ACTOR='approved-launch:ADR-008'
def files():
    sql=f"""-- NGN-001 / ADR-008: country/currency facts approved 4 October2026; no selling policy.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM commerce.currency WHERE code='NGN' AND minor_unit<>2) THEN RAISE EXCEPTION 'NGN_METADATA_CONFLICT'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.market WHERE code='NG' AND currency_code<>'NGN') THEN RAISE EXCEPTION 'NG_MARKET_METADATA_CONFLICT'; END IF;
 INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit,active)
 VALUES ('{IDS['currency-NGN']}','{ACTOR}','{ACTOR}','NGN',2,true)
 ON CONFLICT(code) DO UPDATE SET active=true,version=commerce.currency.version+1,updated_by='{ACTOR}',updated_at=now() WHERE NOT commerce.currency.active;
 INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled,policy_revision,tax_policy)
 VALUES ('{IDS['market-NG']}','{ACTOR}','{ACTOR}','NG','Nigeria','NGN',false,1,'{{}}'::jsonb)
 ON CONFLICT(code) DO NOTHING;
 INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled)
 SELECT '{IDS['destination-NG']}','{ACTOR}','{ACTOR}',id,'NG','{{}}'::jsonb,false FROM commerce.market WHERE code='NG'
 ON CONFLICT(market_id,country_code) DO NOTHING;
END $$;
"""
    rollback=f"""-- Remove only this migration's untouched metadata. Preserve preexisting approved facts and all commercial evidence.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM commerce.market WHERE id='{IDS['market-NG']}' AND (created_by<>'{ACTOR}' OR version<>0 OR enabled OR policy_revision<>1 OR tax_policy<>'{{}}'::jsonb OR returns_policy_revision IS NOT NULL OR retail_price_book_id IS NOT NULL)) THEN RAISE EXCEPTION 'POPULATED_NG_METADATA_USE_FORWARD_CORRECTION'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.market_destination WHERE id='{IDS['destination-NG']}' AND (created_by<>'{ACTOR}' OR version<>0 OR delivery_enabled OR address_rules<>'{{}}'::jsonb)) THEN RAISE EXCEPTION 'POPULATED_NG_DESTINATION_USE_FORWARD_CORRECTION'; END IF;
 IF EXISTS(SELECT 1 FROM commerce.currency WHERE id='{IDS['currency-NGN']}' AND (created_by<>'{ACTOR}' OR version<>0 OR code<>'NGN' OR minor_unit<>2)) THEN RAISE EXCEPTION 'POPULATED_NGN_METADATA_USE_FORWARD_CORRECTION'; END IF;
 DELETE FROM commerce.market_destination WHERE id='{IDS['destination-NG']}' AND created_by='{ACTOR}';
 DELETE FROM commerce.market WHERE id='{IDS['market-NG']}' AND created_by='{ACTOR}';
 DELETE FROM commerce.currency WHERE id='{IDS['currency-NGN']}' AND created_by='{ACTOR}';
END $$;
"""
    guard="SELECT CASE WHEN current_user='nigson_commerce_migrator' AND current_setting('server_version_num')::int BETWEEN 180000 AND 189999 THEN 1 ELSE 0 END"
    change={'databaseChangeLog':[{'changeSet':{'id':'commerce-20261010-ngn-001','author':'nigson-ngn','logicalFilePath':'commerce/'+STEM+'.yaml','preConditions':[{'onFail':'HALT'},{'dbms':{'type':'postgresql'}},{'sqlCheck':{'expectedResult':'1','sql':guard}}],'changes':[{'sqlFile':{'path':STEM+'.sql','relativeToChangelogFile':True,'splitStatements':False}}],'rollback':[{'sqlFile':{'path':STEM+'-rollback.sql','relativeToChangelogFile':True,'splitStatements':False}}]}}]}
    return {BASE/(STEM+'.sql'):sql,BASE/(STEM+'-rollback.sql'):rollback,BASE/(STEM+'.yaml'):json.dumps(change,indent=2)+'\n'}
