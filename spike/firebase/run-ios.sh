#!/usr/bin/env bash
# Runs the spike's tests on the booted iOS simulator, inside the Firebase emulators.
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "${FIREBASE_JAVA_HOME:-}" ]; then
  export PATH="$FIREBASE_JAVA_HOME/bin:$PATH"
fi
exec ../../firestore-tests/node_modules/.bin/firebase emulators:exec \
  --only auth,firestore,functions,storage \
  --project demo-coplanly \
  "../../gradlew iosSimulatorArm64Test --continue"
