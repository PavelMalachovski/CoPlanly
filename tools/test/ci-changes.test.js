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

test('the build and the workflow run everything', () => {
  for (const path of [
    'app/build.gradle.kts',
    'gradle/libs.versions.toml',
    'gradle.properties',
    '.github/workflows/ci.yml',
    'app/proguard-rules.pro',
  ]) {
    assert.deepEqual(decide([path]), everything(), path);
  }
});

test('an unfamiliar path is never skipped by the Android gate', () => {
  const d = decide(['tools/something-new.js']);
  assert.equal(d.android, true);
  assert.equal(d.e2e, true);
});

test('format writes one GITHUB_OUTPUT line per key, the matrix as one-line JSON', () => {
  const lines = format(decide(['app/src/main/res/values/strings.xml'])).trim().split('\n');
  assert.deepEqual(lines.map((l) => l.split('=')[0]), ['android', 'e2e', 'screenshots', 'matrix']);
  const matrix = JSON.parse(lines[3].slice('matrix='.length));
  assert.equal(matrix[0]['api-level'], 30);
});

test('only API 26 records the screen: screenrecord crashed the API 30 and 16 KB images', () => {
  const recording = everything().matrix.filter((leg) => leg.record).map((leg) => leg['api-level']);
  assert.deepEqual(recording, [26]);
  for (const leg of everything().matrix) assert.equal(typeof leg.record, 'boolean');
});
