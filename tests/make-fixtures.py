# Minimal RAR4 stored file, using the published container format.
from pathlib import Path
import struct,zlib
def header(kind,flags,data=b""):
 tail=struct.pack("<BHH",kind,flags,7+len(data))+data
 return struct.pack("<H",zlib.crc32(tail)&0xffff)+tail
data=b"RAR4 local test\n";name=b"rar-test.txt"
main=header(0x73,0,b"\0"*6)
body=struct.pack("<IIBIIBBHI",len(data),len(data),2,zlib.crc32(data),0,20,0x30,len(name),0x20)+name
rar=b"Rar!\x1a\x07\0"+main+header(0x74,0x8000,body)+data+header(0x7b,0x4000)
root=Path(__file__).resolve().parent.parent
(root/"build"/"runtime-input.rar").write_bytes(rar)
