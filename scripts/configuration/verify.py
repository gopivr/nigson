from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/platform"))
from verify import main

if __name__ == "__main__":
    main(
        java_tests="PlatformPersistenceTest,EventTransportTest,PaystackTransactionGuardTest",
        evidence=ROOT / ".build/configuration001/database-evidence",
        completion="PASS: native owner ledger acquisition bounds, replay/hash/rollback/event races and transaction guard; migrations and original local state preserved",
    )
