#!/usr/bin/env python3
from pathlib import Path
import concurrent.futures,hashlib,urllib.request,json
root=Path(__file__).resolve().parent.parent
items=[
("zip4j","net/lingala/zip4j/zip4j","2.11.5"),
("commons-compress","org/apache/commons/commons-compress","1.27.1"),
("commons-io","commons-io/commons-io","2.16.1"),
("commons-codec","commons-codec/commons-codec","1.17.1"),
("commons-lang3","org/apache/commons/commons-lang3","3.16.0"),
("xz","org/tukaani/xz","1.9"),
("junrar","com/github/junrar/junrar","7.5.5"),
("slf4j-api","org/slf4j/slf4j-api","1.7.36"),
("slf4j-nop","org/slf4j/slf4j-nop","1.7.36")]
def fetch(row):
 name,path,version=row
 url=f"https://repo.maven.apache.org/maven2/{path}/{version}/{name}-{version}.jar"
 file=root/"tools"/"deps"/f"{name}.jar"
 file.parent.mkdir(parents=True,exist_ok=True)
 data=urllib.request.urlopen(url,timeout=25).read()
 digest=hashlib.sha256(data).hexdigest()
 lockfile=root/"dependencies.json"
 if lockfile.exists():
  prior=next((x for x in json.loads(lockfile.read_text()) if x["name"]==name),None)
  if prior and digest!=prior["sha256"]:raise RuntimeError("Digest mismatch: "+name)
 file.write_bytes(data)
 return dict(name=name,version=version,url=url,sha256=digest,size=len(data))
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
 result=list(pool.map(fetch,items))
(root/"dependencies.json").write_text(json.dumps(result,indent=2)+"\n")
print("Downloaded and locked",len(result),"dependencies")
