#!/usr/bin/env bash
# Runs a command (the instrumented tests) while recording the emulator's screen, then pulls the
# recording into $VIDEO_DIR (default app/build/outputs/emulator-video). Used by the CI
# `instrumented` job:
#
#   bash tools/with-screen-recording.sh ./gradlew connectedDebugAndroidTest
#
# The exit status is the command's, always, and is also written to $STATUS_FILE when that is set. Recording is a convenience for a person reading a
# failure, so nothing in it may fail the job: every adb call here is allowed to fail, and a
# recorder that cannot start gives up after a few tries instead of spinning.
#
# `screenrecord` stops by itself after 180 s, so the recorder runs in segments of 170 s,
# part-001.mp4, part-002.mp4, …, until the command finishes. It is stopped with SIGINT, which is
# what makes screenrecord finish the MP4 it is writing; a killed one leaves an unplayable file.
# 540x960 at 1 Mbit/s keeps a segment near 20 MB, which is readable and cheap to store.
set -u

VIDEO_DIR="${VIDEO_DIR:-app/build/outputs/emulator-video}"
REMOTE=/sdcard/ci-video
MAX_SEGMENTS=30
STOP_FLAG="$(mktemp -u)"

mkdir -p "$VIDEO_DIR"
adb shell "rm -rf $REMOTE; mkdir -p $REMOTE" >/dev/null 2>&1 || true

record_loop() {
  local i=0 quick_failures=0 size_args="--size 540x960" started
  while [ ! -e "$STOP_FLAG" ] && [ "$i" -lt "$MAX_SEGMENTS" ]; do
    i=$((i + 1))
    started=$SECONDS
    # shellcheck disable=SC2086 # size_args is deliberately split, or empty
    if ! adb shell screenrecord --bit-rate 1000000 $size_args --time-limit 170 \
        "$REMOTE/part-$(printf %03d "$i").mp4" >/dev/null 2>&1; then
      if [ $((SECONDS - started)) -lt 5 ]; then
        quick_failures=$((quick_failures + 1))
        # Some emulator encoders refuse a size they were not asked for before; try the native one.
        size_args=""
        if [ "$quick_failures" -ge 3 ]; then
          echo "::warning::screenrecord would not start; this leg has no video."
          return 0
        fi
        sleep 2
      fi
    fi
  done
}

record_loop &
recorder=$!

"$@"
status=$?

touch "$STOP_FLAG"
adb shell "pkill -INT screenrecord || killall -INT screenrecord" >/dev/null 2>&1 || true
for _ in $(seq 1 20); do
  kill -0 "$recorder" 2>/dev/null || break
  sleep 1
done
kill "$recorder" 2>/dev/null || true
wait "$recorder" 2>/dev/null || true
sleep 2 # let the device flush the last segment's moov atom

adb pull "$REMOTE/." "$VIDEO_DIR/" >/dev/null 2>&1 || true
rm -f "$STOP_FLAG"
echo "Screen recording: $(find "$VIDEO_DIR" -name '*.mp4' 2>/dev/null | wc -l) segment(s) in $VIDEO_DIR"

# A crashed process ("Process crashed", "System has crashed") leaves no stack trace in Gradle's
# output, and the log is often the only thing a reader can reach. Print the crash buffer — the
# stack of every process that died — so the failure can be diagnosed from the job log alone.
if [ "$status" -ne 0 ]; then
  echo "::group::logcat crash buffer (tests exited with $status)"
  adb logcat -d -b crash 2>/dev/null | tail -n 300 || true
  echo "::endgroup::"
fi

# The CI job reads this to decide the leg, rather than the emulator step's own outcome: that step
# can also fail or time out *after* the tests, while the emulator shuts down (see ci.yml).
if [ -n "${STATUS_FILE:-}" ]; then
  echo "$status" > "$STATUS_FILE"
fi

exit "$status"
