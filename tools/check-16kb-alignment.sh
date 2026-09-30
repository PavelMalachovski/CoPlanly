#!/usr/bin/env bash
# Fails when a 64-bit native library in the release APK is not ready for 16 KB memory pages,
# which Google Play requires for apps targeting Android 15+.
#
# The API 35 16 KB emulator leg only loads the libraries NativeLibrariesTest touches (SQLCipher,
# ML Kit); a library nothing loads there — CameraX's, behind the QR scanner — would fail only on
# a real 16 KB device or at Play upload. Two things are checked per arm64-v8a/x86_64 `.so`:
# every ELF LOAD segment is aligned to at least 16 KB, and, when the library is stored
# uncompressed, its offset in the APK is 16 KB-aligned (`zipalign -c -P 16`).
set -euo pipefail

apk=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1 || true)
if [ -z "$apk" ]; then
  echo "::error::No release APK under app/build/outputs/apk/release/ — run assembleRelease first"
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'lib/arm64-v8a/*' 'lib/x86_64/*' -d "$work" 2>/dev/null || true

failed=0
checked=0
while IFS= read -r so; do
  checked=$((checked + 1))
  while read -r align; do
    if [ $((align)) -lt 16384 ]; then
      echo "::error::${so#"$work"/} has a LOAD segment aligned to $align (< 0x4000)"
      failed=1
      break
    fi
  done < <(readelf -lW "$so" | awk '$1 == "LOAD" { print $NF }')
done < <(find "$work/lib" -name '*.so' 2>/dev/null)

zipalign=$(ls -d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)zipalign
if [ -x "$zipalign" ]; then
  if ! "$zipalign" -c -P 16 4 "$apk" > "$work/zipalign.txt" 2>&1; then
    grep -i "lib/" "$work/zipalign.txt" | head -n 20 || true
    echo "::error::zipalign -c -P 16 failed for $apk"
    failed=1
  fi
else
  echo "::warning::zipalign not found under \$ANDROID_HOME/build-tools; skipped the zip-offset check"
fi

echo "16 KB check: $checked 64-bit native libraries inspected in $(basename "$apk")"
exit "$failed"
