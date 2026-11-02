#!/usr/bin/env bash
# Clears system dialogs off the connected emulator before UI tests run.
#
# A system dialog takes the window focus from the app, and an Espresso or Compose action then waits
# for a focus that never returns. It happens after a cold boot, which every emulator job falls back
# to when its snapshot boot dies ("Did … start?" in ci.yml): PR #138's e2e run lost two on-screen
# tests to it ("has-window-focus=false" on MainActivity), and on 2 November 2026 an API 35
# instrumented leg lost LocalDatePickerDialogTest the same way after a cold boot. The UI tour met it
# behind "Pixel Launcher isn't responding". Error dialogs are hidden for the session, which the
# system reads as it changes, and an open one is closed — stopping the launcher dismisses its dialog
# with it, the app under test never needs the launcher, and the close-dialogs broadcast answers the
# rest. Every step is best effort: a command an image lacks changes nothing.
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
home_activity="$(adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -n 1)" ||
  home_activity=""
case "$home_activity" in
  android/* | '') ;;
  */*) adb shell am force-stop "${home_activity%%/*}" >/dev/null 2>&1 || true ;;
esac
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
exit 0
