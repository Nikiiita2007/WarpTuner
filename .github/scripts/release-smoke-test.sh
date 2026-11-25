#!/usr/bin/env bash
# Checks the release package, then installs the minified APK re-signed with
# the debug key and verifies startup. Does not test production signing or VPN traffic.
set -euo pipefail

PKG=com.example.warptuner
UNSIGNED=$(ls app/build/outputs/apk/release/*.apk | head -n1)
APKSIGNER=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n1)
APKANALYZER="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
MAPPING=app/build/outputs/mapping/release/mapping.txt

# Inspect the actual APK manifest rather than relying on source-set placement.
"$APKANALYZER" manifest print "$UNSIGNED" > smoke-release-manifest.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET

root = ET.parse('smoke-release-manifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
app = root.find('application')
if root.get('package') != 'com.example.warptuner' or app is None:
    raise SystemExit('Unexpected release package or missing application')
for flag in ('debuggable', 'testOnly'):
    if app.get(android + flag, 'false').lower() != 'false':
        raise SystemExit('Release application must not enable ' + flag)
for element in root.iter():
    if any('TlsProbe' in value for value in element.attrib.values()):
        raise SystemExit('TLS debug diagnostic found in release manifest')
PY

# Mapping retains original names even when the packaged classes are obfuscated.
if [ ! -s "$MAPPING" ]; then
  echo "Missing R8 mapping: cannot check release diagnostic class exclusion" >&2
  exit 1
fi
python3 - "$MAPPING" <<'PY'
import sys
from pathlib import Path

mapping = Path(sys.argv[1]).read_text(encoding='utf-8')
if 'com.example.warptuner.TlsProbe' in mapping:
    raise SystemExit('TLS debug diagnostic appears in release R8 mapping')
PY
echo "Release manifest and TLS diagnostic mapping checks passed"

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
