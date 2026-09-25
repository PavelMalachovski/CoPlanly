const test = require('firebase-functions-test')();
const assert = require('assert');
const {Timestamp} = require('firebase-admin/firestore');
const receipts = require('../export-receipts');

/**
 * Storage limitation (GDPR Art. 5(1)(e), September 2026): export receipts and invitations that
 * nothing needs any more are deleted on a schedule, and nothing that is still needed is.
 *
 * The fake applies every filter the sweeps send — equality, `in`, and the ranges, on numbers and
 * on `Timestamp`s alike — because the filters *are* the safety property: a range matches only a
 * value of its own type, and the status filter is what keeps an accepted invitation out. A fake
 * that returned everything would let an indiscriminate sweep pass.
 *
 * @param {!Object<string, !Object<string, !Object>>} collections Documents by collection and id.
 * @return {!Object} The fake, exposing `_store`.
 */
function fakeDb(collections) {
  const store = {};
  Object.keys(collections).forEach((name) => {
    store[name] = Object.assign({}, collections[name]);
  });
  const docs = (name) => (store[name] = store[name] || {});

  const comparable = (v) => (v instanceof Timestamp ? v.toMillis() : v);
  const sameType = (a, b) => (a instanceof Timestamp) === (b instanceof Timestamp) &&
    typeof comparable(a) === typeof comparable(b);
  const matches = (value, op, bound) => {
    if (op === '==') return value === bound;
    if (op === 'in') return bound.includes(value);
    if (value === undefined || value === null || !sameType(value, bound)) return false;
    const a = comparable(value);
    const b = comparable(bound);
    if (op === '<') return a < b;
    if (op === '<=') return a <= b;
    if (op === '>') return a > b;
    throw new Error(`fakeDb: unsupported operator ${op}`);
  };

  const query = (name, predicates, max) => ({
    where(field, op, value) {
      return query(name, predicates.concat([[field, op, value]]), max);
    },
    limit(n) {
      return query(name, predicates, n);
    },
    async get() {
      const ids = Object.keys(docs(name))
          .filter((id) => predicates.every(([f, o, v]) => matches(docs(name)[id][f], o, v)))
          .slice(0, max || Infinity);
      return {
        docs: ids.map((id) => ({id, data: () => docs(name)[id], ref: {_name: name, _id: id}})),
      };
    },
  });

  return {
    _store: store,
    collection(name) {
      return Object.assign(query(name, [], 0), {doc: (id) => ({
        _name: name,
        _id: id,
        async get() {
          const data = docs(name)[id];
          return {id, exists: data !== undefined, data: () => data};
        },
      })});
    },
    batch() {
      const ops = [];
      return {
        delete(ref) {
          ops.push(ref);
        },
        async commit() {
          ops.forEach((ref) => delete docs(ref._name)[ref._id]);
        },
      };
    },
  };
}

const NOW = Date.parse('2026-09-25T10:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const ts = (millis) => Timestamp.fromMillis(millis);
const TEN_YEARS_AGO = Date.parse('2016-09-25T10:00:00Z');

/**
 * A receipt as `registerImpl` leaves it, registered at [recordedMillis].
 *
 * @param {number} recordedMillis Registration time.
 * @return {!Object} The stored receipt.
 */
function registered(recordedMillis) {
  return {
    state: 'registered', generatorUid: 'alice', familyId: 'alice__bob',
    fromDate: '2016-01-01', toDate: '2016-03-31', format: 'pdf',
    reservedAt: ts(recordedMillis - 1000), recordedAt: ts(recordedMillis),
    sha256: 'a'.repeat(64), byteLength: 4096, formatVersion: 1,
  };
}

/**
 * A reservation that never received a hash, made at [reservedMillis].
 *
 * @param {number} reservedMillis Reservation time.
 * @return {!Object} The stored reservation.
 */
function reserved(reservedMillis) {
  return {
    state: 'reserved', generatorUid: 'alice', familyId: 'alice__bob',
    fromDate: '2026-01-01', toDate: '2026-03-31', format: 'csv',
    reservedAt: ts(reservedMillis), formatVersion: 1,
  };
}

describe('export receipt retention', () => {
  it('counts ten calendar years, so a leap day never shortens it', () => {
    assert.strictEqual(receipts.yearsBefore(NOW, 10), TEN_YEARS_AGO);
    assert.strictEqual(receipts.RECEIPT_RETENTION_YEARS, 10);
    assert.strictEqual(receipts.RESERVATION_RETENTION_DAYS, 7);
  });

  it('deletes a receipt registered more than ten years ago and keeps one exactly ten', async () => {
    const db = fakeDb({export_receipts: {
      'OLD': registered(TEN_YEARS_AGO - 1),
      'EXACT': registered(TEN_YEARS_AGO),
      'YOUNGER': registered(TEN_YEARS_AGO + 1),
      'RECENT': registered(NOW - 30 * DAY),
    }});

    const swept = await receipts.sweepReceiptsImpl(db, NOW, Timestamp.fromMillis);

    assert.deepStrictEqual(swept, {registered: 1, reservations: 0});
    assert.deepStrictEqual(Object.keys(db._store.export_receipts).sort(),
        ['EXACT', 'RECENT', 'YOUNGER']);
  });

  it('deletes a reservation older than seven days and keeps one exactly seven', async () => {
    const db = fakeDb({export_receipts: {
      'STALE': reserved(NOW - 7 * DAY - 1),
      'EXACT': reserved(NOW - 7 * DAY),
      'FRESH': reserved(NOW - 7 * DAY + 1),
      'TODAY': reserved(NOW - 60 * 1000),
    }});

    const swept = await receipts.sweepReceiptsImpl(db, NOW, Timestamp.fromMillis);

    assert.deepStrictEqual(swept, {registered: 0, reservations: 1});
    assert.deepStrictEqual(Object.keys(db._store.export_receipts).sort(),
        ['EXACT', 'FRESH', 'TODAY']);
  });

  it('never deletes a registered receipt for being an old reservation', async () => {
    // A registered receipt keeps its `reservedAt`; the reservation query must not reach it.
    const db = fakeDb({export_receipts: {'REG': registered(NOW - 400 * DAY)}});

    await receipts.sweepReceiptsImpl(db, NOW, Timestamp.fromMillis);

    assert.deepStrictEqual(Object.keys(db._store.export_receipts), ['REG']);
  });

  it('never deletes a receipt whose time is not a Timestamp', async () => {
    const odd = registered(0);
    odd.recordedAt = 0;
    const oddReservation = reserved(0);
    oddReservation.reservedAt = '2001-01-01';
    const db = fakeDb({export_receipts: {'ODD': odd, 'ODD-RES': oddReservation}});

    const swept = await receipts.sweepReceiptsImpl(db, NOW, Timestamp.fromMillis);

    assert.deepStrictEqual(swept, {registered: 0, reservations: 0});
  });

  // Nothing to change in `verifyImpl`: a swept receipt reads as an id that never existed, which
  // says nothing about whether a record ever did.
  it('answers "not found" for a swept receipt, as for an id that never existed', async () => {
    const id = '0000000000000000';
    const db = fakeDb({export_receipts: {[id]: registered(TEN_YEARS_AGO - DAY)}});
    assert.strictEqual((await receipts.verifyImpl(db, {recordId: id})).found, true);

    await receipts.sweepReceiptsImpl(db, NOW, Timestamp.fromMillis);

    assert.deepStrictEqual(await receipts.verifyImpl(db, {recordId: id}), {found: false});
    assert.deepStrictEqual(await receipts.verifyImpl(db, {recordId: 'ZZZZZZZZZZZZZZZZ'}),
        {found: false});
  });
});

describe('sweepUnacceptedInvitationsImpl', () => {
  let index;

  before(() => {
    index = require('../index');
  });

  after(() => {
    test.cleanup();
  });

  /**
   * An invitation as the clients write it.
   *
   * @param {string} status Its state.
   * @param {*} expiresAt When the code stops working.
   * @param {*=} createdAt When it was made.
   * @param {string=} kind Absent for a co-parent invitation.
   * @return {!Object} The stored invitation.
   */
  function invitation(status, expiresAt, createdAt, kind) {
    const doc = {
      code: 'ABC123', fromUserId: 'alice', fromUserName: 'Alice', fromUserEmail: 'a@x.test',
      toEmail: '', status, expiresAt,
      createdAt: createdAt === undefined ? NOW - 400 * DAY : createdAt,
      acceptedBy: status === 'accepted' ? 'bob' : null,
    };
    if (kind) doc.kind = kind;
    return doc;
  }

  it('keeps the limits the owner chose', () => {
    assert.strictEqual(index.INVITATION_EXPIRED_GRACE_DAYS, 30);
    assert.strictEqual(index.INVITATION_UNDATED_MAX_AGE_DAYS, 90);
  });

  it('deletes an unaccepted invitation that expired more than thirty days ago, of any kind',
      async () => {
        const past = NOW - 30 * DAY - 1;
        const db = fakeDb({invitations: {
          'pending': invitation('pending', past),
          'cancelled': invitation('cancelled', past),
          'rejected': invitation('rejected', past),
          'guest': invitation('pending', past, undefined, 'guest'),
          'friend': invitation('pending', past, undefined, 'friend'),
          'professional': invitation('pending', past, undefined, 'professional'),
        }});

        const swept = await index.sweepUnacceptedInvitationsImpl(db, NOW);

        assert.deepStrictEqual(swept, {expired: 6, undated: 0});
        assert.deepStrictEqual(db._store.invitations, {});
      });

  it('keeps one that expired exactly thirty days ago, or more recently', async () => {
    const db = fakeDb({invitations: {
      'exact': invitation('pending', NOW - 30 * DAY),
      'recent': invitation('pending', NOW - DAY),
      'live': invitation('pending', NOW + DAY),
    }});

    const swept = await index.sweepUnacceptedInvitationsImpl(db, NOW);

    assert.deepStrictEqual(swept, {expired: 0, undated: 0});
    assert.deepStrictEqual(Object.keys(db._store.invitations).sort(), ['exact', 'live', 'recent']);
  });

  // `hadAnotherCoParent` and the server-side familyId stamping read an accepted invitation as the
  // evidence of a past relationship; sweeping it would make an old household look like the new.
  it('never deletes an accepted invitation, however old', async () => {
    const db = fakeDb({invitations: {
      'accepted': invitation('accepted', NOW - 3650 * DAY, NOW - 3660 * DAY),
      'accepted-undated': invitation('accepted', null, NOW - 3660 * DAY),
    }});

    const swept = await index.sweepUnacceptedInvitationsImpl(db, NOW);

    assert.deepStrictEqual(swept, {expired: 0, undated: 0});
    assert.deepStrictEqual(Object.keys(db._store.invitations).sort(),
        ['accepted', 'accepted-undated']);
  });

  it('deletes one with no expiry once it is more than ninety days old, and not before',
      async () => {
        const db = fakeDb({invitations: {
          'old-undated': invitation('pending', undefined, NOW - 90 * DAY - 1),
          'exact-undated': invitation('pending', undefined, NOW - 90 * DAY),
          'young-undated': invitation('pending', undefined, NOW - DAY),
          'text-expiry': invitation('cancelled', '2026-01-01', NOW - 200 * DAY),
        }});

        const swept = await index.sweepUnacceptedInvitationsImpl(db, NOW);

        assert.deepStrictEqual(swept, {expired: 0, undated: 2});
        assert.deepStrictEqual(Object.keys(db._store.invitations).sort(),
            ['exact-undated', 'young-undated']);
      });

  it('leaves an old invitation whose expiry is still running to the expiry rule', async () => {
    // Created long ago, but its code works for another day: the age rule is only for invitations
    // with no expiry at all.
    const db = fakeDb({invitations: {'long': invitation('pending', NOW + DAY, NOW - 400 * DAY)}});

    const swept = await index.sweepUnacceptedInvitationsImpl(db, NOW);

    assert.deepStrictEqual(swept, {expired: 0, undated: 0});
  });
});
