import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]

class ContentKnowledgeContract(unittest.TestCase):
    def test_private_api_routes_exact_workload_scopes_and_closed_dtos(self):
        spec=json.loads((ROOT/'contracts/openapi/experience.yaml').read_text())
        paths=spec['paths']
        validate=paths['/internal/v1/content/revision-validation']['post']
        read=paths['/internal/v1/content/knowledge/{id}']['get']
        self.assertEqual(validate['operationId'],'API-125')
        self.assertEqual(validate['security'],[{'workload':['knowledge.validate']}])
        self.assertEqual(read['operationId'],'API-126')
        self.assertEqual(read['security'],[{'workload':['knowledge.read']}])
        self.assertEqual(validate['responses']['200']['headers']['Cache-Control']['schema'],{'const':'no-store'})
        self.assertEqual(read['responses']['200']['headers']['Cache-Control']['schema'],{'const':'no-store'})
        schemas=spec['components']['schemas']
        for name in ['ContentKnowledgeRevisionRequest','ContentKnowledgeValidationInput','ContentKnowledgeRevisionStatus','ContentKnowledgeValidationResult','ContentKnowledgeDTO','ContentBody','SeoDTO']:
            self.assertFalse(schemas[name]['additionalProperties'],name)
        self.assertEqual(schemas['ContentKnowledgeValidationInput']['properties']['items']['maxItems'],20)
        self.assertEqual(set(schemas['ContentKnowledgeDTO']['properties']),{'id','type','slug','locale','revision','title','body','metadata','publishedAt','approved','checksum','visibility'})
        self.assertEqual(schemas['ContentKnowledgeDTO']['properties']['approved'],{'const':True})
        self.assertEqual(schemas['ContentKnowledgeDTO']['properties']['visibility'],{'const':'PUBLIC'})

    def test_route_registry_binds_exact_ai_client_phase_and_scopes(self):
        routes=json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        by_api={route['api']:route for route in routes}
        self.assertEqual({key:by_api[key] for key in ['API-125','API-126']},{
            'API-125':{'api':'API-125','method':'POST','path':'/internal/v1/content/revision-validation','owner':'experience','client':'nigson-ai','scope':'knowledge.validate','phase':5},
            'API-126':{'api':'API-126','method':'GET','path':'/internal/v1/content/knowledge/{id}','owner':'experience','client':'nigson-ai','scope':'knowledge.read','phase':5}})

if __name__=='__main__': unittest.main()
