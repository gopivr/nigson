"""Owned manual fulfillment qualification; no external carrier execution."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
if __name__=='__main__':
    main(java_tests='FulfillmentValuesTest,DeliveryRulePersistenceTest,ShipmentPersistenceTest,BuildCompatibilityTest,SecurityAssemblyTest',evidence=ROOT/'.build/carrier-handover/database-evidence',run_python=False,completion='PASS: manual fulfillment and automatic-booking fail-closed checks; no external carrier execution implied')
