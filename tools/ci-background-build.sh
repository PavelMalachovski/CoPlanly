#!/usr/bin/env bash
# Compiles the app and test APKs while the emulator boots, instead of after it. Used by the CI
# `instrumented` job:
#
#   bash tools/ci-background-build.sh start   # a step before the emulator: returns at once
#   bash tools/ci-background-build.sh wait    # first line of the emulator script: blocks
#
# `start` launches `./gradlew assembleDebug assembleDebugAndroidTest` detached and returns; the
# process outlives its step (a runner reaps a job's processes at the end of the job, not of the
# step). Restoring the AVD and booting the emulator take one to two minutes that the build used
# to wait behind — measured on PR #102: boot finished at 08:33:03, and only then did two minutes
# of compilation start. `wait` blocks until the build ends and exits with its status, so a
# compile error still fails the leg before any test runs; the `connectedDebugAndroidTest` after
# it finds both APKs up to date and goes straight to installing them.
#
# Nothing here is needed for correctness: without `start`, `wait` returns at once and Gradle
# builds as it always did.
#
# BACKGROUND_BUILD_TASKS overrides what `start` builds (the `r8-runtime` job builds
# `assembleR8Test`); unset, it is the two APKs the instrumented and e2e jobs install.
set -u

TASKS="${BACKGROUND_BUILD_TASKS:-assembleDebug assembleDebugAndroidTest}"
DIR="${BACKGROUND_BUILD_DIR:-${RUNNER_TEMP:-/tmp}/background-build}"
LOG="$DIR/build.log"
STATUS="$DIR/status"
PID="$DIR/pid"

case "${1:-}" in
  start)
    mkdir -p "$DIR"
    rm -f "$STATUS"
    # setsid + nohup: the build must not die with the step's shell, and must not hold its stdout.
    # $2 is deliberately unquoted inside: it is a list of task names.
    setsid nohup bash -c '
      ./gradlew $2 >"$0" 2>&1
      echo $? >"$1"
    ' "$LOG" "$STATUS" "$TASKS" </dev/null >/dev/null 2>&1 &
    echo $! >"$PID"
    echo "Background build of '$TASKS' started (pid $(cat "$PID")); log: $LOG"
    ;;
  wait)
    if [ ! -f "$PID" ]; then
      echo "No background build was started; Gradle will build in the foreground."
      exit 0
    fi
    started=$SECONDS
    while [ ! -f "$STATUS" ]; do
      if ! kill -0 "$(cat "$PID")" 2>/dev/null && [ ! -f "$STATUS" ]; then
        # The wrapper writes the status before it exits; give the file system a moment.
        sleep 2
        [ -f "$STATUS" ] || { echo "::error::The background build died without a status."; cat "$LOG"; exit 1; }
      fi
      sleep 2
    done
    status=$(cat "$STATUS")
    # The leg is decided by $STATUS_FILE (ci.yml); a compile error is a result too, not a hang.
    if [ "$status" != "0" ] && [ -n "${STATUS_FILE:-}" ]; then
      echo "$status" >"$STATUS_FILE"
    fi
    echo "::group::Background build (waited $((SECONDS - started)) s, exit $status)"
    cat "$LOG"
    echo "::endgroup::"
    exit "$status"
    ;;
  *)
    echo "usage: $0 start|wait" >&2
    exit 2
    ;;
esac
