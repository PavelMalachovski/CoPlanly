/**
 * The `support_agreements` block (25 October 2026): child support agreed between the parents.
 *
 * What these cases pin:
 * - money is never set for somebody else: the first agreement is a proposal, and an agreement
 *   only ever becomes exactly the proposal the *other* parent accepted;
 * - a parent's proposal cannot be overwritten by the co-parent while it waits, only answered;
 * - either parent clears a proposal (a decline or a withdrawal), and the agreement stays;
 * - the two parents read it, nobody else, nothing in a family that ended, and nobody deletes it.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-support';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FAMILY = `${ALICE}__${BOB}`;
const PATH = `support_agreements/${FAMILY}`;

/**
 * A proposal as `SupportAgreementDocument` writes it.
 *
 * @param {string} by The proposer.
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The proposal.
 */
function proposal(by, overrides) {
  return Object.assign({
    amount: '5000.00',
    currency: 'CZK',
    dayOfMonth: 5,
    payerUid: BOB,
    fromMonth: '2026-10',
    proposedBy: by,
    proposedAtMillis: 1790000000000,
  }, overrides);
}

describe('support_agreements', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      [`families/${FAMILY}`]: {members: [ALICE, BOB]},
      'users/alice-uid': {name: 'Alice', partnerId: BOB, partnerIds: [BOB]},
      'users/bob-uid': {name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const created = (by) => ({participants: [ALICE, BOB], proposal: proposal(by), lastModifiedBy: by});

  it('lets a parent propose the first agreement, and the other accept it as it stands', async () => {
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertSucceeds(as(BOB).doc(PATH).set({
      participants: [ALICE, BOB],
      agreement: {amount: '5000.00', currency: 'CZK', dayOfMonth: 5, payerUid: BOB, fromMonth: '2026-10'},
      lastModifiedBy: BOB,
    }));
  });

  it('refuses an agreement written without a proposal, or by the proposer', async () => {
    await assertFails(as(ALICE).doc(PATH).set({
      participants: [ALICE, BOB],
      agreement: {amount: '5000.00', currency: 'CZK', dayOfMonth: 5, payerUid: BOB, fromMonth: '2026-10'},
      lastModifiedBy: ALICE,
    }));
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertFails(as(ALICE).doc(PATH).set({
      participants: [ALICE, BOB],
      agreement: {amount: '5000.00', currency: 'CZK', dayOfMonth: 5, payerUid: BOB, fromMonth: '2026-10'},
      lastModifiedBy: ALICE,
    }));
  });

  it('refuses an acceptance that changes what was proposed', async () => {
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertFails(as(BOB).doc(PATH).set({
      participants: [ALICE, BOB],
      agreement: {amount: '50.00', currency: 'CZK', dayOfMonth: 5, payerUid: BOB, fromMonth: '2026-10'},
      lastModifiedBy: BOB,
    }));
  });

  it('refuses the co-parent overwriting a waiting proposal, and lets them decline it', async () => {
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertFails(as(BOB).doc(PATH).update({proposal: proposal(BOB, {amount: '1.00'}), lastModifiedBy: BOB}));
    await assertSucceeds(as(BOB).doc(PATH).set({participants: [ALICE, BOB], lastModifiedBy: BOB}));
  });

  it('lets the proposer change or withdraw their own proposal', async () => {
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertSucceeds(as(ALICE).doc(PATH).update({proposal: proposal(ALICE, {amount: '6000.00'}), lastModifiedBy: ALICE}));
    await assertSucceeds(as(ALICE).doc(PATH).set({participants: [ALICE, BOB], lastModifiedBy: ALICE}));
  });

  it('refuses a proposal in somebody else\'s name, a day past the 28th, or a payer outside the family', async () => {
    await assertFails(as(ALICE).doc(PATH).set(Object.assign(created(ALICE), {proposal: proposal(BOB)})));
    await assertFails(as(ALICE).doc(PATH).set(Object.assign(created(ALICE), {proposal: proposal(ALICE, {dayOfMonth: 31})})));
    await assertFails(as(ALICE).doc(PATH).set(Object.assign(created(ALICE), {proposal: proposal(ALICE, {payerUid: CAROL})})));
  });

  it('lets the two parents read it, a stranger nothing, and nobody delete it', async () => {
    await assertSucceeds(as(ALICE).doc(PATH).get());
    await assertSucceeds(as(ALICE).doc(PATH).set(created(ALICE)));
    await assertSucceeds(as(BOB).doc(PATH).get());
    await assertFails(as(CAROL).doc(PATH).get());
    await assertFails(as(ALICE).doc(PATH).delete());
  });

  it('refuses any write once the family has ended', async () => {
    await env.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc(`families/${FAMILY}`).delete();
    });
    await assertFails(as(ALICE).doc(PATH).set(created(ALICE)));
  });
});
