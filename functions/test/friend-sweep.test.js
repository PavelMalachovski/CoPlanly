const test = require('firebase-functions-test')();
const assert = require('assert');

/**
 * Fake Firestore for `calendar_friends`, applying the sweep's range filters itself.
 *
 * The filters are applied rather than ignored because they *are* the safety property: a range
 * filter on `expiresAtMillis` matches only documents whose field is a number, which is what
 * keeps a grant with no expiry out of the sweep. A fake that returned everything would let a
 * sweep that deleted indiscriminately pass.
 *
 * @param {!Object<string, !Object>} grants Grants keyed by friend uid.
 * @return {!Object} A fake with `_deleted` and `_commits` recorders.
 */
function fakeDb(grants) {
  const deleted = [];
  const commits = [];

  const query = (filters) => ({
    where(field, op, value) {
      assert.strictEqual(field, 'expiresAtMillis');
      return query(filters.concat([{op, value}]));
    },
    async get() {
      const ids = Object.keys(grants).filter((id) => {
        const v = grants[id].expiresAtMillis;
        return typeof v === 'number' && filters.every(({op, value}) => {
          if (op === '>') return v > value;
          if (op === '<=') return v <= value;
          throw new Error(`unsupported operator ${op}`);
        });
      });
      return {docs: ids.map((id) => ({id, data: () => grants[id], ref: {id}}))};
    },
  });

  return {
    _deleted: deleted,
    _commits: commits,
    collection(name) {
      assert.strictEqual(name, 'calendar_friends');
      return query([]);
    },
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push(ref.id);
        },
        async commit() {
          ops.forEach((id) => deleted.push(id));
          commits.push(ops.length);
        },
      };
    },
  };
}

const NOW = Date.parse('2026-09-23T12:00:00Z');
const DAY = 24 * 60 * 60 * 1000;

/**
 * A grant as `acceptCalendarFriendInvitation` writes it.
 *
 * @param {*} expiresAtMillis When access ends.
 * @return {!Object} The stored grant.
 */
function grant(expiresAtMillis) {
  return {
    familyParents: ['alice', 'bob'],
    familyId: 'alice__bob',
    name: 'Nina',
    grantedBy: 'alice',
    grantedAtMillis: NOW - 30 * DAY,
    expiresAtMillis,
  };
}

describe('sweepLapsedCalendarFriends', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  it('deletes a grant whose expiry has passed', async () => {
    const db = fakeDb({nina: grant(NOW - DAY)});

    const removed = await index.sweepLapsedCalendarFriendsImpl(db, NOW);

    assert.strictEqual(removed, 1);
    assert.deepStrictEqual(db._deleted, ['nina']);
  });

  it('keeps a grant that is still running', async () => {
    const db = fakeDb({nina: grant(NOW + DAY)});

    const removed = await index.sweepLapsedCalendarFriendsImpl(db, NOW);

    assert.strictEqual(removed, 0);
    assert.deepStrictEqual(db._deleted, []);
  });

  it('sweeps a grant ending exactly now, as the rule already refuses it', async () => {
    // `isCalendarFriendOf` admits only while `request.time < expiresAtMillis`. If the sweep
    // rounded the other way the parents' list would name a friend the rule had already ended.
    const db = fakeDb({nina: grant(NOW)});

    await index.sweepLapsedCalendarFriendsImpl(db, NOW);

    assert.deepStrictEqual(db._deleted, ['nina']);
  });

  it('never deletes a grant with no expiry', async () => {
    // Absent, null, a string, or not positive: none of these is an expiry, and what such a
    // grant means is a person's call, not a scheduled job's.
    const db = fakeDb({
      absent: grant(undefined),
      nulled: grant(null),
      text: grant('2026-01-01'),
      zero: grant(0),
      lapsed: grant(NOW - DAY),
    });

    await index.sweepLapsedCalendarFriendsImpl(db, NOW);

    assert.deepStrictEqual(db._deleted, ['lapsed']);
  });

  it('splits a large sweep into batches below the write cap', async () => {
    const grants = {};
    for (let i = 0; i < 900; i++) {
      grants[`f${i}`] = grant(NOW - DAY);
    }
    const db = fakeDb(grants);

    const removed = await index.sweepLapsedCalendarFriendsImpl(db, NOW);

    assert.strictEqual(removed, 900);
    assert.ok(db._commits.every((n) => n <= 400), `commits: ${db._commits}`);
  });
});
