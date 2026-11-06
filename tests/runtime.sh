#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
. scripts/java-env.sh
mkdir -p build/runtime-classes build/runtime-dex
if [ -f build/runtime-dex/classes.dex ]; then chmod 600 build/runtime-dex/classes.dex;fi
jrun --module jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -classpath "build/classes:$gpan_cp" -d build/runtime-classes tests/RuntimeTest.java
jrun --module jdk.jartool/sun.tools.jar.Main cf build/runtime-classes.jar -C build/runtime-classes .
jrun -Xmx512m -cp ../pulse-player/tools/r8.jar com.android.tools.r8.D8 --lib ../pulse-player/tools/android.jar --classpath build/classes.jar --min-api 26 --output build/runtime-dex build/runtime-classes.jar
python tests/make-fixtures.py
gpan_url="https://repo.maven.apache.org/maven2/net/lingala/zip4j/zip4j/2.11.5/zip4j-2.11.5.jar"
gpan_hash=$(python -c 'import json; print(next(x["sha256"] for x in json.load(open("dependencies.json")) if x["name"]=="zip4j"))')
if [ -f build/runtime.apk ];then chmod 600 build/runtime.apk;fi
cp build/G-pan-0.1.apk build/runtime.apk
chmod 400 build/runtime-dex/classes.dex build/runtime.apk
CLASSPATH="$PWD/build/runtime-dex/classes.dex:$PWD/build/runtime.apk" /system/bin/app_process /system/bin RuntimeTest "$(pwd)/build/art-fixtures" "$gpan_url" "$gpan_hash" "$PWD/build/runtime-input.rar"
