#!/usr/bin/env node
/**
 * Decides which CI jobs a pull request needs, from the paths it changes. Used by the `changes`
 * job in .github/workflows/ci.yml:
 *
 *   git diff --name-only "$BASE_SHA"...HEAD | node tools/ci-changes.js >> "$GITHUB_OUTPUT"
 *
 * With `--event` it decides something else: not which jobs the paths need but whether the expensive
 * emulator jobs may run at all (below). With `--none` it prints the answer for "run nothing".
 *
 * It prints eight `key=value` lines: `android`, `e2e`, `screenshots`, `upgrade`, `matrix` (the
 * emulator legs as a JSON array for the `instrumented` job's `strategy.matrix.include`),
 * `r8runtime` (the minified build run on an emulator, `r8-runtime`), `web` (the verification
 * page in a browser and the calendar feed through an RFC 5545 parser, `web`) and `shared` (the
 * Kotlin Multiplatform modules' own tests and iOS compile, `shared` on Linux and `shared-ios` on
 * macOS). With
 * `--all` it
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
 * The Kotlin Multiplatform modules (MON-24), `shared/` (the domain), `shared-data/` (the data
 * layer, from phase 2) and `shared-ui/` (the UI, from phase 2's Task 9, but for the paths
 * [SHARED_UI_AS_APP] maps onto the app), their build files and their sources in every source set. The app compiles both, so a path here is an Android
 * path for every other output too, and it is treated as the build is: every Android job, every
 * emulator leg. Their commonMain holds the domain the app's screens render and the clients the
 * imports call, their stored models reach `StoredJson` and R8, and their Android targets are held
 * to minSdk 26, which is what the API 26 leg confirms at runtime. The `shared` output, which runs
 * both modules' jobs, is true for this and for [BUILD], whose root and module build files and
 * workflow can change what the modules compile against.
 */
const SHARED = /^shared(-data|-ui)?\//;

/**
 * `:shared-ui` (MON-24 phase 2, from Task 9) holds what the app's `res/` and `presentation/` held:
 * the strings (and, later, the fonts and drawables) in `composeResources/`, and the screens. A path
 * there is decided as the app path it stands for, so a screen-only or string-only change skips e2e
 * and the R8 probe as it did in `app/` — and the `shared` jobs still run, because the iPhone
 * compiles it. Its tests stand for the app's unit tests. Anything else in the module — its build
 * file, and Kotlin outside `presentation/` — is [SHARED]: the build.
 */
const SHARED_UI_AS_APP = [
  [/^shared-ui\/src\/commonMain\/composeResources\//, 'app/src/main/res/'],
  [/^shared-ui\/src\/(?![A-Za-z]*Test\/)[A-Za-z]+\/kotlin\/com\/coparently\/app\/presentation\//,
    'app/src/main/java/com/coparently/app/presentation/'],
  [/^shared-ui\/src\/[A-Za-z]*Test\//, 'app/src/test/'],
];

/** @return {string} a `:shared-ui` path as the app path it stands for ([SHARED_UI_AS_APP]); others unchanged */
function asAppPath(path) {
  for (const [re, to] of SHARED_UI_AS_APP) if (re.test(path)) return path.replace(re, to);
  return path;
}

/**
 * What the screenshot job renders, what renders it, and what it compares against: the committed
 * baselines under `app/src/test/screenshots/` (written by the Regenerate workflow), so a commit
 * that changes only a baseline is verified against the code it was recorded from. `utils/` is
 * here because `LocalizedDates` formats the dates and times the rendered components show.
 */
const SCREENSHOT_INPUTS = /^(app\/src\/main\/java\/com\/coparently\/app\/(presentation|domain|utils)\/|app\/src\/main\/res\/|app\/src\/test\/java\/com\/coparently\/app\/screenshots\/|app\/src\/test\/screenshots\/|tools\/screenshot-gallery\.js$)/;

/**
 * Where the database's sources live (MON-24 phase 2): `data/local/` and `data/security/` in the
 * app (the preference store still, and anything added there later), and in `shared-data` in every
 * source set — the entities, DAOs and `Converters` in `commonMain`, the migrations, SQLCipher and
 * the Keystore wrapper in `androidMain`, the iPhone's builder in `iosMain`. Any path under
 * `shared-data/` already runs every job (see [SHARED]); naming the moved directories here as well
 * keeps the database's own rules true should that ever be narrowed.
 */
const DATABASE_SOURCES = '(app\\/src\\/main\\/java|shared-data\\/src\\/[^/]+\\/kotlin)\\/com\\/coparently\\/app\\/';

/**
 * What the API 26 and 16 KB emulator legs exist to catch beyond API 30: native libraries and the
 * encrypted database (`data/local/`) with the Keystore wrapper that seals its passphrase
 * (`data/security/`), wherever their sources live ([DATABASE_SOURCES]), the manifest, the
 * instrumented tests themselves, the debug-only network config, and how the emulator step runs. A
 * newer-API call elsewhere in the code is lint's NewApi check first, on every pull request; these
 * two legs are its runtime confirmation, and they run on every push to `main`.
 */
const EMULATOR_SENSITIVE = new RegExp(
  '^(app\\/src\\/main\\/AndroidManifest\\.xml$|app\\/src\\/main\\/jniLibs\\/|' +
    DATABASE_SOURCES + '(data\\/local\\/|data\\/security\\/)|' +
    'app\\/src\\/androidTest\\/|app\\/src\\/debug\\/|tools\\/with-screen-recording\\.sh$)',
);

/**
 * What the `upgrade` job (the base build's data opened by this build) can be broken by: the
 * database and everything under `data/local/` (entities, migrations, SQLCipher, the preference
 * store), the Keystore wrapper, the builder in `DatabaseBuilder.kt` (with the file name beside it,
 * `DatabaseName.kt`) and `databaseModule`, wherever their sources live ([DATABASE_SOURCES]), the
 * telemetry answer's stored form, the constructors the seed calls (`UpgradeFixture` lists them),
 * the exported schemas, the manifest, native libraries, the test runner, and the job's own tests
 * and scripts. Also `app/src/test/resources/wire/current/`: the job runs the *base* build's
 * `WireContractTest` over what this branch writes, and a change there is exactly a change to the
 * wire format — a mapper change that alters a document cannot land without one, because
 * `CurrentWireFixturesTest` fails.
 */
const UPGRADE_INPUTS = new RegExp(
  '^(app\\/src\\/test\\/resources\\/wire\\/current\\/|' +
    DATABASE_SOURCES +
    '(data\\/local\\/|data\\/security\\/|data\\/crashlytics\\/|data\\/telemetry\\/|domain\\/telemetry\\/|' +
    'di\\/DatabaseBuilder\\.kt$|di\\/DatabaseName\\.kt$|di\\/koin\\/DatabaseModule\\.kt$)|' +
    'app\\/schemas\\/|app\\/src\\/main\\/AndroidManifest\\.xml$|app\\/src\\/main\\/jniLibs\\/|' +
    'app\\/src\\/androidTest\\/java\\/com\\/coparently\\/app\\/(upgrade\\/|KoinTestRunner\\.kt$)|' +
    'tools\\/upgrade\\/|tools\\/ci-background-build\\.sh$|tools\\/stop-emulator\\.sh$)',
);

/**
 * Paths the `upgrade` job is known not to depend on. Anything outside this list *and* outside
 * [UPGRADE_INPUTS] — an unfamiliar path — runs it: the rule at the top of this file.
 * `app/src/main/java/` as a whole is here because what reaches the stored data lives in the
 * directories [UPGRADE_INPUTS] names; a new package that stores something belongs there.
 */
const UPGRADE_UNAFFECTED = /^(docs\/|functions\/|firestore-tests\/|web\/|web-tests\/|\.cursor\/|[^/]*\.md$|.*\.rules(\.simple)?$|firestore\.indexes\.json$|firebase\.json$|\.gitignore$|LICENSE$|app\/src\/main\/java\/|app\/src\/main\/res\/|app\/src\/test\/|app\/src\/androidTest\/|app\/src\/debug\/|tools\/(test\/|e2e\/|(check-e2e-coverage|check-invariants|check-koin-aliases|check-r8-mapping|ci-changes|ci-report|manual-test-plan|mocha-ci-reporter|screenshot-gallery|wrap-legal-page)\.js$|generate-[^/]+\.py$|with-screen-recording\.sh$))/;

/**
 * Android paths the R8 runtime probe (`r8-runtime` job) cannot be affected by: nothing in them is
 * written through `StoredJson`, compiled into the probe, or read by R8 as a rule. Everything else Android runs
 * it — the data, domain and DI layers (the models, their converters and the constructors the probe
 * calls), `presentation/event/` (`EventDraft`), the manifest, the probe itself and its scripts —
 * and the build files, proguard rules and workflow run everything through `BUILD`. A new stored model
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
const NON_WEB = /^(docs\/|\.cursor\/|[^/]*\.md$|\.gitignore$|LICENSE$|app\/|shared(-data|-ui)?\/|build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|gradle\/|gradlew(\.bat)?$)/;

/**
 * Paths no CI job reads: the UI tour's request file, its workflow and its scripts. The tour is a
 * separate, manual workflow (`.github/workflows/ui-tour.yml`); asking for one — which is a commit
 * to `.github/ui-tour-request` — must not cost a pull request a full CI run. They are dropped
 * before any other rule looks, so a diff of nothing else runs nothing, and one with anything else
 * is decided by that. The tour's *tests* live in `app/src/androidTest/` and are not here: they
 * compile in every Android job, and the e2e job excludes them by name.
 */
const UI_TOUR_ONLY = /^(\.github\/ui-tour-request$|\.github\/workflows\/ui-tour\.yml$|tools\/ui-tour\/)/;

/**
 * The same for the virtual tester (`.github/workflows/virtual-tester.yml`), another manual
 * workflow: its request file, its workflow and its scripts. Its scenarios live in
 * `app/src/androidTest/java/com/coparently/app/qa/`, compile in every Android job, and run in
 * none (the e2e job filters its own package; elsewhere they skip for want of a host).
 */
const VIRTUAL_TESTER_ONLY = /^(\.github\/virtual-tester-request$|\.github\/workflows\/virtual-tester\.yml$|tools\/virtual-tester\/)/;

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
// MockK's inline-mocking agent does not dlopen on 16 KB pages, and every test that loads the fake
// Firebase override (`@UsesFakeFirebase`) mocks through it, so this leg runs the others only
// (NativeLibrariesTest is what it exists for).
const LEG_16KB = {
  'api-level': 35,
  target: 'google_apis_ps16k',
  arch: 'x86_64',
  label: ', 16 KB pages',
  'test-args': '-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.coparently.app.testing.UsesFakeFirebase',
  record: false,
};
const FULL_MATRIX = [LEG_26, LEG_30, LEG_16KB];

/** The pull request label that asks for the emulator jobs on a draft. */
const FULL_CI_LABEL = 'full-ci';

/**
 * Whether the emulator jobs (`instrumented`, `e2e`, `upgrade`, `r8-runtime`) may run, from the
 * event alone (owner decision, 1 November 2026). Each of those jobs still has its own path gate;
 * this is ANDed with it. On a pull request they run when it is not a draft or carries the
 * `full-ci` label; on anything else (a push to `main`, a manual run) they always do.
 *
 * `unrelatedLabel` marks the run a pull request's `labeled` event starts for a label other than
 * `full-ci`: nothing in it needs building, so the workflow runs no job from the paths and posts no
 * report over the real run's.
 *
 * @param {Object<string,string|undefined>} env EVENT_NAME, EVENT_ACTION, LABEL_NAME, PR_DRAFT
 *   ("true" for a draft) and PR_FULL_CI ("true" when the pull request carries the label)
 * @return {{emulators: boolean, unrelatedLabel: boolean, skippedOnDraft: boolean}}
 */
function decideEmulators(env) {
  if (env.EVENT_NAME !== 'pull_request') return { emulators: true, unrelatedLabel: false, skippedOnDraft: false };
  const unrelatedLabel = env.EVENT_ACTION === 'labeled' && env.LABEL_NAME !== FULL_CI_LABEL;
  const emulators = !unrelatedLabel && (env.PR_DRAFT !== 'true' || env.PR_FULL_CI === 'true');
  return { emulators, unrelatedLabel, skippedOnDraft: !emulators && !unrelatedLabel };
}

/** The `$GITHUB_OUTPUT` lines for [decideEmulators]. */
function formatEvent(decision) {
  return [
    `emulators=${decision.emulators}`,
    `unrelated_label=${decision.unrelatedLabel}`,
    `emulators_skipped_on_draft=${decision.skippedOnDraft}`,
  ].join('\n') + '\n';
}

/** The answer for "run everything". */
function everything() {
  return { android: true, e2e: true, screenshots: true, upgrade: true, matrix: FULL_MATRIX, r8runtime: true, web: true, shared: true };
}

/** The answer for a diff no job reads. */
function nothing() {
  return { android: false, e2e: false, screenshots: false, upgrade: false, matrix: [LEG_30], r8runtime: false, web: false, shared: false };
}

/**
 * Decides the jobs for [paths], the files a pull request changes. An empty list is treated as
 * unknown and runs everything.
 */
function decide(paths) {
  const all = paths.map((p) => p.trim()).filter(Boolean);
  if (all.length === 0) return everything();
  const kept = all.filter((p) => !UI_TOUR_ONLY.test(p) && !VIRTUAL_TESTER_ONLY.test(p));
  if (kept.length === 0) return nothing();
  const changed = kept.map(asAppPath);

  // A change to a shared module is decided as a change to the build is (see [SHARED]).
  const build = changed.some((p) => BUILD.test(p) || SHARED.test(p));
  const android = changed.some((p) => !NON_ANDROID.test(p));
  const e2e = build || changed.some((p) => !NON_E2E.test(p) && !UI_ONLY.test(p));
  const screenshots = android && (build || changed.some((p) => SCREENSHOT_INPUTS.test(p)));
  const fullMatrix = build || changed.some((p) => EMULATOR_SENSITIVE.test(p));
  const upgrade = build || changed.some((p) => UPGRADE_INPUTS.test(p) || !UPGRADE_UNAFFECTED.test(p));
  const r8runtime = build || changed.some((p) => !NON_ANDROID.test(p) && !NON_R8_RUNTIME.test(p));
  const web = changed.some((p) => !NON_WEB.test(p));
  // Every `:shared-ui` path, screens and strings included: the iPhone compiles them.
  const shared = build || kept.some((p) => /^shared-ui\//.test(p));
  return { android, e2e, screenshots, upgrade, matrix: fullMatrix ? FULL_MATRIX : [LEG_30], r8runtime, web, shared };
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
    `shared=${decision.shared}`,
  ].join('\n') + '\n';
}

if (require.main === module) {
  if (process.argv.includes('--all')) {
    process.stdout.write(format(everything()));
  } else if (process.argv.includes('--none')) {
    process.stdout.write(format(nothing()));
  } else if (process.argv.includes('--event')) {
    const decision = decideEmulators(process.env);
    process.stdout.write(formatEvent(decision));
    process.stderr.write(
      `emulator jobs: ${decision.emulators ? 'allowed' : 'not run'}` +
        `${decision.unrelatedLabel ? ' (an unrelated label was added)' : ''}` +
        `${decision.skippedOnDraft ? ' (draft pull request without the full-ci label)' : ''}\n`,
    );
  } else {
    const input = require('fs').readFileSync(0, 'utf8');
    const decision = decide(input.split('\n'));
    process.stdout.write(format(decision));
    const legs = decision.matrix.map((leg) => leg['api-level']).join(', ');
    process.stderr.write(
      `android=${decision.android} e2e=${decision.e2e} screenshots=${decision.screenshots} ` +
        `upgrade=${decision.upgrade} ` +
        `emulators=[${legs}] r8runtime=${decision.r8runtime} web=${decision.web} shared=${decision.shared}\n`,
    );
  }
}

module.exports = {
  decide, format, everything, nothing, decideEmulators, formatEvent, FULL_CI_LABEL, FULL_MATRIX, LEG_30,
  EMULATOR_SENSITIVE, UPGRADE_INPUTS, asAppPath,
};
