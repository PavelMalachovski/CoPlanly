#!/usr/bin/env bash
# Runs the spike's instrumented tests on the booted Android emulator, inside the Firebase emulators.
# Needs `npm ci` in firestore-tests/ (the pinned Firebase CLI) and `npm install` in functions/.
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "${FIREBASE_JAVA_HOME:-}" ]; then
  export PATH="$FIREBASE_JAVA_HOME/bin:$PATH"
fi
exec ../../firestore-tests/node_modules/.bin/firebase emulators:exec \
  --only auth,firestore,functions,storage \
  --project demo-coplanly \
  "../../gradlew connectedDebugAndroidTest --continue"
