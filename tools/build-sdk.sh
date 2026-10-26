#!/usr/bin/env bash
# Compatibility entry point. Cast/AndroidX require Gradle's AAR resource/manifest merge.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon testDebugUnitTest assembleDebug
mkdir -p build/sdk
cp app/build/outputs/apk/debug/app-debug.apk build/sdk/StreamClean.apk
printf 'APK: %s\n' "$PWD/build/sdk/StreamClean.apk"
