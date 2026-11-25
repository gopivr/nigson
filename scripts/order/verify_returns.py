"""Native owned customer-return quantity, receipt, restock and refund linkage qualification."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
if __name__=='__main__':
    # BuildCompatibilityTest is intentionally excluded: its module-cycle check already failed at HEAD
    # because the separate cancellation feature introduced order<->payment edges. Returns are checked here.
    main(java_tests='ReturnValuesTest,ReturnPersistenceTest',java_projects='services/commerce',evidence=ROOT/'.build/order-returns/database-evidence',run_python=False,completion='PASS: native owned delivered-product returns, Operations review/receipt/restock and Finance refund linkage; genuine provider/browser acceptance not implied')
