#!/usr/bin/env bash
# The iPhone app on a simulator, for the `ios-app` CI job (MON-24 phase 3). Two commands:
#
#   iosApp/scripts/simulator.sh pick
#       Prints the UDID of the simulator to use: an available "iPhone 16" when the image has one,
#       else the first available iPhone on the newest iOS runtime. Logs its choice to stderr.
#
#   iosApp/scripts/simulator.sh launch <udid> <app> <out-dir>
#       Boots it, installs <app>, launches app.coplanly with its stdout and stderr in <out-dir>,
#       waits, fails unless the app is still running, and saves <out-dir>/launch.png. Whatever
#       happens it leaves the app's recent system log and any crash report in <out-dir>, and
#       prints them into the job's log.
#
#   iosApp/scripts/simulator.sh shell-test <udid> <out-dir>
#       Inside `firebase emulators:exec`: seeds a paired family (tools/ios/seed-shell-family.js),
#       runs the CoPlanlyUITests target (built by `build-for-testing`) on the booted simulator
#       signed in as its first parent, keeps the result bundle in <out-dir>/shell.xcresult and
#       exports its screenshots to <out-dir>/shots, printing a small copy of each into the log.
#       Fails when the test fails. Runs `testShellWalk` alone.
#
#   iosApp/scripts/simulator.sh tour <udid> <out-dir>
#       The same inside a fresh `firebase emulators:exec`, for the tour (MON-24 phase 3, part B,
#       Task 13): seeds the family with the records the tour opens (`--tour`), runs `testTour`,
#       keeps <out-dir>/tour.xcresult, exports its screenshots to <out-dir>/shots and writes the
#       tour's own lines (`COPLANLY_TOUR …`: every destination it reached or skipped, with the
#       reason) to <out-dir>/tour.txt. Fails only when the tour does: a crash or a failed launch.
set -euo pipefail

BUNDLE_ID=app.coplanly
PREFERRED=${PREFERRED_SIMULATOR:-iPhone 16}
WAIT_SECONDS=${LAUNCH_WAIT_SECONDS:-20}

pick() {
  xcrun simctl list devices available --json | PREFERRED="$PREFERRED" python3 -c '
import json, os, re, sys
devices = json.load(sys.stdin)["devices"]
def version(runtime):
    match = re.search(r"iOS-(\d+)(?:-(\d+))?(?:-(\d+))?$", runtime)
    return tuple(int(part or 0) for part in match.groups()) if match else None
phones = []
for runtime, entries in devices.items():
    v = version(runtime)
    if v is None:
        continue
    for device in entries:
        if device.get("isAvailable", True) and device["name"].startswith("iPhone"):
            phones.append((v, device["name"], device["udid"]))
if not phones:
    sys.exit("No available iPhone simulator on this image")
phones.sort(key=lambda p: p[0], reverse=True)
preferred = [p for p in phones if p[1] == os.environ["PREFERRED"]]
choice = (preferred or phones)[0]
print("Simulator: %s, iOS %s (%s)" % (choice[1], ".".join(map(str, choice[0])), choice[2]), file=sys.stderr)
print(choice[2])
'
}

collect() {
  local udid=$1 out=$2
  xcrun simctl spawn "$udid" log show --last 5m --style compact \
    --predicate 'process == "CoPlanly"' > "$out/system.log" 2>&1 || true
  find "$HOME/Library/Logs/DiagnosticReports" -name 'CoPlanly*' -mmin -30 -exec cp {} "$out/" \; 2>/dev/null || true
}

# Prints what <out-dir> holds into the job's log, since an artifact may be out of a reader's reach.
show() {
  local out=$1 file
  for file in "$out"/stdout.log "$out"/stderr.log; do
    [ -f "$file" ] || continue
    echo "::group::$(basename "$file")"; tail -n 200 "$file"; echo "::endgroup::"
  done
  echo "::group::system.log (last 150 lines)"; tail -n 150 "$out/system.log" 2>/dev/null || true; echo "::endgroup::"
  for file in "$out"/CoPlanly*; do
    [ -f "$file" ] || continue
    echo "::group::crash report $(basename "$file")"; head -c 20000 "$file"; echo; echo "::endgroup::"
  done
}

launch() {
  local udid=$1 app=$2 out=$3
  mkdir -p "$out"
  out=$(cd "$out" && pwd)
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b
  xcrun simctl list devices booted
  xcrun simctl install "$udid" "$app"
  echo "Installed $(xcrun simctl get_app_container "$udid" "$BUNDLE_ID")"

  # The app's own stdout and stderr (Kotlin's uncaught exceptions among them) go to files on this
  # host; simctl prints "<bundle id>: <pid>" and returns.
  local started pid
  started=$(xcrun simctl launch --terminate-running-process \
    --stdout="$out/stdout.log" --stderr="$out/stderr.log" "$udid" "$BUNDLE_ID")
  echo "$started"
  pid=${started##*: }
  sleep "$WAIT_SECONDS"

  # A simulator's app is a process of this Mac.
  if ! ps -p "$pid" > /dev/null 2>&1; then
    echo "::error::The app (pid $pid) is not running ${WAIT_SECONDS} s after launch."
    collect "$udid" "$out"
    show "$out"
    exit 1
  fi
  xcrun simctl io "$udid" screenshot "$out/launch.png"
  echo "The app (pid $pid) is running ${WAIT_SECONDS} s after launch; screenshot in $out/launch.png"
  collect "$udid" "$out"
  show "$out"
  # A small copy of the screenshot in the log, for a reader who cannot download the artifact.
  sips -Z 360 -s format jpeg -s formatOptions 60 "$out/launch.png" --out "$out/launch-small.jpg" > /dev/null
  echo "::group::launch-small.jpg (base64)"; base64 -i "$out/launch-small.jpg"; echo "::endgroup::"
}

# Samples the running app for a second every 20 seconds, keeping the last two samples in <out-dir>.
watch_app() {
  local out=$1 n=0 pid
  while sleep 20; do
    pid=$(pgrep -nx CoPlanly) || continue
    sample "$pid" 1 -mayDie -file "$out/sample-$((n % 2)).txt" > /dev/null 2>&1 || true
    n=$((n + 1))
  done
}

# Stops the watch_app loop running as <pid> and whatever it is running (its `sleep` or `sample`),
# which would otherwise outlive it: a killed subshell's children are handed to launchd and keep
# running. The loop is stopped first so it cannot start another child between the two kills.
stop_watcher() {
  local pid=$1
  kill -STOP "$pid" 2>/dev/null || true
  pkill -P "$pid" 2>/dev/null || true
  kill "$pid" 2>/dev/null || true
  kill -CONT "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

# Prints the main thread of the newest sample watch_app took, if any.
show_samples() {
  local out=$1 newest
  newest=$(ls -t "$out"/sample-*.txt 2>/dev/null | head -n 1) || true
  [ -n "$newest" ] || return 0
  echo "::group::the app's main thread, last sampled while the walk ran"
  # `head` closing the pipe early must not end the script (SIGPIPE, exit 141, under pipefail)
  # before the app's own log lines below are printed.
  { sed -n '/com.apple.main-thread/,/^ *[0-9]* Thread_/p' "$newest" | head -c 40000; } || true
  echo
  echo "::endgroup::"
}

shell_test() {
  ui_test "$1" "$2" shell testShellWalk yes
}

tour() {
  ui_test "$1" "$2" tour testTour no --tour
}

# Runs one UI test method, <test>, on <udid> after seeding a family (<seed-args> passed to the
# seed), keeping <out>/<name>.xcresult and its attachments in <out>/shots. <small-copies> "yes"
# prints a small copy of each screenshot into the log, as the walk does; the tour has too many.
ui_test() {
  local udid=$1 out=$2 name=$3 test=$4 small=$5 seed status=0
  shift 5
  mkdir -p "$out"
  out=$(cd "$out" && pwd)
  seed=$(node tools/ios/seed-shell-family.js "$@")
  # xcodebuild hands the test runner every TEST_RUNNER_-prefixed variable, without the prefix.
  export TEST_RUNNER_COPLANLY_UITEST_EMAIL
  export TEST_RUNNER_COPLANLY_UITEST_PASSWORD
  TEST_RUNNER_COPLANLY_UITEST_EMAIL=$(node -e 'console.log(JSON.parse(process.argv[1]).email)' "$seed")
  TEST_RUNNER_COPLANLY_UITEST_PASSWORD=$(node -e 'console.log(JSON.parse(process.argv[1]).password)' "$seed")
  echo "Seeded family; signing in as $TEST_RUNNER_COPLANLY_UITEST_EMAIL"
  rm -rf "$out/$name.xcresult"
  # While the walk runs, the app's threads are sampled now and then, so a walk that fails because
  # the app's main thread stopped answering ("main thread busy") says where it was busy.
  local watcher
  watch_app "$out" &
  watcher=$!
  xcodebuild test-without-building \
    -project iosApp/CoPlanly.xcodeproj \
    -scheme CoPlanly \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath iosApp/build \
    -only-testing:"CoPlanlyUITests/ShellUITests/$test" \
    -resultBundlePath "$out/$name.xcresult" 2>&1 | tee "$out/xcodebuild-$name.log" || status=$?
  stop_watcher "$watcher"
  if [ "$status" -ne 0 ]; then show_samples "$out"; fi
  mkdir -p "$out/shots"
  xcrun xcresulttool export attachments --path "$out/$name.xcresult" --output-path "$out/shots" || true
  # The tour's own record of every destination, reached or skipped with the reason, for a reader
  # of the artifact: its summary, or, when it never got there, the lines it printed as it went.
  if [ "$name" = tour ]; then
    grep -E "COPLANLY_TOUR [^ ]+: |COPLANLY_TOUR_SUMMARY reached" "$out/xcodebuild-$name.log" > "$out/tour.txt" 2>/dev/null ||
      grep "COPLANLY_TOUR_VISIT" "$out/xcodebuild-$name.log" > "$out/tour.txt" 2>/dev/null || true
    echo "::group::the tour's destinations"; cat "$out/tour.txt"; echo "::endgroup::"
  fi
  # A crash the walk met, as the test recorded it, and the app's own lines from the system log: what
  # stopped it, for a reader of the log alone.
  local crash
  for crash in "$out"/shots/*.ips; do
    [ -f "$crash" ] || continue
    echo "::group::crash log $(basename "$crash")"; head -c 60000 "$crash"; echo; echo "::endgroup::"
  done
  echo "::group::the app's errors in the system log"
  xcrun simctl spawn "$udid" log show --last 30m --style compact \
    --predicate 'process == "CoPlanly" AND (messageType == error OR messageType == fault OR eventMessage CONTAINS[c] "exception")' \
    2>/dev/null | tail -n 200 || true
  echo "::endgroup::"
  # A small copy of each screenshot in the log, named as the test named it, for a reader who cannot
  # download the artifact.
  if [ "$small" != yes ]; then return "$status"; fi
  local file shot
  node -e '
    const manifest = require(process.argv[1]);
    for (const test of manifest) {
      for (const a of test.attachments || []) {
        if (a.exportedFileName.endsWith(".png")) console.log(a.exportedFileName + " " + a.suggestedHumanReadableName);
      }
    }' "$out/shots/manifest.json" 2>/dev/null | while read -r file shot; do
    sips -Z 300 -s format jpeg -s formatOptions 50 "$out/shots/$file" --out "$out/shots/${file%.png}-small.jpg" > /dev/null
    echo "::group::${shot%%_*}.png small (base64)"; base64 -i "$out/shots/${file%.png}-small.jpg"; echo "::endgroup::"
  done
  return "$status"
}

case "${1:-}" in
  pick) pick ;;
  launch) shift; launch "$@" ;;
  shell-test) shift; shell_test "$@" ;;
  tour) shift; tour "$@" ;;
  *) echo "usage: $0 pick | launch <udid> <app> <out-dir> | shell-test <udid> <out-dir> | tour <udid> <out-dir>" >&2; exit 2 ;;
esac
