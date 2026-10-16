from pathlib import Path
import json,urllib.request
keys=json.loads(Path("docs/dependencies/java-lock.json").read_text())["artifacts_sha256"]
queries=[]
for key in keys:
 g,a,t,c,v=key.split(":")
 queries.append({"package":{"ecosystem":"Maven","name":g+":"+a},"version":v})
body=json.dumps({"queries":queries}).encode()
Path(".build/carrier-account-osv-request.json").write_bytes(body)
req=urllib.request.Request("https://api.osv.dev/v1/querybatch",data=body,headers={"Content-Type":"application/json"})
with urllib.request.urlopen(req,timeout=45) as response: result=json.load(response)
Path(".build/carrier-account-osv-response.json").write_text(json.dumps(result,indent=2)+"\n")
assert len(result["results"])==len(queries)
count=sum(len(item.get("vulns",[])) for item in result["results"])
print(f"OSV: {len(queries)} public Maven coordinates; {count} findings")
assert count==0
