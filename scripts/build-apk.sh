#!/usr/bin/env bash
# SDK-only build for this dependency-free Android app. JDK 17+, Android SDK 35.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_path="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_path" ]]; then printf '%s\n' 'Set ANDROID_SDK_ROOT to your Android SDK.' >&2; exit 1; fi
build_tools="$sdk_path/build-tools/35.0.0"
android_jar="$sdk_path/platforms/android-35/android.jar"
out="$PWD/build/sdk"
mkdir -p "$out/generated" "$out/classes" "$out/dex" "$PWD/debug-signing"
python3 - "$out" <<'PY'
import pathlib, sys, xml.etree.ElementTree as E
E.register_namespace('android','http://schemas.android.com/apk/res/android'); E.register_namespace('tools','http://schemas.android.com/tools')
t=E.parse('app/src/main/AndroidManifest.xml'); r=t.getroot(); r.set('package','work.timelens.app')
r.set('{http://schemas.android.com/apk/res/android}versionCode','1'); r.set('{http://schemas.android.com/apk/res/android}versionName','0.1.0')
r.find('application').set('{http://schemas.android.com/apk/res/android}debuggable','true')
t.write(pathlib.Path(sys.argv[1])/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
PY
"$build_tools/aapt2" compile --dir app/src/main/res -o "$out/resources.zip"
"$build_tools/aapt2" link -o "$out/resources.apk" -I "$android_jar" --manifest "$out/AndroidManifest.xml" --java "$out/generated" --min-sdk-version 29 --target-sdk-version 35 "$out/resources.zip"
find app/src/main/java "$out/generated" -name '*.java' -print > "$out/sources.txt"
if command -v javac >/dev/null 2>&1; then
  javac -source 17 -target 17 -classpath "$android_jar" -d "$out/classes" @"$out/sources.txt"
else
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -classpath "$android_jar" -d "$out/classes" @"$out/sources.txt"
fi
find "$out/classes" -name '*.class' -print > "$out/classes.txt"
"$build_tools/d8" --lib "$android_jar" --min-api 29 --output "$out/dex" @"$out/classes.txt"
python3 - "$out" <<'PY'
import pathlib, shutil, sys, zipfile
p=pathlib.Path(sys.argv[1]); shutil.copyfile(p/'resources.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for f in sorted((p/'dex').glob('*.dex')): z.write(f,f.name)
PY
"$build_tools/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
key_path="$PWD/debug-signing/timelens-debug.keystore"
if [[ ! -f "$key_path" ]]; then
  keytool -genkeypair -noprompt -keystore "$key_path" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=TimeLens Debug, O=Personal Testing, C=IN'
fi
"$build_tools/apksigner" sign --ks "$key_path" --ks-pass pass:android --key-pass pass:android --out "$out/TimeLens-0.1.0-debug.apk" "$out/aligned.apk"
"$build_tools/apksigner" verify --verbose "$out/TimeLens-0.1.0-debug.apk"
printf 'APK: %s\n' "$out/TimeLens-0.1.0-debug.apk"
