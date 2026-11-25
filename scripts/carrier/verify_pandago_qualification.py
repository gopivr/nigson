"""Isolated PostgreSQL and loopback HTTP tests for PandaGo notes/pickup feedback. No stage/live bookings."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/platform"))
from verify import main

if __name__ == "__main__":
    main(java_tests="PickupCodeTest,PandagoBookingPersistenceTest,PandagoAdapterTest,PandagoCommerceClientTest",
         evidence=ROOT / ".build/pandago-qualification004/database-evidence", run_python=False,
         completion="PASS: pickup code derivation, immutable booking context, recipient-only notes and durable booking safety; no real PandaGo orders created")
