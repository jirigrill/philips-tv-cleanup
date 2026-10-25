#!/usr/bin/env bash
# Disable curated package lists for the current user (reversible; nothing is uninstalled).
# Writes every package this script actually disables to backup/disabled-by-us.txt.
#
# Usage:
#   ./scripts/debloat.sh                              # apply debloat/*.txt in order
#   ./scripts/debloat.sh debloat/01-philips-telemetry.txt
#
# Packages that were already disabled before this project are skipped and never
# recorded, so restore.sh cannot accidentally re-enable them.
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RECORD="$ROOT/backup/disabled-by-us.txt"
ADB="${ADB:-adb}"

export ANDROID_SERIAL="$HOST"

if [[ $# -gt 0 ]]; then
  LISTS=("$@")
else
  LISTS=("$ROOT"/debloat/*.txt)
fi

for list in "${LISTS[@]}"; do
  if [[ ! -f "$list" ]]; then
    echo "No list at $list" >&2
    exit 1
  fi
done

if [[ ! -f "$ROOT/backup/home-before.txt" ]]; then
  echo "Run scripts/backup.sh first; refusing to change anything without a backup." >&2
  exit 1
fi

"$ADB" connect "$HOST" >/dev/null 2>&1 || true
"$ADB" wait-for-device

# Never disable the stock launcher unless our launcher is already HOME.
if grep -hqE '^[[:space:]]*com\.google\.android\.tvlauncher([[:space:]]|$)' "${LISTS[@]}"; then
  CURRENT_HOME="$("$ADB" shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
  if [[ "$CURRENT_HOME" != *"com.jirigrill.naturehome"* ]]; then
    echo "Stock launcher is in the list but Nature Home is not HOME yet ($CURRENT_HOME)." >&2
    echo "Install it and run: adb shell cmd package set-home-activity com.jirigrill.naturehome/.MainActivity" >&2
    exit 1
  fi
fi

if [[ ! -f "$RECORD" ]]; then
  : > "$RECORD"
fi

echo "==> Reading current package state"
INSTALLED="$("$ADB" shell pm list packages -u </dev/null | tr -d '\r' | sed 's/^package://' | sort -u)"
DISABLED="$("$ADB" shell pm list packages -d </dev/null | tr -d '\r' | sed 's/^package://' | sort -u)"
export INSTALLED DISABLED

echo "==> Disabling packages"
for list in "${LISTS[@]}"; do
  echo "--- $list"
  # fd 3 keeps the list out of adb's stdin.
  while IFS= read -r raw <&3; do
    pkg="$(echo "$raw" | sed -e 's/#.*//' -e 's/[[:space:]]//g')"
    [[ -z "$pkg" ]] && continue

    if ! grep -qxF "$pkg" <<<"$INSTALLED"; then
      echo "  skip (not installed): $pkg"
      continue
    fi
    if grep -qxF "$pkg" <<<"$DISABLED"; then
      # Either already disabled by us (already recorded) or disabled by the factory.
      # Never add it to the record here: restore must leave factory state alone.
      echo "  already disabled: $pkg"
      continue
    fi

    echo "  disable: $pkg"
    if "$ADB" shell pm disable-user --user 0 "$pkg" </dev/null >/dev/null; then
      grep -qxF "$pkg" "$RECORD" || echo "$pkg" >> "$RECORD"
      DISABLED="$DISABLED"$'\n'"$pkg"
    else
      echo "  FAILED: $pkg" >&2
    fi
  done 3< "$list"
done

echo "==> Waiting for package restrictions to persist"
sleep 12

echo "==> Verifying disabled packages"
"$ADB" shell pm list packages -d | tr -d '\r' | sed 's/^package://' | sort > "$ROOT/backup/packages-disabled-after.txt"
missing=0
while IFS= read -r pkg; do
  [[ -z "$pkg" ]] && continue
  if ! grep -qxF "$pkg" "$ROOT/backup/packages-disabled-after.txt"; then
    echo "  NOT DISABLED: $pkg" >&2
    missing=$((missing + 1))
  fi
done < "$RECORD"
if [[ "$missing" -gt 0 ]]; then
  echo "==> $missing package(s) did not stay disabled. Re-run or check the TV log." >&2
  exit 2
fi
echo "==> All recorded packages are disabled."
