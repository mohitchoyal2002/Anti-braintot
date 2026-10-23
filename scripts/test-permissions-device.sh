#!/usr/bin/env bash
# Run only on a disposable emulator: resets the app's local test data and grants.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_path="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
adb_path="$sdk_path/platform-tools/adb"
[[ "$("$adb_path" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { printf '%s\n' 'Permission tests require a disposable emulator.' >&2; exit 1; }
bash scripts/build-permission-tests.sh
version_name="$(sed -n 's/^versionName=//p' version.properties)"
"$adb_path" install -r "build/sdk/TimeLens-$version_name-debug.apk"
"$adb_path" install -r build/permission-tests/permission-tests.apk
mkdir -p build/permission-evidence

run_case() {
  local scenario="$1" usage="$2" overlay="$3" notifications="$4"
  "$adb_path" shell pm clear work.timelens.app >/dev/null
  "$adb_path" shell appops set work.timelens.app GET_USAGE_STATS "$usage"
  "$adb_path" shell appops set work.timelens.app SYSTEM_ALERT_WINDOW "$overlay"
  if [[ "$notifications" == allow ]]; then
    "$adb_path" shell pm grant work.timelens.app android.permission.POST_NOTIFICATIONS
  fi
  "$adb_path" shell am instrument -w -e scenario "$scenario" work.timelens.app.test/work.timelens.app.PermissionFlowInstrumentation | tee "build/permission-evidence/$scenario.txt"
  rg -q 'timelens_success=true' "build/permission-evidence/$scenario.txt" || { "$adb_path" logcat -d -t 2000 > "build/permission-evidence/$scenario-logcat.txt"; return 1; }
  # Collect screenshots before the next scenario clears app data.
  while IFS= read -r file; do
    [[ "$file" == qa-*.png ]] || continue
    "$adb_path" exec-out run-as work.timelens.app cat "files/$file" > "build/permission-evidence/$scenario-$file"
  done < <("$adb_path" shell run-as work.timelens.app ls files | tr -d '\r')
}

"$adb_path" shell cmd uimode night no
run_case usage deny deny deny
run_case overlay allow deny deny
run_case notifications allow allow deny
run_case complete allow allow allow
run_case channel allow allow allow
"$adb_path" shell cmd uimode night yes
"$adb_path" shell settings put system font_scale 2.0
run_case large deny deny deny
"$adb_path" shell settings put system font_scale 1.0
printf '%s\n' 'All permission flow scenarios passed.'
