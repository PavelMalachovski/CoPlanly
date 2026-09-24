#!/usr/bin/env node
/**
 * Decides which CI jobs a pull request needs, from the paths it changes. Used by the `changes`
 * job in .github/workflows/ci.yml:
 *
 *   git diff --name-only "$BASE_SHA"...HEAD | node tools/ci-changes.js >> "$GITHUB_OUTPUT"
 *
 * It prints four `key=value` lines: `android`, `e2e`, `screenshots` and `matrix` (the emulator
 * legs as a JSON array for the `instrumented` job's `strategy.matrix.include`). With `--all` it
 * prints the answer for "run everything", which is what a push to `main`, a manual run, or a diff
 * that could not be computed gets.
 *
 * One rule governs every list below: **a path wrongly left out of a "skip" list costs a few runner
 * minutes; a path wrongly put in one silently stops testing a real change.** So each skip is
 * narrow and each "run" list is broad, and `node --test tools/test/ci-changes.test.js` pins the
 * cases that matter. No dependencies.
 */
'use strict';

/** Paths that never need an Android build: docs, the server side, the rules and their tests. */
const NON_ANDROID = /^(docs\/|functions\/|firestore-tests\/|\.cursor\/|[^/]*\.md$|.*\.rules(\.simple)?$|firestore\.indexes\.json$|firebase\.json$|\.gitignore$|LICENSE$)/;

/** Paths the two-parent e2e job can never be affected by. */
const NON_E2E = /^(docs\/|\.cursor\/|[^/]*\.md$|\.gitignore$|LICENSE$)/;

/**
 * Android paths that are UI only, so the e2e job — the data layer against the real backend —
 * cannot be affected by them. `presentation/common/` is deliberately *not* here: the e2e parents
 * construct `ParentsSource`, which lives there.
 */
const UI_ONLY = /^(app\/src\/main\/java\/com\/coparently\/app\/presentation\/(?!common\/)|app\/src\/main\/res\/|app\/src\/test\/)/;

/** The build itself: a change here can change anything, so it runs everything. */
const BUILD = /^(build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|gradle\/|app\/build\.gradle\.kts$|app\/proguard|\.github\/workflows\/)/;

/** What the screenshot job renders, and what renders it. */
const SCREENSHOT_INPUTS = /^(app\/src\/main\/java\/com\/coparently\/app\/(presentation|domain)\/|app\/src\/main\/res\/|app\/src\/test\/java\/com\/coparently\/app\/screenshots\/|tools\/screenshot-gallery\.js$)/;

/**
 * What the API 26 and 16 KB emulator legs exist to catch beyond API 30: native libraries and the
 * encrypted database (`data/local/`), the manifest, the instrumented tests themselves, the
 * debug-only network config, and how the emulator step runs. A newer-API call elsewhere in the
 * code is lint's NewApi check first, on every pull request; these two legs are its runtime
 * confirmation, and they run on every push to `main`.
 */
const EMULATOR_SENSITIVE = /^(app\/src\/main\/AndroidManifest\.xml$|app\/src\/main\/jniLibs\/|app\/src\/main\/java\/com\/coparently\/app\/data\/local\/|app\/src\/androidTest\/|app\/src\/debug\/|tools\/with-screen-recording\.sh$)/;

/**
 * The emulator legs. API 30 always runs when Android does; the other two by the rule above.
 *
 * `record` turns on tools/with-screen-recording.sh's video, and only API 26 has it. On the API 30
 * image, screenrecord's software H.264 encoder aborted `media.codec` and surfaceflinger then
 * crashed in `eglCreateImageKHR` on the virtual display's buffers — "System has crashed" in the
 * middle of the suite, on a run whose tests were otherwise passing — and the 16 KB image died the
 * same way ("Process/System crashed"). A video is a convenience for reading a failure; it must
 * never be the failure. Turn it back on for a leg only with a green run to show for it.
 */
const LEG_30 = { 'api-level': 30, target: 'default', arch: 'x86_64', label: '', 'test-args': '', record: false };
const LEG_26 = { 'api-level': 26, target: 'default', arch: 'x86', label: '', 'test-args': '', record: true };
// MockK's inline-mocking agent does not dlopen on 16 KB pages, and every Hilt test mocks through
// it, so this leg runs the non-Hilt tests only (NativeLibrariesTest is what it exists for).
const LEG_16KB = {
  'api-level': 35,
  target: 'google_apis_ps16k',
  arch: 'x86_64',
  label: ', 16 KB pages',
  'test-args': '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=dagger.hilt.android.testing.HiltAndroidTest',
  record: false,
};
const FULL_MATRIX = [LEG_26, LEG_30, LEG_16KB];

/** The answer for "run everything". */
function everything() {
  return { android: true, e2e: true, screenshots: true, matrix: FULL_MATRIX };
}

/**
 * Decides the jobs for [paths], the files a pull request changes. An empty list is treated as
 * unknown and runs everything.
 */
function decide(paths) {
  const changed = paths.map((p) => p.trim()).filter(Boolean);
  if (changed.length === 0) return everything();

  const build = changed.some((p) => BUILD.test(p));
  const android = changed.some((p) => !NON_ANDROID.test(p));
  const e2e = build || changed.some((p) => !NON_E2E.test(p) && !UI_ONLY.test(p));
  const screenshots = android && (build || changed.some((p) => SCREENSHOT_INPUTS.test(p)));
  const fullMatrix = build || changed.some((p) => EMULATOR_SENSITIVE.test(p));
  return { android, e2e, screenshots, matrix: fullMatrix ? FULL_MATRIX : [LEG_30] };
}

/** The `$GITHUB_OUTPUT` lines for a decision. */
function format(decision) {
  return [
    `android=${decision.android}`,
    `e2e=${decision.e2e}`,
    `screenshots=${decision.screenshots}`,
    `matrix=${JSON.stringify(decision.matrix)}`,
  ].join('\n') + '\n';
}

if (require.main === module) {
  if (process.argv.includes('--all')) {
    process.stdout.write(format(everything()));
  } else {
    const input = require('fs').readFileSync(0, 'utf8');
    const decision = decide(input.split('\n'));
    process.stdout.write(format(decision));
    const legs = decision.matrix.map((leg) => leg['api-level']).join(', ');
    process.stderr.write(
      `android=${decision.android} e2e=${decision.e2e} screenshots=${decision.screenshots} ` +
        `emulators=[${legs}]\n`,
    );
  }
}

module.exports = { decide, format, everything, FULL_MATRIX, LEG_30 };
