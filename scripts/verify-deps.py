from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parent.parent
for entry in json.loads((root/"dependencies.json").read_text()):
 file=root/"tools"/"deps"/(entry["name"]+".jar")
 if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest()!=entry["sha256"]:
  raise SystemExit("Missing or changed dependency: "+entry["name"]+"; run python scripts/fetch-deps.py")
print("Dependency digests verified")
