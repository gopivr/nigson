#!/usr/bin/env python3
"""Seed explicitly synthetic documents for the user-approved local BP3 workflow.

These are local test fixtures, not real business verification or scanner/provider
qualification. Bytes exist in LocalStack; browser sample is intentionally generic.
"""
import argparse,hashlib,json,os,re,uuid
from pathlib import Path
from seed_demo import ROOT,uid,literal
from provision_demo_user import command


def sample_pdf():
 lines=['LOCAL BP3 TEST SAMPLE - NOT A REAL BUSINESS REGISTRATION','Demo Nigson distributor application','Synthetic document for local submission and review testing only.','This document establishes no real identity, registration or provider approval.']
 text='BT /F1 11 Tf 40 790 Td '+ ' 0 -22 Td '.join('('+line+') Tj' for line in lines)+' ET'
 objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>','<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',f'<< /Length {len(text.encode())} >>\nstream\n{text}\nendstream']
 pdf=b'%PDF-1.4\n';offsets=[0]
 for i,body in enumerate(objects,1):offsets.append(len(pdf));pdf+=f'{i} 0 obj\n{body}\nendobj\n'.encode()
 start=len(pdf);pdf+=b'xref\n0 6\n0000000000 65535 f \n'
 pdf+=''.join(f'{offset:010d} 00000 n \n' for offset in offsets[1:]).encode()
 return pdf+f'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{start}\n%%EOF\n'.encode()


def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--batch',default='initial');parser.add_argument('--all-local-customers',action='store_true');args=parser.parse_args()
 if not re.fullmatch('[a-z0-9-]{1,40}',args.batch):raise ValueError('Invalid local fixture batch')
 if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):raise ValueError('Local Docker required')
 if not json.loads(command('context','inspect'))[0]['Endpoints']['docker']['Host'].startswith('unix://'):raise ValueError('Local Docker required')
 config=json.loads(command('compose','--profile','app','config','--format','json'))
 env=config['services']['commerce']['environment']
 if config.get('name')!='nigson-local' or env.get('APP_ENV')!='local' or env.get('NIGSON_PRODUCT_PHASE')!='3':raise ValueError('User-approved local BP3 project required')
 fixtures=json.loads((ROOT/'tests/fixtures/local-identities.json').read_text())['subjects']
 subjects=[x['id'] for x in fixtures if x['username'] in ('admin','retailA','retailB')]
 state=ROOT/'.build/demo-validator-identity.json'
 if state.exists():subjects.append(json.loads(state.read_text())['id'])
 sqlbase=('compose','exec','-T','postgres','psql','-X','-qAt','-U','nigson_bootstrap','-d','nigson','-v','ON_ERROR_STOP=1')
 parties=command(*sqlbase,'-c',f"SELECT id FROM commerce.party WHERE identity_issuer={literal(env['NIGSON_OIDC_ISSUER'])} AND identity_subject IN ({','.join(literal(s) for s in subjects)}) AND status='ACTIVE' ORDER BY id").splitlines()
 if args.all_local_customers:
  parties=command(*sqlbase,'-c',f"SELECT p.id FROM commerce.party p JOIN commerce.customer_profile c ON c.party_id=p.id WHERE p.identity_issuer={literal(env['NIGSON_OIDC_ISSUER'])} AND p.status='ACTIVE' ORDER BY p.id").splitlines()
 if not parties:raise ValueError('Local fixture accounts must sign in first')
 data=sample_pdf();checksum=hashlib.sha256(data).hexdigest();bucket='nigson-local-distributor-documents'
 try:command('compose','exec','-T','localstack','awslocal','s3api','head-bucket','--bucket',bucket)
 except RuntimeError:command('compose','exec','-T','localstack','awslocal','s3api','create-bucket','--bucket',bucket)
 # ASCII PDF can be streamed without a host filesystem mount or credentials.
 command('compose','exec','-T','localstack','sh','-c','cat > /tmp/nigson-local-distributor.pdf',input=data.decode())
 statements=['BEGIN;'];mapping={};probes={}
 for raw in parties:
  party=str(uuid.UUID(raw))
  for kind in ('sample','probe'):
   file=uid('bp3-document/'+args.batch+'/'+kind+'/'+party);key='local/experience/distributor_proof/'+file+'/0/pdf'
   command('compose','exec','-T','localstack','awslocal','s3api','put-object','--bucket',bucket,'--key',key,'--body','/tmp/nigson-local-distributor.pdf','--content-type','application/pdf')
   statements.append(f"INSERT INTO experience.file_asset(id,created_by,updated_by,owner_party_id,object_key,purpose,mime_type,byte_size,checksum_sha256,classification,scan_status,scan_result) VALUES ({literal(file)},'local-bp3-test-fixture','local-bp3-test-fixture',{literal(party)},{literal(key)},'DISTRIBUTOR_PROOF','application/pdf',{len(data)},{literal(checksum)},'PRIVATE','CLEAN','EXPLICIT_LOCAL_TEST_FIXTURE_NOT_PROVIDER_QUALIFIED') ON CONFLICT(id) DO NOTHING;")
   (mapping if kind=='sample' else probes)[party]={'id':file,'url':'/local-distributor-sample.pdf'}
 statements.append('COMMIT;');command(*sqlbase,input='\n'.join(statements))
 # Publish only unused sample IDs; ownership remains enforced by the real API.
 free=set(command(*sqlbase,'-c',"SELECT id FROM experience.file_asset WHERE created_by='local-bp3-test-fixture' AND linked_resource_id IS NULL AND scan_status='CLEAN'").splitlines())
 mapping={p:d for p,d in mapping.items() if d['id'] in free}
 market=json.loads(command(*sqlbase,'-c',"SELECT json_build_object('id',id,'code',code,'currency',currency_code) FROM commerce.market WHERE code='NG' AND enabled"))
 public=ROOT.parent/'nigson-micro-frontend/projects/shell/public'
 (public/'local-distributor-sample.pdf').write_bytes(data)
 (public/'local-distributor-fixtures.json').write_text(json.dumps({'localOnly':True,'phase':3,'documents':mapping,'market':market},indent=2)+'\n')
 (ROOT/'.build/local-distributor-documents.json').write_text(json.dumps({'samples':mapping,'probes':probes,'bucket':bucket,'market':market},indent=2)+'\n')
 print(f'PASS: {len(parties)*2} explicit local test document fixtures with real PDF bytes; {len(mapping)} sample documents available to their owners.')


if __name__=='__main__':main()
