#!/usr/bin/env bash
# Installs the minified release APK (re-signed with the debug key) on the
# running emulator, launches it and fails if the process crashes.
# Catches R8 / keep-rule problems that unit and instrumented tests miss.
set -euo pipefail

PKG=com.example.warptuner
UNSIGNED=$(ls app/build/outputs/apk/release/*.apk | head -n1)
APKSIGNER=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out smoke-release.apk "$UNSIGNED"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r smoke-release.apk
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 8
PID=$(adb shell pidof "$PKG" || true)
adb logcat -d > smoke-logcat.txt
if [ -z "$PID" ] || grep -q "FATAL EXCEPTION" smoke-logcat.txt; then
  echo "Release build crashed on start"; grep -A30 "FATAL EXCEPTION" smoke-logcat.txt || true
  exit 1
fi
echo "Release smoke test passed (pid $PID)"
