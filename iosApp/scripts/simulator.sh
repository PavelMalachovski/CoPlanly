#!/usr/bin/env bash
# The iPhone app on a simulator, for the `ios-app` CI job (MON-24 phase 3). Two commands:
#
#   iosApp/scripts/simulator.sh pick
#       Prints the UDID of the simulator to use: an available "iPhone 16" when the image has one,
#       else the first available iPhone on the newest iOS runtime. Logs its choice to stderr.
#
#   iosApp/scripts/simulator.sh launch <udid> <app> <out-dir>
#       Boots it, installs <app>, launches app.coplanly with its console in <out-dir>/console.log,
#       waits, fails unless the app is still running, and saves <out-dir>/launch.png. Whatever
#       happens it leaves the app's recent system log and any crash report in <out-dir>.
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
    --predicate 'process == "CoPlanly" OR subsystem BEGINSWITH "com.google.firebase"' \
    > "$out/system.log" 2>&1 || true
  find "$HOME/Library/Logs/DiagnosticReports" -name 'CoPlanly*' -mmin -30 -exec cp {} "$out/" \; 2>/dev/null || true
}

launch() {
  local udid=$1 app=$2 out=$3
  mkdir -p "$out"
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b
  xcrun simctl install "$udid" "$app"
  xcrun simctl launch --console-pty "$udid" "$BUNDLE_ID" < /dev/null > "$out/console.log" 2>&1 &
  sleep "$WAIT_SECONDS"

  local entry
  entry=$(xcrun simctl spawn "$udid" launchctl list | grep "UIKitApplication:$BUNDLE_ID" || true)
  echo "launchctl: ${entry:-<not listed>}"
  if ! awk '{ print $1 }' <<<"$entry" | grep -Eq '^[0-9]+$'; then
    echo "::error::The app is not running ${WAIT_SECONDS} s after launch."
    collect "$udid" "$out"
    tail -n 80 "$out/console.log" || true
    exit 1
  fi
  xcrun simctl io "$udid" screenshot "$out/launch.png"
  echo "The app is running ${WAIT_SECONDS} s after launch; screenshot in $out/launch.png"
  collect "$udid" "$out"
  tail -n 40 "$out/console.log" || true
}

case "${1:-}" in
  pick) pick ;;
  launch) shift; launch "$@" ;;
  *) echo "usage: $0 pick | launch <udid> <app> <out-dir>" >&2; exit 2 ;;
esac
