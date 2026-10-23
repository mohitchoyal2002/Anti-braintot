#!/usr/bin/env bash
# Exports your own installed copy through an authorized adb connection.
set -euo pipefail
destination="${1:-$PWD/brainpal-apks}"
command -v adb >/dev/null
adb get-state >/dev/null
mkdir -p "$destination"
paths="$(adb shell pm path com.brainrot.android | tr -d '\r')"
if [[ "$paths" != package:* ]]; then printf '%s\n' 'BrainPal is not installed in the selected device/user profile.' >&2; exit 1; fi
while IFS= read -r entry; do
  [[ "$entry" == package:* ]] || continue
  remote="${entry#package:}"
  adb pull "$remote" "$destination/$(basename "$remote")"
done <<< "$paths"
printf 'Exported BrainPal APKs to %s\n' "$destination"
