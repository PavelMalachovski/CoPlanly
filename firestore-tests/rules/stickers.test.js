/**
 * Stickers between parents (October 2026, owner decision; docs/DESIGN-stickers-and-subscription.md).
 *
 * `stickers/{id}` is a small badge one parent receives: from the co-parent, in one of a fixed set
 * of phrases, or from the app, for answering requests on time. These cases pin what the rules
 * hold:
 *
 * - **Only its receiver and its sender read it.** Not a third adult, not another family.
 * - **A parent's sticker goes to the other parent of a live family**, never to a stranger, never
 *   in the other parent's name, never with free text.
 * - **The app's sticker is the caller's own, once a week per kind**, under an id spelling both.
 * - **Nothing changes after the create** but the receiver's one `seenAt`, and nobody deletes.
 * - **`users/{uid}.parentRole` is one of three words.**
 */

const {serverTimestamp} = require('firebase/firestore');
const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-stickers';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';

/** Alice and Bob's family, as `FamilyKey.of` spells it. */
const FAMILY = [ALICE, BOB].sort().join('__');

/** Bob and Carol's family, which Alice is not in. */
const OTHER_FAMILY = [BOB, CAROL].sort().join('__');

const USERS = {
  'users/alice-uid': {name: 'Alice', email: 'a@x.test', partnerId: BOB, partnerIds: [BOB]},
  'users/bob-uid': {name: 'Bob', email: 'b@x.test', partnerId: ALICE, partnerIds: [ALICE, CAROL]},
  'users/carol-uid': {name: 'Carol', email: 'c@x.test', partnerId: BOB, partnerIds: [BOB]},
  [`families/${FAMILY}`]: {members: [ALICE, BOB].sort()},
  [`families/${OTHER_FAMILY}`]: {members: [BOB, CAROL].sort()},
};

/**
 * Alice's sticker to Bob as the client writes it.
 *
 * @param {!Object} over Fields to override.
 * @return {!Object} The document data.
 */
function fromAlice(over) {
  return Object.assign({
    to: BOB,
    from: ALICE,
    participants: [ALICE, BOB],
    familyId: FAMILY,
    kind: 'great_parent',
    createdAt: serverTimestamp(),
  }, over);
}

/**
 * The app's sticker to Alice as her phone writes it.
 *
 * @param {!Object} over Fields to override.
 * @return {!Object} The document data.
 */
function fromApp(over) {
  return Object.assign({
    to: ALICE,
    from: 'app',
    participants: [ALICE],
    familyId: FAMILY,
    kind: 'on_time',
    createdAt: serverTimestamp(),
  }, over);
}

const APP_ID = `app_${ALICE}_on_time_2026-W43`;

describe('stickers', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, USERS);
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const sticker = (uid, id) => as(uid).doc(`stickers/${id}`);

  it('lets a parent send the other parent of a live family a sticker from the set', async () => {
    await assertSucceeds(sticker(ALICE, 's1').set(fromAlice({})));
  });

  it('refuses a sticker to somebody outside the family, or in a family that ended', async () => {
    await assertFails(sticker(ALICE, 's1').set(fromAlice({to: CAROL, participants: [ALICE, CAROL]})));
    await assertFails(sticker(ALICE, 's2').set(fromAlice({familyId: OTHER_FAMILY})));
    await env.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`families/${FAMILY}`).delete());
    await assertFails(sticker(ALICE, 's3').set(fromAlice({})));
  });

  it('refuses a sticker in the other parent\'s name, to oneself, with free text or a client time', async () => {
    await assertFails(sticker(ALICE, 's1').set(fromAlice({from: BOB})));
    await assertFails(sticker(ALICE, 's2').set(fromAlice({to: ALICE, participants: [ALICE]})));
    await assertFails(sticker(ALICE, 's3').set(fromAlice({kind: 'you are useless'})));
    await assertFails(sticker(ALICE, 's4').set(fromAlice({note: 'thanks!'})));
    await assertFails(sticker(ALICE, 's5').set(fromAlice({createdAt: new Date()})));
    await assertFails(sticker(ALICE, 's6').set(fromAlice({participants: [ALICE, CAROL]})));
  });

  it('lets a phone award its own parent the app\'s sticker once a week, and nobody else', async () => {
    await assertSucceeds(sticker(ALICE, APP_ID).set(fromApp({})));
    // A second create of the same week is an update the rules refuse.
    await assertFails(sticker(ALICE, APP_ID).set(fromApp({})));
    await assertFails(sticker(ALICE, `app_${ALICE}_on_time_week43`).set(fromApp({})));
    await assertFails(sticker(ALICE, `app_${BOB}_on_time_2026-W43`).set(fromApp({to: BOB, participants: [BOB]})));
    await assertFails(sticker(ALICE, `app_${ALICE}_great_parent_2026-W43`).set(fromApp({kind: 'great_parent'})));
    await assertFails(sticker(ALICE, `app_${ALICE}_regular_2026-W43`).set(fromApp({kind: 'regular', familyId: OTHER_FAMILY})));
    await assertSucceeds(sticker(ALICE, `app_${ALICE}_regular_2026-W43`).set(fromApp({kind: 'regular', familyId: ''})));
  });

  it('shows a sticker to its receiver and its sender, and to nobody else', async () => {
    await seed(env, {'stickers/s1': fromAlice({createdAt: new Date()})});
    await assertSucceeds(sticker(BOB, 's1').get());
    await assertSucceeds(sticker(ALICE, 's1').get());
    await assertFails(sticker(CAROL, 's1').get());
    await assertSucceeds(as(BOB).collection('stickers').where('to', '==', BOB).get());
    await assertSucceeds(as(ALICE).collection('stickers').where('from', '==', ALICE).get());
    await assertFails(as(CAROL).collection('stickers').get());
  });

  it('lets the receiver mark it seen once, with the server\'s clock, and changes nothing else', async () => {
    await seed(env, {'stickers/s1': fromAlice({createdAt: new Date()})});
    await assertFails(sticker(ALICE, 's1').update({seenAt: serverTimestamp()}));
    await assertFails(sticker(BOB, 's1').update({seenAt: new Date()}));
    await assertFails(sticker(BOB, 's1').update({seenAt: serverTimestamp(), kind: 'thank_you'}));
    await assertSucceeds(sticker(BOB, 's1').update({seenAt: serverTimestamp()}));
    await assertFails(sticker(BOB, 's1').update({seenAt: serverTimestamp()}));
    await assertFails(sticker(BOB, 's1').delete());
    await assertFails(sticker(ALICE, 's1').delete());
  });

  it('keeps a parent\'s role to one of three words', async () => {
    const me = as(ALICE).doc(`users/${ALICE}`);
    await assertSucceeds(me.set({parentRole: 'mom'}, {merge: true}));
    await assertSucceeds(me.set({parentRole: 'parent'}, {merge: true}));
    await assertFails(me.set({parentRole: 'boss'}, {merge: true}));
    await assertFails(me.set({parentRole: 1}, {merge: true}));
    await assertFails(as(BOB).doc(`users/${ALICE}`).set({parentRole: 'dad'}, {merge: true}));
  });
});
