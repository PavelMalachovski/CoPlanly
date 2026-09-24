#!/usr/bin/env bash
# The UI tour: full-screen screenshots of the real app, with a realistic family in it, in several
# variants (theme, language, font scale) — for a design review without a phone. Run by
# .github/workflows/ui-tour.yml; not part of CI.
#
#   tools/ui-tour/run-ui-tour.sh            # after ./gradlew assembleDebug assembleDebugAndroidTest
#
# Needs what tools/e2e/run-two-parent-tests.sh needs: an Android emulator on adb, `npm ci` done in
# `firestore-tests/` (the Firebase CLI) and `functions/`, and a JDK 21+ for the emulators
# (FIREBASE_JAVA_HOME). It starts Auth, Firestore, Functions and Storage with
# `firebase emulators:exec` and, inside, calls itself with --inside-emulators, which:
#
#   1. installs the app and test APKs (built beforehand — no Gradle here, so the three runs share
#      one install and the app's data survives until this script clears it);
#   2. for each variant `<theme>-<language>-<font scale %>[-wide]`:
#        - `pm clear` the app (each run signs up a new Alice; nothing of the last one may show),
#        - sets the device's night mode and font scale (`cmd uimode night`, `font_scale`), and for
#          a `-wide` variant a 1280 × 800 dp display (`wm size` and `wm density`: a tablet held
#          sideways, where the navigation rail and the detail screens' width cap show),
#        - `am instrument`s UiTourTest and UiTourOnboardingTest with `-e coplanlyUiTour true` and
#          `-e coplanlyUiTourVariant <variant>` (the test switches the app's theme and language),
#        - streams files/ui-tour/<variant>/ out of the app's data dir with `run-as` — the PNGs
#          and manifest.json UiTourCamera writes there (adb pull cannot read an app's files);
#   3. writes index.html (tools/ui-tour/gallery.js).
#
# `am instrument` is used rather than connectedDebugAndroidTest because Gradle uninstalls the app
# after a connected run, and the screenshots live in the app's external files directory.
#
# Output ($UI_TOUR_OUT, default build/ui-tour-site): ui-tour/<variant>/*.png + manifest.json,
# index.html, and logs/ (instrumentation output and logcat per variant — not published).
# Exits non-zero only when no screenshot at all was taken; a screen the tour could not reach is in
# the manifest as skipped, with the reason.
#
# Environment:
#   UI_TOUR_VARIANTS        Space-separated variants.
#                           Default: light-en-100 dark-en-100 light-ru-130 light-en-100-wide.
#   UI_TOUR_OUT             Output directory. Default: build/ui-tour-site.
#   COPLANLY_EMULATOR_HOST  The host as the device reaches it. Default 10.0.2.2 (see the e2e script).
#   FIREBASE_JAVA_HOME      A JDK 21+ for the emulators.
#   APP_APK, TEST_APK       The APKs. Default: the debug build's.
set -euo pipefail

cd "$(dirname "$0")/../.."

APP_ID="${APP_ID:-app.coplanly}"
APP_APK="${APP_APK:-app/build/outputs/apk/debug/app-debug.apk}"
TEST_APK="${TEST_APK:-app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk}"
OUT="${UI_TOUR_OUT:-build/ui-tour-site}"
VARIANTS="${UI_TOUR_VARIANTS:-light-en-100 dark-en-100 light-ru-130 light-en-100-wide}"
WIDE_SIZE="2560x1600"   # pixels; at WIDE_DENSITY, 1280 x 800 dp
WIDE_DENSITY="320"
HOST="${COPLANLY_EMULATOR_HOST:-10.0.2.2}"
CLASSES="com.coparently.app.e2e.UiTourTest,com.coparently.app.e2e.UiTourOnboardingTest"
REMOTE="files/ui-tour"  # relative to the app's data dir, which is where run-as starts

fail() {
  echo "::error::$*"
  exit 1
}

# A system dialog takes the window focus from the app, and every Back the tour presses then waits
# for a focus that never returns. The week-5 tour stopped at its third screen in every variant
# behind "Pixel Launcher isn't responding", raised while the emulator booted and never answered.
# The tour reviews the app, not the runner's load, so error dialogs are hidden for the session
# (`hide_error_dialogs`, which the system reads as it changes) and an open one is closed: stopping
# the launcher dismisses its dialog with it, and the app on top never needs the launcher; the
# close-dialogs broadcast answers the rest.
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
    "node tools/e2e/pairing-smoke.js && bash tools/ui-tour/run-ui-tour.sh --inside-emulators"
fi

# --- Inside the emulators -------------------------------------------------------------------
adb wait-for-device
adb logcat -G 16M >/dev/null 2>&1 || true

echo "::group::Install the app and the test APK"
adb install -r -t "$APP_APK"
adb install -r -t "$TEST_APK"
clear_system_dialogs
echo "::endgroup::"

# "instrumentation:app.coplanly.test/com.coparently.app.HiltTestRunner (target=app.coplanly)"
instrumentation="$(adb shell pm list instrumentation | tr -d '\r' \
  | awk -v target="(target=$APP_ID)" 'index($0, target) && !found {
      sub(/^instrumentation:/, ""); sub(/ \(target=.*$/, ""); print; found = 1
    }' || true)"
[ -n "$instrumentation" ] || fail "No instrumentation targets $APP_ID after installing $TEST_APK"

mkdir -p "$OUT/ui-tour" "$OUT/logs"

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

  echo "::group::UI tour — $variant (night $night, font scale $scale, wide $wide)"
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
  clear_system_dialogs
  adb logcat -c || true
  # `|| true`: a crashed run still leaves whatever it captured, and the manifest says how far it got.
  adb shell am instrument -w -r \
    -e coplanlyEmulatorHost "$HOST" \
    -e coplanlyUiTour true \
    -e coplanlyUiTourVariant "$variant" \
    -e class "$CLASSES" \
    "$instrumentation" | tr -d '\r' | tee "$OUT/logs/$variant-instrument.txt" || true
  adb logcat -d -v time >"$OUT/logs/$variant-logcat.txt" 2>&1 || true
  rm -rf "${OUT:?}/ui-tour/$variant"
  # The tour writes into the app's internal files dir (UiTourCamera), which only the app's own uid
  # may read; the debug build is debuggable, so `run-as` streams it out as that uid.
  mkdir -p "$OUT/ui-tour"
  adb exec-out run-as "$APP_ID" tar -cf - -C "$REMOTE" "$variant" 2>"$OUT/logs/$variant-pull.txt" \
    | tar -xf - -C "$OUT/ui-tour/" 2>>"$OUT/logs/$variant-pull.txt" || true
  if [ -z "$(find "$OUT/ui-tour/$variant" -name '*.png' 2>/dev/null | head -n 1)" ]; then
    echo "::warning::Nothing pulled for $variant from $REMOTE/$variant — see logs/$variant-pull.txt"
  fi
  echo "::endgroup::"
  echo "::group::logcat — $variant: tour steps, skips, crashes"
  grep -E "I/E2E|W/UiTour|AndroidRuntime" "$OUT/logs/$variant-logcat.txt" | tail -n 300 || true
  echo "::endgroup::"
done

adb shell settings put system font_scale 1.0 || true
adb shell cmd uimode night no || true
adb shell wm size reset || true
adb shell wm density reset || true

# shellcheck disable=SC2086 # the variant list is word-split on purpose
node tools/ui-tour/gallery.js "$OUT" $VARIANTS

count="$(find "$OUT/ui-tour" -name '*.png' | wc -l | tr -d ' ')"
echo "UI tour: $count screenshots in $OUT"
[ "$count" -gt 0 ] || fail "The UI tour took no screenshot at all — see $OUT/logs/."
