#!/usr/bin/env bash
# "Install the new build over the previous one", on an emulator — the CI `upgrade` job's script
# (docs/DEVICE-CHECKLIST.md §2.1). Runs inside reactivecircus/android-emulator-runner once the
# emulator has booted and both builds exist:
#
#   BASE_APK=upgrade-base/app/build/outputs/apk/debug/app-debug.apk \
#     bash tools/upgrade/run-upgrade-test.sh
#
#  1. Both APKs must carry the same signing certificate — `adb install -r` refuses an update
#     signed by another key, and an instrumentation must share its target's certificate. Both
#     builds run in one job under one HOME, so AGP signs both with the same
#     ~/.android/debug.keystore; this checks that instead of assuming it.
#  2. A fresh install of the BASE app and of this branch's test APK.
#  3. The seed (UpgradeSeedTest) runs in the base app's process and writes the real database and
#     preference store through the base build's own code.
#  4. `adb install -r` of this branch's app APK — the update keeps the app's data, as Play's does.
#  5. The verify (UpgradeVerifyTest) runs against this branch's classes and checks every seeded
#     fact survived.
#
# Each phase is `am instrument -w -r`, whose exit status and "OK (1 test)" say nothing — a
# skipped test prints OK too — so tools/upgrade/instrument-results.js decides from the per-test
# status codes and writes JUnit XML into $RESULTS_DIR. Exits non-zero on anything but two passes.
set -euo pipefail

APP_ID="${APP_ID:-app.coplanly}"
BASE_APK="${BASE_APK:?BASE_APK must name the app APK of the base build}"
NEW_APK="${NEW_APK:-app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${TEST_APK:-app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}"
RESULTS_DIR="${RESULTS_DIR:-app/build/outputs/upgrade-results}"
PHASE_ARGUMENT=coplanlyUpgradePhase
SEED_CLASS=com.coparently.app.upgrade.UpgradeSeedTest
VERIFY_CLASS=com.coparently.app.upgrade.UpgradeVerifyTest

mkdir -p "$RESULTS_DIR"

fail() {
  echo "::error::$*"
  exit 1
}

for apk in "$BASE_APK" "$NEW_APK" "$TEST_APK"; do
  [ -f "$apk" ] || fail "Missing APK: $apk"
done

# --- 1. One signing certificate for all three -----------------------------------------------
build_tools="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools"
apksigner="$( (find "$build_tools" -maxdepth 2 -name apksigner -type f 2>/dev/null || true) | sort -V | tail -n 1)"
[ -n "$apksigner" ] || fail "apksigner not found under $build_tools"
certificate() {
  # awk reads the whole (short) output, so apksigner never writes into a closed pipe.
  "$apksigner" verify --print-certs "$1" \
    | awk '/certificate SHA-256 digest/ && !found { print $NF; found = 1 }' || true
}
base_cert="$(certificate "$BASE_APK")"
new_cert="$(certificate "$NEW_APK")"
test_cert="$(certificate "$TEST_APK")"
echo "Signing certificates (SHA-256): base $base_cert, new $new_cert, test $test_cert"
[ -n "$base_cert" ] || fail "Could not read the base APK's signing certificate"
[ "$base_cert" = "$new_cert" ] || fail "The base and new APKs are signed with different keys; adb install -r would refuse the update"
[ "$new_cert" = "$test_cert" ] || fail "The test APK is signed with a different key from the app it instruments"

# --- 2. A fresh install of the base build and the test APK ---------------------------------
adb wait-for-device
# The AVD snapshot holds no app, but a leftover would make the seed refuse to run (it writes the
# real database only on a fresh install), so remove whatever might be there.
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb uninstall "$APP_ID.test" >/dev/null 2>&1 || true

echo "::group::Install the base build ($BASE_APK)"
adb install -t "$BASE_APK"
adb install -t "$TEST_APK"
echo "::endgroup::"

# "instrumentation:app.coplanly.test/com.coparently.app.HiltTestRunner (target=app.coplanly)"
instrumentation="$(adb shell pm list instrumentation | tr -d '\r' \
  | awk -v target="(target=$APP_ID)" 'index($0, target) && !found {
      sub(/^instrumentation:/, ""); sub(/ \(target=.*$/, ""); print; found = 1
    }' || true)"
[ -n "$instrumentation" ] || fail "No instrumentation targets $APP_ID after installing $TEST_APK"
echo "Instrumentation: $instrumentation"

# Runs one phase and judges it. `am instrument` output is kept whole for the reader.
run_phase() {
  local phase="$1" class="$2" suite="$3"
  local raw="$RESULTS_DIR/$phase-am-instrument.txt"
  echo "::group::am instrument — $phase ($class)"
  adb logcat -c || true
  # `|| true`: the verdict comes from the output, not from adb's exit status.
  adb shell am instrument -w -r -e "$PHASE_ARGUMENT" "$phase" -e class "$class" "$instrumentation" \
    | tr -d '\r' | tee "$raw" || true
  adb logcat -d -v time >"$RESULTS_DIR/$phase-logcat.txt" 2>&1 || true
  echo "::endgroup::"
  if ! node tools/upgrade/instrument-results.js --raw "$raw" --junit "$RESULTS_DIR/TEST-upgrade-$phase.xml" \
      --suite "$suite" --expect-class "$class"; then
    echo "::group::logcat — $phase: the check's own lines, failures, crashes"
    grep -E "UpgradeCheck|TestRunner|AndroidRuntime|EncryptedDatabase|DatabaseKey|EncryptedPreferences" \
      "$RESULTS_DIR/$phase-logcat.txt" | tail -n 300 || true
    echo "::endgroup::"
    return 1
  fi
}

# --- 3. Seed through the base build ---------------------------------------------------------
run_phase seed "$SEED_CLASS" "Upgrade — seed (base build)" \
  || fail "The seed did not pass on the base build, so there is nothing to upgrade. If a signature it calls changed in this branch, see UpgradeFixture's KDoc."

# --- 4. Install this branch over it, keeping the data ---------------------------------------
echo "::group::Install this branch's build over the base build ($NEW_APK)"
adb install -r -t "$NEW_APK" || fail "adb install -r of this branch's APK over the base build failed"
echo "::endgroup::"

# --- 5. Verify through this branch's build --------------------------------------------------
run_phase verify "$VERIFY_CLASS" "Upgrade — verify (this build)" \
  || fail "The upgraded build did not open what the base build wrote intact — see the verify's failure above."

echo "Upgrade over the base build: seed and verify both passed."
