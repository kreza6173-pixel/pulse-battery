#!/system/bin/sh
# pulse-battery — Action button summary. Read-only: prints current state.

DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/lib.sh"

level="$(dumpsys battery 2>/dev/null | grep -m1 'level:' | sed 's/.*level: *//')"
status_num="$(dumpsys battery 2>/dev/null | grep -m1 'status:' | sed 's/.*status: *//')"
case "$status_num" in
  2) status_label="charging" ;;
  3) status_label="discharging" ;;
  4) status_label="not charging" ;;
  5) status_label="full" ;;
  *) status_label="unknown" ;;
esac

if [ -d "$VAULT_ROOT" ]; then
  vault_count="$(find "$VAULT_ROOT" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | grep -c .)"
else
  vault_count=0
fi

uid_line="$(id 2>/dev/null)"
case "$uid_line" in
  *"uid=0(root)"*) mode_label="root" ;;
  *) mode_label="adb/shell" ;;
esac

echo "Battery: ${level:-?}% ($status_label)  |  Mode: $mode_label"
echo "Vaults on device: $vault_count"
