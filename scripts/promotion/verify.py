"""Isolated PostgreSQL acceptance for immutable promotion rules, coupon budgets and wheel draws."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/platform'))
from verify import main
if __name__=='__main__':
 main(java_tests='PromotionPersistenceTest,BuildCompatibilityTest',evidence=ROOT/'.build/promotion/database-evidence',run_python=False,completion='PASS: isolated PostgreSQL promotion rule approval, copy-on-write revisions, wheel coupon budget, coupon calculation, duplicate draw replay and per-party daily limit')
