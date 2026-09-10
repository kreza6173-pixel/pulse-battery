#!/system/bin/sh
# pulse-battery — shared shell helpers.
# Sourced by action.sh. The WebUI does its own quoting in JS (shq in
# webui/script.js) since it talks to the device directly through
# window.Shizuku.exec(), not through this file.

PROTECTED_PACKAGES="android com.android.systemui com.android.settings com.google.android.gms com.hamondev.shevery moe.shizuku.privileged.api"

is_protected() {
  pkg="$1"
  for p in $PROTECTED_PACKAGES; do
    [ "$pkg" = "$p" ] && return 0
  done
  return 1
}

VAULT_ROOT="/sdcard/pulse-vault"
