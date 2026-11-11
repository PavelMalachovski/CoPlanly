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
#       Fails when the test fails.
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

shell_test() {
  local udid=$1 out=$2 seed status=0
  mkdir -p "$out"
  out=$(cd "$out" && pwd)
  seed=$(node tools/ios/seed-shell-family.js)
  # xcodebuild hands the test runner every TEST_RUNNER_-prefixed variable, without the prefix.
  export TEST_RUNNER_COPLANLY_UITEST_EMAIL
  export TEST_RUNNER_COPLANLY_UITEST_PASSWORD
  TEST_RUNNER_COPLANLY_UITEST_EMAIL=$(node -e 'console.log(JSON.parse(process.argv[1]).email)' "$seed")
  TEST_RUNNER_COPLANLY_UITEST_PASSWORD=$(node -e 'console.log(JSON.parse(process.argv[1]).password)' "$seed")
  echo "Seeded family; signing in as $TEST_RUNNER_COPLANLY_UITEST_EMAIL"
  rm -rf "$out/shell.xcresult"
  xcodebuild test-without-building \
    -project iosApp/CoPlanly.xcodeproj \
    -scheme CoPlanly \
    -destination "platform=iOS Simulator,id=$udid" \
    -derivedDataPath iosApp/build \
    -only-testing:CoPlanlyUITests \
    -resultBundlePath "$out/shell.xcresult" || status=$?
  mkdir -p "$out/shots"
  xcrun xcresulttool export attachments --path "$out/shell.xcresult" --output-path "$out/shots" || true
  echo "::group::Exported attachments"; cat "$out/shots/manifest.json" 2>/dev/null || ls "$out/shots"; echo; echo "::endgroup::"
  # A small copy of each screenshot in the log, for a reader who cannot download the artifact.
  local shot
  for shot in "$out"/shots/*.png; do
    [ -f "$shot" ] || continue
    sips -Z 300 -s format jpeg -s formatOptions 50 "$shot" --out "${shot%.png}-small.jpg" > /dev/null
    echo "::group::$(basename "$shot") small (base64)"; base64 -i "${shot%.png}-small.jpg"; echo "::endgroup::"
  done
  return "$status"
}

case "${1:-}" in
  pick) pick ;;
  launch) shift; launch "$@" ;;
  shell-test) shift; shell_test "$@" ;;
  *) echo "usage: $0 pick | launch <udid> <app> <out-dir> | shell-test <udid> <out-dir>" >&2; exit 2 ;;
esac
