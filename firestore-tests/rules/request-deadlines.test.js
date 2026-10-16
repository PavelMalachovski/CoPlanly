/**
 * Phase 3a — the clock of a request that waits on the other parent.
 *
 * `request_deadlines/{id}` is one small document beside every day swap, change request, event
 * awaiting acceptance and custody proposal: the deadline its proposer chose, the first moment it
 * would change something, and the marks the two phones add as it is received, opened, reminded
 * about, given a last chance and closed
 * (docs/superpowers/specs/2026-10-15-zuzana-feedback-phase-3a-requests-never-hang-design.md §5).
 *
 * These cases pin what the rules have to hold for that to be worth reading on a screen:
 *
 * - **A clock cannot be forged.** Its id is rebuilt from the caller's uid and its own fields, so
 *   the other parent can neither start a request's clock first and strip its deadline, nor sign
 *   one in the proposer's name.
 * - **Each field has one writer and is written once.** The deadline, the cap, the two parents and
 *   the creation time never move after the create, and neither parent writes the other's fields.
 * - **The server vouches for the times that matter.** `createdAt`, `lastChanceAt` and `closedAt`
 *   are the server's clock (pinned to `request.time`, as `event_versions.recordedAt` is). What a
 *   phone reads off its own clock - when it received or opened a request, where a last chance
 *   ends - may be five minutes either side of the server's and no more: a clock that is a little
 *   wrong is wrong by the same amount on every retry, so a bound with no room for it refuses that
 *   phone for good.
 * - **Nothing in the clock refuses an answer.** That is the point of these rules not touching
 *   `change_requests`, `custody_models` or `events`: a co-parent on an older build answers a
 *   request whatever its clock says.
 */

const assert = require('assert');
const {Timestamp, serverTimestamp, increment} = require('firebase/firestore');
const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-request-deadlines';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** Alice and Bob's family, as `FamilyKey.of` spells it: the two uids sorted and joined. */
const FAMILY = [ALICE, BOB].sort().join('__');

/** A family Alice is not in. */
const OTHER_FAMILY = [BOB, CAROL].sort().join('__');

const PAIRED_USERS = {
  'users/alice-uid': {name: 'Alice', email: 'a@x.test', partnerId: BOB, partnerIds: [BOB]},
  'users/bob-uid': {name: 'Bob', email: 'b@x.test', partnerId: ALICE, partnerIds: [ALICE]},
  'users/carol-uid': {name: 'Carol', email: 'c@x.test', partnerId: '', partnerIds: []},
  // The family document pairing writes. Its existence is the relationship's liveness
  // (`isLiveFamilyMember`); unpair deletes it.
  [`families/${FAMILY}`]: {members: [ALICE, BOB].sort()},
};

/** The id of the swap clock the tests use: `swap_<proposerUid>_<groupId>`. */
const SWAP_ID = `swap_${ALICE}_g-1`;

/**
 * A swap clock from Alice to Bob as the create writes it: the required keys and nothing else, the
 * creation time left to the server and a deadline three days out.
 *
 * @param {!Object} over Fields to override.
 * @return {!Object} The document data.
 */
function clockDoc(over) {
  return Object.assign({
    kind: 'swap',
    ref: 'g-1',
    familyId: FAMILY,
    proposerUid: ALICE,
    responderUid: BOB,
    participants: [ALICE, BOB],
    createdAt: serverTimestamp(),
    answerByMillis: Date.now() + 3 * DAY,
  }, over);
}

/**
 * The swap clock as the store holds it after the create: `createdAt` a real timestamp, set a minute
 * back so that a phone's first receipt, a moment ago or now, comes after it.
 *
 * @param {!Object} over Fields to override.
 * @return {!Object} The document data.
 */
function storedClock(over) {
  const now = Date.now();
  return Object.assign(clockDoc({}), {
    createdAt: Timestamp.fromMillis(now - MINUTE),
    answerByMillis: now + 3 * DAY,
  }, over);
}

/**
 * The swap clock once its deadline has passed: created three days ago, due an hour ago. Seeded
 * with the rules off, since a create cannot produce it.
 *
 * @param {!Object} over Fields to override.
 * @return {!Object} The document data.
 */
function pastDueClock(over) {
  const now = Date.now();
  return storedClock(Object.assign({
    createdAt: Timestamp.fromMillis(now - 3 * DAY),
    answerByMillis: now - HOUR,
  }, over));
}

/**
 * What the answering parent's phone writes when it first receives a clock: when, and the
 * reminders it planned, in one write.
 *
 * @param {number} n How many reminders were planned, 0 to 3.
 * @param {!Object=} over Fields to override.
 * @return {!Object} The fields of the write.
 */
function plan(n, over) {
  const now = Date.now();
  return Object.assign({
    deliveredAtMillis: now,
    remindersPlanned: n,
    reminderTimesMillis: [now + HOUR, now + DAY, now + 2 * DAY].slice(0, n),
  }, over);
}

/**
 * The proposer's last chance: the server's stamp and when it ends.
 *
 * @param {*} untilMillis When the last chance ends.
 * @param {!Object=} over Fields to override.
 * @return {!Object} The fields of the write.
 */
function lastChance(untilMillis, over) {
  return Object.assign({
    lastChanceAt: serverTimestamp(),
    lastChanceUntilMillis: untilMillis,
  }, over);
}

/**
 * The proposer closing the clock: what the end of a request nobody answered writes.
 *
 * @param {!Object=} over Fields to override.
 * @return {!Object} The fields of the write.
 */
function closure(over) {
  return Object.assign({outcome: 'no_answer', closedAt: serverTimestamp()}, over);
}

/**
 * What `unpairCoParent` leaves behind: both profiles cleared and `families/{id}` deleted. The id
 * still names Alice and Bob, so membership of the id alone would keep admitting a clock.
 *
 * @param {!Object} env Rules test environment.
 * @return {!Promise<void>} Resolves when the family has ended.
 */
async function endFamily(env) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`families/${FAMILY}`).delete();
    await ctx.firestore().doc('users/alice-uid').set({partnerId: '', partnerIds: []}, {merge: true});
    await ctx.firestore().doc('users/bob-uid').set({partnerId: '', partnerIds: []}, {merge: true});
  });
}

describe('request_deadlines', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, PAIRED_USERS);
  });

  const as = (uid) => env.authenticatedContext(uid).firestore();

  /** The clock `id` (the swap clock by default) as `uid` sees it. */
  const clock = (uid, id) => as(uid).doc(`request_deadlines/${id || SWAP_ID}`);

  /** Seeds the clock `id` (the swap clock by default) with the rules off. */
  const put = (doc, id) => seed(env, {[`request_deadlines/${id || SWAP_ID}`]: doc});

  // ---- Create ------------------------------------------------------------------------------

  it('lets the proposer start a clock for a request to their live co-parent', async () => {
    await assertSucceeds(clock(ALICE).set(clockDoc({})));
  });

  it('starts each kind of clock under the id its kind spells, with the optional fields', async () => {
    const proposedAt = '2026-10-20T09:30:00';
    await assertSucceeds(clock(ALICE, `change_${ALICE}_cr-1`).set(
        clockDoc({kind: 'change', ref: 'cr-1'})));
    await assertSucceeds(clock(ALICE, `event_${ALICE}_ev-1`).set(
        clockDoc({kind: 'event', ref: 'ev-1'})));
    // A proposal's reference is a timestamp, unique within one family's custody document only, so
    // its id carries the family too - and the family's own `__` is part of it.
    await assertSucceeds(clock(ALICE, `proposal_${ALICE}_${FAMILY}_${proposedAt}`).set(
        clockDoc({kind: 'proposal', ref: proposedAt})));
    // A swap names its first day and how many days it covers; any request may carry a cap.
    const answerBy = Date.now() + 3 * DAY;
    await assertSucceeds(clock(ALICE, `swap_${ALICE}_g-2`).set(clockDoc({
      ref: 'g-2',
      answerByMillis: answerBy,
      capMillis: answerBy + 2 * DAY,
      firstDate: '2026-10-20',
      dayCount: 3,
    })));
  });

  it('lets either parent be the proposer', async () => {
    await assertSucceeds(clock(BOB, `swap_${BOB}_g-7`).set(clockDoc({
      ref: 'g-7',
      proposerUid: BOB,
      responderUid: ALICE,
      participants: [BOB, ALICE],
    })));
  });

  it('refuses a clock under somebody else\'s uid, or one whose id does not match its fields', async () => {
    // The id's second segment is the proposer's uid and the rule rebuilds it from the caller:
    // Bob can neither start Alice's clock first and strip its deadline, nor sign it as his own.
    await assertFails(clock(BOB).set(clockDoc({})));
    await assertFails(clock(BOB).set(clockDoc({proposerUid: BOB, responderUid: ALICE})));
    // Nor can he put Alice's name on a clock under his own id: the proposer is the caller.
    await assertFails(clock(BOB, `swap_${BOB}_g-1`).set(clockDoc({
      proposerUid: ALICE,
      responderUid: ALICE,
      participants: [BOB, ALICE],
    })));
    // The id is built from the fields: another reference, another kind...
    await assertFails(clock(ALICE, `swap_${ALICE}_g-2`).set(clockDoc({ref: 'g-1'})));
    await assertFails(clock(ALICE, `change_${ALICE}_g-1`).set(clockDoc({kind: 'swap'})));
    // ...a proposal's id without its family, or with some other family's...
    const proposedAt = '2026-10-20T09:30:00';
    const proposal = clockDoc({kind: 'proposal', ref: proposedAt});
    await assertFails(clock(ALICE, `proposal_${ALICE}_${proposedAt}`).set(proposal));
    await assertFails(clock(ALICE, `proposal_${ALICE}_${OTHER_FAMILY}_${proposedAt}`).set(proposal));
    // ...and a kind nobody defines.
    await assertFails(clock(ALICE, `handover_${ALICE}_g-1`).set(clockDoc({kind: 'handover'})));
    // Nobody signed in starts one either.
    await assertFails(env.unauthenticatedContext().firestore()
        .doc(`request_deadlines/${SWAP_ID}`).set(clockDoc({})));
  });

  it('refuses a responder outside the family, and a family that has ended', async () => {
    // Carol shares no family with Alice, whoever the participants say.
    await assertFails(clock(ALICE).set(clockDoc({
      responderUid: CAROL,
      participants: [ALICE, CAROL],
    })));
    // Nobody answers to themselves.
    await assertFails(clock(ALICE).set(clockDoc({
      responderUid: ALICE,
      participants: [ALICE, ALICE],
    })));
    // A family that exists but is not Alice's, and one that names more than two adults: only the
    // pair's own id is a family, and `families/` documents are written by the server alone.
    await seed(env, {
      [`families/${OTHER_FAMILY}`]: {members: [BOB, CAROL].sort()},
      [`families/${FAMILY}__${CAROL}`]: {members: [ALICE, BOB, CAROL].sort()},
    });
    await assertFails(clock(ALICE).set(clockDoc({familyId: OTHER_FAMILY})));
    await assertFails(clock(ALICE).set(clockDoc({familyId: `${FAMILY}__${CAROL}`})));
    // The pair ended: the id still names Alice and Bob, the family document no longer exists.
    await endFamily(env);
    await assertFails(clock(ALICE).set(clockDoc({})));
    await assertFails(clock(BOB, `swap_${BOB}_g-1`).set(clockDoc({
      proposerUid: BOB,
      responderUid: ALICE,
      participants: [BOB, ALICE],
    })));
  });

  it('names exactly the two parents in participants', async () => {
    await assertFails(clock(ALICE).set(clockDoc({participants: [ALICE]})));
    await assertFails(clock(ALICE).set(clockDoc({participants: [ALICE, BOB, CAROL]})));
    await assertFails(clock(ALICE).set(clockDoc({participants: [ALICE, CAROL]})));
    await assertFails(clock(ALICE).set(clockDoc({participants: [BOB, CAROL]})));
    await assertFails(clock(ALICE).set(clockDoc({participants: [BOB, BOB]})));
    await assertFails(clock(ALICE).set(clockDoc({participants: ALICE})));
    // A map keyed by the two uids has two entries and answers `in` to both, and is no list.
    await assertFails(clock(ALICE).set(clockDoc({participants: {[ALICE]: true, [BOB]: true}})));
    // The order is free.
    await assertSucceeds(clock(ALICE).set(clockDoc({participants: [BOB, ALICE]})));
  });

  it('refuses a deadline in the past or more than 31 days out, and a cap not after it', async () => {
    const now = Date.now();
    // Already gone.
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: now - MINUTE})));
    // Beyond 31 days. The app asks for 30 at most; the rule leaves a day for a clock that runs fast.
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: now + 31 * DAY + MINUTE})));
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: now + 90 * DAY})));
    // Not a whole number of millis.
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: String(now + DAY)})));
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: now + DAY + 0.5})));
    // A cap is the first moment the request would change something: it comes after the deadline.
    const answerBy = now + 3 * DAY;
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: answerBy, capMillis: answerBy})));
    await assertFails(clock(ALICE).set(clockDoc({
      answerByMillis: answerBy,
      capMillis: answerBy - HOUR,
    })));
    await assertFails(clock(ALICE).set(clockDoc({
      answerByMillis: answerBy,
      capMillis: String(answerBy + DAY),
    })));
    await assertFails(clock(ALICE).set(clockDoc({
      answerByMillis: answerBy,
      capMillis: answerBy + DAY + 0.5,
    })));
    // One step inside the bounds is fine, and a cap may be as far off as the day it names.
    await assertSucceeds(clock(ALICE).set(clockDoc({
      answerByMillis: now + 31 * DAY - MINUTE,
      capMillis: now + 90 * DAY,
    })));
  });

  it('bounds the optional fields and the reference', async () => {
    await assertFails(clock(ALICE).set(clockDoc({firstDate: '2026-5-1'})));
    await assertFails(clock(ALICE).set(clockDoc({firstDate: 20261020})));
    await assertFails(clock(ALICE).set(clockDoc({dayCount: 0})));
    await assertFails(clock(ALICE).set(clockDoc({dayCount: 367})));
    await assertFails(clock(ALICE).set(clockDoc({dayCount: 1.5})));
    await assertFails(clock(ALICE).set(clockDoc({dayCount: '2'})));
    // An absent optional field is not a null one: the writer leaves it out.
    await assertFails(clock(ALICE).set(clockDoc({capMillis: null})));
    await assertFails(clock(ALICE).set(clockDoc({firstDate: null})));
    await assertFails(clock(ALICE).set(clockDoc({dayCount: null})));
    // The reference is one to 100 characters.
    await assertFails(clock(ALICE, `swap_${ALICE}_`).set(clockDoc({ref: ''})));
    const tooLong = 'r'.repeat(101);
    await assertFails(clock(ALICE, `swap_${ALICE}_${tooLong}`).set(clockDoc({ref: tooLong})));
    // The edges of each bound are inside it.
    await assertSucceeds(clock(ALICE).set(clockDoc({firstDate: '2026-10-20', dayCount: 1})));
    await assertSucceeds(clock(ALICE, `swap_${ALICE}_g-2`).set(clockDoc({
      ref: 'g-2',
      dayCount: 366,
    })));
    const longest = 'r'.repeat(100);
    await assertSucceeds(clock(ALICE, `swap_${ALICE}_${longest}`).set(clockDoc({ref: longest})));
  });

  it('refuses a createdAt the client chose, and any later field on create', async () => {
    // The server's clock, and only the server's: the client writes `serverTimestamp()`.
    await assertFails(clock(ALICE).set(clockDoc({createdAt: Timestamp.fromMillis(Date.now())})));
    await assertFails(clock(ALICE).set(clockDoc({createdAt: Date.now()})));
    const without = clockDoc({});
    delete without.createdAt;
    await assertFails(clock(ALICE).set(without));
    // A new clock holds none of the fields that are written later, and nothing the schema lacks.
    const later = {
      deliveredAtMillis: Date.now(),
      remindersPlanned: 0,
      reminderTimesMillis: [],
      seenAtMillis: Date.now(),
      remindersShown: 0,
      lastChanceAt: serverTimestamp(),
      lastChanceUntilMillis: Date.now() + HOUR,
      outcome: 'no_answer',
      closedAt: serverTimestamp(),
      note: 'smuggled',
    };
    for (const [key, value] of Object.entries(later)) {
      await assertFails(clock(ALICE).set(clockDoc({[key]: value})));
    }
  });

  // ---- Read --------------------------------------------------------------------------------

  it('lets the two parents read and list it by participants and family, and nobody else', async () => {
    await put(storedClock({}));
    await assertSucceeds(clock(ALICE).get());
    await assertSucceeds(clock(BOB).get());
    await assertFails(clock(CAROL).get());
    await assertFails(env.unauthenticatedContext().firestore()
        .doc(`request_deadlines/${SWAP_ID}`).get());

    // Firestore judges a list by its structure: a query nothing narrows to the caller is refused,
    // even though every document in it would have passed.
    const bob = as(BOB).collection('request_deadlines');
    await assertFails(bob.get());
    await assertFails(bob.where('familyId', '==', FAMILY).get());
    await assertFails(bob.where('participants', 'array-contains', ALICE).get());
    // The two queries the app runs: one family's clocks, and the open ones since a moment.
    const family = await assertSucceeds(bob
        .where('participants', 'array-contains', BOB)
        .where('familyId', '==', FAMILY).get());
    assert.strictEqual(family.size, 1);
    await assertSucceeds(bob
        .where('participants', 'array-contains', BOB)
        .where('answerByMillis', '>=', Date.now()).get());
    // A stranger's own query is allowed and finds nothing.
    const carol = await assertSucceeds(as(CAROL).collection('request_deadlines')
        .where('participants', 'array-contains', CAROL).get());
    assert.strictEqual(carol.size, 0);
  });

  // ---- The answering parent's phone --------------------------------------------------------

  it('lets the answering parent record delivery and its plan once, together', async () => {
    // However many reminders the plan holds - none to three - it is one write.
    for (const n of [0, 1, 2, 3]) {
      await put(storedClock({}));
      await assertSucceeds(clock(BOB).update(plan(n)));
    }

    // The refusals, against one clock, each a step off a plan that is accepted.
    const doc = storedClock({});
    const createdAt = doc.createdAt.toMillis();
    const answerBy = doc.answerByMillis;
    const now = Date.now();
    await put(doc);
    // Delivery and plan are one write: neither alone.
    await assertFails(clock(BOB).update({deliveredAtMillis: now}));
    await assertFails(clock(BOB).update({
      remindersPlanned: 1,
      reminderTimesMillis: [now + HOUR],
    }));
    // At most three, and as many times as the count says.
    await assertFails(clock(BOB).update(plan(4, {
      reminderTimesMillis: [now + HOUR, now + 2 * HOUR, now + 3 * HOUR, now + 4 * HOUR],
    })));
    await assertFails(clock(BOB).update(plan(2, {reminderTimesMillis: [now + HOUR]})));
    await assertFails(clock(BOB).update(plan(1, {reminderTimesMillis: [now + HOUR, now + 2 * HOUR]})));
    await assertFails(clock(BOB).update(plan(1, {reminderTimesMillis: now + HOUR})));
    // An empty map is as long as a plan of none, and is no list.
    await assertFails(clock(BOB).update(plan(0, {reminderTimesMillis: {}})));
    // Each time lies between the clock's creation and its deadline - a reminder after the
    // deadline would remind about nothing. Whichever of the three places it sits in.
    for (let place = 0; place < 3; place++) {
      for (const outside of [answerBy + 1, createdAt - 1]) {
        const times = [now + HOUR, now + DAY, now + 2 * DAY];
        times[place] = outside;
        await assertFails(clock(BOB).update(plan(3, {reminderTimesMillis: times})));
      }
    }
    await assertFails(clock(BOB).update(plan(1, {reminderTimesMillis: [now + HOUR + 0.5]})));
    await assertFails(clock(BOB).update(plan(1, {reminderTimesMillis: [String(now + HOUR)]})));
    // A phone's clock may be a few minutes off the server's, and ten minutes is not believed:
    // not ten before the clock was created, nor ten ahead of the server (four minutes is either
    // way: device clocks drift, see the skew cases below).
    await assertFails(clock(BOB).update(plan(1, {deliveredAtMillis: createdAt - 10 * MINUTE})));
    await assertFails(clock(BOB).update(plan(1, {deliveredAtMillis: now + 10 * MINUTE})));
    await assertFails(clock(BOB).update(plan(1, {deliveredAtMillis: String(now)})));
    await assertFails(clock(BOB).update(plan(1, {deliveredAtMillis: now + 0.5})));
    // Nobody else writes it, and no other field rides along.
    await assertFails(clock(ALICE).update(plan(1)));
    await assertFails(clock(CAROL).update(plan(1)));
    await assertFails(clock(BOB).update(plan(1, {seenAtMillis: now})));
    await assertFails(clock(BOB).update(plan(1, {answerByMillis: answerBy + DAY})));
    await assertSucceeds(clock(BOB).update(plan(1, {deliveredAtMillis: now + 4 * MINUTE})));
    // Once: neither the same plan again nor another one.
    await assertFails(clock(BOB).update(plan(1)));
    await assertFails(clock(BOB).update(plan(2)));
  });

  it('takes a reminder at either end of its window', async () => {
    const doc = storedClock({});
    await put(doc);
    await assertSucceeds(clock(BOB).update(plan(2, {
      reminderTimesMillis: [doc.createdAt.toMillis(), doc.answerByMillis],
    })));
  });

  it('lets a phone stamp delivery five minutes either side of the server, and no further', async () => {
    // The answering phone stamps delivery with its own clock about a second after the server
    // creates the clock. One that runs even slightly behind stamps a moment *before* `createdAt`,
    // and a retry stamps the same lag again: with no room for it, that phone is refused for good.
    const doc = storedClock({});
    const createdAt = doc.createdAt.toMillis();
    const now = Date.now();
    const deliveredAt = (millis) => plan(1, {deliveredAtMillis: millis});
    await put(doc);
    await assertFails(clock(BOB).update(deliveredAt(createdAt - 10 * MINUTE)));
    await assertSucceeds(clock(BOB).update(deliveredAt(createdAt - 4 * MINUTE)));
    // And the same room on the fast side, on a fresh clock (a plan is written once).
    await put(doc);
    await assertFails(clock(BOB).update(deliveredAt(now + 10 * MINUTE)));
    await assertSucceeds(clock(BOB).update(deliveredAt(now + 4 * MINUTE)));
  });

  it('lets the answering parent record opening once', async () => {
    const doc = storedClock(plan(2));
    const createdAt = doc.createdAt.toMillis();
    const now = Date.now();
    await put(doc);
    // Only the answering parent opens a request; it is an int, and within a few minutes of the
    // server's clock - not ten before the clock was created, nor ten ahead of the server.
    await assertFails(clock(ALICE).update({seenAtMillis: now}));
    await assertFails(clock(CAROL).update({seenAtMillis: now}));
    await assertFails(clock(BOB).update({seenAtMillis: String(now)}));
    await assertFails(clock(BOB).update({seenAtMillis: now + 0.5}));
    await assertFails(clock(BOB).update({seenAtMillis: createdAt - 10 * MINUTE}));
    await assertFails(clock(BOB).update({seenAtMillis: now + 10 * MINUTE}));
    await assertFails(clock(BOB).update({seenAtMillis: now, remindersShown: 1}));
    await assertSucceeds(clock(BOB).update({seenAtMillis: now + 4 * MINUTE}));
    // Once.
    await assertFails(clock(BOB).update({seenAtMillis: now}));
    await assertFails(clock(BOB).update({seenAtMillis: now + MINUTE}));
  });

  it('lets a phone stamp opening five minutes either side of the server, and no further', async () => {
    // The same room as delivery, for the same reason: a phone a little behind the server stamps
    // the opening of a request it was just handed before the clock existed, every time.
    const doc = storedClock({});
    const createdAt = doc.createdAt.toMillis();
    const now = Date.now();
    await put(doc);
    await assertFails(clock(BOB).update({seenAtMillis: createdAt - 10 * MINUTE}));
    await assertSucceeds(clock(BOB).update({seenAtMillis: createdAt - 4 * MINUTE}));
    // And on the fast side, on a fresh clock (opening is written once).
    await put(doc);
    await assertFails(clock(BOB).update({seenAtMillis: now + 10 * MINUTE}));
    await assertSucceeds(clock(BOB).update({seenAtMillis: now + 4 * MINUTE}));
  });

  it('lets the answering parent count shown reminders one at a time, up to the plan', async () => {
    await put(storedClock(plan(2)));
    const now = Date.now();
    // One more than the last, never a jump, a repeat or a step back; and only Bob's phone shows.
    await assertFails(clock(BOB).update({remindersShown: 2}));
    await assertFails(clock(BOB).update({remindersShown: 0}));
    await assertFails(clock(BOB).update({remindersShown: -1}));
    await assertFails(clock(BOB).update({remindersShown: 1.5}));
    await assertFails(clock(BOB).update({remindersShown: '1'}));
    await assertFails(clock(ALICE).update({remindersShown: 1}));
    await assertFails(clock(CAROL).update({remindersShown: 1}));
    await assertFails(clock(BOB).update({remindersShown: 1, seenAtMillis: now}));
    // As the phone reports them: a plain value, then the increment the app sends.
    await assertSucceeds(clock(BOB).update({remindersShown: 1}));
    await assertFails(clock(BOB).update({remindersShown: 1}));
    await assertSucceeds(clock(BOB).update({remindersShown: increment(1)}));
    // And never above what was planned.
    await assertFails(clock(BOB).update({remindersShown: increment(1)}));
    await assertFails(clock(BOB).update({remindersShown: 3}));

    // A clock with no plan, or a plan of none, has nothing to count.
    await put(storedClock({}));
    await assertFails(clock(BOB).update({remindersShown: 1}));
    await assertFails(clock(BOB).update({remindersShown: increment(1)}));
    await put(storedClock(plan(0)));
    await assertFails(clock(BOB).update({remindersShown: increment(1)}));
  });

  // ---- The proposer: the end ---------------------------------------------------------------

  it('lets the proposer give one last chance after the deadline, within a day and the cap', async () => {
    const now = Date.now();
    // Before the deadline there is nothing to give a chance to.
    await put(storedClock({}));
    await assertFails(clock(ALICE).update(lastChance(now + 23 * HOUR)));

    await put(pastDueClock({}));
    // No more than a day from now, and in the future.
    await assertFails(clock(ALICE).update(lastChance(now + 25 * HOUR)));
    await assertFails(clock(ALICE).update(lastChance(now - MINUTE)));
    await assertFails(clock(ALICE).update(lastChance(String(now + HOUR))));
    await assertFails(clock(ALICE).update(lastChance(now + HOUR + 0.5)));
    // The server's stamp, not the phone's, and both fields together.
    await assertFails(clock(ALICE).update(
        lastChance(now + HOUR, {lastChanceAt: Timestamp.fromMillis(now)})));
    await assertFails(clock(ALICE).update({lastChanceUntilMillis: now + HOUR}));
    await assertFails(clock(ALICE).update({lastChanceAt: serverTimestamp()}));
    // It is the proposer's to give, and it carries nothing else - least of all the closure.
    await assertFails(clock(BOB).update(lastChance(now + HOUR)));
    await assertFails(clock(CAROL).update(lastChance(now + HOUR)));
    await assertFails(clock(ALICE).update(lastChance(now + HOUR, {outcome: 'no_answer'})));
    await assertSucceeds(clock(ALICE).update(lastChance(now + 23 * HOUR)));
    // Once.
    await assertFails(clock(ALICE).update(lastChance(now + 2 * HOUR)));

    // A cap shortens it: no later than the moment the request would change something.
    await put(pastDueClock({capMillis: now + 5 * HOUR}));
    await assertFails(clock(ALICE).update(lastChance(now + 5 * HOUR + MINUTE)));
    await assertFails(clock(ALICE).update(lastChance(now + 20 * HOUR)));
    await assertSucceeds(clock(ALICE).update(lastChance(now + 5 * HOUR)));

    // A cap already behind us leaves no last chance to give.
    await put(pastDueClock({capMillis: now - MINUTE}));
    await assertFails(clock(ALICE).update(lastChance(now + HOUR)));

    // A clock that was closed stays closed.
    await put(pastDueClock({outcome: 'no_answer', closedAt: Timestamp.fromMillis(now - MINUTE)}));
    await assertFails(clock(ALICE).update(lastChance(now + HOUR)));
  });

  it('lets a last chance end five minutes past a day, and no later', async () => {
    // The proposer's phone ends a last chance a day from *its* now, and the rule measures the day
    // from the server's. A phone whose clock leads by more than the write's latency sends an end
    // past a day, and the lead is the same on every retry, so there is room for a few minutes of it.
    // A day exactly is accepted too, which pins the length: nothing else reaches the 24 hours.
    // (The cap takes no such room: the cases above refuse an end a minute past it.)
    const now = Date.now();
    const giveLastChance = async (untilMillis) => {
      await put(pastDueClock({}));
      return clock(ALICE).update(lastChance(untilMillis));
    };
    await assertSucceeds(giveLastChance(now + 24 * HOUR));
    await assertSucceeds(giveLastChance(now + 24 * HOUR + 4 * MINUTE));
    await assertFails(giveLastChance(now + 24 * HOUR + 10 * MINUTE));
    await assertFails(giveLastChance(now + 25 * HOUR));
  });

  it('lets the proposer close it as no_answer once, only after the deadline', async () => {
    // Not before.
    await put(storedClock({}));
    await assertFails(clock(ALICE).update(closure()));

    await put(pastDueClock({}));
    // The one outcome there is, with the server's stamp, and both together.
    await assertFails(clock(ALICE).update(closure({outcome: 'declined'})));
    await assertFails(clock(ALICE).update(closure({outcome: 'accepted'})));
    await assertFails(clock(ALICE).update(closure({outcome: ''})));
    await assertFails(clock(ALICE).update(closure({closedAt: Timestamp.fromMillis(Date.now())})));
    await assertFails(clock(ALICE).update({outcome: 'no_answer'}));
    await assertFails(clock(ALICE).update({closedAt: serverTimestamp()}));
    // The proposer's to write, and it carries nothing else.
    await assertFails(clock(BOB).update(closure()));
    await assertFails(clock(CAROL).update(closure()));
    await assertFails(clock(ALICE).update(closure({seenAtMillis: Date.now()})));
    await assertSucceeds(clock(ALICE).update(closure()));
    // Once.
    await assertFails(clock(ALICE).update(closure()));

    // A clock whose last chance has run out is closed the same way.
    const now = Date.now();
    await put(pastDueClock({
      lastChanceAt: Timestamp.fromMillis(now - 2 * HOUR),
      lastChanceUntilMillis: now - HOUR,
    }));
    await assertSucceeds(clock(ALICE).update(closure()));
  });

  // ---- Everything else ---------------------------------------------------------------------

  it('refuses each parent the other one\'s fields, a stranger everything, and every delete', async () => {
    const now = Date.now();

    // The answering parent has no say in the end, however late it is.
    await put(pastDueClock({}));
    await assertFails(clock(BOB).update(lastChance(now + HOUR)));
    await assertFails(clock(BOB).update(closure()));
    await assertFails(clock(CAROL).update(lastChance(now + HOUR)));
    await assertFails(clock(CAROL).update(closure()));

    // The proposer has none in what the other phone reports.
    await put(storedClock({}));
    await assertFails(clock(ALICE).update(plan(1)));
    await assertFails(clock(ALICE).update({seenAtMillis: now}));
    await assertFails(clock(CAROL).update(plan(1)));
    await assertFails(clock(CAROL).update({seenAtMillis: now}));
    await put(storedClock(plan(2)));
    await assertFails(clock(ALICE).update({remindersShown: 1}));
    await assertFails(clock(CAROL).update({remindersShown: 1}));

    // Nobody moves the deadline, the cap, the parents, the family or the id's own fields: not the
    // proposer who chose them, and not as part of a whole-document rewrite either.
    await put(storedClock({capMillis: now + 9 * DAY}));
    const frozen = [
      {answerByMillis: now + 5 * DAY},
      {capMillis: now + 10 * DAY},
      {responderUid: CAROL},
      {proposerUid: BOB},
      {participants: [ALICE, CAROL]},
      {familyId: OTHER_FAMILY},
      {kind: 'change'},
      {ref: 'g-2'},
      {createdAt: serverTimestamp()},
    ];
    for (const fields of frozen) {
      await assertFails(clock(ALICE).update(fields));
      await assertFails(clock(BOB).update(fields));
    }
    await assertFails(clock(ALICE).set(clockDoc({answerByMillis: now + 5 * DAY})));

    // Nobody deletes a clock: account deletion runs as the server, which bypasses the rules.
    await assertFails(clock(ALICE).delete());
    await assertFails(clock(BOB).delete());
    await assertFails(clock(CAROL).delete());
  });
});
