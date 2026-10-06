"""Exact registered event/producer allowlists, shared by local bootstrap and Terraform."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
QUEUES={'integration':'integration-work','experience':'experience-projections','commerce':'commerce-domain','ai':'ai-ingestion'}
def filters(owner,phase=5):
 registry=json.loads((ROOT/'contracts/events/registry.json').read_text())
 branches=[{'producer':[producer],'event_type':sorted(name for name,item in registry.items() if producer in item['producers'] and owner in item['consumers'] and item['phase']<=phase)} for producer in ('commerce','experience') if any(producer in item['producers'] and owner in item['consumers'] and item['phase']<=phase for item in registry.values())]
 # Closed envelopes always have event_type; AWS rejects an empty allowlist.
 if not branches:return {'event_type':[{'exists':False}]}
 return branches[0] if len(branches)==1 else {'$or':branches}

if __name__=='__main__':
 print(json.dumps({str(phase):{QUEUES[o]:filters(o,phase) for o in QUEUES if o!='ai' or phase==5} for phase in range(1,6)},indent=2))
