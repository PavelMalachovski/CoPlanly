#!/usr/bin/env bash
# Waits until the connected emulator's system services answer, before anything is installed on it.
#
# `sys.boot_completed` reads 1 before every service is published: a snapshot boot came up without
# its input service (PR #130, "No service published for: input"), and on 24 November 2026 an API 35
# instrumented leg without its package service, so installing the APKs failed ("cmd: Can't find
# service: package") and no test ran. Every emulator job runs this first, **before** it touches its
# $STARTED_FILE: an emulator that never gets its services counts as one that never started, and the
# job boots it again, cold, as "Did … start?" in ci.yml does for a boot that died outright.
#
# Usage: wait-for-device-services.sh [seconds], 180 by default. Exits 1 when time runs out.
set -u
limit="${1:-180}"
deadline=$(( $(date +%s) + limit ))
services=(package activity window input)
while :; do
  missing=()
  for service in "${services[@]}"; do
    adb shell service check "$service" 2>/dev/null | grep -q ": found" || missing+=("$service")
  done
  # The package manager answering a query, not only being published, is what an install needs.
  if [ "${#missing[@]}" -eq 0 ] && adb shell pm path android 2>/dev/null | grep -q '^package:'; then
    echo "The emulator's services answer: ${services[*]}."
    exit 0
  fi
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "::error::The emulator's services did not answer within ${limit} s (missing: ${missing[*]:-pm path android})."
    exit 1
  fi
  sleep 3
done
