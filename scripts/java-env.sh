# Source from the project root. This affects only child Java commands.
gpan_prefix="/data/data/com.termux/files/usr"
gpan_native="$(pwd)/tools/java-native"
mkdir -p "$gpan_native"
if [ ! -f "$gpan_native/libandroid.so" ] || [ scripts/java-compat.c -nt "$gpan_native/libandroid.so" ]; then
 clang -shared -fPIC -O2 -Wl,-soname,libandroid.so -o "$gpan_native/libandroid.so" scripts/java-compat.c
fi
jrun() { env LD_LIBRARY_PATH="$gpan_native:$gpan_prefix/lib" java "$@"; }
gpan_cp=""
for gpan_jar in tools/deps/*.jar; do
 if [ -n "$gpan_cp" ]; then gpan_cp="$gpan_cp:$gpan_jar"; else gpan_cp="$gpan_jar"; fi
done
