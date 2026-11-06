# Third party libraries

Libraries are downloaded from Maven Central by scripts/fetch-deps.py and verified against dependencies.json on every build. Available license and notice files are bundled at assets/licenses in the APK.

| Library | Version | License | Source |
|---|---|---|---|
| zip4j | 2.11.5 | Apache-2.0 | https://github.com/srikanth-lingala/zip4j |
| Apache Commons Compress | 1.27.1 | Apache-2.0 | https://commons.apache.org/proper/commons-compress/ |
| Apache Commons IO | 2.16.1 | Apache-2.0 | https://commons.apache.org/proper/commons-io/ |
| Apache Commons Codec | 1.17.1 | Apache-2.0 | https://commons.apache.org/proper/commons-codec/ |
| Apache Commons Lang | 3.16.0 | Apache-2.0 | https://commons.apache.org/proper/commons-lang/ |
| XZ for Java | 1.9 | Public domain | https://tukaani.org/xz/java.html |
| junrar | 7.5.5 | UnRAR license | https://github.com/junrar/junrar |
| SLF4J API and NOP | 1.7.36 | MIT | https://www.slf4j.org/ |

junrar only performs extraction. Its license prohibits using its code to recreate the proprietary RAR compression algorithm. No RAR compression is implemented.

Public protocol observations were cross-checked against OpenList's drivers/lanzou and drivers/123_share at https://github.com/OpenListTeam/OpenList (2026-11-06). G pan's adapters are independently implemented Java and do not incorporate OpenList source files. These interfaces are unofficial and may change.

The project-local Java host compatibility shim is original code and is not shipped in the APK.
