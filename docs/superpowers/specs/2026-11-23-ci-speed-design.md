# CI speed: the iPhone half — design

**Date:** 23 November 2026. **Branch:** `chore/ci-speed`.

## Why

A pull request that touches shared code waits about 2 h 20 min for CI, and the Android half is done
after 11 minutes. Everything after that is macOS, on one critical path, the `ios-app` job:

| `ios-app` step (run of #175) | Minutes | Of which |
|---|---|---|
| iPhone tests on the emulators | 45 | 33 compiling `FirebaseFirestoreInternal` |
| The iPhone tour | 30 | |
| The native shell walk and the push test | 27 | |
| The tour in dark, Increase Contrast, large text | 18 | |
| Build the app | 13 | the Swift package cache missed |

`shared-ios` (in parallel, 34–47 min) compiles the same pods for 22 minutes. Both jobs restore the
pods' cache under an exact key, and `xcodebuild` rebuilds them anyway: the plugin's `pod install`
runs again first and rewrites the Pods project, so the cached derived data never counts as up to
date.

**Goal:** a pull request's CI in well under an hour, with the same checks run. **Non-goals:**
fewer tests, a different test framework, Android changes. **Anti-goals:** a check that silently
stops running (a skip must read as a skip), a cache that can serve pods built from another spec.

## The four changes (owner: "apply all four", 23 November 2026)

### 1. The Firebase pods are built once per spec, not once per run

When the pods' cache is restored under its exact key, the products it holds
(`shared-data/build/cocoapods`: the built frameworks and the plugin's `buildSettings` files) are
linked as they are, and the plugin's pod tasks are not run. A Gradle property says so,
`-Pcoplanly.prebuiltPods=true`, which `shared-data/build.gradle.kts` reads to disable the
`podGen*`, `podInstall*`, `podSetupBuild*` and `podBuild*` tasks. CI passes it only on an exact hit
(`steps.pods.outputs.cache-hit == 'true'`); a miss or a near miss builds as today and saves the
cache. The key is unchanged: the Xcode version and the three lines that decide the pods.

**Approaches considered.** (a) This switch — small, keeps CocoaPods and the GitLive setup as they
are; the cost is a property CI must pass correctly. (b) Firebase's prebuilt XCFrameworks from the
11.8.0 release in place of the pods — no compile even when cold, but it replaces the CocoaPods plugin
for the test link and `gatherFirebaseFrameworksForTests`, a larger change. (c) Make `xcodebuild`
incremental across runs — what the cache was meant to do and does not; depends on `pod install`'s
output mtimes, which we do not control. **Chosen: (a)**; (b) stays an option if a cold build ever
matters.

### 2. `ios-app` becomes parallel jobs

- **`ios-build`**: generates the project, builds the app and its UI tests
  (`build-for-testing`), launches it (the 20-second check), uploads the build products as an
  artifact.
- **`ios-tests`**: the Kotlin tests on the simulator against the emulators and the write gate (the
  former "iPhone tests on the emulators" and its two checks). Needs no app build: starts at once.
- **`ios-shell`**: after `ios-build`, the shell walk and the push test.
- **`ios-tour`** and **`ios-tour-appearance`**: after `ios-build`, one each, only when change 3
  says so.

Each job keeps today's artifacts, check runs and failure rules; `report` names every job in its
`needs`. At most five macOS jobs run at once (`shared-ios`, `ios-tests`, `ios-shell` and the two
tours), the free plan's limit.

### 3. The tours run when a pull request touches what they show

A new `changes` output, `ios_tour`: true on `main`, on a manual run, and on a pull request whose
diff reaches what is drawn — `shared-ui/src/*/composeResources/`, `shared-ui/src/*Main/` under
`presentation/` and the theme, `iosApp/` (but its tests and scripts other than the tour's), the
tour's seed and the build. Data, server, Android-only and test-only changes skip it, reported as a
skip with its reason in the summary. Tested in `tools/test/ci-changes.test.js`.

### 4. Drafts and the Swift package cache

- `shared-ios` and the iPhone jobs join the four Android emulator jobs behind `emulators` (a draft
  runs them only with `full-ci`).
- The Swift package cache is restored and saved explicitly (`cache/restore` and `cache/save`), saved
  after a successful resolve rather than only when the whole job passes, so a red run still warms it.

## What changes beside the workflow

- `tools/ci-changes.js` (+ tests), `tools/ci-report.js` if it names `ios-app`.
- `shared-data/build.gradle.kts` (the property).
- `iosApp/scripts/simulator.sh` only if a split job needs a command it lacks.
- CLAUDE.md: the `ios-app` paragraph, the tour's "on every run" (owner decision reversed: now when the
  diff reaches the screens), the draft paragraph, the shared modules' cache paragraph.

## Left to the builder

The job and step names, how the build products travel (one artifact of
`iosApp/build/Build/Products`, restored at the same path so `test-without-building` finds it), and
the exact path list for `ios_tour`.

## How it is verified

On this branch's pull request: a first run warms the pods cache (or hits it), a second run shows
`podBuild*` skipped, and the per-job times. `ci-changes` by its unit tests. A run with the `full-ci`
label on a draft, and a data-only test commit to see the tours skip.

## Revision after the first run (23 November 2026)

The first run showed two things. A job waiting for a macOS runner spends its own time limit:
`ios-tour-appearance` waited its whole 45 minutes and was cancelled before its first step, because
the free plan runs five macOS jobs at once and the run held five beside a run on `main`. And
`ios-tests` and `shared-ios` each built the pods cold and each ran `:shared-data`'s tests. So
`ios-tests` joined `shared-ios` (one pods build, one job; at most four macOS jobs of a run at once),
and every macOS job's limit leaves room for a queue.

## Round two (24 November 2026, owner: "all three")

What was left on the critical path: `ios-build` (17 min, 6 of them its launch check) and then
`ios-shell` (35) or the tour (41), each spending 4 to 5 minutes booting a cold simulator.

1. The launch check moved from `ios-build` to `ios-shell`, whose warm-up launched the app anyway.
2. Every UI test job starts booting its simulator right after the checkout.
3. The tour runs in two halves on two runners (`COPLANLY_TOUR_SHARD`), the visits dealt out in turn.
