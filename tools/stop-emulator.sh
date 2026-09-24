#!/usr/bin/env bash
# Stops the Android emulator and waits until its process is gone. Called by
# tools/with-screen-recording.sh after the tests, when STOP_EMULATOR=true.
#
# Why this exists: after `connectedDebugAndroidTest` finished, `reactivecircus/android-emulator-runner`
# sent `adb emu kill` and the emulator did not exit — on API 26 *and* API 30, for 9 to 11 minutes a
# run, until the step's timeout (PR #102: tests done at 08:36, job done at 08:47). A *detached*
# `pkill` tried earlier did not help: nothing waited for it. This one is synchronous — ask politely,
# wait, then SIGKILL the qemu process and wait again — so by the time the action sends its own
# `adb emu kill` there is nothing left to hang on, and that call fails fast on "no devices".
#
# It never fails: the tests' status was already written, and a leftover emulator is the runner's
# problem at the end of the job, not a test result.
set -u

gone() { ! pgrep -f 'qemu-system|emulator64-crash-service' >/dev/null 2>&1; }

adb emu kill >/dev/null 2>&1 || true
for _ in $(seq 1 15); do
  gone && { echo "Emulator stopped."; exit 0; }
  sleep 1
done

echo "The emulator ignored 'adb emu kill' for 15 s; killing its process."
pkill -9 -f 'qemu-system' >/dev/null 2>&1 || true
pkill -9 -f 'emulator64-crash-service' >/dev/null 2>&1 || true
for _ in $(seq 1 15); do
  gone && { echo "Emulator killed."; exit 0; }
  sleep 1
done
echo "::warning::The emulator process was still alive after SIGKILL."
exit 0
