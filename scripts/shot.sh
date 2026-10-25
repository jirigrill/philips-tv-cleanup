#!/usr/bin/env bash
# Capture a screenshot and the current foreground activity.
# Usage: TV_HOST=192.168.0.44:5555 ./scripts/shot.sh 03-name
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="${1:-shot}"
ADB="${ADB:-adb}"

export ANDROID_SERIAL="$HOST"

"$ADB" connect "$HOST" >/dev/null
"$ADB" wait-for-device
mkdir -p "$ROOT/screenshots"

"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
sleep 1
"$ADB" exec-out screencap -p > "$ROOT/screenshots/$NAME.png"
{
  echo "== focused activity =="
  "$ADB" shell dumpsys activity activities | grep -E "mResumedActivity|mFocusedApp" | head -5
  echo "== disabled packages =="
  "$ADB" shell pm list packages -d
} | tr -d '\r' > "$ROOT/screenshots/$NAME.txt"

echo "Saved screenshots/$NAME.png"
