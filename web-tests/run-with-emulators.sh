#!/usr/bin/env bash
# Runs web-tests/ (the verification page in Chromium, and the calendar feed through an RFC 5545
# parser) inside `firebase emulators:exec`, so the emulators live exactly as long as the tests.
#
# Needs: `npm ci` in web-tests/ (Playwright, ical.js), in functions/ (the code the Functions
# emulator loads) and in firestore-tests/ (the Firebase CLI — pinned there, so the rules job, the
# e2e job and this one run one emulator build), a JDK 21+ for the Firestore emulator, and a
# Chromium Playwright can find (`npx playwright install chromium` once, or PLAYWRIGHT_BROWSERS_PATH).
#
#   web-tests/run-with-emulators.sh [playwright args…]
#
# Environment:
#   FIREBASE_JAVA_HOME  A JDK 21+ for the emulators, put first on PATH for them alone. CI sets it;
#                       locally, leave it unset if `java` on PATH is already 21+.
#
# Behind an HTTP(S) proxy that does not honour NO_PROXY for loopback, the Functions emulator's
# calls to the Firestore emulator fail with "Unable to parse JSON" while registering triggers.
# Unset the proxy variables for this command: nothing here needs the network.
set -euo pipefail

cd "$(dirname "$0")/.."

firebase="firestore-tests/node_modules/.bin/firebase"
if [ ! -x "$firebase" ]; then
  echo "Firebase CLI not found at $firebase — run 'npm ci' in firestore-tests/ first." >&2
  exit 1
fi
if [ ! -d functions/node_modules ]; then
  echo "functions/node_modules is missing — run 'npm ci' in functions/ first." >&2
  exit 1
fi
if [ ! -d web-tests/node_modules ]; then
  echo "web-tests/node_modules is missing — run 'npm ci' in web-tests/ first." >&2
  exit 1
fi
if [ -n "${FIREBASE_JAVA_HOME:-}" ]; then
  export PATH="$FIREBASE_JAVA_HOME/bin:$PATH"
fi

# Storage is not started: neither the page nor the feed touches it.
exec "$firebase" emulators:exec \
  --only auth,firestore,functions \
  --project demo-coplanly \
  "cd web-tests && npx playwright test $*"
