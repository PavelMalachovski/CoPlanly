const assert = require('assert');
const {Timestamp} = require('firebase-admin/firestore');
const receipts = require('../export-receipts');

/**
 * MON-16 — export receipts.
 *
 * What is pinned is what a court would lean on: an id is minted before the file exists and says
 * nothing by itself; a hash is registered once, by the parent who reserved the id, at the
 * server's time; a verifier with no account learns whether a file was registered and when, and
 * nothing about who; and erasing an account scrubs the receipts rather than un-verifying a file
 * that may already be evidence.
 */

const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FAMILY = [ALICE, BOB].sort().join('__');
const SHA = 'a'.repeat(64);
const OTHER_SHA = 'b'.repeat(64);

/**
 * A Firestore-shaped fake: documents by collection, equality queries with `limit`, `create`
 * that refuses an existing id, and a transaction that applies its update on return.
 *
 * @return {!Object} The fake, exposing `_store`.
 */
function fakeDb() {
  const store = {};
  const docs = (name) => (store[name] = store[name] || {});
  const snapOf = (name, id) => {
    const data = docs(name)[id];
    return {id, exists: data !== undefined, data: () => data};
  };
  const docRef = (name, id) => ({
    _name: name,
    _id: id,
    async get() {
      return snapOf(name, id);
    },
    async create(data) {
      if (docs(name)[id] !== undefined) {
        const err = new Error('exists');
        err.code = 6;
        throw err;
      }
      docs(name)[id] = Object.assign({}, data);
    },
    async update(update) {
      Object.assign(docs(name)[id], update);
    },
    async delete() {
      delete docs(name)[id];
    },
  });
  const query = (name, predicates, max) => ({
    where(field, op, value) {
      assert.strictEqual(op, '==');
      return query(name, predicates.concat([[field, value]]), max);
    },
    limit(n) {
      return query(name, predicates, n);
    },
    async get() {
      const found = Object.keys(docs(name))
          .filter((id) => predicates.every(([f, v]) => docs(name)[id][f] === v))
          .slice(0, max || Infinity)
          .map((id) => snapOf(name, id));
      return {docs: found, size: found.length};
    },
  });
  return {
    _store: store,
    collection(name) {
      return Object.assign(query(name, [], 0), {doc: (id) => docRef(name, id)});
    },
    async runTransaction(fn) {
      const updates = [];
      const result = await fn({
        get: (ref) => ref.get(),
        update: (ref, data) => updates.push([ref, data]),
      });
      for (const [ref, data] of updates) await ref.update(data);
      return result;
    },
  };
}

/**
 * A server clock the test moves by hand.
 *
 * @param {number} startMillis Where it starts.
 * @return {{now: function(): !Timestamp, advance: function(number): void}} The clock.
 */
function clock(startMillis) {
  let millis = startMillis;
  return {
    now: () => Timestamp.fromMillis(millis),
    advance: (ms) => {
      millis += ms;
    },
  };
}

const RANGE = {familyId: FAMILY, fromDate: '2026-06-01', toDate: '2026-08-31', format: 'pdf'};

/**
 * Reserves and registers one receipt for Alice.
 *
 * @param {!Object} db The fake.
 * @param {!Object} deps The clock.
 * @return {Promise<string>} The record id.
 */
async function registered(db, deps) {
  const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
  await receipts.registerImpl(db, ALICE, {recordId, sha256: SHA, byteLength: 4096}, deps);
  return recordId;
}

describe('export receipts (MON-16)', () => {
  describe('record ids', () => {
    it('are 16 characters of Crockford base 32, with no I, L, O or U', () => {
      for (let i = 0; i < 200; i++) {
        assert.match(receipts.newRecordId(), /^[0-9ABCDEFGHJKMNPQRSTVWXYZ]{16}$/);
      }
    });

    it('encode 80 bits exactly', () => {
      assert.strictEqual(receipts.crockford(Buffer.alloc(10, 0)), '0000000000000000');
      assert.strictEqual(receipts.crockford(Buffer.alloc(10, 0xff)), 'ZZZZZZZZZZZZZZZZ');
    });

    it('read back however a person retyped them', () => {
      assert.strictEqual(receipts.normalizeRecordId('7k3q-0abc-defg-hjkm'), '7K3Q0ABCDEFGHJKM');
      assert.strictEqual(receipts.normalizeRecordId('7K3Q OABC DEFG HJKM'), '7K3Q0ABCDEFGHJKM');
      assert.strictEqual(receipts.normalizeRecordId('ILIL-ILIL-ILIL-ILIL'), '1111111111111111');
    });

    it('refuse anything else', () => {
      assert.strictEqual(receipts.normalizeRecordId('UUUU-UUUU-UUUU-UUUU'), '');
      assert.strictEqual(receipts.normalizeRecordId('short'), '');
      assert.strictEqual(receipts.normalizeRecordId(42), '');
    });
  });

  describe('reserve', () => {
    it('mints an id bound to the caller, the family, the range and the format', async () => {
      const db = fakeDb();
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, clock(1000));
      const stored = db._store.export_receipts[recordId];
      assert.strictEqual(stored.state, 'reserved');
      assert.strictEqual(stored.generatorUid, ALICE);
      assert.strictEqual(stored.familyId, FAMILY);
      assert.strictEqual(stored.fromDate, '2026-06-01');
      assert.strictEqual(stored.format, 'pdf');
      assert.strictEqual(stored.sha256, undefined);
      assert.ok(!recordId.includes(ALICE) && !recordId.includes('__'));
    });

    it('accepts a parent who has since unpaired: the id names them, no live pairing needed', async () => {
      const db = fakeDb();
      await receipts.reserveImpl(db, BOB, RANGE, clock(1000));
    });

    it('accepts an account with no co-parent, under a blank family', async () => {
      const db = fakeDb();
      const {recordId} = await receipts.reserveImpl(
          db, CAROL, Object.assign({}, RANGE, {familyId: ''}), clock(1000));
      assert.strictEqual(db._store.export_receipts[recordId].familyId, '');
    });

    it('refuses a family the caller is not in', async () => {
      await assert.rejects(
          receipts.reserveImpl(fakeDb(), CAROL, RANGE, clock(1000)),
          (err) => err.reason === 'not-a-parent');
    });

    it('refuses a range turned inside out, or a date that does not exist', async () => {
      for (const bad of [
        {fromDate: '2026-09-01', toDate: '2026-08-01'},
        {fromDate: '2026-02-30'},
        {toDate: 'yesterday'},
      ]) {
        await assert.rejects(
            receipts.reserveImpl(fakeDb(), ALICE, Object.assign({}, RANGE, bad), clock(1000)),
            (err) => err.reason === 'bad-range');
      }
    });

    it('refuses a format the app does not make', async () => {
      await assert.rejects(
          receipts.reserveImpl(fakeDb(), ALICE, Object.assign({}, RANGE, {format: 'docx'}), clock(1)),
          (err) => err.reason === 'bad-format');
    });

    it('draws again on a collision rather than overwriting a receipt', async () => {
      const db = fakeDb();
      const draws = [Buffer.alloc(10, 1), Buffer.alloc(10, 1), Buffer.alloc(10, 2)];
      const deps = Object.assign(clock(1), {random: () => draws.shift()});
      const first = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      const second = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      assert.notStrictEqual(first.recordId, second.recordId);
    });
  });

  describe('register', () => {
    it('records the hash at the server\'s time, and returns that time', async () => {
      const db = fakeDb();
      const deps = clock(1000000);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      deps.advance(5000);
      const result = await receipts.registerImpl(
          db, ALICE, {recordId, sha256: SHA, byteLength: 4096}, deps);
      assert.deepStrictEqual(result, {recordId, recordedAtMillis: 1005000});
      const stored = db._store.export_receipts[recordId];
      assert.strictEqual(stored.state, 'registered');
      assert.strictEqual(stored.sha256, SHA);
      assert.strictEqual(stored.byteLength, 4096);
      assert.strictEqual(stored.recordedAt.toMillis(), 1005000);
    });

    it('accepts the id as it is printed, with separators', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      const printed = recordId.match(/.{4}/g).join('-');
      await receipts.registerImpl(db, ALICE, {recordId: printed, sha256: SHA, byteLength: 1}, deps);
    });

    it('is create-once: another hash under a registered id is refused', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const recordId = await registered(db, deps);
      await assert.rejects(
          receipts.registerImpl(db, ALICE, {recordId, sha256: OTHER_SHA, byteLength: 4096}, deps),
          (err) => err.reason === 'already-registered');
      assert.strictEqual(db._store.export_receipts[recordId].sha256, SHA);
    });

    it('answers a retry of the same hash with the original time, not a new one', async () => {
      const db = fakeDb();
      const deps = clock(1000);
      const recordId = await registered(db, deps);
      deps.advance(60000);
      const again = await receipts.registerImpl(
          db, ALICE, {recordId, sha256: SHA, byteLength: 4096}, deps);
      assert.strictEqual(again.recordedAtMillis, 1000);
    });

    it('refuses the co-parent registering against an id they did not reserve — as "not found"', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      await assert.rejects(
          receipts.registerImpl(db, BOB, {recordId, sha256: SHA, byteLength: 1}, deps),
          (err) => err.code === 'not-found' && err.reason === 'no-reservation');
    });

    it('refuses an id nobody reserved', async () => {
      await assert.rejects(
          receipts.registerImpl(fakeDb(), ALICE,
              {recordId: '0000000000000000', sha256: SHA, byteLength: 1}, clock(1)),
          (err) => err.reason === 'no-reservation');
    });

    it('refuses a reservation older than its time to live', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      deps.advance(receipts.RESERVATION_TTL_MS + 1);
      await assert.rejects(
          receipts.registerImpl(db, ALICE, {recordId, sha256: SHA, byteLength: 1}, deps),
          (err) => err.reason === 'reservation-expired');
    });

    it('refuses a hash that is not 64 lowercase hex characters', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      for (const sha256 of ['A'.repeat(64), 'a'.repeat(63), 'g'.repeat(64), 42]) {
        await assert.rejects(
            receipts.registerImpl(db, ALICE, {recordId, sha256, byteLength: 1}, deps),
            (err) => err.reason === 'bad-sha256');
      }
    });

    it('refuses a byte length that is not a positive integer', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, deps);
      for (const byteLength of [0, -1, 1.5, '100']) {
        await assert.rejects(
            receipts.registerImpl(db, ALICE, {recordId, sha256: SHA, byteLength}, deps),
            (err) => err.reason === 'bad-length');
      }
    });
  });

  describe('verify — for somebody with no account', () => {
    it('finds a registered file by its hash, in either case', async () => {
      const db = fakeDb();
      const recordId = await registered(db, clock(1790000000000));
      for (const sha256 of [SHA, SHA.toUpperCase()]) {
        const answer = await receipts.verifyImpl(db, {sha256});
        assert.strictEqual(answer.found, true);
        assert.strictEqual(answer.recordId, recordId);
        assert.strictEqual(answer.recordedAtMillis, 1790000000000);
        assert.strictEqual(answer.recordedAt, new Date(1790000000000).toISOString());
      }
    });

    it('answers with the receipt and nothing that identifies anybody', async () => {
      const db = fakeDb();
      await registered(db, clock(1));
      const answer = await receipts.verifyImpl(db, {sha256: SHA});
      assert.deepStrictEqual(Object.keys(answer).sort(), [
        'byteLength', 'format', 'found', 'fromDate', 'generatedBy', 'recordId', 'recordedAt',
        'recordedAtMillis', 'toDate',
      ]);
      assert.strictEqual(answer.generatedBy, receipts.GENERATED_BY);
      const text = JSON.stringify(answer);
      for (const secret of [ALICE, BOB, FAMILY, 'Alice']) {
        assert.ok(!text.includes(secret), `${secret} leaked`);
      }
    });

    it('does not find a file one byte away (a different hash)', async () => {
      const db = fakeDb();
      await registered(db, clock(1));
      assert.deepStrictEqual(await receipts.verifyImpl(db, {sha256: OTHER_SHA}), {found: false});
    });

    it('finds a registered receipt by its printed record id', async () => {
      const db = fakeDb();
      const recordId = await registered(db, clock(1));
      const answer = await receipts.verifyImpl(db, {recordId: recordId.match(/.{4}/g).join('-')});
      assert.strictEqual(answer.found, true);
      assert.strictEqual(answer.fromDate, '2026-06-01');
      assert.strictEqual(answer.toDate, '2026-08-31');
      assert.strictEqual(answer.format, 'pdf');
    });

    it('treats a reservation that never received a hash as not found', async () => {
      const db = fakeDb();
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, clock(1));
      assert.deepStrictEqual(await receipts.verifyImpl(db, {recordId}), {found: false});
    });

    it('refuses a malformed question rather than scanning', async () => {
      await assert.rejects(receipts.verifyImpl(fakeDb(), {sha256: 'nope'}),
          (err) => err.reason === 'bad-sha256');
      await assert.rejects(receipts.verifyImpl(fakeDb(), {}),
          (err) => err.reason === 'bad-record-id');
    });
  });

  describe('account deletion', () => {
    it('scrubs the departing parent from their registered receipts, which still verify', async () => {
      const db = fakeDb();
      const recordId = await registered(db, clock(1));
      const result = await receipts.scrubReceipts(db, ALICE, []);
      const stored = db._store.export_receipts[recordId];
      assert.strictEqual(stored.generatorUid, '');
      assert.strictEqual(stored.familyId, '');
      assert.strictEqual(stored.sha256, SHA);
      assert.strictEqual((await receipts.verifyImpl(db, {sha256: SHA})).found, true);
      assert.strictEqual(result.scrubbed, 1);
    });

    it('deletes reservations that never received a hash', async () => {
      const db = fakeDb();
      const {recordId} = await receipts.reserveImpl(db, ALICE, RANGE, clock(1));
      const result = await receipts.scrubReceipts(db, ALICE, []);
      assert.strictEqual(db._store.export_receipts[recordId], undefined);
      assert.strictEqual(result.deleted, 1);
    });

    it('blanks the family on the co-parent\'s receipts, and leaves their own uid alone', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const {recordId} = await receipts.reserveImpl(db, BOB, RANGE, deps);
      await receipts.registerImpl(db, BOB, {recordId, sha256: OTHER_SHA, byteLength: 9}, deps);
      await receipts.scrubReceipts(db, ALICE, [FAMILY]);
      const stored = db._store.export_receipts[recordId];
      assert.strictEqual(stored.familyId, '');
      assert.strictEqual(stored.generatorUid, BOB);
    });

    it('finds the co-parent\'s receipts through the family on the departing parent\'s own', async () => {
      const db = fakeDb();
      const deps = clock(1);
      await registered(db, deps);
      const {recordId} = await receipts.reserveImpl(db, BOB, RANGE, deps);
      await receipts.registerImpl(db, BOB, {recordId, sha256: OTHER_SHA, byteLength: 9}, deps);
      await receipts.scrubReceipts(db, ALICE, []);
      assert.strictEqual(db._store.export_receipts[recordId].familyId, '');
    });

    it('ignores a candidate family that does not name the departing parent', async () => {
      const db = fakeDb();
      const deps = clock(1);
      const other = [BOB, CAROL].sort().join('__');
      const {recordId} = await receipts.reserveImpl(
          db, BOB, Object.assign({}, RANGE, {familyId: other}), deps);
      await receipts.registerImpl(db, BOB, {recordId, sha256: OTHER_SHA, byteLength: 9}, deps);
      await receipts.scrubReceipts(db, ALICE, [other, 'legacy-random-conversation-id']);
      assert.strictEqual(db._store.export_receipts[recordId].familyId, other);
    });
  });

  describe('rate limit', () => {
    it('allows the limit per window, then refuses, then forgets', () => {
      const limiter = receipts.rateLimiter(3, 1000);
      assert.deepStrictEqual([1, 2, 3, 4].map(() => limiter.allow('ip', 0)), [true, true, true, false]);
      assert.strictEqual(limiter.allow('other-ip', 0), true);
      assert.strictEqual(limiter.allow('ip', 1000), true);
    });

    it('keys on the request address, and never on nothing', () => {
      assert.strictEqual(receipts.clientKey({rawRequest: {ip: '203.0.113.9', headers: {}}}), '203.0.113.9');
      assert.strictEqual(
          receipts.clientKey({rawRequest: {headers: {'x-forwarded-for': '198.51.100.1, 10.0.0.1'}}}),
          '198.51.100.1');
      assert.strictEqual(receipts.clientKey({}), 'unknown');
    });
  });
});
