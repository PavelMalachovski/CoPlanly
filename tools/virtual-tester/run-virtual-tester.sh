#!/usr/bin/env bash
# The virtual tester: the real app walked like a human tester would — Home, Calendar, Chat,
# Expenses, receipts, two isolated families, a random-tap pass — every step asserted and
# photographed. Run by .github/workflows/virtual-tester.yml; not part of CI.
#
#   tools/virtual-tester/run-virtual-tester.sh    # after ./gradlew assembleDebug assembleDebugAndroidTest
#
# Needs what tools/ui-tour/run-ui-tour.sh needs: an Android emulator on adb, `npm ci` done in
# `firestore-tests/` (the Firebase CLI) and `functions/`, and a JDK 21+ for the emulators
# (FIREBASE_JAVA_HOME). It starts Auth, Firestore, Functions and Storage with
# `firebase emulators:exec` and, inside, calls itself with --inside-emulators, which:
#
#   1. installs the app and test APKs (built beforehand);
#   2. for each variant `<theme>-<language>-<font scale %>[-wide]` (as the UI tour's):
#        - `pm clear`s the app and sets night mode, font scale and, for `-wide`, the display;
#        - runs **one `am instrument` per scenario class** of `com.coparently.app.qa`, with
#          `-e coplanlyVirtualTester true` and `-e coplanlyUiTourVariant <variant>`, so a crash costs
#          one class rather than the run; a class whose process crashed gets a line in the report
#          saying so, with the logcat it left;
#        - streams files/virtual-tester/<variant>/ (steps.jsonl and shots/) out with `run-as`;
#   3. writes index.html, results.json and summary.md (tools/virtual-tester/report.js).
#
# Output ($VIRTUAL_TESTER_OUT, default build/virtual-tester-site): virtual-tester/<variant>/,
# the report files, and logs/ (instrumentation output and logcat per variant and class).
# Exits non-zero only when no step at all was recorded: a failed step is in the report, not in the
# exit status.
#
# Environment:
#   VIRTUAL_TESTER_VARIANTS  Space-separated variants. Default: light-cs-100.
#   VIRTUAL_TESTER_CLASSES   Space-separated simple class names (e.g. "SmokeScenarioTest").
#                            Default: every *ScenarioTest in app/src/androidTest/.../qa/.
#   VIRTUAL_TESTER_OUT       Output directory. Default: build/virtual-tester-site.
#   COPLANLY_EMULATOR_HOST   The host as the device reaches it. Default 10.0.2.2.
#   FIREBASE_JAVA_HOME       A JDK 21+ for the emulators.
#   APP_APK, TEST_APK        The APKs. Default: the debug build's.
set -euo pipefail

cd "$(dirname "$0")/../.."

APP_ID="${APP_ID:-app.coplanly}"
APP_APK="${APP_APK:-app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${TEST_APK:-app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}"
OUT="${VIRTUAL_TESTER_OUT:-build/virtual-tester-site}"
VARIANTS="${VIRTUAL_TESTER_VARIANTS:-light-cs-100}"
WIDE_SIZE="2560x1600"   # pixels; at WIDE_DENSITY, 1280 x 800 dp
WIDE_DENSITY="320"
HOST="${COPLANLY_EMULATOR_HOST:-10.0.2.2}"
PACKAGE="com.coparently.app.qa"
SOURCES="app/src/androidTest/java/com/coparently/app/qa"
REMOTE="files/virtual-tester"  # relative to the app's data dir, where run-as starts

fail() {
  echo "::error::$*"
  exit 1
}

classes="${VIRTUAL_TESTER_CLASSES:-}"
if [ -z "$classes" ]; then
  # Smoke first: when even it fails, everything after it reads in its light.
  classes="SmokeScenarioTest $(cd "$SOURCES" && ls ./*ScenarioTest.kt | sed -e 's|^\./||' -e 's|\.kt$||' \
    | grep -v '^SmokeScenarioTest$' | tr '\n' ' ')"
fi

# The UI tour's reasons apply unchanged: a system dialog steals the window focus and every Back
# then waits on it. See tools/ui-tour/run-ui-tour.sh.
clear_system_dialogs() {
  adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
  local home
  home="$(adb shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -n 1)"
  case "$home" in
    android/* | '') ;;
    */*) adb shell am force-stop "${home%%/*}" >/dev/null 2>&1 || true ;;
  esac
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
}

if [ "${1:-}" != "--inside-emulators" ]; then
  firebase="firestore-tests/node_modules/.bin/firebase"
  [ -x "$firebase" ] || fail "Firebase CLI not found at $firebase — run 'npm ci' in firestore-tests/ first."
  [ -d functions/node_modules ] || fail "functions/node_modules is missing — run 'npm ci' in functions/ first."
  for apk in "$APP_APK" "$TEST_APK"; do
    [ -f "$apk" ] || fail "Missing $apk — run ./gradlew assembleDebug assembleDebugAndroidTest first."
  done
  if [ -n "${FIREBASE_JAVA_HOME:-}" ]; then
    export PATH="$FIREBASE_JAVA_HOME/bin:$PATH"
  fi
  exec "$firebase" emulators:exec \
    --only auth,firestore,functions,storage \
    --project demo-coplanly \
    "node tools/e2e/pairing-smoke.js && bash tools/virtual-tester/run-virtual-tester.sh --inside-emulators"
fi

# --- Inside the emulators -------------------------------------------------------------------
adb wait-for-device
adb logcat -G 16M >/dev/null 2>&1 || true

echo "::group::Install the app and the test APK"
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"
clear_system_dialogs
echo "::endgroup::"

instrumentation="$(adb shell pm list instrumentation | tr -d '\r' \
  | awk -v target="(target=$APP_ID)" 'index($0, target) && !found {
      sub(/^instrumentation:/, ""); sub(/ \(target=.*$/, ""); print; found = 1
    }' || true)"
[ -n "$instrumentation" ] || fail "No instrumentation targets $APP_ID after installing $TEST_APK"

mkdir -p "$OUT/virtual-tester" "$OUT/logs"

pull_variant() {
  local variant="$1"
  mkdir -p "$OUT/virtual-tester"
  adb exec-out run-as "$APP_ID" tar -cf - -C "$REMOTE" "$variant" 2>>"$OUT/logs/$variant-pull.txt" \
    | tar -xf - -C "$OUT/virtual-tester/" 2>>"$OUT/logs/$variant-pull.txt" || true
}

for variant in $VARIANTS; do
  base="$variant"
  wide=no
  case "$variant" in
    *-wide) wide=yes; base="${variant%-wide}" ;;
  esac
  theme="${base%%-*}"
  percent="${base##*-}"
  case "$percent" in
    '' | *[!0-9]*) fail "Variant $variant does not end in a font scale percentage" ;;
  esac
  scale="$(awk -v p="$percent" 'BEGIN { printf "%.2f", p / 100 }')"
  night=no
  [ "$theme" = dark ] && night=yes

  adb shell pm clear "$APP_ID" >/dev/null 2>&1 || true
  adb shell cmd uimode night "$night" || true
  adb shell settings put system font_scale "$scale" || true
  if [ "$wide" = yes ]; then
    adb shell wm size "$WIDE_SIZE" || true
    adb shell wm density "$WIDE_DENSITY" || true
  else
    adb shell wm size reset || true
    adb shell wm density reset || true
  fi
  rm -rf "${OUT:?}/virtual-tester/$variant"
  crashes=""

  for class in $classes; do
    echo "::group::Virtual tester — $variant — $class"
    clear_system_dialogs
    adb logcat -c || true
    log="$OUT/logs/$variant-$class"
    # `|| true`: a crashed class still leaves the steps it finished, and the next class runs.
    adb shell am instrument -w -r \
      -e coplanlyEmulatorHost "$HOST" \
      -e coplanlyVirtualTester true \
      -e coplanlyUiTourVariant "$variant" \
      -e class "$PACKAGE.$class" \
      "$instrumentation" | tr -d '\r' | tee "$log-instrument.txt" || true
    adb logcat -d -v time >"$log-logcat.txt" 2>&1 || true
    if grep -q "Process crashed" "$log-instrument.txt"; then
      echo "::warning::$class crashed the app process in $variant — see logs/$variant-$class-logcat.txt"
      crashes="$crashes $class"
    fi
    pull_variant "$variant"
    echo "::endgroup::"
    echo "::group::logcat — $variant — $class: steps and crashes"
    grep -E "I/E2E|AndroidRuntime" "$log-logcat.txt" | tail -n 200 || true
    echo "::endgroup::"
  done

  # A crash writes no step of its own; say it in the report, beside what the class did finish.
  mkdir -p "$OUT/virtual-tester/$variant"
  for class in $crashes; do
    scenario="${class%ScenarioTest}"
    printf '{"variant":"%s","scenario":"%s","method":"(process)","id":"crash","title":"The app keeps running","expected":"The scenario runs to its end","actual":"The app process crashed. See logs/%s-%s-logcat.txt","verdict":"failed","delivery":null,"screenshot":null,"findings":[],"startedAtMillis":0,"durationMs":0}\n' \
      "$variant" "$scenario" "$variant" "$class" >>"$OUT/virtual-tester/$variant/steps.jsonl"
  done
done

adb shell settings put system font_scale 1.0 || true
adb shell cmd uimode night no || true
adb shell wm size reset || true
adb shell wm density reset || true

# shellcheck disable=SC2086 # the variant list is word-split on purpose
node tools/virtual-tester/report.js "$OUT" $VARIANTS

count="$(cat "$OUT"/virtual-tester/*/steps.jsonl 2>/dev/null | grep -c '"verdict"' || true)"
echo "Virtual tester: $count steps recorded in $OUT"
[ "${count:-0}" -gt 0 ] || fail "The virtual tester recorded no step at all — see $OUT/logs/."
