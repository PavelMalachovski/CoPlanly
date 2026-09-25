#!/usr/bin/env bash
# Checks that the app's baseline profile reached the release build (release audit, week 8).
#
# AndroidX and Compose bring profiles of their own, so a baseline.prof inside the APK proves
# nothing about ours: the merged human-readable profile must carry the app's rules, and the APK
# the compiled binary. Run after `./gradlew assembleRelease`.
set -euo pipefail

merged=$(find app/build/intermediates -path '*art_profile*' -name 'baseline-prof.txt' | head -n 1)
if [ -z "$merged" ]; then
  echo "::error::No merged ART profile under app/build/intermediates — did assembleRelease run?"
  exit 1
fi
if ! grep -q '^HSPLcom/coparently/app/' "$merged"; then
  echo "::error::$merged holds no rule for com/coparently/app — app/src/main/baseline-prof.txt did not merge"
  exit 1
fi
apk=$(find app/build/outputs/apk/release -name '*.apk' | head -n 1)
if [ -z "$apk" ]; then
  echo "::error::No release APK under app/build/outputs/apk/release"
  exit 1
fi
if ! unzip -l "$apk" | grep -q 'assets/dexopt/baseline.prof'; then
  echo "::error::$apk carries no assets/dexopt/baseline.prof"
  exit 1
fi
echo "Baseline profile: $(grep -c '^HSPLcom/coparently/app/' "$merged") app rules in $merged; compiled into $apk."
