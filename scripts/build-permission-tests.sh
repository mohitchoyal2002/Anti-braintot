#!/usr/bin/env bash
# Build a tiny native instrumentation APK against the already compiled app.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_path="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
build_tools="$sdk_path/build-tools/35.0.0"
android_jar="$sdk_path/platforms/android-35/android.jar"
out="$PWD/build/permission-tests"
rm -rf "$out"
mkdir -p "$out/classes" "$out/dex"
"$build_tools/aapt2" link -o "$out/resources.apk" -I "$android_jar" --manifest app/src/androidTest/AndroidManifest.xml
find app/src/androidTest/java -name '*.java' -print > "$out/sources.txt"
if command -v javac >/dev/null 2>&1; then
  javac -source 17 -target 17 -classpath "$android_jar:$PWD/build/sdk/classes" -d "$out/classes" @"$out/sources.txt"
else
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -classpath "$android_jar:$PWD/build/sdk/classes" -d "$out/classes" @"$out/sources.txt"
fi
find "$out/classes" -name '*.class' -print > "$out/classes.txt"
"$build_tools/d8" --lib "$android_jar" --classpath "$PWD/build/sdk/classes" --min-api 29 --output "$out/dex" @"$out/classes.txt"
python3 - "$out" <<'PY'
import pathlib, shutil, sys, zipfile
p=pathlib.Path(sys.argv[1]); shutil.copyfile(p/'resources.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for f in sorted((p/'dex').glob('*.dex')): z.write(f,f.name)
PY
"$build_tools/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
"$build_tools/apksigner" sign --ks "$PWD/debug-signing/timelens-debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out/permission-tests.apk" "$out/aligned.apk"
