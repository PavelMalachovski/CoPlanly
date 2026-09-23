/**
 * MON-16 — export receipts are the callables' alone.
 *
 * `export_receipts/{recordId}` holds the hash a verifier checks a court document against. The
 * whole value of a receipt is that a parent could not have written it themselves, so no client —
 * not the parent who made the export, not the co-parent, not a stranger — may create, change,
 * delete or read one. The callables in `functions/export-receipts.js` write as admin.
 */

const {Timestamp} = require('firebase/firestore');
const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-export-receipts';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const FAMILY = [ALICE, BOB].sort().join('__');
const RECORD = '7K3Q0ABCDEFGHJKM';

/**
 * A registered receipt as `registerImpl` leaves it.
 *
 * @param {!Object} overrides Fields to override.
 * @return {!Object} The document.
 */
function receipt(overrides) {
  return Object.assign({
    state: 'registered',
    generatorUid: ALICE,
    familyId: FAMILY,
    fromDate: '2026-06-01',
    toDate: '2026-08-31',
    format: 'pdf',
    reservedAt: Timestamp.fromMillis(1790000000000),
    sha256: 'a'.repeat(64),
    byteLength: 4096,
    recordedAt: Timestamp.fromMillis(1790000005000),
    formatVersion: 1,
  }, overrides);
}

describe('export_receipts', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      'users/alice-uid': {name: 'Alice', partnerId: BOB, partnerIds: [BOB]},
      'users/bob-uid': {name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
      [`export_receipts/${RECORD}`]: receipt({}),
    });
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();
  const anonymous = () => env.unauthenticatedContext().firestore();

  it('the rules suite can see the seeded receipt (so the refusals below mean something)', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await assertSucceeds(ctx.firestore().doc(`export_receipts/${RECORD}`).get());
    });
  });

  it('refuses the parent who made the export reading their own receipt', async () => {
    await assertFails(as(ALICE).doc(`export_receipts/${RECORD}`).get());
  });

  it('refuses the co-parent reading it', async () => {
    await assertFails(as(BOB).doc(`export_receipts/${RECORD}`).get());
  });

  it('refuses a verifier with no account reading it — they go through verifyExport', async () => {
    await assertFails(anonymous().doc(`export_receipts/${RECORD}`).get());
  });

  it('refuses a query by hash, the one a verifier would want to run', async () => {
    await assertFails(as(ALICE).collection('export_receipts')
        .where('sha256', '==', 'a'.repeat(64)).get());
    await assertFails(anonymous().collection('export_receipts')
        .where('sha256', '==', 'a'.repeat(64)).get());
  });

  it('refuses a parent registering a hash for a file the app never made', async () => {
    await assertFails(as(ALICE).doc('export_receipts/NEWRECORD0000000')
        .set(receipt({sha256: 'c'.repeat(64)})));
  });

  it('refuses the author re-dating or re-hashing their receipt', async () => {
    await assertFails(as(ALICE).doc(`export_receipts/${RECORD}`)
        .update({recordedAt: Timestamp.fromMillis(1700000000000)}));
    await assertFails(as(ALICE).doc(`export_receipts/${RECORD}`)
        .update({sha256: 'd'.repeat(64)}));
  });

  it('refuses the co-parent deleting it, and the author too', async () => {
    await assertFails(as(BOB).doc(`export_receipts/${RECORD}`).delete());
    await assertFails(as(ALICE).doc(`export_receipts/${RECORD}`).delete());
  });
});
