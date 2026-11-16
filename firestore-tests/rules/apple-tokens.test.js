/**
 * MON-24 phase 4, part A — `apple_tokens/{uid}`, Apple's refresh token kept by the server.
 *
 * Closed to every client, the owner included: a refresh token read from here is a credential,
 * and one written here could make the server revoke somebody else's authorization. Only the
 * callables, on Admin credentials, read and write it (`functions/index.js`).
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-apple-tokens';
const ALICE = 'alice-uid';
const MALLORY = 'mallory-uid';
const TOKEN_DOC = {refreshToken: 'rt-alice', clientId: 'app.coplanly', storedAtMillis: 1794787200000};

describe('apple_tokens: Apple\'s refresh token is the server\'s alone', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice'},
      [`apple_tokens/${ALICE}`]: TOKEN_DOC,
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  it('the rules suite can see the seeded token (so the refusals below mean something)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await assertSucceeds(ctx.firestore().doc(`apple_tokens/${ALICE}`).get());
    });
  });

  for (const [who, uid] of [['the owner', ALICE], ['a stranger', MALLORY]]) {
    it(`refuses ${who} reading the token`, async () => {
      await assertFails(as(uid).doc(`apple_tokens/${ALICE}`).get());
    });

    it(`refuses ${who} listing the collection`, async () => {
      await assertFails(as(uid).collection('apple_tokens').get());
    });

    it(`refuses ${who} writing the token`, async () => {
      await assertFails(as(uid).doc(`apple_tokens/${ALICE}`).set(TOKEN_DOC));
    });

    it(`refuses ${who} deleting the token`, async () => {
      await assertFails(as(uid).doc(`apple_tokens/${ALICE}`).delete());
    });
  }

  it('refuses the owner creating a document of their own', async () => {
    await env.clearFirestore();
    await assertFails(as(ALICE).doc(`apple_tokens/${ALICE}`).set(TOKEN_DOC));
  });

  it('refuses a reader who is not signed in', async () => {
    await assertFails(env.unauthenticatedContext().firestore().doc(`apple_tokens/${ALICE}`).get());
  });
});
