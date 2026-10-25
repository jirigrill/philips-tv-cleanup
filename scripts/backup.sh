#!/usr/bin/env bash
# Back up the TV's package state and settings before any change.
# Usage: TV_HOST=192.168.0.44:5555 ./scripts/backup.sh
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP="$ROOT/backup"
SHOTS="$ROOT/screenshots"
ADB="${ADB:-adb}"

export ANDROID_SERIAL="$HOST"

mkdir -p "$BACKUP" "$SHOTS"

echo "==> Connecting to $HOST"
"$ADB" connect "$HOST" >/dev/null
"$ADB" wait-for-device

echo "==> Saving package inventory"
"$ADB" shell pm list packages -f -u | tr -d '\r' > "$BACKUP/packages-all.txt"
"$ADB" shell pm list packages -e | tr -d '\r' > "$BACKUP/packages-enabled-before.txt"
"$ADB" shell pm list packages -d | tr -d '\r' > "$BACKUP/packages-disabled-before.txt"
"$ADB" shell pm list packages -s | tr -d '\r' > "$BACKUP/packages-system.txt"
"$ADB" shell pm list packages -3 | tr -d '\r' > "$BACKUP/packages-thirdparty.txt"

echo "==> Saving package details"
"$ADB" shell dumpsys package > "$BACKUP/dumpsys-package.txt"
"$ADB" shell getprop > "$BACKUP/getprop.txt"
"$ADB" shell wm size > "$BACKUP/display.txt"
"$ADB" shell wm density >> "$BACKUP/display.txt"
"$ADB" shell df -h > "$BACKUP/disk.txt"
"$ADB" shell ps -A > "$BACKUP/processes-before.txt"

echo "==> Saving settings"
for ns in global secure system; do
  "$ADB" shell settings list "$ns" | tr -d '\r' > "$BACKUP/settings-$ns.txt" || true
done

echo "==> Saving current HOME activity"
"$ADB" shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME | tr -d '\r' > "$BACKUP/home-before.txt"
cat "$BACKUP/home-before.txt"

echo "==> Saving home screen screenshot"
"$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
sleep 2
"$ADB" exec-out screencap -p > "$SHOTS/00-before.png"

echo "==> Done. Backup in $BACKUP"
