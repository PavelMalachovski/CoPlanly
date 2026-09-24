#!/usr/bin/env bash
# Runs the two-parent instrumented tests against the Firebase emulators.
#
# Needs: an Android emulator (or a phone — see below) visible to adb, `npm ci` done in
# `firestore-tests/` (for the Firebase CLI) and in `functions/` (for the code the Functions
# emulator loads), a JDK 21+ for the emulators and a JDK 17 for Gradle.
#
#   tools/e2e/run-two-parent-tests.sh
#
# What it does, in order, inside one `firebase emulators:exec` so the emulators live exactly as
# long as the tests:
#   1. `pairing-smoke.js` pairs two accounts from Node — fails in seconds, with a reason, if an
#      emulator is missing or `functions/index.js` does not load, instead of after an APK install.
#   2. `connectedDebugAndroidTest`, filtered to `com.coparently.app.e2e` and given the emulator host.
#
# Environment:
#   FIREBASE_JAVA_HOME     A JDK 21+ for the emulators, put first on PATH for them alone. Gradle
#                          keeps using JAVA_HOME. CI sets it; locally, leave it unset if `java` on
#                          PATH is already 21+.
#   COPLANLY_EMULATOR_HOST The address the *device* reaches the host on. Default `10.0.2.2`, the
#                          Android emulator's alias for the host loopback. For a physical phone,
#                          run `adb reverse tcp:9099 tcp:9099` (and 8080, 5001, 9199) and set 127.0.0.1.
#   GRADLE_ARGS            Extra Gradle arguments.
set -euo pipefail

cd "$(dirname "$0")/../.."

host="${COPLANLY_EMULATOR_HOST:-10.0.2.2}"
firebase="firestore-tests/node_modules/.bin/firebase"

if [ ! -x "$firebase" ]; then
  echo "Firebase CLI not found at $firebase — run 'npm ci' in firestore-tests/ first." >&2
  exit 1
fi
if [ ! -d functions/node_modules ]; then
  echo "functions/node_modules is missing — run 'npm ci' in functions/ first." >&2
  exit 1
fi
if [ -n "${FIREBASE_JAVA_HOME:-}" ]; then
  export PATH="$FIREBASE_JAVA_HOME/bin:$PATH"
fi

# The UI tour (tools/ui-tour/) lives in the same package but is not a test of anything: it runs
# only with `-e coplanlyUiTour true`, and would otherwise report as skipped, which the e2e job
# counts as a failure. So its classes are excluded by name rather than left to skip.
ui_tour_classes="com.coparently.app.e2e.UiTourTest,com.coparently.app.e2e.UiTourOnboardingTest"

gradle_command="./gradlew connectedDebugAndroidTest \
-Pandroid.testInstrumentationRunnerArguments.package=com.coparently.app.e2e \
-Pandroid.testInstrumentationRunnerArguments.notClass=$ui_tour_classes \
-Pandroid.testInstrumentationRunnerArguments.coplanlyEmulatorHost=$host ${GRADLE_ARGS:-}"

exec "$firebase" emulators:exec \
  --only auth,firestore,functions,storage \
  --project demo-coplanly \
  "node tools/e2e/pairing-smoke.js && $gradle_command"
