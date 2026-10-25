#!/usr/bin/env bash
# Manage Nature Home wallpapers on the TV.
#
# Images live in the app's own external files directory, so no storage
# permission is needed on the TV:
#   /sdcard/Android/data/com.jirigrill.naturehome/files/wallpapers/
#
# Usage:
#   ./scripts/wallpapers.sh push ~/Pictures/nature        # copy jpg/png/webp files
#   ./scripts/wallpapers.sh urls urls.txt                 # copy a URL list
#   ./scripts/wallpapers.sh list                          # show what the TV has
#   ./scripts/wallpapers.sh clear                         # delete pushed files and cache
#
# URL list format: one https:// image URL per line, # starts a comment.
# The launcher downloads each URL once, caches it, and falls back to the
# bundled photos when a download fails.
set -euo pipefail

HOST="${TV_HOST:-192.168.0.44:5555}"
ADB="${ADB:-adb}"
DIR="/sdcard/Android/data/com.jirigrill.naturehome/files/wallpapers"
CACHE="/sdcard/Android/data/com.jirigrill.naturehome/files/wallpaper-cache"

export ANDROID_SERIAL="$HOST"
"$ADB" connect "$HOST" >/dev/null 2>&1 || true
"$ADB" wait-for-device
"$ADB" shell "mkdir -p '$DIR'" >/dev/null

case "${1:-}" in
  push)
    src="${2:?usage: wallpapers.sh push DIR}"
    count=0
    while IFS= read -r -d '' file; do
      "$ADB" push "$file" "$DIR/" >/dev/null
      count=$((count + 1))
    done < <(find "$src" -maxdepth 1 -type f \
      \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) -print0)
    echo "Pushed $count image(s) to $DIR"
    echo "Restart Nature Home (or press HOME) to pick them up."
    ;;
  urls)
    src="${2:?usage: wallpapers.sh urls FILE}"
    "$ADB" push "$src" "$DIR/urls.txt" >/dev/null
    echo "Installed $src as $DIR/urls.txt"
    ;;
  list)
    echo "Local files:"
    "$ADB" shell "ls -1 '$DIR' 2>/dev/null" | sed 's/^/  /'
    echo "Cached downloads:"
    "$ADB" shell "ls -1 '$CACHE' 2>/dev/null | wc -l" | sed 's/^/  /'
    ;;
  clear)
    "$ADB" shell "rm -rf '$DIR'/* '$CACHE'/*" >/dev/null
    echo "Cleared pushed wallpapers and download cache."
    ;;
  *)
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
