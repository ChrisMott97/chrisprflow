#!/bin/bash
# Builds mac/build/Dictation.app. Quiet: prints errors only.
# Signing: uses the first "Apple Development" identity, else ad-hoc (permissions then reset every rebuild).
set -euo pipefail
cd "$(dirname "$0")"
swift build -c release -q 2>&1 | grep -E "error" || true
BIN=$(swift build -c release --show-bin-path)/Dictation
[ -x "$BIN" ] || { echo "build failed"; exit 1; }
APP=build/Dictation.app
rm -rf "$APP"; mkdir -p "$APP/Contents/MacOS"
cp "$BIN" "$APP/Contents/MacOS/Dictation"
cp Resources/Info.plist "$APP/Contents/Info.plist"
ID=$(security find-identity -v -p codesigning | grep -m1 "Apple Development" | awk '{print $2}' || true)
codesign --force --sign "${ID:--}" "$APP" 2>&1 | grep -v "replacing existing signature" || true
echo "built $APP (signed with ${ID:-ad-hoc})"
