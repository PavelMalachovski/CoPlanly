const assert = require('assert');

/**
 * Erasing an account.
 *
 * `deleteAccountDataImpl` is the half of Play's in-app-deletion requirement and GDPR Art. 17
 * that the client cannot perform: it removes documents the signing-out user cannot read, let
 * alone write. These cases pin the two decisions that are easy to get wrong and impossible to
 * notice afterwards — **what is deleted versus merely narrowed**, and **the order**.
 *
 * The fake below is fuller than the one in `unpair.test.js` because this path uses more of
 * Firestore: equality and array-contains queries, per-document deletes, and the transaction
 * `unpairCoParentImpl` runs. It stores documents by collection and applies writes to them, so
 * assertions are about the resulting state rather than about which calls were made.
 *
 * @param {!Object<string, !Array<!Object>>} collections Seed documents, keyed by collection.
 * @return {!Object} A Firestore-shaped fake exposing its documents as `_store`.
 */
function fakeDb(collections) {
  const store = {};
  Object.keys(collections).forEach((name) => {
    store[name] = collections[name].map((doc) => Object.assign({}, doc));
  });

  const docsOf = (name) => (store[name] = store[name] || []);

  const matches = (doc, field, op, value) => {
    const actual = doc[field];
    if (op === '==') return actual === value;
    if (op === 'array-contains') return Array.isArray(actual) && actual.includes(value);
    throw new Error(`fakeDb: unsupported operator ${op}`);
  };

  const wrap = (name, doc) => ({
    id: doc.id,
    exists: true,
    data: () => doc,
    ref: {_collection: name, _id: doc.id},
  });

  const applyUpdate = (name, id, update) => {
    const doc = docsOf(name).find((d) => d.id === id);
    if (!doc) return;
    Object.keys(update).forEach((key) => {
      const value = update[key];
      // Real `FieldValue` sentinels, applied rather than stored. They arrive as
      // `ArrayRemoveTransform` / `DeleteTransform` instances from firebase-admin; storing one
      // verbatim would replace the array with an opaque object and quietly pass a test that
      // asserts only "the field changed".
      const transform = value && value.constructor && value.constructor.name;
      if (transform === 'ArrayRemoveTransform') {
        doc[key] = (doc[key] || []).filter((v) => !value.elements.includes(v));
      } else if (transform === 'ArrayUnionTransform') {
        doc[key] = (doc[key] || []).concat(
            value.elements.filter((v) => !(doc[key] || []).includes(v)));
      } else if (transform === 'DeleteTransform') {
        delete doc[key];
      } else {
        doc[key] = value;
      }
    });
  };

  const removeDoc = (name, id) => {
    store[name] = docsOf(name).filter((d) => d.id !== id);
  };

  const collection = (name) => ({
    where(field, op, value) {
      const build = (predicates) => ({
        where(f2, o2, v2) {
          return build(predicates.concat([[f2, o2, v2]]));
        },
        async get() {
          const found = docsOf(name).filter((doc) =>
            predicates.every(([f, o, v]) => matches(doc, f, o, v)));
          return {docs: found.map((doc) => wrap(name, doc)), size: found.length};
        },
      });
      return build([[field, op, value]]);
    },
    doc(id) {
      return {
        _collection: name,
        _id: id,
        async get() {
          const doc = docsOf(name).find((d) => d.id === id);
          return doc ?
            wrap(name, doc) :
            {id, exists: false, data: () => undefined, ref: {_collection: name, _id: id}};
        },
        async update(update) {
          applyUpdate(name, id, update);
        },
        async delete() {
          removeDoc(name, id);
        },
      };
    },
    async add(doc) {
      docsOf(name).push(Object.assign({id: `gen-${docsOf(name).length}`}, doc));
    },
  });

  return {
    _store: store,
    collection,
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push({kind: 'delete', ref});
        },
        update(ref, update) {
          ops.push({kind: 'update', ref, update});
        },
        async commit() {
          ops.forEach((op) => {
            if (op.kind === 'delete') removeDoc(op.ref._collection, op.ref._id);
            else applyUpdate(op.ref._collection, op.ref._id, op.update);
          });
          ops.length = 0;
        },
      };
    },
    async runTransaction(fn) {
      return fn({
        async get(ref) {
          return collection(ref._collection).doc(ref._id).get();
        },
        update(ref, update) {
          applyUpdate(ref._collection, ref._id, update);
        },
        set(ref, value) {
          removeDoc(ref._collection, ref._id);
          docsOf(ref._collection).push(Object.assign({id: ref._id}, value));
        },
        delete(ref) {
          removeDoc(ref._collection, ref._id);
        },
      });
    },
  };
}

const ALICE = 'alice';
const BOB = 'bob';

/**
 * A paired family with one document of every shape the erasure has to reason about.
 *
 * @return {!Object<string, !Array<!Object>>} Seed documents for [fakeDb].
 */
function family() {
  return {
    users: [
      {id: ALICE, name: 'Alice', partnerId: BOB},
      {id: BOB, name: 'Bob', partnerId: ALICE},
    ],
    events: [
      {id: 'ev-alice', createdByFirebaseUid: ALICE, sharedWith: [ALICE, BOB], title: 'Pickup'},
      {id: 'ev-bob', createdByFirebaseUid: BOB, sharedWith: [ALICE, BOB], title: 'Dentist'},
    ],
    child_info: [
      {id: 'ch-1', createdByFirebaseUid: ALICE, sharedWith: [ALICE, BOB], childName: 'Ema'},
    ],
    pets: [],
    expenses: [{id: 'ex-1', createdByFirebaseUid: ALICE, amount: 100}],
    budgets: [{id: 'bu-1', createdByFirebaseUid: ALICE, category: 'school'}],
    change_requests: [
      {id: 'cr-1', requestedBy: ALICE, requestedTo: BOB},
      {id: 'cr-2', requestedBy: BOB, requestedTo: ALICE},
    ],
    conversations: [{id: `${ALICE}__${BOB}`, participants: [ALICE, BOB]}],
    messages: [
      {id: 'm-1', conversationId: `${ALICE}__${BOB}`, senderId: ALICE, content: 'hi'},
      {id: 'm-2', conversationId: `${ALICE}__${BOB}`, senderId: BOB, content: 'hello'},
    ],
    custody_models: [{id: `${ALICE}__${BOB}`, participants: [ALICE, BOB]}],
    calendar_friends: [],
    calendar_feeds: [
      {id: 'hash-alice', ownerUid: ALICE, familyMembers: [ALICE, BOB]},
      {id: 'hash-bob', ownerUid: BOB, familyMembers: [ALICE, BOB]},
      {id: 'hash-other', ownerUid: 'carol', familyMembers: ['carol', 'dave']},
    ],
    friend_profiles: [],
    invitations: [{id: 'inv-1', fromUserId: ALICE, status: 'pending'}],
    notification_queue: [{id: 'n-1', targetUserId: ALICE}],
  };
}

describe('deleteAccountDataImpl', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  // MON-17: a feed link into a family this account was in names the account, whoever made it.
  it('removes every calendar-feed link into the departing parent\'s families', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);
    assert.deepStrictEqual(db._store.calendar_feeds.map((f) => f.id), ['hash-other']);
  });

  it('removes the account profile itself', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);
    assert.deepStrictEqual(db._store.users.map((u) => u.id), [BOB]);
  });

  // The decision this whole function turns on: what the departing user *authored* goes, what
  // the co-parent authored stays. Getting this backwards either fails the erasure request or
  // deletes the other parent's records.
  it('deletes documents the user authored and keeps the co-parent\'s', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.events.map((e) => e.id), ['ev-bob']);
    assert.deepStrictEqual(db._store.child_info, []);
    assert.deepStrictEqual(db._store.expenses, []);
    assert.deepStrictEqual(db._store.budgets, []);
  });

  // The other half: a surviving document must not still name the deleted account in its
  // audience, or the co-parent's screen keeps listing somebody who no longer exists.
  it('scrubs the uid from the audience of documents it did not author', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);

    const survivor = db._store.events.find((e) => e.id === 'ev-bob');
    assert.ok(!survivor.sharedWith.includes(ALICE), 'ex-account still in sharedWith');
    assert.ok(survivor.sharedWith.includes(BOB), 'the author lost their own audience');
  });

  // MON-4. A revision is the one document no client may ever delete, so this is the only path
  // that removes one — and it must remove exactly the departing parent's, not the co-parent's
  // record of what *they* changed on the departing parent's event.
  it('deletes the revisions the user saved and narrows the co-parent\'s', async () => {
    const db = fakeDb(Object.assign(family(), {
      event_versions: [
        {id: 'v-alice', eventId: 'ev-alice', editorUid: ALICE, sharedWith: [ALICE, BOB]},
        {id: 'v-bob', eventId: 'ev-alice', editorUid: BOB, sharedWith: [ALICE, BOB]},
      ],
    }));

    const removed = await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.event_versions.map((v) => v.id), ['v-bob']);
    assert.deepStrictEqual(db._store.event_versions[0].sharedWith, [BOB]);
    assert.strictEqual(removed.event_versions, 1);
    assert.strictEqual(removed.event_versions_scrubbed, 1);
  });

  it('deletes the conversation and every message in it', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.conversations, []);
    assert.deepStrictEqual(db._store.messages, [], 'half a thread was left behind');
  });

  it('deletes change requests in both directions', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);
    assert.deepStrictEqual(db._store.change_requests, []);
  });

  it('deletes the shared custody schedule and the account\'s invitations', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.custody_models, []);
    assert.deepStrictEqual(db._store.invitations, []);
  });

  // Queued pushes *addressed to* the deleted account go — they would otherwise be delivered
  // to whatever device still holds its token. The one unpair queues *for the co-parent* is a
  // different thing and must survive: it is how Bob learns the link ended, and it is the last
  // message this account will ever cause.
  it('drops pushes addressed to the account, keeps the one telling the co-parent', async () => {
    const db = fakeDb(family());
    await myFunctions.deleteAccountDataImpl(db, ALICE);

    const remaining = db._store.notification_queue;
    assert.ok(!remaining.some((n) => n.targetUserId === ALICE),
        'a push addressed to the deleted account survived');
    assert.deepStrictEqual(remaining.map((n) => n.targetUserId), [BOB]);
  });

  // Unpairing first is what lets the co-parent's own client notice the link ended, while both
  // accounts still exist. Running it afterwards would clear a partnerId pointing at a user
  // document that had already gone.
  it('tears the co-parent link down and reports who it unpaired', async () => {
    const db = fakeDb(family());
    const result = await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.strictEqual(result.unpairedFrom, BOB);
    assert.strictEqual(db._store.users.find((u) => u.id === BOB).partnerId, '');
  });

  // An erasure request must not be blocked by the state of somebody's pairing.
  it('erases an account that was never paired', async () => {
    const solo = family();
    solo.users = [{id: ALICE, name: 'Alice', partnerId: ''}];
    solo.conversations = [];
    solo.messages = [];
    const db = fakeDb(solo);

    const result = await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.strictEqual(result.unpairedFrom, null);
    assert.deepStrictEqual(db._store.users, []);
    assert.deepStrictEqual(db._store.events.map((e) => e.id), ['ev-bob']);
  });

  it('deletes the parenting plan and the OAuth fingerprint', async () => {
    // Both were missed: the plan holds this parent's answers under their uid, keyed by the
    // pair, and `google_oauth/{uid}` is the fingerprint of a refresh token issued to an account
    // that is about to stop existing.
    const seed = family();
    seed.parenting_plans = [{id: `${ALICE}__${BOB}`, answers: {[ALICE]: {}, [BOB]: {}}}];
    seed.google_oauth = [{id: ALICE, fingerprint: 'abc'}, {id: BOB, fingerprint: 'def'}];
    const db = fakeDb(seed);

    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.parenting_plans, []);
    assert.deepStrictEqual(db._store.google_oauth.map((d) => d.id), [BOB]);
  });

  it('tears down every co-parent link of a parent with two families', async () => {
    // A bare `unpairCoParentImpl(db, uid)` is refused as ambiguous for a parent with two
    // co-parents, so the erasure used to run with both pairings intact: each co-parent kept a
    // `partnerIds` entry naming a uid nobody could sign in as.
    const seed = family();
    seed.users = [
      {id: ALICE, name: 'Alice', partnerId: BOB, partnerIds: [BOB, 'carol']},
      {id: BOB, name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
      {id: 'carol', name: 'Carol', partnerId: ALICE, partnerIds: [ALICE]},
    ];
    const db = fakeDb(seed);

    await myFunctions.deleteAccountDataImpl(db, ALICE);

    const bob = db._store.users.find((u) => u.id === BOB);
    const carol = db._store.users.find((u) => u.id === 'carol');
    assert.deepStrictEqual(bob.partnerIds || [], []);
    assert.deepStrictEqual(carol.partnerIds || [], []);
    assert.ok(!bob.partnerId, 'Bob must no longer name Alice');
    assert.ok(!carol.partnerId, 'Carol must no longer name Alice');
  });

  it('removes the friend grant in both directions', async () => {
    const withFriend = family();
    withFriend.calendar_friends = [
      {id: 'granny', familyParents: [ALICE, BOB], expiresAtMillis: 4102444800000},
      {id: ALICE, familyParents: ['other-a', 'other-b'], expiresAtMillis: 4102444800000},
    ];
    withFriend.friend_profiles = [{id: ALICE, name: 'Alice', familyParents: ['other-a', 'other-b']}];
    const db = fakeDb(withFriend);

    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.calendar_friends, [],
        'a grant naming the deleted family, or held by it, survived');
    assert.deepStrictEqual(db._store.friend_profiles, []);
  });

  it('removes professional grants in both directions (MON-18)', async () => {
    const seed = family();
    seed.professional_grants = [
      {id: 'alice__bob__med', familyId: 'alice__bob', familyParents: [ALICE, BOB], proUid: 'med',
        expiresAtMillis: 4102444800000},
      {id: 'x__y__alice', familyId: 'x__y', familyParents: ['x', 'y'], proUid: ALICE,
        expiresAtMillis: 4102444800000},
      {id: 'x__y__med', familyId: 'x__y', familyParents: ['x', 'y'], proUid: 'med',
        expiresAtMillis: 4102444800000},
    ];
    const db = fakeDb(seed);

    await myFunctions.deleteAccountDataImpl(db, ALICE);

    assert.deepStrictEqual(db._store.professional_grants.map((g) => g.id), ['x__y__med'],
        'only a grant that neither names nor is held by the deleted account may survive');
  });

  // The documents were erased and the files they named were not: a child's medical photographs
  // stayed in the bucket under ids nothing could look up any more. The files have to go first,
  // while the documents still say which files exist.
  it('deletes the files of authored records, and only those', async () => {
    const seed = family();
    seed.pets = [{id: 'pet-1', createdByFirebaseUid: ALICE, sharedWith: [ALICE, BOB]}];
    const db = fakeDb(seed);
    const bucket = fakeBucket();

    const result = await myFunctions.deleteAccountDataImpl(db, ALICE, bucket);

    assert.deepStrictEqual(bucket.deletedObjects.sort(),
        ['event_images/ev-alice.jpg', 'receipts/ex-1.jpg']);
    assert.deepStrictEqual(bucket.deletedPrefixes.sort(),
        [`chat_attachments/${ALICE}__${BOB}/`, 'medical_photos/ch-1/', 'pet_photos/pet-1/']);
    assert.ok(!bucket.deletedObjects.includes('event_images/ev-bob.jpg'),
        'the co-parent\'s event photo was deleted');
    assert.strictEqual(result.storage, 4);
    assert.strictEqual(result.chat_attachments, 1);
  });

  // MON-23. The vault's files are the departing parent's uploads, and its documents are authored
  // records like any other; the co-parent's own filings stay, with the departing uid scrubbed.
  it('deletes the vault files this parent filed, and only those', async () => {
    const seed = family();
    const fam = `${ALICE}__${BOB}`;
    seed.family_documents = [
      {id: 'vd-alice', familyId: fam, createdByFirebaseUid: ALICE, sharedWith: [ALICE, BOB]},
      {id: 'vd-bob', familyId: fam, createdByFirebaseUid: BOB, sharedWith: [ALICE, BOB]},
    ];
    const db = fakeDb(seed);
    const bucket = fakeBucket();

    const result = await myFunctions.deleteAccountDataImpl(db, ALICE, bucket);

    assert.ok(bucket.deletedPrefixes.includes(`family_documents/${fam}/vd-alice/`));
    assert.ok(!bucket.deletedPrefixes.includes(`family_documents/${fam}/vd-bob/`),
        'the co-parent\'s filing was deleted');
    assert.strictEqual(result.family_documents, 1);
    assert.deepStrictEqual(db._store.family_documents.map((d) => d.id), ['vd-bob']);
    assert.deepStrictEqual(db._store.family_documents[0].sharedWith, [BOB]);
  });

  it('never turns a vault document without a family into a prefix of the whole vault',
      async () => {
        const seed = family();
        seed.family_documents = [
          {id: 'vd-odd', familyId: '', createdByFirebaseUid: ALICE, sharedWith: [ALICE]},
        ];
        const bucket = fakeBucket();

        await myFunctions.deleteAccountDataImpl(fakeDb(seed), ALICE, bucket);

        assert.ok(!bucket.deletedPrefixes.some((p) => p.startsWith('family_documents/')),
            'a blank familyId produced a vault-wide prefix');
      });

  it('erases the chat files of every thread with the rest of the chat', async () => {
    const db = fakeDb(family());
    const bucket = fakeBucket();

    await myFunctions.deleteAccountDataImpl(db, ALICE, bucket);

    assert.ok(bucket.deletedPrefixes.includes(`chat_attachments/${ALICE}__${BOB}/`));
    assert.deepStrictEqual(db._store.messages, []);
  });

  it('keeps the documents when a file cannot be deleted, so a retry finds it', async () => {
    const db = fakeDb(family());
    const bucket = fakeBucket({failOn: 'receipts/ex-1.jpg'});

    await assert.rejects(myFunctions.deleteAccountDataImpl(db, ALICE, bucket));

    assert.deepStrictEqual(db._store.expenses.map((e) => e.id), ['ex-1']);
    assert.ok(db._store.users.some((u) => u.id === ALICE), 'the profile went before the files');
  });

  // MON-16. A receipt vouches for a file that may already be evidence; erasing the parent who
  // made it must not un-verify it. What goes is what identifies them.
  it('scrubs export receipts rather than deleting them, and drops unused reservations', async () => {
    const seedDocs = Object.assign(family(), {
      export_receipts: [
        {id: 'R-ALICE', state: 'registered', generatorUid: ALICE, familyId: `${ALICE}__${BOB}`,
          sha256: 'a'.repeat(64)},
        {id: 'R-PENDING', state: 'reserved', generatorUid: ALICE, familyId: `${ALICE}__${BOB}`},
        {id: 'R-BOB', state: 'registered', generatorUid: BOB, familyId: `${ALICE}__${BOB}`,
          sha256: 'b'.repeat(64)},
      ],
    });
    const db = fakeDb(seedDocs);
    const removed = await myFunctions.deleteAccountDataImpl(db, ALICE);

    const byId = (id) => db._store.export_receipts.find((r) => r.id === id);
    assert.strictEqual(byId('R-PENDING'), undefined);
    assert.deepStrictEqual(
        [byId('R-ALICE').generatorUid, byId('R-ALICE').familyId, byId('R-ALICE').sha256],
        ['', '', 'a'.repeat(64)]);
    assert.deepStrictEqual([byId('R-BOB').generatorUid, byId('R-BOB').familyId], [BOB, '']);
    assert.strictEqual(removed.export_receipts_deleted, 1);
    assert.ok(!JSON.stringify(db._store.export_receipts).includes(ALICE), 'the erased uid survived');
  });
});

/**
 * A Storage bucket that records what it was asked to delete.
 *
 * @param {{failOn: (string|undefined)}=} options An object path whose delete throws.
 * @return {!Object} The fake, exposing `deletedObjects` and `deletedPrefixes`.
 */
function fakeBucket(options) {
  const failOn = options && options.failOn;
  const deletedObjects = [];
  const deletedPrefixes = [];
  return {
    deletedObjects,
    deletedPrefixes,
    file(path) {
      return {
        async delete(opts) {
          assert.ok(opts && opts.ignoreNotFound, 'a missing photo must not fail the erasure');
          if (path === failOn) throw new Error(`storage unavailable for ${path}`);
          deletedObjects.push(path);
        },
      };
    },
    async deleteFiles(opts) {
      deletedPrefixes.push(opts.prefix);
    },
  };
}
