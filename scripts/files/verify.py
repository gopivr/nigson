"""Isolated owned file lifecycle with actual TLS/SigV4 stateful storage HTTP."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main
from storage_mock import fixture

if __name__ == '__main__':
    main(java_tests='FileConfigurationTest,FileValuesTest,FilePersistenceTest,BuildCompatibilityTest,SecurityAssemblyTest',
         evidence=ROOT / '.build/files001/database-evidence', run_python=False,
         java_environment=fixture,
         completion='PASS: owned file lifecycle, TLS/SigV4 storage, authorization/quarantine/checksum/scan binding/uncertainty/rollback/races; owner migrations and original local state preserved; genuine storage/scanner/cloud qualification not implied')
