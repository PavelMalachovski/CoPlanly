'use strict';

/**
 * The bound on wrong invitation codes (`redeem-guard.js`): every redeem callable counts a miss
 * per account on the server, and refuses an account that missed too often before reading any
 * invitation.
 */

const assert = require('assert');
const guard = require('../redeem-guard');

/** Stands in for `functions.https.HttpsError`. */
class FakeHttpsError extends Error {
  /**
   * @param {string} code The error code.
   * @param {string} message The message.
   * @param {Object=} details The details.
   */
  constructor(code, message, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}
const https = {HttpsError: FakeHttpsError};

/**
 * A Firestore holding `redeem_attempts` only, with transactions.
 *
 * @return {!Object} The fake.
 */
function fakeDb() {
  const store = {};
  const ref = (id) => ({
    async get() {
      return {exists: id in store, data: () => store[id]};
    },
    async set(data) {
      store[id] = Object.assign({}, data);
    },
  });
  return {
    _store: store,
    collection(name) {
      assert.strictEqual(name, guard.REDEEM_ATTEMPTS_COLLECTION);
      return {doc: ref};
    },
    async runTransaction(fn) {
      const writes = [];
      const out = await fn({get: (r) => r.get(), set: (r, data) => writes.push([r, data])});
      for (const [r, data] of writes) await r.set(data);
      return out;
    },
  };
}

const NOW = 1800000000000;
const miss = () => Promise.reject(new FakeHttpsError('not-found', 'Invitation not found', {reason: 'not-found'}));

describe('redeem-guard: wrong invitation codes are counted per account', () => {
  it('passes a redeem through and counts nothing when it succeeds', async () => {
    const db = fakeDb();
    const out = await guard.guardRedeem(db, 'alice', async () => ({ok: true}), https, NOW);
    assert.deepStrictEqual(out, {ok: true});
    assert.strictEqual(db._store.alice, undefined);
  });

  it('counts a miss and rethrows the redeem\'s own error', async () => {
    const db = fakeDb();
    await assert.rejects(guard.guardRedeem(db, 'alice', miss, https, NOW), (e) => e.code === 'not-found');
    assert.deepStrictEqual(db._store.alice, {windowStartMillis: NOW, failures: 1});
  });

  it('does not count a code that exists but is refused for another reason', async () => {
    const db = fakeDb();
    const expired = () => Promise.reject(
        new FakeHttpsError('failed-precondition', 'Expired', {reason: 'invitation-expired'}));
    const wrongKind = () => Promise.reject(
        new FakeHttpsError('failed-precondition', 'Guest code', {reason: 'guest-invitation'}));
    await assert.rejects(guard.guardRedeem(db, 'alice', expired, https, NOW));
    await assert.rejects(guard.guardRedeem(db, 'alice', wrongKind, https, NOW));
    assert.strictEqual(db._store.alice, undefined);
  });

  it('refuses the account once it reached the limit, without running the redeem', async () => {
    const db = fakeDb();
    for (let i = 0; i < guard.REDEEM_FAILURE_LIMIT; i++) {
      await assert.rejects(guard.guardRedeem(db, 'alice', miss, https, NOW + i));
    }
    let ran = false;
    await assert.rejects(
        guard.guardRedeem(db, 'alice', async () => {
          ran = true;
        }, https, NOW + 100),
        (e) => e.code === 'resource-exhausted' && e.details.reason === guard.TOO_MANY_ATTEMPTS);
    assert.strictEqual(ran, false);
  });

  it('keeps one account\'s misses from refusing another', async () => {
    const db = fakeDb();
    for (let i = 0; i < guard.REDEEM_FAILURE_LIMIT; i++) {
      await assert.rejects(guard.guardRedeem(db, 'mallory', miss, https, NOW));
    }
    const out = await guard.guardRedeem(db, 'alice', async () => 'linked', https, NOW);
    assert.strictEqual(out, 'linked');
  });

  it('opens a new window once the old one has passed', async () => {
    const db = fakeDb();
    for (let i = 0; i < guard.REDEEM_FAILURE_LIMIT; i++) {
      await assert.rejects(guard.guardRedeem(db, 'alice', miss, https, NOW));
    }
    const later = NOW + guard.REDEEM_FAILURE_WINDOW_MS;
    assert.strictEqual(await guard.guardRedeem(db, 'alice', async () => 'linked', https, later), 'linked');
    await assert.rejects(guard.guardRedeem(db, 'alice', miss, https, later));
    assert.deepStrictEqual(db._store.alice, {windowStartMillis: later, failures: 1});
  });

  it('reads a stored window from the future or of the wrong shape as none', () => {
    assert.deepStrictEqual(guard.liveWindow({windowStartMillis: NOW + 1, failures: 99}, NOW),
        {windowStartMillis: NOW, failures: 0});
    assert.deepStrictEqual(guard.liveWindow({windowStartMillis: 'x', failures: 'y'}, NOW),
        {windowStartMillis: NOW, failures: 0});
    assert.deepStrictEqual(guard.liveWindow(null, NOW), {windowStartMillis: NOW, failures: 0});
  });

  it('still answers with the redeem\'s error when counting fails', async () => {
    const db = fakeDb();
    db.runTransaction = async () => {
      throw new Error('aborted');
    };
    const quiet = console.error;
    console.error = () => {};
    try {
      await assert.rejects(guard.guardRedeem(db, 'alice', miss, https, NOW), (e) => e.code === 'not-found');
    } finally {
      console.error = quiet;
    }
  });
});
