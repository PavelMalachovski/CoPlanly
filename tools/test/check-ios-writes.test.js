/**
 * Tests for tools/ios/check-ios-writes.js. Run with `node --test tools/test/*.test.js`; no
 * dependencies.
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  shapeOfJson, compareDocument, ruleFor, parseWritten, parseEmulator, run, REQUIRED_COLLECTIONS,
} = require('../ios/check-ios-writes.js');

const FIXTURES = path.join(__dirname, '..', '..', 'app', 'src', 'test', 'resources', 'wire', 'current');

/** A document as the emulator's REST API answers it. */
const doc = (fields) => ({ name: 'projects/demo-coplanly/databases/(default)/documents/c/1', fields });
const s = (value) => ({ stringValue: value });
const i = (value) => ({ integerValue: String(value) });
const d = (value) => ({ doubleValue: value });
const b = (value) => ({ booleanValue: value });
const arr = (...values) => ({ arrayValue: values.length ? { values } : {} });
const map = (fields) => ({ mapValue: { fields } });

const FIXTURE = shapeOfJson('{"a": 1, "b": "x", "c": {"d": true}, "e": ["s"]}');

test('a double where the fixture holds an integer is one mismatch', () => {
  const written = doc({ a: d(1.5), b: s('x'), c: map({ d: b(true) }), e: arr(s('s')) });
  assert.deepEqual(compareDocument(FIXTURE, written), ['a: integer expected, double found']);
});

test('a key the fixture has and the document lacks is a mismatch', () => {
  const written = doc({ a: i(1), c: map({ d: b(true) }), e: arr(s('s')) });
  assert.deepEqual(compareDocument(FIXTURE, written), ['b: missing (string expected)']);
});

test('a key the document has and the fixture lacks is a mismatch', () => {
  const written = doc({ a: i(1), b: s('x'), c: map({ d: b(true), z: s('q') }), e: arr(s('s')) });
  assert.deepEqual(compareDocument(FIXTURE, written), ['c.z: unexpected key (string found)']);
});

test('a Firestore timestamp where the fixture holds ISO text is a mismatch', () => {
  const written = doc({
    a: i(1), b: { timestampValue: '2026-05-01T08:00:00Z' }, c: map({ d: b(true) }), e: arr(s('s')),
  });
  assert.deepEqual(compareDocument(FIXTURE, written), ['b: string expected, timestamp found']);
});

test('equal shapes with other values are no mismatch', () => {
  const written = doc({ a: i(42), b: s('other'), c: map({ d: b(false) }), e: arr(s('t'), s('u')) });
  assert.deepEqual(compareDocument(FIXTURE, written), []);
});

test('a number with a fraction or an exponent in the fixture text is a double, even 1.0', () => {
  const shape = shapeOfJson('{"x": 1.0, "y": 2e3, "z": 7}');
  assert.deepEqual(compareDocument(shape, doc({ x: d(1), y: d(2000), z: i(7) })), []);
  assert.deepEqual(compareDocument(shape, doc({ x: i(1), y: d(2000), z: i(7) })), [
    'x: double expected, integer found',
  ]);
});

test('array elements and nested maps are compared with the fixture\'s first element', () => {
  const shape = shapeOfJson('{"list": [{"name": "a", "notes": null}]}');
  const written = doc({ list: arr(map({ name: s('n'), notes: { nullValue: null } }), map({ name: i(3), notes: s('x') })) });
  assert.deepEqual(compareDocument(shape, written), [
    'list[1].name: string expected, integer found',
    'list[1].notes: null expected, string found',
  ]);
});

test('an empty array where the fixture has elements, or elements where it has none, is a mismatch', () => {
  const shape = shapeOfJson('{"full": ["a"], "empty": []}');
  assert.deepEqual(compareDocument(shape, doc({ full: arr(), empty: arr(s('x')) })), [
    'full: empty, so the fixture\'s elements are not compared',
    'empty[]: elements found where the fixture has none to compare',
  ]);
});

test('written paths are read one per line, blanks and duplicates dropped', () => {
  assert.deepEqual(parseWritten('events/a\n\n events/b \r\nevents/a\n'), ['events/a', 'events/b']);
  assert.throws(() => parseWritten('events'), /collection\/document/);
  assert.throws(() => parseWritten('events/a/b'), /collection\/document/);
});

test('only a loopback emulator is accepted', () => {
  assert.deepEqual(parseEmulator('127.0.0.1:8080'), { host: '127.0.0.1', port: 8080 });
  assert.deepEqual(parseEmulator('localhost:8081'), { host: 'localhost', port: 8081 });
  assert.deepEqual(parseEmulator('[::1]:8080'), { host: '[::1]', port: 8080 });
  assert.throws(() => parseEmulator('firestore.googleapis.com:443'), /loopback/);
  assert.throws(() => parseEmulator('10.0.2.2:8080'), /loopback/);
  assert.throws(() => parseEmulator('127.0.0.1'), /host:port/);
});

/** A run over [documents] (path → REST document), with [written] as the file's text. */
async function runOver(written, documents, options = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ios-gate-'));
  const file = path.join(dir, 'written.txt');
  if (written !== null) fs.writeFileSync(file, written);
  const lines = [];
  const result = await run({
    written: file,
    fixtures: FIXTURES,
    required: options.required || [],
    fetchDocument: async (p) => documents[p] || null,
    log: (line) => lines.push(line),
  });
  return { ...result, lines };
}

test('a missing or empty written.txt fails: a gate that wrote nothing cannot pass', async () => {
  assert.notEqual((await runOver(null, {})).problems.length, 0);
  assert.notEqual((await runOver('\n', {})).problems.length, 0);
});

test('a written document the emulator does not have, or of an unknown collection, fails', async () => {
  const missing = await runOver('events/nope\n', {});
  assert.deepEqual(missing.problems, ['events/nope: not found on the emulator']);
  const unknown = await runOver('stickers/x\n', { 'stickers/x': doc({ a: s('x') }) });
  assert.equal(unknown.problems.length, 1);
  assert.match(unknown.problems[0], /^stickers\/x: no Android shape is known/);
});

test('a required collection the gate wrote nothing to fails', async () => {
  const result = await runOver('notification_queue/q\n', {
    'notification_queue/q': doc({
      targetUserId: s('b'), data: map({ type: s('event_important') }), createdAt: i(1), status: s('pending'),
    }),
  }, { required: ['notification_queue', 'events'] });
  assert.deepEqual(result.problems, ['events: the gate wrote no document to compare']);
  assert.ok(result.lines.some((line) => line.includes('notification_queue/q') && line.includes('ok')));
});

test('the push queue allows the keys the server adds and nothing else', async () => {
  const queued = (extra) => doc(Object.assign({
    targetUserId: s('b'), data: map({ type: s('event_important'), familyId: s('a__b') }),
    createdAt: i(1), status: s('skipped'),
  }, extra));
  const ok = await runOver('notification_queue/q\n', {
    'notification_queue/q': queued({ error: s('No FCM token'), sentAt: { timestampValue: '2026-01-01T00:00:00Z' } }),
  });
  assert.deepEqual(ok.problems, []);
  const bad = await runOver('notification_queue/q\n', {
    'notification_queue/q': queued({ createdAt: d(1.5), title: s('Hello') }),
  });
  assert.deepEqual(bad.problems.sort(), [
    'notification_queue/q: createdAt: integer expected, double found',
    'notification_queue/q: title: unexpected key (string found)',
  ]);
});

test('every collection the gate must write has a rule, and the fixtures it names load', () => {
  // A document with the discriminators the rules read, so each branch is built once.
  const samples = {
    messages: [doc({ messageType: s('TEXT') }), doc({ messageType: s('ACTIVITY') })],
    custody_models: [doc({}), doc({ dayOverrides: map({}) })],
  };
  for (const collection of REQUIRED_COLLECTIONS) {
    for (const sample of samples[collection] || [doc({})]) {
      const rule = ruleFor(collection, sample, FIXTURES);
      assert.ok(rule.source.length > 0, collection);
      assert.equal(rule.shape.type, 'map', collection);
    }
  }
});

test('a real fixture compared with itself as the emulator would store it has no mismatch', () => {
  for (const [collection, file] of [
    ['events', 'events/repository-save.json'],
    ['expenses', 'expenses/repository-save.json'],
    ['pets', 'pets/repository-save.json'],
  ]) {
    const text = fs.readFileSync(path.join(FIXTURES, file), 'utf8');
    const stored = doc(restFieldsOf(JSON.parse(text).document, text));
    assert.deepEqual(compareDocument(ruleFor(collection, stored, FIXTURES).shape, stored), [], collection);
  }
});

/**
 * [value] as the REST API would answer it. Numbers are typed by [text]'s lexeme of the key's value,
 * good enough for the flat fixtures above (a whole number written as `1250.0` is a double).
 */
function restFieldsOf(value, text) {
  const restOf = (v, key) => {
    if (v === null) return { nullValue: null };
    if (typeof v === 'boolean') return { booleanValue: v };
    if (typeof v === 'string') return { stringValue: v };
    if (typeof v === 'number') {
      const lexeme = new RegExp(`"${key}":\\s*(-?[0-9.eE+-]+)`).exec(text);
      return lexeme && /[.eE]/.test(lexeme[1]) ? { doubleValue: v } : { integerValue: String(v) };
    }
    if (Array.isArray(v)) return { arrayValue: v.length ? { values: v.map((e) => restOf(e, key)) } : {} };
    return { mapValue: { fields: restFieldsOf(v, text) } };
  };
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, restOf(v, k)]));
}
