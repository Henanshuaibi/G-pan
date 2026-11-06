from pathlib import Path
import hashlib,json,re,shutil,zipfile
root=Path(__file__).resolve().parent.parent
release=root.parent
top=[".gitignore","AGENTS.md","AndroidManifest.xml","build.sh","dependencies.json","signing-certificate.sha256","README.md","THIRD_PARTY.md","VALIDATION.md","RELEASE.md","SOURCE_AUDIT.md"]
files=[root/p for p in top]
for folder in ("src","res","scripts","tests","licenses"):
 files.extend(p for p in (root/folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
files=sorted(files)
password=(root/"tools"/"signing-password").read_bytes().strip()
patterns=[
 re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
 re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})"),
 re.compile(rb"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{30,}"),
 re.compile(rb"/data/data/com[.]termux/files/home/(?:[.]ssh|[.]config|[.]codex|account[0-9])")]
def scan(name,data):
 if password and password in data:raise RuntimeError("Signing password found in "+name)
 for pattern in patterns:
  if pattern.search(data):raise RuntimeError("Sensitive pattern found in "+name)
for p in files:
 scan(str(p.relative_to(root)),p.read_bytes())
 if p.suffix.lower() in (".keystore",".jks",".apk",".jar",".part") or p.name.startswith(".env"):raise RuntimeError("Disallowed source file: "+p.name)
apk=root/"build"/"G-pan-0.1.apk"
with zipfile.ZipFile(apk) as z:
 for info in z.infolist():
  if info.filename.lower().endswith((".keystore",".jks",".env",".pem")):raise RuntimeError("Disallowed APK member")
  scan("APK:"+info.filename,z.read(info))
source=release/"G-pan-0.1-source.zip"
with zipfile.ZipFile(source,"w",zipfile.ZIP_DEFLATED) as z:
 for p in files:z.write(p,"G-pan-0.1/"+str(p.relative_to(root)))
with zipfile.ZipFile(source) as z:
 for info in z.infolist():scan("ZIP:"+info.filename,z.read(info))
target=release/"G-pan-0.1.apk";shutil.copyfile(apk,target)
manifest={}
for path in (target,source):
 data=path.read_bytes();manifest[path.name]={"size":len(data),"sha256":hashlib.sha256(data).hexdigest()}
(release/"G-pan-0.1.sha256").write_text("".join(f"{v['sha256']}  {name}\n" for name,v in manifest.items()))
report={"source_files":len(files),"source_bytes":sum(p.stat().st_size for p in files),"sensitive_findings":0,"signing_material_excluded":True,"assets":manifest}
(root/"build"/"source-audit.json").write_text(json.dumps(report,indent=2)+"\n")
(root/"build"/"source-files.txt").write_text("".join(str(p.relative_to(root))+"\n" for p in files))
print(json.dumps(report,indent=2))
