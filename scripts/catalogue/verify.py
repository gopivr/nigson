"""Isolated owned catalogue persistence/contract/race qualification."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/platform'))
from verify import main

if __name__ == '__main__':
    main(java_tests='CatalogueReadLoadTest,ProductPersistenceTest,TaxonomyPersistenceTest,CatalogueValuesTest,BuildCompatibilityTest,SecurityAssemblyTest',
         evidence=ROOT / '.build/catalogue001/database-evidence', run_python=False,
         completion='PASS: native catalogue product/SKU/publication/search/visibility/HTTP/cache/ownership/audit/outbox/taxonomy races and representative source-read load; migrations and original local state preserved; staging30m/provider release acceptance not implied')
