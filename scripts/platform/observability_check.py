"""PLAT-002: real owner transactions, SDK spans and SNS/SQS; isolated local state."""
from pathlib import Path
import verify

if __name__ == "__main__":
 verify.main(java_tests="PlatformPersistenceTest,EventTransportTest,PaystackTransactionGuardTest,AuditPersistenceTest,SafeBoundaryTest,OtlpTransportTest",python_tests=["services/ai/tests"],evidence=Path(__file__).resolve().parents[2]/"docs/implementation/evidence/2026-10-08-PLAT-002")
