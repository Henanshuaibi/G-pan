#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")"
gpan_sdk=$(printenv GPAN_SDK_DIR || echo ../pulse-player/tools)
gpan_key=$(printenv GPAN_KEYSTORE || echo tools/g-pan.keystore)
gpan_password=$(printenv GPAN_SIGNING_PASSWORD_FILE || echo tools/signing-password)
for gpan_file in "$gpan_sdk/android.jar" "$gpan_sdk/android-res.jar" "$gpan_sdk/r8.jar" "$gpan_key" "$gpan_password" signing-certificate.sha256; do
 if [ ! -f "$gpan_file" ]; then echo "缺少构建文件：$gpan_file" >&2;exit 1;fi
done
. scripts/java-env.sh
python scripts/verify-deps.py
gpan_cert=$(jrun --module java.base/sun.security.tools.keytool.Main -exportcert -keystore "$gpan_key" -storepass:file "$gpan_password" -alias gpan 2>/dev/null | sha256sum | cut -d ' ' -f 1)
if [ "$gpan_cert" != "$(cat signing-certificate.sha256)" ]; then echo '签名指纹不匹配，停止构建。' >&2;exit 1;fi
mkdir -p build/generated build/classes build/dex build/assets/licenses
find build/classes -type f -name '*.class' -delete
python scripts/license-assets.py
aapt2 compile --dir res -o build/res.zip
aapt2 link -I "$gpan_sdk/android-res.jar" --manifest AndroidManifest.xml --java build/generated -A build/assets -o build/resources.apk build/res.zip
jrun --module jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -classpath "$gpan_sdk/android.jar:$gpan_cp" -d build/classes src/cn/g/pan/*.java build/generated/cn/g/pan/R.java
jrun --module jdk.jartool/sun.tools.jar.Main cf build/classes.jar -C build/classes .
jrun -Xmx768m -cp "$gpan_sdk/r8.jar" com.android.tools.r8.D8 --lib "$gpan_sdk/android.jar" --min-api 26 --output build/dex build/classes.jar tools/deps/*.jar
cp build/resources.apk build/unsigned.apk
for gpan_dex in build/dex/*.dex; do jrun --module jdk.jartool/sun.tools.jar.Main uf build/unsigned.apk -C build/dex "$(basename "$gpan_dex")";done
zipalign -f 4 build/unsigned.apk build/aligned.apk
jrun -jar "$gpan_prefix/share/java/apksigner.jar" sign --ks "$gpan_key" --ks-pass "file:$gpan_password" --out build/G-pan-0.1.apk build/aligned.apk
jrun -jar "$gpan_prefix/share/java/apksigner.jar" verify --verbose --print-certs build/G-pan-0.1.apk
zipalign -c 4 build/G-pan-0.1.apk
