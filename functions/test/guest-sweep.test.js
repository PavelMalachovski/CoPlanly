const test = require('firebase-functions-test')();
const assert = require('assert');
const sinon = require('sinon');

/**
 * Fake Firestore serving a whole-collection `get()` and recording batched updates.
 *
 * `sharedWith` goes out as an `arrayRemove` sentinel whose value is not introspectable, so
 * the assertions are on the `guests` map — which the sweep writes whole — and on *which*
 * documents were touched at all. What `arrayRemove` does to the stored array is Firestore's
 * contract; that a narrowed audience actually revokes access is covered by
 * firestore-tests/rules/child-info.test.js.
 *
 * Also serves the one range query the indexed sweep issues (`<=` on a field, which, as in
 * Firestore, matches only documents whose field is a number) and the `ops/guestSweep` marker;
 * `_queries` records whether a run scanned or queried.
 *
 * @param {!Array<!Object>} childInfo Documents in the `child_info` collection.
 * @param {?Object=} marker The stored `ops/guestSweep` document, or absent.
 * @return {!Object} A fake with `_updates`, `_commits`, `_queries` and `_marker` recorders.
 */
function fakeDb(childInfo, marker) {
  const updates = [];
  const commits = [];
  const queries = [];
  const snapshot = (docs, name) => ({
    docs: docs.map((doc) => ({
      id: doc.id,
      data: () => doc,
      ref: {id: doc.id, collection: name},
    })),
  });

  const db = {
    _updates: updates,
    _commits: commits,
    _queries: queries,
    _marker: marker,
    collection(name) {
      const docs = name === 'child_info' ? childInfo : [];
      return {
        async get() {
          queries.push({collection: name, kind: 'scan'});
          return snapshot(docs, name);
        },
        where(field, op, value) {
          assert.strictEqual(op, '<=', 'the sweep only ever asks for "ended by now"');
          return {
            async get() {
              queries.push({collection: name, kind: 'range', field, value});
              return snapshot(docs.filter((doc) =>
                typeof doc[field] === 'number' && doc[field] <= value), name);
            },
          };
        },
        doc(id) {
          assert.deepStrictEqual([name, id], ['ops', 'guestSweep']);
          return {
            async get() {
              return {exists: db._marker !== undefined, data: () => db._marker};
            },
            async set(data, options) {
              assert.deepStrictEqual(options, {merge: true});
              db._marker = Object.assign({}, db._marker, data);
            },
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
  return db;
}

const NOW = Date.parse('2026-08-23T12:00:00Z');
// Relative to the real clock, not to NOW: `acceptGuestInvitation` below checks the invitation's
// expiry against `Date.now()`, so a fixed date turned that test red the day it passed. It stays
// after NOW too, which is all the sweep cases need.
const ACTIVE = Date.now() + 30 * 24 * 60 * 60 * 1000;
const ENDED = Date.parse('2026-08-01T00:00:00Z');

/**
 * A grant.
 *
 * @param {number|undefined} expiresAtMillis When it ends.
 * @return {!Object} The stored grant.
 */
function grant(expiresAtMillis) {
  return {
    name: 'Nina',
    grantedBy: 'alice',
    grantedAtMillis: Date.parse('2026-07-01T00:00:00Z'),
    expiresAtMillis,
  };
}

describe('sweepExpiredGuests', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  after(() => {
    test.cleanup();
    sinon.restore();
  });

  it('removes a grant that has ended, and keeps the ones that have not', async () => {
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'bob', 'nina', 'otto'],
      guests: {nina: grant(ENDED), otto: grant(ACTIVE)},
    }]);

    const removed = await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.strictEqual(removed, 1);
    assert.strictEqual(db._updates.length, 1);
    assert.deepStrictEqual(
        Object.keys(db._updates[0].update.guests), ['otto'],
        'the grant that has not ended must survive the sweep');
  });

  it('takes the swept uid out of the audience too', async () => {
    // The rule alone is not enough: it stops the read, but a uid left in `sharedWith` keeps
    // the document coming back from every audience query the guest issues.
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'bob', 'nina'],
      guests: {nina: grant(ENDED)},
    }]);

    await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.ok(
        'sharedWith' in db._updates[0].update,
        'the sweep must narrow the audience, not just the map');
  });

  it('leaves a record whose guests are all still active untouched', async () => {
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'nina'],
      guests: {nina: grant(ACTIVE)},
      guestsMinExpiresAtMillis: ACTIVE,
    }]);

    const removed = await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.strictEqual(removed, 0);
    assert.deepStrictEqual(db._updates, []);
    assert.deepStrictEqual(db._commits, [], 'nothing to write means nothing to commit');
  });

  it('skips a record with no guests at all', async () => {
    const db = fakeDb([
      {id: 'child1', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'bob']},
      {id: 'child2', createdByFirebaseUid: 'alice', sharedWith: ['alice'], guests: {}},
    ]);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 0);
    assert.deepStrictEqual(db._updates, []);
  });

  it('ends a grant exactly at its expiry, matching the rule and the app', async () => {
    // All three implement the same strict comparison. If this one rounded the other way, a
    // grant would be live for the rules and swept by the sweep, or the reverse.
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'nina'],
      guests: {nina: grant(NOW)},
    }]);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 1);
  });

  it('sweeps a grant with no expiry, rather than treating it as permanent', async () => {
    // The one default this feature must never have. A grant that reached the record without
    // an end — an older client, a partial write — is removed, not kept forever.
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'nina', 'otto', 'pia'],
      guests: {nina: grant(undefined), otto: grant(0), pia: grant('2099-01-01')},
    }]);

    const removed = await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.strictEqual(removed, 3);
    assert.deepStrictEqual(Object.keys(db._updates[0].update.guests), []);
  });

  it('never removes the uid that created the record', async () => {
    // Belt to the callable's brace. A parent should never end up in `guests` at all, but if
    // one ever does, sweeping them out of `sharedWith` would hide the child from the parent
    // who entered them — the failure `revokeSharedAudience` guards against by the same rule.
    const db = fakeDb([{
      id: 'child1',
      createdByFirebaseUid: 'alice',
      sharedWith: ['alice', 'bob'],
      guests: {alice: grant(ENDED)},
    }]);

    const removed = await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.strictEqual(removed, 1, 'the stale grant itself is still cleaned off the map');
    assert.deepStrictEqual(Object.keys(db._updates[0].update.guests), []);
    assert.ok(
        !('sharedWith' in db._updates[0].update),
        'the creator must not be taken out of the audience of their own record');
  });

  it('sweeps across records, one update each', async () => {
    const db = fakeDb([
      {
        id: 'child1',
        createdByFirebaseUid: 'alice',
        sharedWith: ['alice', 'nina'],
        guests: {nina: grant(ENDED)},
      },
      {
        id: 'child2',
        createdByFirebaseUid: 'carol',
        sharedWith: ['carol', 'otto'],
        guests: {otto: grant(ENDED)},
      },
    ]);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 2);
    assert.deepStrictEqual(db._updates.map((u) => u.id), ['child1', 'child2']);
    assert.deepStrictEqual(db._commits, [2], 'one batch is enough for two records');
  });

  it('splits a large sweep below the 500-operation write cap', async () => {
    const many = [];
    for (let i = 0; i < 401; i++) {
      many.push({
        id: `child${i}`,
        createdByFirebaseUid: 'alice',
        sharedWith: ['alice', 'nina'],
        guests: {nina: grant(ENDED)},
      });
    }
    const db = fakeDb(many);

    await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.deepStrictEqual(db._commits, [400, 1]);
    db._commits.forEach((size) => assert.ok(size <= 400, 'a batch exceeded the cap'));
  });

  it('ignores a guests field that is not a map', async () => {
    const db = fakeDb([
      {id: 'child1', createdByFirebaseUid: 'alice', sharedWith: ['alice'], guests: 'nina'},
      {id: 'child2', createdByFirebaseUid: 'alice', sharedWith: ['alice'], guests: ['nina']},
    ]);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 0);
  });
});

describe('sweepExpiredGuests range index (L-10)', () => {
  let myFunctions;
  const INDEXED = {guestExpiryIndexVersion: 1};

  before(() => {
    myFunctions = require('../index');
  });

  it('scans once while the marker is missing, stamps as it goes, then writes the marker',
      async () => {
        // Records written before the field existed carry none; only a scan can find them.
        const db = fakeDb([
          {id: 'old', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'nina'],
            guests: {nina: grant(ACTIVE)}},
          {id: 'none', createdByFirebaseUid: 'alice', sharedWith: ['alice']},
        ]);

        await myFunctions.sweepExpiredGuestsImpl(db, NOW);

        assert.deepStrictEqual(db._queries.map((q) => q.kind), ['scan']);
        assert.deepStrictEqual(db._updates,
            [{id: 'old', update: {guestsMinExpiresAtMillis: ACTIVE}}],
            'an active grant is stamped, untouched otherwise; a record without guests is left');
        assert.strictEqual(db._marker.guestExpiryIndexVersion, 1);
        assert.strictEqual(db._marker.backfilledAtMillis, NOW);
      });

  it('queries, never scans, once the marker says the backfill is done', async () => {
    const db = fakeDb([
      {id: 'due', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'nina'],
        guests: {nina: grant(ENDED)}, guestsMinExpiresAtMillis: ENDED},
      {id: 'later', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'otto'],
        guests: {otto: grant(ACTIVE)}, guestsMinExpiresAtMillis: ACTIVE},
    ], INDEXED);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 1);

    assert.deepStrictEqual(db._queries,
        [{collection: 'child_info', kind: 'range', field: 'guestsMinExpiresAtMillis', value: NOW}]);
    assert.deepStrictEqual(db._updates.map((u) => u.id), ['due']);
  });

  it('matches a grant ending exactly now, as the rule does', async () => {
    const db = fakeDb([
      {id: 'c', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'nina'],
        guests: {nina: grant(NOW)}, guestsMinExpiresAtMillis: NOW},
    ], INDEXED);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 1);
  });

  it('moves the index to the next grant to end, or deletes it with the last one', async () => {
    const db = fakeDb([
      {id: 'two', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'nina', 'otto'],
        guests: {nina: grant(ENDED), otto: grant(ACTIVE)}, guestsMinExpiresAtMillis: ENDED},
      {id: 'one', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'nina'],
        guests: {nina: grant(ENDED)}, guestsMinExpiresAtMillis: ENDED},
    ], INDEXED);

    await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    const byId = {};
    db._updates.forEach((u) => {
      byId[u.id] = u.update;
    });
    assert.strictEqual(byId.two.guestsMinExpiresAtMillis, ACTIVE);
    assert.notStrictEqual(typeof byId.one.guestsMinExpiresAtMillis, 'number',
        'a record with no guest left must drop out of the index (a field delete)');
  });

  it('re-stamps a stale-low index without removing anything, so it stops matching', async () => {
    // A dotted `guests.<uid>` delete (account deletion) leaves the old minimum behind until the
    // trigger runs; the sweep then reads the record, finds nothing ended, and corrects it.
    const db = fakeDb([
      {id: 'c', createdByFirebaseUid: 'alice', sharedWith: ['alice', 'otto'],
        guests: {otto: grant(ACTIVE)}, guestsMinExpiresAtMillis: ENDED},
    ], INDEXED);

    assert.strictEqual(await myFunctions.sweepExpiredGuestsImpl(db, NOW), 0);
    assert.deepStrictEqual(db._updates,
        [{id: 'c', update: {guestsMinExpiresAtMillis: ACTIVE}}]);
  });

  it('runs the full scan again when the marker is an older version', async () => {
    const db = fakeDb([], {guestExpiryIndexVersion: 0});

    await myFunctions.sweepExpiredGuestsImpl(db, NOW);

    assert.deepStrictEqual(db._queries.map((q) => q.kind), ['scan']);
    assert.strictEqual(db._marker.guestExpiryIndexVersion, 1);
  });
});

describe('guestsMinExpiresAtMillis', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('is the earliest expiry, null for no guests, and 0 for a grant without one', () => {
    const min = myFunctions.guestsMinExpiresAtMillis;
    assert.strictEqual(min({a: grant(ACTIVE), b: grant(ENDED)}), ENDED);
    assert.strictEqual(min({}), null);
    assert.strictEqual(min(undefined), null);
    assert.strictEqual(min(['nina']), null);
    assert.strictEqual(min('nina'), null);
    // Fail closed: an unusable expiry must fall inside `<= now`, never outside the range.
    assert.strictEqual(min({a: grant(ACTIVE), b: grant(undefined)}), 0);
    assert.strictEqual(min({a: grant('2099-01-01')}), 0);
    assert.strictEqual(min({a: grant(-5)}), 0);
    assert.strictEqual(min({a: null}), 0);
  });
});

describe('maintainGuestExpiryIndex', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  /**
   * A fake with one `child_info` document behind a transaction.
   *
   * @param {?Object} stored The stored document, or null when it does not exist.
   * @return {!Object} The fake, with `_writes` and `_transactions`.
   */
  function txDb(stored) {
    const db = {
      _writes: [],
      _transactions: 0,
      collection(name) {
        assert.strictEqual(name, 'child_info');
        return {doc: (id) => ({id})};
      },
      async runTransaction(fn) {
        db._transactions++;
        return fn({
          get: async () => ({exists: stored !== null, data: () => stored}),
          update: (ref, update) => db._writes.push({id: ref.id, update}),
        });
      },
    };
    return db;
  }

  it('puts back the index a client\'s whole-document set() dropped', async () => {
    // The app writes child_info with set() from Room, which knows nothing of the field.
    const after = {sharedWith: ['alice', 'nina'], guests: {nina: grant(ACTIVE)}};
    const db = txDb(after);

    assert.strictEqual(await myFunctions.syncGuestExpiryIndexImpl(db, 'c', after), 'written');
    assert.deepStrictEqual(db._writes, [{id: 'c', update: {guestsMinExpiresAtMillis: ACTIVE}}]);
  });

  it('reads nothing when the document already agrees, which ends its own loop', async () => {
    const after = {guests: {nina: grant(ACTIVE)}, guestsMinExpiresAtMillis: ACTIVE};
    const db = txDb(after);

    assert.strictEqual(await myFunctions.syncGuestExpiryIndexImpl(db, 'c', after), 'unchanged');
    assert.strictEqual(await myFunctions.syncGuestExpiryIndexImpl(db, 'c', {sharedWith: []}),
        'unchanged', 'no guests and no field is already right');
    assert.strictEqual(db._transactions, 0);
  });

  it('corrects a value a client wrote, from the guests map', async () => {
    const after = {guests: {nina: grant(ENDED)}, guestsMinExpiresAtMillis: 9e15};
    const db = txDb(after);

    await myFunctions.syncGuestExpiryIndexImpl(db, 'c', after);

    assert.strictEqual(db._writes[0].update.guestsMinExpiresAtMillis, ENDED);
  });

  it('removes the field once the last guest is gone', async () => {
    const after = {guests: {}, guestsMinExpiresAtMillis: ACTIVE};
    const db = txDb(after);

    await myFunctions.syncGuestExpiryIndexImpl(db, 'c', after);

    assert.strictEqual(db._writes.length, 1);
    assert.notStrictEqual(typeof db._writes[0].update.guestsMinExpiresAtMillis, 'number');
  });

  it('computes from the stored document, not from a late event', async () => {
    // The event says one thing, but a later write already corrected the document.
    const stale = {guests: {nina: grant(ENDED)}};
    const db = txDb({guests: {nina: grant(ENDED)}, guestsMinExpiresAtMillis: ENDED});

    assert.strictEqual(await myFunctions.syncGuestExpiryIndexImpl(db, 'c', stale), 'unchanged');
    assert.deepStrictEqual(db._writes, []);
  });

  it('does nothing for a deleted record', async () => {
    assert.strictEqual(await myFunctions.syncGuestExpiryIndexImpl(txDb(null), 'c', null),
        'absent');
    const gone = txDb(null);
    assert.strictEqual(
        await myFunctions.syncGuestExpiryIndexImpl(gone, 'c', {guests: {n: grant(ACTIVE)}}),
        'absent', 'deleted between the write and the transaction');
    assert.deepStrictEqual(gone._writes, []);
  });
});

describe('acceptGuestInvitation refuses somebody who already reads the record', () => {
  let myFunctions;

  before(() => {
    myFunctions = require('../index');
  });

  it('does not make the co-parent a guest of their own child', async () => {
    // Found while writing the sweep. A co-parent redeeming a guest code passed every check —
    // they are not the inviter, and the inviter is entitled — and landed in `guests` while
    // already being a parent in `sharedWith`. The grant then expires, and the sweep takes a
    // *parent* out of the audience of their own child's record.
    const db = {
      collection(name) {
        const doc = (id) => ({
          id,
          collection: name,
          async get() {
            const data = (db._docs[name] || {})[id];
            return {exists: data !== undefined, data: () => data, ref: doc(id)};
          },
        });
        return {doc};
      },
      _docs: {
        invitations: {
          inv1: {
            id: 'inv1',
            kind: 'guest',
            status: 'pending',
            fromUserId: 'alice',
            toEmail: '',
            childInfoId: 'child1',
            guestExpiresAt: ACTIVE,
          },
        },
        child_info: {
          child1: {
            id: 'child1',
            createdByFirebaseUid: 'alice',
            sharedWith: ['alice', 'bob'],
          },
        },
        users: {bob: {id: 'bob', name: 'Bob'}},
      },
      async runTransaction(fn) {
        return fn({get: (ref) => ref.get(), update: () => {}});
      },
    };

    await assert.rejects(
        () => myFunctions.acceptGuestInvitationImpl(db, 'bob', 'bob@example.com',
            {code: null, invitationId: 'inv1'}),
        (err) => err.details.reason === 'already-entitled');
  });
});
