/**
 * Tests for tools/check-e2e-coverage.js. Run with `node --test tools/test/*.test.js` (the CI
 * `invariants` job does); no dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const coverage = require('../check-e2e-coverage.js');

/** A throwaway repository holding just the files the checker reads. */
function fixture({rules, storage, push, functions, tests, map}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-coverage-'));
  const write = (p, text) => {
    fs.mkdirSync(path.dirname(path.join(root, p)), {recursive: true});
    fs.writeFileSync(path.join(root, p), text);
  };
  write('firestore.rules', rules);
  write('storage.rules', storage);
  write('app/src/main/java/com/coparently/app/data/remote/firebase/PushPayload.kt', push);
  write('functions/index.js', functions);
  for (const [name, body] of Object.entries(tests)) {
    write(`app/src/androidTest/java/com/coparently/app/e2e/${name}.kt`, body);
  }
  write('tools/e2e/coverage.json', JSON.stringify(map));
  return root;
}

const RULES = `rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function f() { return true; }
    match /events/{eventId} {
      allow read: if f();
      match /nested/{x} { allow read: if true; }
    }
    // match /commented/{id} is prose, not a collection
    match /users/{userId} { allow read: if true; }
  }
}`;
const STORAGE = `service firebase.storage { match /b/{bucket}/o { match /receipts/{p=**} { allow read: if true; } } }`;
const PUSH = `object PushPayload {
    const val TYPE = "type"
    const val EVENT_ID = "eventId"
    const val EVENT_CREATED = "event_created"
}`;
const FUNCTIONS = `exports.helper = helper
exports.acceptX = functions.https.onCall(async () => {})
exports.backfill = functions.runWith({timeoutSeconds: 540}).https.onCall(
  async () => {})
exports.feed = functions.https.onRequest(async () => {})
exports.trigger = functions.firestore.document('a/{b}').onCreate(() => {})`;
const TESTS = {
  EventsTest: `class EventsTest {\n    @Test\n    fun aTest() = Unit\n}`,
};
const FULL = {
  firestore: {events: {e2e: ['EventsTest#aTest']}, users: {exempt: 'server only'}},
  storage: {receipts: {e2e: ['EventsTest']}},
  push: {event_created: {e2e: ['EventsTest']}},
  functions: {acceptX: {e2e: ['EventsTest']}, backfill: {exempt: 'operator'}, feed: {exempt: 'x'}},
};

test('discovery reads each source of truth and nothing else', () => {
  const found = coverage.discover(fixture({rules: RULES, storage: STORAGE, push: PUSH,
    functions: FUNCTIONS, tests: TESTS, map: FULL}));
  assert.deepEqual([...found.firestore].sort(), ['events', 'users']);
  assert.deepEqual([...found.storage], ['receipts']);
  assert.deepEqual([...found.push], ['event_created']);
  assert.deepEqual([...found.functions].sort(), ['acceptX', 'backfill', 'feed']);
});

test('a complete map passes', () => {
  assert.deepEqual(coverage.check(fixture({rules: RULES, storage: STORAGE, push: PUSH,
    functions: FUNCTIONS, tests: TESTS, map: FULL})), []);
});

test('a shared feature missing from the map fails, naming it', () => {
  const map = JSON.parse(JSON.stringify(FULL));
  delete map.push.event_created;
  const problems = coverage.check(fixture({rules: RULES, storage: STORAGE, push: PUSH,
    functions: FUNCTIONS, tests: TESTS, map}));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /push "event_created"/);
});

test('a test that does not exist, or an empty entry, fails', () => {
  const map = JSON.parse(JSON.stringify(FULL));
  map.firestore.events = {e2e: ['EventsTest#noSuchMethod', 'NoSuchTest']};
  map.storage.receipts = {};
  const problems = coverage.check(fixture({rules: RULES, storage: STORAGE, push: PUSH,
    functions: FUNCTIONS, tests: TESTS, map}));
  assert.ok(problems.some((p) => /has no @Test noSuchMethod/.test(p)));
  assert.ok(problems.some((p) => /no e2e test class NoSuchTest/.test(p)));
  assert.ok(problems.some((p) => /storage "receipts": an entry needs/.test(p)));
});

test('an entry for something that no longer exists fails', () => {
  const map = JSON.parse(JSON.stringify(FULL));
  map.firestore.gone = {exempt: 'was removed'};
  const problems = coverage.check(fixture({rules: RULES, storage: STORAGE, push: PUSH,
    functions: FUNCTIONS, tests: TESTS, map}));
  assert.deepEqual(problems, [`firestore "gone" is in tools/e2e/coverage.json but no longer exists — remove the entry`]);
});

test('the repository map is complete', () => {
  assert.deepEqual(coverage.check(path.join(__dirname, '..', '..')), []);
});
