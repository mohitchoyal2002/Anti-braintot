#!/usr/bin/env bash
# Run only on a disposable Android 15–17 emulator: resets test data and grants.
set -euo pipefail
cd "$(dirname "$0")/.."
sdk_path="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
adb_path="$sdk_path/platform-tools/adb"
[[ "$("$adb_path" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { printf '%s\n' 'Permission tests require a disposable emulator.' >&2; exit 1; }
device_api="$("$adb_path" shell getprop ro.build.version.sdk | tr -d '\r')"
evidence_path="build/permission-evidence/api-$device_api"
mkdir -p "$evidence_path"
collect_failure() {
  local result="$?"
  if (( result != 0 )); then
    "$adb_path" logcat -d -t 8000 > "$evidence_path/device-failure-logcat.txt" || true
    "$adb_path" shell df -h /data > "$evidence_path/device-storage.txt" || true
    df -h > "$evidence_path/runner-storage.txt" || true
  fi
}
trap collect_failure EXIT
failed_cases=()
# boot_completed can precede a fully responsive package manager on a fresh image.
package_ready=false
for attempt in {1..30}; do
  if "$adb_path" shell pm path android 2>/dev/null | grep -q 'package:'; then
    package_ready=true
    break
  fi
  sleep 2
done
[[ "$package_ready" == true ]] || { printf '%s\n' 'Android package manager did not become ready.' >&2; exit 1; }
version_name="$(sed -n 's/^versionName=//p' version.properties)"
if [[ "${TIMELENS_TEST_APK_PREBUILT:-false}" == true ]]; then
  [[ -s "build/sdk/TimeLens-$version_name-debug.apk" && -s build/permission-tests/permission-tests.apk ]] || { printf '%s\n' 'Missing prebuilt app or instrumentation APK.' >&2; exit 1; }
else
  bash scripts/build-permission-tests.sh
fi
# A complete streaming install avoids incremental-filesystem startup races.
"$adb_path" install --no-incremental -r "build/sdk/TimeLens-$version_name-debug.apk"
"$adb_path" install --no-incremental -r build/permission-tests/permission-tests.apk

run_case() {
  local scenario="$1" usage="$2" overlay="$3" notifications="$4"
  local instrumentation_ok=true
  "$adb_path" shell pm clear work.timelens.app >/dev/null
  "$adb_path" shell appops set work.timelens.app GET_USAGE_STATS "$usage"
  "$adb_path" shell appops set work.timelens.app SYSTEM_ALERT_WINDOW "$overlay"
  if [[ "$notifications" == allow ]]; then
    "$adb_path" shell pm grant work.timelens.app android.permission.POST_NOTIFICATIONS
  fi
  if ! "$adb_path" shell am instrument -w -r -e scenario "$scenario" work.timelens.app.test/work.timelens.app.PermissionFlowInstrumentation | tee "${evidence_path}/$scenario.txt"; then
    instrumentation_ok=false
  fi
  # Collect screenshots before the next scenario clears app data.
  while IFS= read -r file; do
    [[ "$file" == qa-*.png ]] || continue
    "$adb_path" exec-out run-as work.timelens.app cat "files/$file" > "${evidence_path}/$scenario-$file"
  done < <("$adb_path" shell run-as work.timelens.app ls files 2>/dev/null | tr -d '\r')
  if [[ "$instrumentation_ok" != true ]] || ! grep -Fq 'INSTRUMENTATION_RESULT: timelens_success=true' "${evidence_path}/$scenario.txt"; then
    "$adb_path" logcat -d -t 2000 > "${evidence_path}/$scenario-logcat.txt"
    failed_cases+=("$scenario")
  fi
}

"$adb_path" shell cmd uimode night no
run_case usage deny deny deny
run_case overlay allow deny deny
run_case notifications allow allow deny
run_case complete allow allow allow
run_case channel allow allow allow
run_case settings default deny deny
run_case design allow allow allow
"$adb_path" shell cmd uimode night yes
"$adb_path" shell settings put system font_scale 2.0
run_case large deny deny deny
run_case design-large allow allow allow
"$adb_path" shell settings put system font_scale 1.0
if (( ${#failed_cases[@]} )); then
  printf 'Failed device scenarios: %s\n' "${failed_cases[*]}" >&2
  exit 1
fi
printf '%s\n' 'All permission flow scenarios passed.'
