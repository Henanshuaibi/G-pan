#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
. scripts/java-env.sh
mkdir -p build/test-classes
jrun --module jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -cp "$gpan_cp:tools/json.jar" -d build/test-classes src/cn/g/pan/Links.java src/cn/g/pan/Net.java src/cn/g/pan/DownloadEngine.java src/cn/g/pan/ArchiveEngine.java src/cn/g/pan/Resolver.java tests/CoreTest.java
jrun -Xmx512m -cp "build/test-classes:$gpan_cp:tools/json.jar" CoreTest
