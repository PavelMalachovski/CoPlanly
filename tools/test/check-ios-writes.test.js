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
  shapeOfJson, compareDocument, ruleFor, parseWritten, parseWrittenFiles, compareFile, parseEmulator, run,
  REQUIRED_COLLECTIONS, REQUIRED_FILE_PREFIXES,
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
    requiredFiles: options.requiredFiles || [],
    fetchDocument: async (p) => documents[p] || null,
    fetchFile: options.files ? async (p) => options.files[p] || null : null,
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

test('a real fixture compared with itself as the emulator would store it, server keys added, has no mismatch', () => {
  const cursor = { serverUpdatedAt: { timestampValue: '2026-11-11T07:00:00Z' } };
  for (const [collection, file, added] of [
    ['events', 'events/repository-save.json', cursor],
    ['expenses', 'expenses/repository-save.json', {}],
    ['pets', 'pets/repository-save.json', {}],
  ]) {
    const text = fs.readFileSync(path.join(FIXTURES, file), 'utf8');
    const stored = doc(Object.assign(restFieldsOf(JSON.parse(text).document, text), added));
    assert.deepEqual(compareDocument(ruleFor(collection, stored, FIXTURES).shape, stored), [], collection);
  }
});


test('an event carries the server cursor its data source adds, as a timestamp', () => {
  const text = fs.readFileSync(path.join(FIXTURES, 'events/repository-save.json'), 'utf8');
  const fields = restFieldsOf(JSON.parse(text).document, text);
  const shape = ruleFor('events', doc(fields), FIXTURES).shape;
  assert.deepEqual(compareDocument(shape, doc(fields)), ['serverUpdatedAt: missing (timestamp expected)']);
  const stamped = doc(Object.assign({}, fields, { serverUpdatedAt: { timestampValue: '2026-11-11T07:00:00Z' } }));
  assert.deepEqual(compareDocument(shape, stamped), []);
  const asText = doc(Object.assign({}, fields, { serverUpdatedAt: s('2026-11-11T07:00:00') }));
  assert.deepEqual(compareDocument(shape, asText), ['serverUpdatedAt: timestamp expected, string found']);
});

test('an announced card carries its entity as text attachments, and nothing else', () => {
  const card = (attachments) => doc({
    activity: map({ entityId: s('e'), entityType: s('EVENT'), kind: s('EVENT_CREATED'), title: s('Dentist'),
      whenIso: s('2026-11-12T10:00:00') }),
    attachments, content: s('x'), conversationId: s('a_b'), id: s('m'), isRead: b(false),
    messageType: s('ACTIVITY'), replyToMessageId: s(''), senderId: s('a'), senderName: s('Alice'), timestamp: i(1),
  });
  const shape = ruleFor('messages', card(arr()), FIXTURES).shape;
  assert.deepEqual(compareDocument(shape, card(arr(s('EVENT'), s('e')))), []);
  assert.deepEqual(compareDocument(shape, card(arr(i(1)))), ['attachments[0]: string expected, integer found']);
});


/** A fixture's document as the REST API would store it, [changes] applied over its fields. */
function storedFixture(file, changes = {}) {
  const text = fs.readFileSync(path.join(FIXTURES, file), 'utf8');
  return doc(Object.assign(restFieldsOf(JSON.parse(text).document, text), changes));
}

test('a pattern write alone is held to pattern-write.json, codec lists element by element', () => {
  const rule = (d) => ruleFor('custody_models', d, FIXTURES);
  const pattern = storedFixture('custody_models/pattern-write.json');
  assert.equal(rule(pattern).source, 'wire/current/custody_models/pattern-write.json');
  assert.deepEqual(compareDocument(rule(pattern).shape, pattern), []);
  const empty = storedFixture('custody_models/pattern-write.json', { contactWindows: arr() });
  assert.deepEqual(compareDocument(rule(empty).shape, empty), [
    'contactWindows: empty, so the fixture\'s elements are not compared',
  ]);
  const wrong = storedFixture('custody_models/pattern-write.json', { seasonalLayers: arr(map({ id: s('summer') })) });
  assert.deepEqual(compareDocument(rule(wrong).shape, wrong), ['seasonalLayers[0]: string expected, map found']);
});

test('a swap write\'s codec lists, top level and in the proposal, must hold codec strings', () => {
  const lists = (extra = {}) => Object.assign({
    contactWindows: arr(s('9|15:00|19:00|dad')),
    seasonalLayers: arr(s('L1;summer;0;2027-07-01;2027-08-31;2027-07-01;14;0,1,2,3,4,5,6;;Summer')),
    childOverrides: arr(s('C1;child:c1;2026-11-09;7;0,1,2;')),
  }, extra);
  const swapDoc = (top, inProposal) => {
    const stored = storedFixture('custody_models/swap-beside-proposal.json', lists(top));
    const proposal = stored.fields.proposal.mapValue.fields;
    Object.assign(proposal, lists(inProposal));
    return stored;
  };
  const rule = (d) => ruleFor('custody_models', d, FIXTURES);
  const full = swapDoc();
  assert.equal(rule(full).source, 'wire/current/custody_models/swap-beside-proposal.json');
  assert.deepEqual(compareDocument(rule(full).shape, full), []);
  const emptyTop = swapDoc({ childOverrides: arr() });
  assert.deepEqual(compareDocument(rule(emptyTop).shape, emptyTop), [
    'childOverrides: empty, so the fixture\'s elements are not compared',
  ]);
  const emptyProposal = swapDoc({}, { contactWindows: arr() });
  assert.deepEqual(compareDocument(rule(emptyProposal).shape, emptyProposal), [
    'proposal.contactWindows: empty, so the fixture\'s elements are not compared',
  ]);
  const wrongProposal = swapDoc({}, { seasonalLayers: arr(i(1)) });
  assert.deepEqual(compareDocument(rule(wrongProposal).shape, wrongProposal), [
    'proposal.seasonalLayers[0]: string expected, integer found',
  ]);
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

test('a deleted event is held to the tombstone fixture, its revision too (part B, Task 12)', () => {
  const cursor = { serverUpdatedAt: { timestampValue: '2026-11-11T07:00:00Z' } };
  const tombstoneFields = { deletedAtMillis: i(1778000000000), deletedBy: s('uidA') };
  // The gate's deletion: a repository save (members, a reminder) with the tombstone merged in.
  const deleted = storedFixture('events/repository-save.json', Object.assign({}, cursor, tombstoneFields));
  assert.deepEqual(compareDocument(ruleFor('events', deleted, FIXTURES).shape, deleted), []);
  const asText = storedFixture('events/repository-save.json',
    Object.assign({}, cursor, tombstoneFields, { deletedAtMillis: s('1778000000000') }));
  assert.deepEqual(compareDocument(ruleFor('events', asText, FIXTURES).shape, asText), [
    'deletedAtMillis: integer expected, string found',
  ]);
  const saved = storedFixture('events/repository-save.json', cursor);
  assert.deepEqual(compareDocument(ruleFor('events', saved, FIXTURES).shape, saved), []);

  const recordedAt = { recordedAt: { timestampValue: '2026-11-11T07:00:00Z' } };
  const revisionOf = (eventFile, kind) => storedFixture('event_versions/updated.json', Object.assign({
    kind: s(kind),
    event: map(storedFixture(eventFile).fields),
  }, recordedAt));
  const deletion = storedFixture('event_versions/updated.json', Object.assign({
    kind: s('deleted'),
    event: map(storedFixture('events/repository-save.json', tombstoneFields).fields),
  }, recordedAt));
  assert.deepEqual(compareDocument(ruleFor('event_versions', deletion, FIXTURES).shape, deletion), []);
  const deletionWithoutTombstone = revisionOf('events/repository-save.json', 'deleted');
  assert.deepEqual(
    compareDocument(ruleFor('event_versions', deletionWithoutTombstone, FIXTURES).shape, deletionWithoutTombstone),
    ['event.deletedAtMillis: missing (integer expected)', 'event.deletedBy: missing (string expected)'],
  );
  const creation = revisionOf('events/repository-save.json', 'created');
  assert.deepEqual(compareDocument(ruleFor('event_versions', creation, FIXTURES).shape, creation), []);
});

test('a text message\'s attachments are compared, and an empty list no longer passes (part B, Task 12)', () => {
  const message = storedFixture('messages/text.json');
  const shape = ruleFor('messages', message, FIXTURES).shape;
  assert.deepEqual(compareDocument(shape, message), []);
  assert.deepEqual(compareDocument(shape, storedFixture('messages/text.json', { attachments: arr() })), [
    'attachments: empty, so the fixture\'s elements are not compared',
  ]);
  assert.deepEqual(compareDocument(shape, storedFixture('messages/text.json', { attachments: arr(i(1)) })), [
    'attachments[0]: string expected, integer found',
  ]);
});

// ---- Storage objects (part C, Task 10) ----

const SHA = 'a'.repeat(64);
const VAULT = 'family_documents/alice__bob/doc-1/order.pdf';
/** An object's metadata as the Storage emulator answers it. */
const object = (extra = {}) => Object.assign({
  name: VAULT, bucket: 'demo-coplanly.appspot.com', contentType: 'application/pdf', size: '42',
  metadata: { uploader: 'alice', sha256: SHA },
}, extra);

test('storage lines are read apart from the documents', () => {
  const text = `events/a\nstorage:${VAULT}\nstorage:${VAULT}\n`;
  assert.deepEqual(parseWritten(text), ['events/a']);
  assert.deepEqual(parseWrittenFiles(text), [VAULT]);
  assert.throws(() => parseWrittenFiles('storage:\n'), /Storage object path/);
  assert.throws(() => parseWrittenFiles('storage:a//b\n'), /Storage object path/);
});

test('a vault object with Android\'s path and metadata has no mismatch', () => {
  assert.deepEqual(compareFile(VAULT, object()), []);
});

test('a vault object with another key, a bad digest or a stranger as uploader is a mismatch', () => {
  assert.deepEqual(compareFile(VAULT, object({ metadata: { uploader: 'alice', sha256: SHA, extra: 'x' } })), [
    'metadata: keys [extra, sha256, uploader] where SharedFileStorage.upload at FamilyDocumentPaths.storagePath writes [sha256, uploader]',
  ]);
  assert.deepEqual(compareFile(VAULT, object({ metadata: { uploader: 'alice', sha256: 'ABC' } })), [
    'metadata.sha256: not 64 lowercase hex digits',
  ]);
  assert.deepEqual(compareFile(VAULT, object({ metadata: { uploader: 'carol', sha256: SHA } })), [
    'metadata.uploader: not one of the family the path names',
  ]);
});

test('a vault object off the path shape, of a refused type or of an unknown prefix is a mismatch', () => {
  assert.equal(compareFile('family_documents/alice__bob/order.pdf', object()).length, 1);
  assert.deepEqual(compareFile('family_documents/alice/doc-1/order.pdf', object({ metadata: { uploader: 'alice', sha256: SHA } })), [
    'path: "alice" is not a pair id of two uids, which storage.rules splits',
  ]);
  assert.deepEqual(compareFile(VAULT, object({ contentType: 'text/plain' })), [
    'contentType: "text/plain" is not one storage.rules admits',
  ]);
  assert.match(compareFile('elsewhere/x/y/z', object())[0], /no Android shape is known/);
});

test('a listed object the Storage emulator lacks, or with no emulator to ask, fails', async () => {
  const missing = await runOver(`events/e\nstorage:${VAULT}\n`, { 'events/e': null }, { files: {} });
  assert.ok(missing.problems.includes(`storage:${VAULT}: not found on the Storage emulator`));
  const noEmulator = await runOver(`storage:${VAULT}\nusers/u\n`, {});
  assert.ok(noEmulator.problems.includes(`storage:${VAULT}: no Storage emulator to read it from`));
});

test('the gate must have uploaded a vault file and a pet photo', async () => {
  assert.deepEqual(REQUIRED_FILE_PREFIXES, ['family_documents', 'pet_photos']);
  const none = await runOver('users/u\n', {}, { requiredFiles: REQUIRED_FILE_PREFIXES, files: {} });
  assert.ok(none.problems.includes('storage:family_documents: the gate uploaded no file to compare'));
  assert.ok(none.problems.includes('storage:pet_photos: the gate uploaded no file to compare'));
  const both = await runOver(`users/u\nstorage:${VAULT}\nstorage:${PET_PHOTO}\n`, {}, {
    requiredFiles: REQUIRED_FILE_PREFIXES, files: { [VAULT]: object(), [PET_PHOTO]: photo() },
  });
  assert.ok(!both.problems.some((p) => p.startsWith('storage:')), both.problems.join('\n'));
  assert.ok(both.lines.some((line) => line === `ok storage:${VAULT} against SharedFileStorage.upload at FamilyDocumentPaths.storagePath`));
  assert.ok(both.lines.some((line) => line === `ok storage:${PET_PHOTO} against SharedFileStorage.uploadBytes at RecordPhotoPaths.build`));
});

// ---- Record photos (part C, Task 11) ----

const PET_PHOTO = 'pet_photos/alice__bob/pet-1/0b1e3c9a-5d2f-4e6a-8b7c-1d2e3f4a5b6c.jpg';
/** A pet photo's metadata as the Storage emulator answers it. */
const photo = (extra = {}) => object(Object.assign({ name: PET_PHOTO, contentType: 'image/jpeg' }, extra));

test('a pet photo in the family\'s or the uploader\'s own folder has no mismatch', () => {
  assert.deepEqual(compareFile(PET_PHOTO, photo()), []);
  assert.deepEqual(compareFile('pet_photos/solo_alice/pet-1/0b1e3c9a-5d2f-4e6a-8b7c-1d2e3f4a5b6c.jpg', photo()), []);
});

test('a pet photo off the shape, not a JPEG or in a stranger\'s folder is a mismatch', () => {
  assert.equal(compareFile('pet_photos/alice__bob/pet-1/photo.png', photo()).length, 1);
  assert.deepEqual(compareFile(PET_PHOTO, photo({ contentType: 'application/pdf' })), [
    'contentType: "application/pdf" is not one storage.rules admits',
  ]);
  assert.deepEqual(compareFile('pet_photos/solo_carol/pet-1/0b1e3c9a-5d2f-4e6a-8b7c-1d2e3f4a5b6c.jpg', photo()), [
    'metadata.uploader: not one of the family the path names',
  ]);
  assert.deepEqual(compareFile('pet_photos/solo_/pet-1/0b1e3c9a-5d2f-4e6a-8b7c-1d2e3f4a5b6c.jpg', photo()), [
    'path: "solo_" names no uid',
  ]);
  assert.deepEqual(compareFile('pet_photos/alice/pet-1/0b1e3c9a-5d2f-4e6a-8b7c-1d2e3f4a5b6c.jpg', photo()), [
    'path: "alice" is not a pair id of two uids, which storage.rules splits',
  ]);
});
