/**
 * MON-12 — AI assist: the consent a parent records, and the quota nobody but the server touches.
 *
 * `users/{uid}.aiConsent` is `{version, grantedAt}` on the parent's own profile; `aiAssist`
 * (`functions/ai-assist.js`) reads it as admin and refuses every call without it. The rule keeps it
 * to exactly that shape, lets only the owner give or withdraw it, and never refuses an ordinary
 * profile save because of a consent already stored. `ai_usage/{uid}`, the daily count, is closed
 * to every client — a parent who could write it would reset their own quota.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const firebase = require('firebase/compat/app').default;
require('firebase/compat/firestore');

const PROJECT = 'demo-coplanly-ai-assist';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const GRANTED_AT = 1790000000000;

/**
 * @return {!Object} The compat delete sentinel, what `FieldValue.delete()` sends from the app.
 */
function deleteField() {
  return firebase.firestore.FieldValue.delete();
}

/**
 * @return {!Object} The compat server-timestamp sentinel.
 */
function serverTimestamp() {
  return firebase.firestore.FieldValue.serverTimestamp();
}

/**
 * Merges [patch] into Alice's profile as [uid].
 *
 * @param {!Object} env Rules test environment.
 * @param {!Object} patch Keys to merge.
 * @param {string=} uid Who writes; Alice by default.
 * @return {!Promise} The pending write.
 */
function mergeProfile(env, patch, uid) {
  return env.authenticatedContext(uid || ALICE).firestore()
      .doc(`users/${ALICE}`).set(patch, {merge: true});
}

/**
 * Reads a document back with the rules bypassed.
 *
 * @param {!Object} env Rules test environment.
 * @param {string} refPath Document path.
 * @return {!Promise<!Object>} The stored data.
 */
async function readRaw(env, refPath) {
  let data;
  await env.withSecurityRulesDisabled(async (ctx) => {
    data = (await ctx.firestore().doc(refPath).get()).data();
  });
  return data;
}

describe('users profile: the AI assist consent', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice Novak', email: 'alice@example.com', partnerId: BOB, partnerIds: [BOB]},
      [`users/${BOB}`]: {name: 'Bob Novak', partnerId: ALICE, partnerIds: [ALICE]},
    });
  });

  it('lets the owner record their consent with epoch millis', async () => {
    await assertSucceeds(mergeProfile(env, {aiConsent: {version: 1, grantedAt: GRANTED_AT}}));
  });

  it('lets the owner record it with the server\'s timestamp', async () => {
    await assertSucceeds(mergeProfile(env, {aiConsent: {version: 1, grantedAt: serverTimestamp()}}));
  });

  it('accepts a later version', async () => {
    await assertSucceeds(mergeProfile(env, {aiConsent: {version: 3, grantedAt: GRANTED_AT}}));
  });

  it('lets the owner withdraw it by deleting the field', async () => {
    await mergeProfile(env, {aiConsent: {version: 1, grantedAt: GRANTED_AT}});

    await assertSucceeds(mergeProfile(env, {aiConsent: deleteField()}));
    const stored = await readRaw(env, `users/${ALICE}`);
    if ('aiConsent' in stored) throw new Error('the consent survived its withdrawal');
  });

  it('still allows an ordinary profile save after a server-timestamped consent', async () => {
    await mergeProfile(env, {aiConsent: {version: 1, grantedAt: serverTimestamp()}});

    await assertSucceeds(mergeProfile(env, {name: 'Alice N.', fcmToken: 'token-2'}));
  });

  it('accepts the consent on a create too', async () => {
    await env.clearFirestore();

    await assertSucceeds(mergeProfile(env, {
      id: ALICE, firebaseUid: ALICE, name: 'Alice Novak', email: 'alice@example.com',
      aiConsent: {version: 1, grantedAt: GRANTED_AT},
    }));
  });

  it('refuses a consent with an extra key', async () => {
    await assertFails(mergeProfile(env, {
      aiConsent: {version: 1, grantedAt: GRANTED_AT, scope: 'everything'},
    }));
  });

  it('refuses a consent missing a key', async () => {
    await assertFails(mergeProfile(env, {aiConsent: {version: 1}}));
    await assertFails(mergeProfile(env, {aiConsent: {grantedAt: GRANTED_AT}}));
  });

  it('refuses a version that is not a positive integer', async () => {
    await assertFails(mergeProfile(env, {aiConsent: {version: 0, grantedAt: GRANTED_AT}}));
    await assertFails(mergeProfile(env, {aiConsent: {version: '1', grantedAt: GRANTED_AT}}));
    await assertFails(mergeProfile(env, {aiConsent: {version: 1.5, grantedAt: GRANTED_AT}}));
  });

  it('refuses a grant time that is neither millis nor this write\'s server time', async () => {
    await assertFails(mergeProfile(env, {aiConsent: {version: 1, grantedAt: 0}}));
    await assertFails(mergeProfile(env, {aiConsent: {version: 1, grantedAt: '2026-09-25'}}));
    await assertFails(mergeProfile(env, {
      aiConsent: {version: 1, grantedAt: firebase.firestore.Timestamp.fromMillis(GRANTED_AT)},
    }));
  });

  it('refuses a consent that is not a map', async () => {
    await assertFails(mergeProfile(env, {aiConsent: true}));
  });

  it('does not let the co-parent give or withdraw it', async () => {
    await assertFails(mergeProfile(env, {aiConsent: {version: 1, grantedAt: GRANTED_AT}}, BOB));
    await seed(env, {[`users/${ALICE}`]: {
      name: 'Alice Novak', email: 'alice@example.com', partnerId: BOB, partnerIds: [BOB],
      aiConsent: {version: 1, grantedAt: GRANTED_AT},
    }});
    await assertFails(mergeProfile(env, {aiConsent: deleteField()}, BOB));
  });
});

describe('ai_usage: the daily quota is the server\'s alone', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`users/${ALICE}`]: {name: 'Alice', partnerId: BOB, partnerIds: [BOB]},
      [`users/${BOB}`]: {name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
      [`ai_usage/${ALICE}`]: {day: '2026-09-25', count: 30, updatedAtMillis: GRANTED_AT},
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  it('the rules suite can see the seeded count (so the refusals below mean something)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await assertSucceeds(ctx.firestore().doc(`ai_usage/${ALICE}`).get());
    });
  });

  it('refuses the owner reading their own count', async () => {
    await assertFails(as(ALICE).doc(`ai_usage/${ALICE}`).get());
  });

  it('refuses the owner resetting, deleting or creating it', async () => {
    await assertFails(as(ALICE).doc(`ai_usage/${ALICE}`).set({day: '2026-09-25', count: 0}));
    await assertFails(as(ALICE).doc(`ai_usage/${ALICE}`).update({count: 0}));
    await assertFails(as(ALICE).doc(`ai_usage/${ALICE}`).delete());
    await assertFails(as(BOB).doc(`ai_usage/${BOB}`).set({day: '2026-09-25', count: 0}));
  });

  it('refuses the co-parent and a stranger', async () => {
    await assertFails(as(BOB).doc(`ai_usage/${ALICE}`).get());
    await assertFails(as('stranger-uid').collection('ai_usage').get());
    await assertFails(env.unauthenticatedContext().firestore().doc(`ai_usage/${ALICE}`).get());
  });
});
