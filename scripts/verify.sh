#!/usr/bin/env bash
# Verify that the custom launcher is installed, is the resolved HOME, and is running.
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ADB="${ADB:-adb}"

export ANDROID_SERIAL="$HOST"

"$ADB" connect "$HOST" >/dev/null
"$ADB" wait-for-device

echo "== HOME resolver =="
"$ADB" shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME | tr -d '\r'

echo "== Nature Home package =="
"$ADB" shell pm list packages | tr -d '\r' | grep -F "com.jirigrill.naturehome" || echo "not installed"

echo "== Foreground activity =="
"$ADB" shell dumpsys activity activities | grep -E "mResumedActivity|mFocusedApp" | head -5 | tr -d '\r'

echo "== Disabled package count =="
"$ADB" shell pm list packages -d | wc -l | tr -d ' '
