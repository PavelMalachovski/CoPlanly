const assert = require('assert');
const revisions = require('../event-revisions');

/**
 * MON-4 — the server records the revisions a phone did not.
 *
 * What is pinned: a write an older build made (no client revision) gets exactly one server
 * revision, marked as the server's; a write a phone already recorded gets none; a write that did
 * not change what a parent saved (a server sweep, a sync re-upload) gets none; a private or
 * removed document never gets one; and a retried trigger does not write twice.
 */

const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const FAMILY = [ALICE, BOB].sort().join('__');
const SERVER_TIME = {sentinel: 'serverTimestamp'};
const UPDATE_TIME = {seconds: 1787000000, nanoseconds: 1234};

/**
 * A Firestore-shaped fake: documents by collection, `==` queries, and `create` that refuses an
 * existing id with gRPC code 6, as Firestore does.
 *
 * @param {!Object<string, !Object>=} versions Seed revisions by id.
 * @return {!Object} The fake, exposing `_versions` and `_creates`.
 */
function fakeDb(versions) {
  const store = Object.assign({}, versions || {});
  const creates = [];
  return {
    _versions: store,
    _creates: creates,
    collection(name) {
      assert.strictEqual(name, 'event_versions');
      return {
        where(field, op, value) {
          assert.strictEqual(op, '==');
          return {
            async get() {
              const ids = Object.keys(store).filter((id) => store[id][field] === value);
              return {docs: ids.map((id) => ({id, data: () => store[id]}))};
            },
          };
        },
        doc(id) {
          return {
            async create(data) {
              creates.push(id);
              if (store[id] !== undefined) {
                const err = new Error('already exists');
                err.code = 6;
                throw err;
              }
              store[id] = data;
            },
          };
        },
      };
    },
  };
}

/**
 * An event document as `EventRepositoryImpl.toFirestoreMap()` writes it.
 *
 * @param {!Object=} overrides Fields to override.
 * @return {!Object} The document.
 */
function eventDoc(overrides) {
  return Object.assign({
    id: 'event-1',
    title: 'Swimming lesson',
    startDateTime: '2026-08-05T16:00:00',
    updatedAt: '2026-08-01T10:00:00',
    createdByFirebaseUid: ALICE,
    lastModifiedBy: ALICE,
    familyId: FAMILY,
    sharedWith: [ALICE, BOB],
  }, overrides);
}

const deps = {serverTimestamp: () => SERVER_TIME, graceMs: 0};

/**
 * Runs the trigger body over one write.
 *
 * @param {!Object} db The fake.
 * @param {?Object} before The document before.
 * @param {?Object} after The document after.
 * @param {!Object=} extraDeps Dependencies to override.
 * @return {!Promise<!Object>} The outcome.
 */
function run(db, before, after, extraDeps) {
  return revisions.recordServerRevisionImpl(db,
      {eventId: 'event-1', before, after, updateTime: UPDATE_TIME},
      Object.assign({}, deps, extraDeps || {}));
}

describe('event revisions recorded by the server (MON-4)', () => {
  describe('writeKey', () => {
    it('keys a save on its updatedAt and a tombstone on its deletion time', () => {
      assert.strictEqual(revisions.writeKey(eventDoc({})), 'saved|2026-08-01T10:00:00');
      assert.strictEqual(
          revisions.writeKey(eventDoc({deletedAtMillis: 1787000000000, deletedBy: BOB})),
          'deleted|1787000000000');
    });

    it('has no key for a document with neither', () => {
      assert.strictEqual(revisions.writeKey(eventDoc({updatedAt: ''})), null);
      assert.strictEqual(revisions.writeKey(null), null);
    });

    it('reads a non-positive deletion time as alive, as Tombstone.deletedAtMillisIn does', () => {
      assert.strictEqual(revisions.writeKey(eventDoc({deletedAtMillis: 0})), 'saved|2026-08-01T10:00:00');
    });
  });

  it('records an older build\'s create, marked as the server\'s', async () => {
    const db = fakeDb();
    const outcome = await run(db, null, eventDoc({}));

    assert.strictEqual(outcome.recorded, true);
    const id = `srv_event-1_1787000000000001234`;
    assert.strictEqual(outcome.id, id);
    assert.deepStrictEqual(db._versions[id], {
      eventId: 'event-1',
      kind: 'created',
      editorUid: ALICE,
      editorField: 'lastModifiedBy',
      deviceTimeMillis: null,
      recordedAt: SERVER_TIME,
      sharedWith: [ALICE, BOB],
      familyId: FAMILY,
      event: eventDoc({}),
      formatVersion: 1,
      recordedBy: 'server',
    });
  });

  it('records an older build\'s edit as an update, naming whom the document names', async () => {
    const db = fakeDb();
    const after = eventDoc({updatedAt: '2026-08-02T09:00:00', lastModifiedBy: BOB});
    await run(db, eventDoc({}), after);

    const [written] = Object.values(db._versions);
    assert.strictEqual(written.kind, 'updated');
    assert.strictEqual(written.editorUid, BOB);
  });

  it('records a tombstone as a deletion by deletedBy', async () => {
    const db = fakeDb();
    await run(db, eventDoc({}), eventDoc({deletedAtMillis: 1787000000000, deletedBy: BOB}));

    const [written] = Object.values(db._versions);
    assert.strictEqual(written.kind, 'deleted');
    assert.strictEqual(written.editorUid, BOB);
    assert.strictEqual(written.editorField, 'deletedBy');
    assert.strictEqual(written.event.deletedAtMillis, 1787000000000);
  });

  it('records a document restored over its tombstone (an Undo) as a creation', async () => {
    const db = fakeDb();
    await run(db, eventDoc({deletedAtMillis: 1787000000000, deletedBy: ALICE}), eventDoc({}));
    assert.strictEqual(Object.values(db._versions)[0].kind, 'created');
  });

  it('falls back to the creator when the document names no editor', async () => {
    const db = fakeDb();
    await run(db, null, eventDoc({lastModifiedBy: ''}));
    const [written] = Object.values(db._versions);
    assert.strictEqual(written.editorUid, ALICE);
    assert.strictEqual(written.editorField, 'createdByFirebaseUid');
  });

  it('records nothing when a phone already recorded this write', async () => {
    const db = fakeDb({
      'client-uuid': {eventId: 'event-1', kind: 'updated', editorUid: ALICE, event: eventDoc({})},
    });
    const outcome = await run(db, null, eventDoc({}));
    assert.deepStrictEqual(outcome, {recorded: false, reason: 'clientRecorded'});
    assert.deepStrictEqual(db._creates, []);
  });

  it('records nothing when a phone recorded the deletion', async () => {
    const tombstone = eventDoc({deletedAtMillis: 1787000000000, deletedBy: ALICE});
    const db = fakeDb({'client-uuid': {eventId: 'event-1', kind: 'deleted', event: tombstone}});
    const outcome = await run(db, eventDoc({}), tombstone);
    assert.strictEqual(outcome.reason, 'clientRecorded');
  });

  it('waits once for a phone\'s revision that lands during the grace', async () => {
    const db = fakeDb();
    const sleep = async () => {
      db._versions['client-late'] = {eventId: 'event-1', event: eventDoc({})};
    };
    const outcome = await run(db, null, eventDoc({}), {graceMs: 1, sleep});
    assert.strictEqual(outcome.reason, 'clientRecorded');
    assert.deepStrictEqual(db._creates, []);
  });

  it('is not satisfied by a revision of a different save, or by its own earlier one', async () => {
    const db = fakeDb({
      'client-old': {eventId: 'event-1', event: eventDoc({updatedAt: '2026-07-01T10:00:00'})},
      'client-other': {eventId: 'event-2', event: eventDoc({})},
      'srv_event-1_1': {eventId: 'event-1', recordedBy: 'server', event: eventDoc({})},
    });
    const outcome = await run(db, null, eventDoc({}));
    assert.strictEqual(outcome.recorded, true);
  });

  it('records nothing for a write that did not change what was saved', async () => {
    // The unpair sweep narrowing the audience, the family backfill, a sync re-upload.
    const db = fakeDb();
    const outcome = await run(db, eventDoc({}), eventDoc({sharedWith: [ALICE], familyId: ''}));
    assert.deepStrictEqual(outcome, {recorded: false, reason: 'unchanged'});
    assert.deepStrictEqual(db._creates, []);
  });

  it('never records a private event', async () => {
    const db = fakeDb();
    const outcome = await run(db, null, eventDoc({isPrivate: true}));
    assert.deepStrictEqual(outcome, {recorded: false, reason: 'private'});
    assert.deepStrictEqual(db._creates, []);
  });

  it('records nothing for a document removed outright', async () => {
    const db = fakeDb();
    const outcome = await run(db, eventDoc({}), null);
    assert.deepStrictEqual(outcome, {recorded: false, reason: 'removed'});
  });

  it('records nothing it could not match or nobody could read', async () => {
    const db = fakeDb();
    assert.strictEqual((await run(db, null, eventDoc({updatedAt: undefined}))).reason, 'undated');
    assert.strictEqual((await run(db, null, eventDoc({sharedWith: []}))).reason, 'noAudience');
    assert.strictEqual(
        (await run(db, null, eventDoc({lastModifiedBy: '', createdByFirebaseUid: ''}))).reason,
        'noEditor');
    assert.deepStrictEqual(db._creates, []);
  });

  it('writes once when the trigger is retried', async () => {
    const db = fakeDb();
    await run(db, null, eventDoc({}));
    const second = await run(db, null, eventDoc({}));
    assert.strictEqual(second.reason, 'alreadyRecorded');
    assert.strictEqual(Object.keys(db._versions).length, 1);
  });

  it('gives every server revision the prefix clients are refused', () => {
    assert.ok(revisions.serverRevisionId('e', UPDATE_TIME, 'k').startsWith(revisions.SERVER_ID_PREFIX));
    assert.ok(revisions.serverRevisionId('e', null, 'k').startsWith(revisions.SERVER_ID_PREFIX));
  });
});
