/**
 * Tests for tools/ci-changes.js. Run with `node --test tools/test/*.test.js`; no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { decide, format, everything } = require('../ci-changes.js');

const legs = (decision) => decision.matrix.map((leg) => leg['api-level']);

test('an unknown diff runs everything', () => {
  assert.deepEqual(decide([]), everything());
  assert.deepEqual(decide(['', '  ']), everything());
  assert.deepEqual(legs(everything()), [26, 30, 35]);
});

test('docs only runs no Android job and no e2e', () => {
  const d = decide(['docs/ROADMAP.md', 'CLAUDE.md']);
  assert.equal(d.android, false);
  assert.equal(d.e2e, false);
  assert.equal(d.screenshots, false);
});

test('functions and rules skip Android but keep e2e, the one job that runs them together', () => {
  const d = decide(['functions/index.js', 'firestore.rules']);
  assert.equal(d.android, false);
  assert.equal(d.e2e, true);
  assert.equal(d.screenshots, false);
});

test('a screen-only change skips e2e and runs screenshots on API 30 alone', () => {
  const d = decide([
    'app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt',
    'app/src/main/res/values-de/home_strings.xml',
  ]);
  assert.equal(d.android, true);
  assert.equal(d.e2e, false);
  assert.equal(d.screenshots, true);
  assert.deepEqual(legs(d), [30]);
});

test('presentation/common is not UI-only: the e2e parents construct ParentsSource', () => {
  const d = decide(['app/src/main/java/com/coparently/app/presentation/common/ParentsSource.kt']);
  assert.equal(d.e2e, true);
});

test('a data-layer change runs e2e, skips screenshots, and stays on API 30', () => {
  const d = decide(['app/src/main/java/com/coparently/app/data/sync/SyncService.kt']);
  assert.equal(d.e2e, true);
  assert.equal(d.screenshots, false);
  assert.deepEqual(legs(d), [30]);
});

test('a domain change runs screenshots: the components render domain models', () => {
  assert.equal(decide(['app/src/main/java/com/coparently/app/domain/custody/CustodyModel.kt']).screenshots, true);
});

test('a baseline-only change runs screenshots, not e2e, on API 30 alone', () => {
  const d = decide(['app/src/test/screenshots/home_stat_tiles/en_light_fs100_default.png']);
  assert.equal(d.android, true);
  assert.equal(d.screenshots, true);
  assert.equal(d.e2e, false);
  assert.deepEqual(legs(d), [30]);
});

test('a non-screenshot unit test does not run screenshots', () => {
  assert.equal(decide(['app/src/test/java/com/coparently/app/domain/FooTest.kt']).screenshots, false);
});

test('the database, the manifest and the instrumented tests run all three emulators', () => {
  for (const path of [
    'app/src/main/java/com/coparently/app/data/local/security/EncryptedDatabase.kt',
    'app/src/main/AndroidManifest.xml',
    'app/src/androidTest/java/com/coparently/app/data/local/security/NativeLibrariesTest.kt',
    'app/src/debug/res/xml/network_security_config.xml',
    'tools/with-screen-recording.sh',
  ]) {
    assert.deepEqual(legs(decide([path])), [26, 30, 35], path);
  }
});

test('the build and the workflow run every Android job; the workflow runs the web job too', () => {
  for (const path of [
    'app/build.gradle.kts',
    'gradle/libs.versions.toml',
    'gradle.properties',
    '.github/workflows/ci.yml',
    'app/proguard-rules.pro',
  ]) {
    const { web, ...android } = decide([path]);
    const { web: _all, ...everythingAndroid } = everything();
    assert.deepEqual(android, everythingAndroid, path);
    assert.equal(web, path.startsWith('.github/'), path);
  }
});

test('an unfamiliar path is never skipped by the Android gate', () => {
  const d = decide(['tools/something-new.js']);
  assert.equal(d.android, true);
  assert.equal(d.e2e, true);
});

test('format writes one GITHUB_OUTPUT line per key, the matrix as one-line JSON', () => {
  const lines = format(decide(['app/src/main/res/values/strings.xml'])).trim().split('\n');
  assert.deepEqual(lines.map((l) => l.split('=')[0]),
    ['android', 'e2e', 'screenshots', 'upgrade', 'matrix', 'r8runtime', 'web']);
  const matrix = JSON.parse(lines[4].slice('matrix='.length));
  assert.equal(matrix[0]['api-level'], 30);
});

test('only API 26 records the screen: screenrecord crashed the API 30 and 16 KB images', () => {
  const recording = everything().matrix.filter((leg) => leg.record).map((leg) => leg['api-level']);
  assert.deepEqual(recording, [26]);
  for (const leg of everything().matrix) assert.equal(typeof leg.record, 'boolean');
});

test('the upgrade job runs on the database, the stored preferences, the schemas and its own files', () => {
  for (const path of [
    'app/src/main/java/com/coparently/app/data/local/DatabaseMigrations.kt',
    'app/src/main/java/com/coparently/app/data/local/entity/EventEntity.kt',
    'app/src/main/java/com/coparently/app/data/local/preferences/EncryptedPreferences.kt',
    'app/src/main/java/com/coparently/app/data/local/security/DatabaseKey.kt',
    'app/src/main/java/com/coparently/app/data/security/EncryptionManager.kt',
    'app/src/main/java/com/coparently/app/data/crashlytics/CrashlyticsManager.kt',
    'app/src/main/java/com/coparently/app/domain/telemetry/TelemetryConsent.kt',
    'app/src/main/java/com/coparently/app/di/DatabaseModule.kt',
    'app/schemas/com.coparently.app.data.local.CoPlanlyDatabase/44.json',
    'app/src/main/AndroidManifest.xml',
    'app/src/androidTest/java/com/coparently/app/upgrade/UpgradeSeedTest.kt',
    'app/src/androidTest/java/com/coparently/app/HiltTestRunner.kt',
    'tools/upgrade/run-upgrade-test.sh',
    'tools/stop-emulator.sh',
    // A wire-format change: the base build's WireContractTest reads the new documents in this job.
    'app/src/test/resources/wire/current/events/sync-upload.json',
    'app/build.gradle.kts',
    'gradle/libs.versions.toml',
    '.github/workflows/ci.yml',
  ]) {
    assert.equal(decide([path]).upgrade, true, path);
  }
});

test('the upgrade job skips what cannot reach stored data', () => {
  for (const path of [
    'docs/DEVICE-CHECKLIST.md',
    'functions/index.js',
    'firestore.rules',
    'app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt',
    'app/src/main/java/com/coparently/app/data/sync/SyncService.kt',
    'app/src/main/res/values/strings.xml',
    'app/src/test/java/com/coparently/app/domain/FooTest.kt',
    // Another build's documents, read by this build's own tests: build-test runs those.
    'app/src/test/resources/wire/events/older-build.json',
    'app/src/test/java/com/coparently/app/wire/WireContractTest.kt',
    'app/src/androidTest/java/com/coparently/app/e2e/TwoParentChatTest.kt',
    'tools/ci-report.js',
    'tools/test/ci-changes.test.js',
  ]) {
    assert.equal(decide([path]).upgrade, false, path);
  }
});

test('an unfamiliar path runs the upgrade job', () => {
  assert.equal(decide(['tools/something-new.sh']).upgrade, true);
  assert.equal(decide(['app/src/main/assets/seed.db']).upgrade, true);
  assert.equal(everything().upgrade, true);
});

test('the R8 runtime probe runs for the models, their converters, the probe and its scripts', () => {
  for (const path of [
    'app/src/main/java/com/coparently/app/domain/model/MedicalProfile.kt',
    'app/src/main/java/com/coparently/app/data/repository/ChildInfoRepositoryImpl.kt',
    'app/src/main/java/com/coparently/app/data/local/Converters.kt',
    'app/src/main/java/com/coparently/app/di/SerializationModule.kt',
    'app/src/main/java/com/coparently/app/presentation/event/EventViewModel.kt',
    'app/src/main/AndroidManifest.xml',
    'app/src/r8Test/java/com/coparently/app/r8probe/R8GsonProbe.kt',
    'app/proguard-r8test.pro',
    'tools/run-r8-probe.sh',
    'tools/check-r8-probe.js',
    'tools/check-invariants.js',
    'tools/something-new.js',
  ]) {
    assert.equal(decide([path]).r8runtime, true, path);
  }
});

test('the R8 runtime probe skips what Gson and R8 never see', () => {
  for (const path of [
    'app/src/main/java/com/coparently/app/presentation/home/HomeScreen.kt',
    'app/src/main/res/values/strings.xml',
    'app/src/test/java/com/coparently/app/domain/FooTest.kt',
    'app/src/androidTest/java/com/coparently/app/e2e/TwoParentChatTest.kt',
    'docs/DEVICE-CHECKLIST.md',
    'functions/index.js',
  ]) {
    assert.equal(decide([path]).r8runtime, false, path);
  }
});

test('the build, the proguard rules and the workflow run the R8 runtime probe', () => {
  for (const path of ['app/build.gradle.kts', 'app/proguard-rules.pro', '.github/workflows/ci.yml']) {
    assert.equal(decide([path]).r8runtime, true, path);
  }
  assert.equal(everything().r8runtime, true);
});

test('the web job runs on the page, its tests, the functions, the emulator config and the workflow', () => {
  for (const path of [
    'web/verify/index.html',
    'web/README.md',
    'web-tests/verify-page.spec.js',
    'web-tests/package-lock.json',
    'functions/calendar-feed.js',
    'functions/export-receipts.js',
    'functions/package-lock.json',
    'firebase.json',
    'firestore-tests/package-lock.json',
    '.github/workflows/ci.yml',
    'tools/something-new.js',
  ]) {
    assert.equal(decide([path]).web, true, path);
  }
  assert.equal(everything().web, true);
});

test('the web job skips Android-only and docs-only changes', () => {
  for (const path of [
    'docs/DEVICE-CHECKLIST.md',
    'CLAUDE.md',
    'app/src/main/java/com/coparently/app/presentation/export/ExportViewModel.kt',
    'app/src/main/res/values/strings.xml',
    'app/build.gradle.kts',
    'gradle/libs.versions.toml',
  ]) {
    assert.equal(decide([path]).web, false, path);
  }
});

test('a change to web-tests alone runs no Android job, no e2e and no upgrade', () => {
  const d = decide(['web-tests/calendar-feed.spec.js']);
  assert.equal(d.android, false);
  assert.equal(d.e2e, false);
  assert.equal(d.upgrade, false);
  assert.equal(d.web, true);
});
