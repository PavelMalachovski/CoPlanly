/**
 * The `payments` block (25 October 2026): "I paid" and "Money arrived" for a month's settle-up
 * and for child support.
 *
 * What these cases pin:
 * - only the payer records a payment, with the server's clock, under the id its own fields spell,
 *   in a family that is live;
 * - only the parent paid confirms it, once, with the server's clock, and nothing else moves;
 * - the two parents read it, nobody else, and nobody deletes it.
 */

const {serverTimestamp} = require('firebase/firestore');
const {CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails} = require('../harness');

const PROJECT = 'demo-coplanly-payments';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';
const FAMILY = `${ALICE}__${BOB}`;
const ID = `${FAMILY}__expenses__2026-10__CZK`;

const serverTime = () => serverTimestamp();

/**
 * A payment as `PaymentDocument.paid` writes it, from [payer] to [payee].
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document data.
 */
function paid(overrides) {
  return Object.assign({
    familyId: FAMILY,
    participants: [ALICE, BOB].sort(),
    kind: 'expenses',
    month: '2026-10',
    currency: 'CZK',
    amount: '450.00',
    payerUid: BOB,
    payeeUid: ALICE,
    paidBy: BOB,
    paidAt: serverTime(),
  }, overrides);
}

describe('payments', () => {
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

  async function recorded() {
    await assertSucceeds(as(BOB).doc(`payments/${ID}`).set(paid({})));
  }

  describe('I paid', () => {
    it('lets the payer record it', recorded);

    it('refuses recording that somebody else paid', async () => {
      await assertFails(as(ALICE).doc(`payments/${ID}`).set(paid({})));
    });

    it('refuses a payment to the payer themselves, or to somebody outside the family', async () => {
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({payeeUid: BOB})));
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({payeeUid: CAROL})));
    });

    it('refuses an id its own fields do not spell', async () => {
      await assertFails(as(BOB).doc(`payments/${FAMILY}__expenses__2026-09__CZK`).set(paid({})));
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({currency: 'EUR'})));
    });

    it('refuses a phone clock, a confirmation written at once, and an unbounded amount', async () => {
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({paidAt: 1790000000000})));
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({confirmedAt: serverTime()})));
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({amount: '450'})));
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({amount: 450})));
    });

    it('refuses a payment in a family that has ended', async () => {
      await env.withSecurityRulesDisabled(async (context) => {
        await context.firestore().doc(`families/${FAMILY}`).delete();
      });
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({})));
    });

    it('refuses a second payment for the same month and currency', async () => {
      await recorded();
      await assertFails(as(BOB).doc(`payments/${ID}`).set(paid({amount: '10.00'})));
    });
  });

  describe('Money arrived', () => {
    it('lets the parent paid confirm it, once', async () => {
      await recorded();
      const ref = as(ALICE).doc(`payments/${ID}`);
      await assertSucceeds(ref.update({confirmedAt: serverTime(), confirmedBy: ALICE}));
      await assertFails(ref.update({confirmedAt: serverTime(), confirmedBy: ALICE}));
    });

    it('refuses the payer confirming their own payment', async () => {
      await recorded();
      await assertFails(as(BOB).doc(`payments/${ID}`).update({confirmedAt: serverTime(), confirmedBy: BOB}));
    });

    it('refuses a confirmation that changes the amount or uses a phone clock', async () => {
      await recorded();
      const ref = as(ALICE).doc(`payments/${ID}`);
      await assertFails(ref.update({confirmedAt: serverTime(), confirmedBy: ALICE, amount: '1.00'}));
      await assertFails(ref.update({confirmedAt: 1790000000000, confirmedBy: ALICE}));
    });
  });

  describe('read and delete', () => {
    it('lets the two parents read it and list their family\'s, and nobody else', async () => {
      await recorded();
      await assertSucceeds(as(ALICE).doc(`payments/${ID}`).get());
      await assertSucceeds(as(ALICE).collection('payments')
          .where('participants', 'array-contains', ALICE).where('familyId', '==', FAMILY).get());
      await assertFails(as(CAROL).doc(`payments/${ID}`).get());
    });

    it('refuses every client delete', async () => {
      await recorded();
      await assertFails(as(BOB).doc(`payments/${ID}`).delete());
      await assertFails(as(ALICE).doc(`payments/${ID}`).delete());
    });
  });
});
