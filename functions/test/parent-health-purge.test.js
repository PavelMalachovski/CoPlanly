const test = require('firebase-functions-test')();
const assert = require('assert');
const {FieldValue} = require('firebase-admin/firestore');

/**
 * Fake Firestore serving a whole-collection `get()` of `users` and recording batched updates.
 *
 * @param {!Array<!Object>} users Documents in the `users` collection, each with an `id`.
 * @return {!Object} A fake with `_updates` and `_commits` recorders.
 */
function fakeDb(users) {
  const updates = [];
  const commits = [];

  return {
    _updates: updates,
    _commits: commits,
    collection(name) {
      return {
        async get() {
          const docs = name === 'users' ? users : [];
          return {
            docs: docs.map((doc) => ({
              id: doc.id,
              data: () => doc,
              ref: {id: doc.id, collection: name},
            })),
          };
        },
      };
    },
    batch() {
      const ops = [];
      return {
        update(ref, update) {
          ops.push({id: ref.id, update});
        },
        async commit() {
          ops.forEach((op) => updates.push(op));
          commits.push(ops.length);
        },
      };
    },
  };
}

describe('purgeParentHealthFields', () => {
  const ORIGINAL_ADMIN_UIDS = process.env.BACKFILL_ADMIN_UIDS;
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  afterEach(() => {
    if (ORIGINAL_ADMIN_UIDS === undefined) {
      delete process.env.BACKFILL_ADMIN_UIDS;
    } else {
      process.env.BACKFILL_ADMIN_UIDS = ORIGINAL_ADMIN_UIDS;
    }
  });

  after(() => {
    test.cleanup();
  });

  it('deletes both keys from every profile that holds either, and counts them', async () => {
    const db = fakeDb([
      {id: 'alice', name: 'Alice', allergies: ['peanuts'], medicalProfile: {bloodType: 'O_NEGATIVE'}},
      {id: 'bob', name: 'Bob', medicalProfile: {}},
      {id: 'carol', name: 'Carol', allergies: []},
      {id: 'dan', name: 'Dan', phone: '+420123456789'},
    ]);

    const summary = await myFunctions.purgeParentHealthFieldsImpl(db);

    assert.deepStrictEqual(summary, {scanned: 4, purged: 3});
    assert.deepStrictEqual(db._updates.map((op) => op.id), ['alice', 'bob', 'carol']);
    db._updates.forEach(({update}) => {
      assert.deepStrictEqual(Object.keys(update).sort(), ['allergies', 'medicalProfile']);
      assert.ok(update.allergies.isEqual(FieldValue.delete()), 'allergies is not a delete');
      assert.ok(update.medicalProfile.isEqual(FieldValue.delete()), 'medicalProfile is not a delete');
    });
  });

  it('touches nothing else on the profile', async () => {
    const db = fakeDb([
      {id: 'alice', name: 'Alice', partnerId: 'bob', phone: '+420', allergies: ['peanuts']},
    ]);

    await myFunctions.purgeParentHealthFieldsImpl(db);

    assert.deepStrictEqual(
        Object.keys(db._updates[0].update).sort(), ['allergies', 'medicalProfile']);
  });

  it('writes nothing when no profile holds the keys, so a re-run is harmless', async () => {
    const db = fakeDb([{id: 'alice', name: 'Alice'}, {id: 'bob', name: 'Bob'}]);

    const summary = await myFunctions.purgeParentHealthFieldsImpl(db);

    assert.deepStrictEqual(summary, {scanned: 2, purged: 0});
    assert.deepStrictEqual(db._commits, []);
  });

  it('commits in batches below Firestore\'s 500-write cap', async () => {
    const users = [];
    for (let i = 0; i < 901; i++) {
      users.push({id: `user-${i}`, allergies: ['x']});
    }
    const db = fakeDb(users);

    const summary = await myFunctions.purgeParentHealthFieldsImpl(db);

    assert.strictEqual(summary.purged, 901);
    assert.deepStrictEqual(db._commits, [400, 400, 101]);
  });

  it('refuses a caller who is not an operator', async () => {
    process.env.BACKFILL_ADMIN_UIDS = 'op-uid';
    const wrapped = test.wrap(myFunctions.purgeParentHealthFields);

    await assert.rejects(
        () => wrapped({}, {auth: {uid: 'someone-else'}}),
        (err) => err.code === 'permission-denied');
    await assert.rejects(
        () => wrapped({}, {}),
        (err) => err.code === 'permission-denied');
  });
});
