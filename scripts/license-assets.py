from pathlib import Path
import zipfile,shutil
root=Path(__file__).resolve().parent.parent
out=root/"build"/"assets"/"licenses"
out.mkdir(parents=True,exist_ok=True)
for jar in (root/"tools"/"deps").glob("*.jar"):
 with zipfile.ZipFile(jar) as z:
  found=False
  for name in z.namelist():
   if name.endswith("/") or not any(x in Path(name).name.lower() for x in ("license","notice","copying")):continue
   if name.endswith(".class"):continue
   (out/(jar.stem+"-"+Path(name).name)).write_bytes(z.read(name));found=True
  if not found:print("License not embedded in jar:",jar.stem)
shutil.copy(root/"THIRD_PARTY.md",out/"THIRD_PARTY.md")

for source in (root/"licenses").glob("*"):shutil.copy(source,out/source.name)
