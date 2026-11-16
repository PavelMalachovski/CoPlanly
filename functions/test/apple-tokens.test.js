const assert = require('assert');
const crypto = require('crypto');
const sinon = require('sinon');

const {fakeDb} = require('./support/account-fake-db');

/**
 * Apple's refresh token, kept by the server (MON-24 phase 4, part A).
 *
 * `exchangeAppleAuthCodeImpl` keeps it in `apple_tokens/{uid}`; `unlinkAppleImpl` revokes it,
 * deletes it and unlinks Apple from the Firebase account, never the last way in; account deletion
 * revokes and deletes it, and a revoke that fails never stops the erasure. Every log line names
 * the uid and an outcome, never a token.
 */

const ALICE = 'alice';
const BOB = 'bob';
const NOW = Date.parse('2026-11-16T10:00:00Z');
const TOKEN = 'rt-secret-alice';

const KEYS = crypto.generateKeyPairSync('ec', {namedCurve: 'P-256'});

/** @return {!Object} A complete Apple configuration signing with the test key. */
function config() {
  return {
    teamId: 'TEAM123456',
    keyId: 'KEY1234567',
    privateKey: KEYS.privateKey.export({type: 'pkcs8', format: 'pem'}),
    bundleId: 'app.coplanly',
    servicesId: 'app.coplanly.signin',
  };
}

/**
 * A `fetch` that answers Apple's two endpoints and records each request.
 *
 * @param {!{token: (number|undefined), revoke: (number|string|undefined)}=} answers The status
 *     each endpoint answers (200 by default); `'throw'` makes the revoke unreachable.
 * @return {function(string, !Object): !Promise<!Object>} The fake, carrying `calls`.
 */
function appleFetch(answers) {
  const statuses = Object.assign({token: 200, revoke: 200}, answers || {});
  const calls = [];
  const fetch = async (url, init) => {
    const form = Object.fromEntries(new URLSearchParams(init.body));
    calls.push({url, form});
    if (url.endsWith('/auth/token')) {
      return {
        status: statuses.token,
        text: async () => (statuses.token === 200 ?
          JSON.stringify({access_token: 'at', refresh_token: TOKEN, id_token: 'it'}) :
          JSON.stringify({error: 'invalid_grant'})),
      };
    }
    if (statuses.revoke === 'throw') throw new Error('timeout');
    return {
      status: statuses.revoke,
      text: async () => (statuses.revoke === 200 ? '' : JSON.stringify({error: 'server_error'})),
    };
  };
  fetch.calls = calls;
  return fetch;
}

/**
 * A fake of the Admin SDK's `auth()`, holding the user's linked providers.
 *
 * @param {!Array<string>} providers The provider ids linked to the user.
 * @return {!Object} The fake, carrying `unlinked` (each `providersToUnlink` it was given).
 */
function fakeAuth(providers) {
  const auth = {
    unlinked: [],
    async getUser(uid) {
      return {uid, providerData: providers.map((providerId) => ({providerId}))};
    },
    async updateUser(uid, update) {
      auth.unlinked.push(...(update.providersToUnlink || []));
      return {uid};
    },
  };
  return auth;
}

/** @return {!Object<string, !Array<!Object>>} An unpaired Alice, with her Apple token kept. */
function aliceWithToken() {
  return {
    users: [{id: ALICE, name: 'Alice'}],
    apple_tokens: [
      {id: ALICE, refreshToken: TOKEN, clientId: 'app.coplanly', storedAtMillis: NOW - 1000},
    ],
  };
}

/**
 * Asserts that nothing logged during [sandbox]'s life carried the token.
 *
 * @param {!Array<!Object>} spies The console spies.
 */
function assertNoTokenLogged(spies) {
  for (const spy of spies) {
    for (const call of spy.getCalls()) {
      const text = call.args.map((arg) => (arg && arg.stack) || String(arg)).join(' ');
      assert.ok(!text.includes(TOKEN), `a log line carried the token: ${text}`);
    }
  }
}

describe('apple tokens', () => {
  let myFunctions;
  let sandbox;
  let spies;

  before(() => {
    myFunctions = require('../index');
  });

  beforeEach(() => {
    sandbox = sinon.createSandbox();
    spies = ['log', 'info', 'warn', 'error'].map((level) => sandbox.stub(console, level));
  });

  afterEach(() => {
    assertNoTokenLogged(spies);
    sandbox.restore();
  });

  describe('exchangeAppleAuthCodeImpl', () => {
    it('stores the refresh token and the client id it was issued to', async () => {
      const db = fakeDb({users: [{id: ALICE}]});
      const fetch = appleFetch();

      const result = await myFunctions.exchangeAppleAuthCodeImpl(
          db, config(), fetch, ALICE, 'code-1', 'app.coplanly', NOW);

      assert.deepStrictEqual(result, {stored: true});
      assert.deepStrictEqual(db._store.apple_tokens,
          [{id: ALICE, refreshToken: TOKEN, clientId: 'app.coplanly', storedAtMillis: NOW}]);
      assert.strictEqual(fetch.calls[0].form.code, 'code-1');
    });

    it('accepts a code issued to the Services ID', async () => {
      const db = fakeDb({});
      await myFunctions.exchangeAppleAuthCodeImpl(
          db, config(), appleFetch(), ALICE, 'code-1', 'app.coplanly.signin', NOW);
      assert.strictEqual(db._store.apple_tokens[0].clientId, 'app.coplanly.signin');
    });

    it('answers apple-not-configured without the APPLE_ values, and asks Apple nothing',
        async () => {
          const db = fakeDb({});
          const fetch = appleFetch();
          await assert.rejects(
              myFunctions.exchangeAppleAuthCodeImpl(
                  db, null, fetch, ALICE, 'code-1', 'app.coplanly', NOW),
              (err) => err.code === 'failed-precondition' &&
                err.details.reason === 'apple-not-configured');
          assert.strictEqual(fetch.calls.length, 0);
          assert.strictEqual((db._store.apple_tokens || []).length, 0);
        });

    it('refuses a client id that is neither the bundle id nor the Services ID', async () => {
      const fetch = appleFetch();
      await assert.rejects(
          myFunctions.exchangeAppleAuthCodeImpl(
              fakeDb({}), config(), fetch, ALICE, 'code-1', 'com.example.other', NOW),
          (err) => err.code === 'invalid-argument');
      assert.strictEqual(fetch.calls.length, 0);
    });

    it('stores nothing when Apple refuses the code', async () => {
      const db = fakeDb({});
      await assert.rejects(
          myFunctions.exchangeAppleAuthCodeImpl(
              db, config(), appleFetch({token: 400}), ALICE, 'code-1', 'app.coplanly', NOW),
          (err) => err.code === 'failed-precondition' && err.details.reason === 'apple-refused');
      assert.strictEqual((db._store.apple_tokens || []).length, 0);
    });
  });

  describe('unlinkAppleImpl', () => {
    it('revokes the token, deletes the document and unlinks Apple', async () => {
      const db = fakeDb(aliceWithToken());
      const fetch = appleFetch();
      const auth = fakeAuth(['password', 'apple.com']);

      const result = await myFunctions.unlinkAppleImpl(db, auth, config(), fetch, ALICE);

      assert.deepStrictEqual(result, {unlinked: true});
      assert.strictEqual(fetch.calls.length, 1);
      assert.strictEqual(fetch.calls[0].url, 'https://appleid.apple.com/auth/revoke');
      assert.strictEqual(fetch.calls[0].form.token, TOKEN);
      assert.strictEqual(fetch.calls[0].form.client_id, 'app.coplanly');
      assert.deepStrictEqual(db._store.apple_tokens, []);
      assert.deepStrictEqual(auth.unlinked, ['apple.com']);
    });

    it('refuses to unlink the last sign-in method, and touches nothing', async () => {
      const db = fakeDb(aliceWithToken());
      const fetch = appleFetch();
      const auth = fakeAuth(['apple.com']);

      await assert.rejects(
          myFunctions.unlinkAppleImpl(db, auth, config(), fetch, ALICE),
          (err) => err.code === 'failed-precondition' && err.details.reason === 'last-method');

      assert.strictEqual(fetch.calls.length, 0);
      assert.strictEqual(db._store.apple_tokens.length, 1);
      assert.deepStrictEqual(auth.unlinked, []);
    });

    it('unlinks an Apple method that stored no token (one added through the web flow)',
        async () => {
          const db = fakeDb({users: [{id: ALICE}]});
          const fetch = appleFetch();
          const auth = fakeAuth(['google.com', 'apple.com']);

          const result = await myFunctions.unlinkAppleImpl(db, auth, null, fetch, ALICE);

          assert.deepStrictEqual(result, {unlinked: true});
          assert.strictEqual(fetch.calls.length, 0);
          assert.deepStrictEqual(auth.unlinked, ['apple.com']);
        });

    it('keeps everything as it was when Apple cannot be reached, so it can be tried again',
        async () => {
          const db = fakeDb(aliceWithToken());
          const auth = fakeAuth(['password', 'apple.com']);

          await assert.rejects(
              myFunctions.unlinkAppleImpl(
                  db, auth, config(), appleFetch({revoke: 'throw'}), ALICE),
              (err) => err.code === 'unavailable' && err.details.reason === 'apple-unavailable');

          assert.strictEqual(db._store.apple_tokens.length, 1);
          assert.deepStrictEqual(auth.unlinked, []);
        });

    it('refuses with apple-not-configured when a token is kept but cannot be revoked',
        async () => {
          const db = fakeDb(aliceWithToken());
          const auth = fakeAuth(['password', 'apple.com']);
          await assert.rejects(
              myFunctions.unlinkAppleImpl(db, auth, null, appleFetch(), ALICE),
              (err) => err.code === 'failed-precondition' &&
                err.details.reason === 'apple-not-configured');
          assert.strictEqual(db._store.apple_tokens.length, 1);
          assert.deepStrictEqual(auth.unlinked, []);
        });
  });

  describe('account deletion', () => {
    it('revokes the token and deletes the document', async () => {
      const db = fakeDb(aliceWithToken());
      const fetch = appleFetch();

      const removed = await myFunctions.deleteAccountDataImpl(
          db, ALICE, null, NOW, {config: config(), fetch});

      assert.strictEqual(fetch.calls.length, 1);
      assert.strictEqual(fetch.calls[0].form.token, TOKEN);
      assert.deepStrictEqual(db._store.apple_tokens, []);
      assert.strictEqual(removed.apple_tokens, 1);
    });

    for (const [failure, answer] of [['refuses', 500], ['times out', 'throw']]) {
      it(`revoke failure does not block deletion (Apple ${failure})`, async () => {
        const seed = aliceWithToken();
        seed.users.push({id: BOB, name: 'Bob'});
        seed.events = [{id: 'ev-1', createdByFirebaseUid: ALICE, sharedWith: [ALICE]}];
        seed.notification_queue = [{id: 'n-1', targetUserId: ALICE}];
        const db = fakeDb(seed);

        const removed = await myFunctions.deleteAccountDataImpl(
            db, ALICE, null, NOW, {config: config(), fetch: appleFetch({revoke: answer})});

        assert.deepStrictEqual(db._store.apple_tokens, []);
        assert.deepStrictEqual(db._store.events, []);
        assert.deepStrictEqual(db._store.notification_queue, []);
        assert.deepStrictEqual(db._store.users.map((u) => u.id), [BOB]);
        assert.strictEqual(removed.apple_tokens, 1);

        const logged = spies.flatMap((spy) => spy.getCalls())
            .map((call) => call.args.map(String).join(' '))
            .filter((line) => line.includes('Apple'));
        assert.ok(logged.some((line) => line.includes(ALICE) && line.includes('revoke-failed')),
            `expected a revoke-failed line naming the uid, got ${JSON.stringify(logged)}`);
      });
    }

    it('deletes the document even when the deployment cannot revoke', async () => {
      const db = fakeDb(aliceWithToken());
      await myFunctions.deleteAccountDataImpl(db, ALICE, null, NOW, {config: null, fetch: null});
      assert.deepStrictEqual(db._store.apple_tokens, []);
    });

    it('does nothing with Apple when there is no token document', async () => {
      const db = fakeDb({users: [{id: ALICE, name: 'Alice'}]});
      const fetch = appleFetch();

      const removed = await myFunctions.deleteAccountDataImpl(
          db, ALICE, null, NOW, {config: config(), fetch});

      assert.strictEqual(fetch.calls.length, 0);
      assert.strictEqual(removed.apple_tokens, 0);
      assert.deepStrictEqual(db._store.users, []);
    });
  });
});
