#!/usr/bin/env bash
# Installs the minified `r8Test` APK on the running emulator or device, runs the R8 runtime probe
# inside it, and checks what it reported (REL-7). Used by the CI `r8-runtime` job; locally:
#
#   ./gradlew assembleR8Test && bash tools/run-r8-probe.sh
#
# The probe is a self-targeting <instrumentation> (app/src/r8Test/AndroidManifest.xml), so it runs
# in the app's own minified process. `am instrument -w -r` prints each case; the output is kept in
# $OUT_DIR and judged by tools/check-r8-probe.js, which also writes JUnit XML for the check run.
#
# The exit status is the check's, and is also written to $STATUS_FILE when that is set — the CI
# job decides on that file, not on the emulator step (see ci.yml). With STOP_EMULATOR=true the
# emulator is then stopped synchronously (tools/stop-emulator.sh), as in the other emulator jobs.
#
# Installing replaces any `app.coplanly` build signed with the same (debug) key and keeps its data;
# the probe itself only touches an in-memory database and a Firebase app of its own.
set -u

APPLICATION_ID="${APPLICATION_ID:-app.coplanly}"
RUNNER="com.coparently.app.r8probe.R8ProbeInstrumentation"
OUT_DIR="${OUT_DIR:-app/build/outputs/r8-probe}"
mkdir -p "$OUT_DIR"

run() {
  local apk
  apk=$(find app/build/outputs/apk/r8Test -name '*.apk' 2>/dev/null | head -n 1)
  if [ -z "$apk" ]; then
    echo "::error::No r8Test APK under app/build/outputs/apk/r8Test — run ./gradlew assembleR8Test first."
    return 1
  fi
  echo "Installing $apk"
  if ! adb install -r "$apk"; then
    echo "::error::adb install of the r8Test APK failed."
    return 1
  fi

  adb logcat -c >/dev/null 2>&1 || true
  # `-w` waits for the process to finish; `-r` prints every key the probe reports. am exits 0
  # whatever the probe found — the check below is the verdict.
  adb shell am instrument -w -r "$APPLICATION_ID/$RUNNER" 2>&1 | tee "$OUT_DIR/am-instrument.txt"
  node tools/check-r8-probe.js "$OUT_DIR/am-instrument.txt" --junit "$OUT_DIR/junit/r8-probe.xml"
}

run
status=$?

adb logcat -d -v time >"$OUT_DIR/logcat.txt" 2>/dev/null || true
if [ "$status" -ne 0 ]; then
  # A crash inside the minified process (an R8-removed member, a stripped signature raised outside
  # a case) leaves its stack here and nowhere else.
  echo "::group::logcat crash buffer (probe exited with $status)"
  adb logcat -d -b crash 2>/dev/null | tail -n 300 || true
  echo "::endgroup::"
fi

if [ -n "${STATUS_FILE:-}" ]; then
  echo "$status" >"$STATUS_FILE"
fi

if [ "${STOP_EMULATOR:-false}" = "true" ]; then
  bash "$(dirname "$0")/stop-emulator.sh"
fi

exit "$status"
