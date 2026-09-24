#!/usr/bin/env node
/**
 * Decides which CI jobs a pull request needs, from the paths it changes. Used by the `changes`
 * job in .github/workflows/ci.yml:
 *
 *   git diff --name-only "$BASE_SHA"...HEAD | node tools/ci-changes.js >> "$GITHUB_OUTPUT"
 *
 * It prints seven `key=value` lines: `android`, `e2e`, `screenshots`, `upgrade`, `matrix` (the
 * emulator legs as a JSON array for the `instrumented` job's `strategy.matrix.include`),
 * `r8runtime` (the minified build run on an emulator, `r8-runtime`) and `web` (the verification
 * page in a browser and the calendar feed through an RFC 5545 parser, `web`). With `--all` it
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
const NON_ANDROID = /^(docs\/|functions\/|firestore-tests\/|web-tests\/|\.cursor\/|[^/]*\.md$|.*\.rules(\.simple)?$|firestore\.indexes\.json$|firebase\.json$|\.gitignore$|LICENSE$)/;

/** Paths the two-parent e2e job can never be affected by. */
const NON_E2E = /^(docs\/|web-tests\/|\.cursor\/|[^/]*\.md$|\.gitignore$|LICENSE$)/;

/**
 * Android paths that are UI only, so the e2e job — the data layer against the real backend —
 * cannot be affected by them. `presentation/common/` is deliberately *not* here: the e2e parents
 * construct `ParentsSource`, which lives there.
 */
const UI_ONLY = /^(app\/src\/main\/java\/com\/coparently\/app\/presentation\/(?!common\/)|app\/src\/main\/res\/|app\/src\/test\/)/;

/** The build itself: a change here can change anything, so it runs everything. */
const BUILD = /^(build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|gradle\/|app\/build\.gradle\.kts$|app\/proguard|\.github\/workflows\/)/;

/**
 * What the screenshot job renders, what renders it, and what it compares against: the committed
 * baselines under `app/src/test/screenshots/` (written by the Regenerate workflow), so a commit
 * that changes only a baseline is verified against the code it was recorded from.
 */
const SCREENSHOT_INPUTS = /^(app\/src\/main\/java\/com\/coparently\/app\/(presentation|domain)\/|app\/src\/main\/res\/|app\/src\/test\/java\/com\/coparently\/app\/screenshots\/|app\/src\/test\/screenshots\/|tools\/screenshot-gallery\.js$)/;

/**
 * What the API 26 and 16 KB emulator legs exist to catch beyond API 30: native libraries and the
 * encrypted database (`data/local/`), the manifest, the instrumented tests themselves, the
 * debug-only network config, and how the emulator step runs. A newer-API call elsewhere in the
 * code is lint's NewApi check first, on every pull request; these two legs are its runtime
 * confirmation, and they run on every push to `main`.
 */
const EMULATOR_SENSITIVE = /^(app\/src\/main\/AndroidManifest\.xml$|app\/src\/main\/jniLibs\/|app\/src\/main\/java\/com\/coparently\/app\/data\/local\/|app\/src\/androidTest\/|app\/src\/debug\/|tools\/with-screen-recording\.sh$)/;

/**
 * What the `upgrade` job (the base build's data opened by this build) can be broken by: the
 * database and everything under `data/local/` (entities, migrations, SQLCipher, the preference
 * store), the Keystore wrapper, the builder in `DatabaseModule`, the telemetry answer's stored
 * form, the constructors the seed calls (`UpgradeFixture` lists them), the exported schemas, the
 * manifest, native libraries, the test runner, and the job's own tests and scripts.
 */
const UPGRADE_INPUTS = /^(app\/src\/main\/java\/com\/coparently\/app\/(data\/local\/|data\/security\/|data\/crashlytics\/|data\/telemetry\/|domain\/telemetry\/|di\/DatabaseModule\.kt$)|app\/schemas\/|app\/src\/main\/AndroidManifest\.xml$|app\/src\/main\/jniLibs\/|app\/src\/androidTest\/java\/com\/coparently\/app\/(upgrade\/|HiltTestRunner\.kt$)|tools\/upgrade\/|tools\/ci-background-build\.sh$|tools\/stop-emulator\.sh$)/;

/**
 * Paths the `upgrade` job is known not to depend on. Anything outside this list *and* outside
 * [UPGRADE_INPUTS] — an unfamiliar path — runs it: the rule at the top of this file.
 * `app/src/main/java/` as a whole is here because what reaches the stored data lives in the
 * directories [UPGRADE_INPUTS] names; a new package that stores something belongs there.
 */
const UPGRADE_UNAFFECTED = /^(docs\/|functions\/|firestore-tests\/|web\/|web-tests\/|\.cursor\/|[^/]*\.md$|.*\.rules(\.simple)?$|firestore\.indexes\.json$|firebase\.json$|\.gitignore$|LICENSE$|app\/src\/main\/java\/|app\/src\/main\/res\/|app\/src\/test\/|app\/src\/androidTest\/|app\/src\/debug\/|tools\/(test\/|e2e\/|(check-e2e-coverage|check-invariants|check-r8-mapping|ci-changes|ci-report|manual-test-plan|mocha-ci-reporter|screenshot-gallery|wrap-legal-page)\.js$|generate-[^/]+\.py$|with-screen-recording\.sh$))/;

/**
 * Android paths the R8 runtime probe (`r8-runtime` job) cannot be affected by: nothing in them is
 * handed to Gson, compiled into the probe, or read by R8 as a rule. Everything else Android runs
 * it — the data, domain and DI layers (the models, their converters and the constructors the probe
 * calls), `presentation/event/` (`EventDraft`), the manifest, the probe itself and its scripts —
 * and the build files, proguard rules and workflow run everything through `BUILD`. A new Gson call
 * in a skipped path is still caught the same day by `invariants` (check-invariants.js check 5),
 * and on `main` by the job itself.
 */
const NON_R8_RUNTIME = /^(app\/src\/main\/res\/|app\/src\/test\/|app\/src\/androidTest\/|app\/src\/debug\/|app\/schemas\/|app\/config\/detekt\/|app\/src\/main\/java\/com\/coparently\/app\/presentation\/(?!event\/)|tools\/(e2e\/|screenshot-gallery\.js$|ci-report\.js$|manual-test-plan\.js$|mocha-ci-reporter\.js$|wrap-legal-page\.js$|check-e2e-coverage\.js$|with-screen-recording\.sh$))/;

/**
 * Paths the `web` job (web-tests/: `web/verify/` in Chromium against the Functions emulator, and
 * the calendar feed through an independent RFC 5545 parser) is known not to depend on: docs, and
 * the Android app and its build. Everything else runs it — `web/`, `web-tests/`, `functions/`
 * (the callables and `calendar-feed.js` it exercises), `firebase.json` (the emulator ports),
 * `firestore-tests/` (whose lock file pins the Firebase CLI it starts the emulators with), the
 * workflow, and any path this file does not know.
 */
const NON_WEB = /^(docs\/|\.cursor\/|[^/]*\.md$|\.gitignore$|LICENSE$|app\/|build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|gradle\/|gradlew(\.bat)?$)/;

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
  return { android: true, e2e: true, screenshots: true, upgrade: true, matrix: FULL_MATRIX, r8runtime: true, web: true };
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
  const upgrade = build || changed.some((p) => UPGRADE_INPUTS.test(p) || !UPGRADE_UNAFFECTED.test(p));
  const r8runtime = build || changed.some((p) => !NON_ANDROID.test(p) && !NON_R8_RUNTIME.test(p));
  const web = changed.some((p) => !NON_WEB.test(p));
  return { android, e2e, screenshots, upgrade, matrix: fullMatrix ? FULL_MATRIX : [LEG_30], r8runtime, web };
}

/** The `$GITHUB_OUTPUT` lines for a decision. */
function format(decision) {
  return [
    `android=${decision.android}`,
    `e2e=${decision.e2e}`,
    `screenshots=${decision.screenshots}`,
    `upgrade=${decision.upgrade}`,
    `matrix=${JSON.stringify(decision.matrix)}`,
    `r8runtime=${decision.r8runtime}`,
    `web=${decision.web}`,
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
        `upgrade=${decision.upgrade} ` +
        `emulators=[${legs}] r8runtime=${decision.r8runtime} web=${decision.web}\n`,
    );
  }
}

module.exports = { decide, format, everything, FULL_MATRIX, LEG_30 };
