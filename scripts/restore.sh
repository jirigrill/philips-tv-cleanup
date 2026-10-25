#!/usr/bin/env bash
# Restore the TV to its pre-change state.
# Re-enables every package recorded in backup/disabled-by-us.txt, restores the
# original HOME activity, and optionally removes the Nature Home launcher.
#
# Usage: TV_HOST=192.168.0.44:5555 ./scripts/restore.sh [--remove-launcher]
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RECORD="$ROOT/backup/disabled-by-us.txt"
HOME_FILE="$ROOT/backup/home-before.txt"
ADB="${ADB:-adb}"
REMOVE_LAUNCHER=0

if [[ "${1:-}" == "--remove-launcher" ]]; then
  REMOVE_LAUNCHER=1
fi

export ANDROID_SERIAL="$HOST"

"$ADB" connect "$HOST" >/dev/null
"$ADB" wait-for-device

echo "==> Re-enabling packages disabled by this project"
if [[ -f "$RECORD" ]]; then
  # Restore the stock launcher first so there is always a HOME.
  if grep -qxF "com.google.android.tvlauncher" "$RECORD"; then
    "$ADB" shell pm enable com.google.android.tvlauncher >/dev/null || true
  fi
  # fd 3 keeps the record out of adb's stdin.
  while IFS= read -r pkg <&3; do
    [[ -z "$pkg" ]] && continue
    echo "  enable: $pkg"
    "$ADB" shell pm enable "$pkg" </dev/null >/dev/null || echo "  (could not enable $pkg)"
  done 3< "$RECORD"
else
  echo "  no backup/disabled-by-us.txt found; nothing recorded" >&2
fi

sleep 5

echo "==> Restoring original HOME activity"
STOCK_HOME=""
if [[ -f "$HOME_FILE" ]]; then
  STOCK_HOME="$(grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' "$HOME_FILE" | tail -1 || true)"
fi
if [[ -z "$STOCK_HOME" ]]; then
  STOCK_HOME="com.google.android.tvlauncher/.MainActivity"
fi
sleep 3
echo "  HOME -> $STOCK_HOME"
"$ADB" shell cmd package set-home-activity --user 0 "$STOCK_HOME" || {
  echo "  set-home-activity failed; pressing HOME instead" >&2
}
"$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true

sleep 5
RESOLVED_HOME="$("$ADB" shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
echo "  HOME now: $RESOLVED_HOME"
if [[ "$RESOLVED_HOME" == *"com.jirigrill.naturehome"* ]]; then
  echo "  WARNING: Nature Home is still the resolved HOME. Reboot the TV once." >&2
fi

if [[ "$REMOVE_LAUNCHER" -eq 1 ]]; then
  echo "==> Removing Nature Home launcher"
  "$ADB" uninstall com.jirigrill.naturehome || true
fi

echo "==> Waiting for changes to settle"
sleep 8
"$ADB" shell pm list packages -d | tr -d '\r' > "$ROOT/backup/packages-disabled-after-restore.txt"

echo "==> Taking restore screenshot"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
sleep 2
mkdir -p "$ROOT/screenshots"
"$ADB" exec-out screencap -p > "$ROOT/screenshots/99-restored.png"

echo "==> Restore complete. Turn off USB/network debugging on the TV when you are done."
