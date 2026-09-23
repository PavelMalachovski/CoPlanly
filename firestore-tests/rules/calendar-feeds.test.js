/**
 * `calendar_feeds/{sha256(token)}` — the read-only iCalendar links (MON-17).
 *
 * The token in a feed URL is the feed's only credential, and the document id is its hash. So the
 * collection is closed to every client in both directions, the way `google_oauth` is: a client
 * that could list it would learn which families have a link, one that could create a record would
 * mint a feed into somebody else's family, and one that could update or delete a record could
 * re-point a link at another family or keep a revoked one alive. The callables and the HTTPS
 * function run on Admin credentials and are the collection's only door — these cases prove that
 * nothing else is, including for the parent who owns the link.
 */

const {
  CURRENT_RULES, testEnv, seed, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-calendar-feeds';
const ALICE = 'uid-alice';
const BOB = 'uid-bob';
const STRANGER = 'uid-stranger';
const FAMILY = [ALICE, BOB].sort().join('__');
const HASH = 'a'.repeat(64);
const PATH = `calendar_feeds/${HASH}`;
const RECORD = {
  feedId: 'feed-1',
  familyId: FAMILY,
  familyMembers: [ALICE, BOB].sort(),
  ownerUid: ALICE,
  locale: 'en',
  createdAtMillis: 1,
  lastUsedAtMillis: 1,
};

/** A live, mutual pairing, so no denial below can be blamed on a missing family. */
const PAIRED = {
  [`users/${ALICE}`]: {id: ALICE, name: 'Alice', partnerId: BOB, partnerIds: [BOB]},
  [`users/${BOB}`]: {id: BOB, name: 'Bob', partnerId: ALICE, partnerIds: [ALICE]},
  [`families/${FAMILY}`]: {members: [ALICE, BOB], slots: {[ALICE]: 'mom', [BOB]: 'dad'}},
};

describe('calendar_feeds are closed to every client', () => {
  let env;

  before(async () => { env = await testEnv(PROJECT, CURRENT_RULES); });
  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, Object.assign({[PATH]: RECORD}, PAIRED));
  });

  it('refuses the owner reading their own link', async () => {
    await assertFails(env.authenticatedContext(ALICE).firestore().doc(PATH).get());
  });

  it('refuses the co-parent reading it', async () => {
    await assertFails(env.authenticatedContext(BOB).firestore().doc(PATH).get());
  });

  it('refuses listing the collection, even filtered to the caller', async () => {
    const db = env.authenticatedContext(ALICE).firestore();
    await assertFails(db.collection('calendar_feeds').where('ownerUid', '==', ALICE).get());
    await assertFails(db.collection('calendar_feeds').get());
  });

  it('refuses the owner minting a link of their own', async () => {
    await assertFails(env.authenticatedContext(ALICE).firestore()
        .doc(`calendar_feeds/${'b'.repeat(64)}`).set(Object.assign({}, RECORD, {feedId: 'feed-2'})));
  });

  it('refuses a stranger minting a link into the family', async () => {
    await assertFails(env.authenticatedContext(STRANGER).firestore()
        .doc(`calendar_feeds/${'c'.repeat(64)}`)
        .set(Object.assign({}, RECORD, {ownerUid: STRANGER})));
  });

  it('refuses re-pointing a link or reviving one by touching lastUsedAtMillis', async () => {
    const db = env.authenticatedContext(ALICE).firestore();
    await assertFails(db.doc(PATH).update({familyId: `${ALICE}__${STRANGER}`}));
    await assertFails(db.doc(PATH).update({lastUsedAtMillis: Date.now()}));
  });

  it('refuses deleting it from the client — revoking goes through the callable', async () => {
    await assertFails(env.authenticatedContext(ALICE).firestore().doc(PATH).delete());
  });

  it('refuses an unauthenticated reader', async () => {
    await assertFails(env.unauthenticatedContext().firestore().doc(PATH).get());
  });
});
