#!/usr/bin/env bash
# Standalone SDK build, alternative to Gradle. Requires JDK 17, Android SDK 35,
# Python 3 and curl. Produces an APK signed with a local development key (generated if absent).
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
bt="$ANDROID_HOME/build-tools/35.0.0"
android="$ANDROID_HOME/platforms/android-35/android.jar"
work="$PWD/build/sdk"
mkdir -p "$work/classes" "$work/dex"
curl --fail --location --proto '=https' --proto-redir '=https' --max-time 90 \
  https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.22.1/jsoup-1.22.1.jar -o "$work/jsoup.jar"
printf '%s  %s\n' cfd3298d8720cddfb0545109ccc6ea0ef39eff7e9c40a10ad95c93a65f01c916 "$work/jsoup.jar" | sha256sum -c -
"$JAVA_HOME/bin/javac" -encoding UTF-8 -cp "$android:$work/jsoup.jar" -d "$work/classes" app/src/main/java/it/reozoz/streamclean/*.java
"$JAVA_HOME/bin/jar" cf "$work/classes.jar" -C "$work/classes" .
"$bt/aapt2" compile --dir app/src/main/res -o "$work/res.zip"
python3 - "$work/AndroidManifest.xml" <<'PY'
import sys
from pathlib import Path
s=Path('app/src/main/AndroidManifest.xml').read_text()
Path(sys.argv[1]).write_text(s.replace('<manifest ', '<manifest package="it.reozoz.streamclean" ', 1))
PY
"$bt/aapt2" link -o "$work/unsigned.apk" -I "$android" --manifest "$work/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 35 --version-code 11 --version-name 0.7.0 "$work/res.zip"
"$JAVA_HOME/bin/java" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$android" --output "$work/dex" "$work/classes.jar" "$work/jsoup.jar"
python3 - "$work" <<'PY'
import sys,zipfile
from pathlib import Path
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for dex in (p/'dex').glob('*.dex'):z.write(dex,dex.name)
PY
"$bt/zipalign" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
if [[ ! -f debug.keystore ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US' -noprompt
fi
"$bt/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out "$work/StreamClean.apk" "$work/aligned.apk"
"$bt/apksigner" verify --verbose "$work/StreamClean.apk"
printf 'APK: %s\n' "$work/StreamClean.apk"
