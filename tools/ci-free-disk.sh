#!/usr/bin/env bash
# Frees disk space on a GitHub-hosted Ubuntu runner before an emulator job (November 2026).
#
# The emulator jobs restore Gradle caches and an AVD, build two APKs and unpack a system image. On
# 2 November 2026 two of them died with "No space left on device" while the SDK manager was
# preparing the emulator or a system image, before any test ran. This removes toolchains the jobs
# never use. It touches nothing the jobs read: not the Android SDK, the JDKs, Node or the Gradle
# home. Every path is optional, so a runner image that no longer ships one is fine.
set -uo pipefail

df -h / | tail -n 1 | awk '{print "Disk before: " $4 " free"}'
for path in \
  /usr/share/dotnet \
  /usr/local/.ghcup \
  /opt/ghc \
  /usr/local/share/boost \
  /usr/share/swift \
  /usr/local/share/powershell \
  /opt/hostedtoolcache/CodeQL \
  /opt/hostedtoolcache/PyPy \
  /opt/hostedtoolcache/Ruby \
  /opt/hostedtoolcache/go; do
  [ -e "$path" ] && sudo rm -rf "$path"
done
df -h / | tail -n 1 | awk '{print "Disk after: " $4 " free"}'
exit 0
