/**
 * SEC-3 — a client cannot write what a notification says.
 *
 * A queued notification used to carry `title` and `body` composed by the *sending* device, and
 * `sendNotification` relayed them for the other phone to render verbatim, with the app's own
 * icon, on a lock screen. Nothing between the two devices decided what a notification was
 * allowed to say: the bounds that used to be here capped its length, which stops a wall of text
 * and nothing else.
 *
 * A payload now carries a **type** and the few names that type needs, and the receiving device
 * writes the sentence from its own string resources. These tests pin the two halves that make
 * that hold rather than merely be the convention:
 *
 * 1. a client may not write `title` or `body` at all, so it cannot go back to composing;
 * 2. a client may not claim one of the three types only a Cloud Function produces, so it cannot
 *    announce a pairing that did not happen or a chat message under a name that is not its own.
 *
 * Cloud Functions are unaffected by any of this — they write with admin credentials, which
 * bypass rules. That is what makes an allow-list safe here.
 */

const {
  CURRENT_RULES, testEnv, seed, assertSucceeds, assertFails,
} = require('../harness');

const PROJECT = 'demo-coplanly-push-payload';
const ALICE = 'alice-uid';
const BOB = 'bob-uid';
const CAROL = 'carol-uid';

/**
 * A queue document as `FcmService.queueNotificationForUser` writes it.
 *
 * @param {!Object} data The `data` payload.
 * @param {string=} target Recipient uid; defaults to Bob.
 * @return {!Object} The document.
 */
function queued(data, target) {
  return {
    targetUserId: target || BOB,
    data: data,
    createdAt: 1787000000000,
    status: 'pending',
  };
}

describe('SEC-3: notification payloads', () => {
  let env;

  before(async () => {
    env = await testEnv(PROJECT, CURRENT_RULES);
  });

  beforeEach(async () => {
    await env.clearFirestore();
    await seed(env, {
      'users/alice-uid': {name: 'Alice', email: 'a@x.test', partnerId: BOB},
      'users/bob-uid': {name: 'Bob', email: 'b@x.test', partnerId: ALICE},
      'users/carol-uid': {name: 'Carol', email: 'c@x.test', partnerId: ''},
    });
  });

  describe('what a client may say', () => {
    it('accepts a typed payload with the names it needs', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'event_created',
        eventId: 'event-1',
        subject: 'Swimming lesson',
        actorName: 'Alice',
      })));
    });

    it('refuses a payload carrying a title', async () => {
      // The whole point. A title is a sentence somebody else's phone would render as if the
      // app had written it.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        title: 'Security alert: verify your account',
      })));
    });

    it('refuses a payload carrying a body', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        body: 'Tap here to confirm',
      })));
    });

    it('refuses an empty title just as firmly as a long one', async () => {
      // A length bound would have let this through — the old rule accepted 0..200. The key
      // being present at all is what is refused now.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        title: '',
      })));
    });

    it('still bounds the names that do ride along', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        subject: 'x'.repeat(201),
      })));
    });

    it('bounds the actor name too', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        actorName: 'x'.repeat(101),
      })));
    });

    it('bounds the deadline a request push carries', async () => {
      // `answerBy` is the deadline as epoch millis in decimal text - 13 characters today. It is
      // a key a client writes, so it has a bound like every other: an unbounded key is a place
      // to put a wall of text.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered',
        answerBy: 'x'.repeat(21),
      })));
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'request_last_chance',
        answerBy: 'x'.repeat(21),
      })));
      // Text, not a number: the other keys here are all strings and so is this.
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered',
        answerBy: 1787000000000,
      })));
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered',
        answerBy: 'x'.repeat(20),
      })));
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered',
        answerBy: '1787000000000',
      })));
    });
  });

  describe('a day swap\'s dates (28 October 2026)', () => {
    // Fourteen ISO dates joined with commas is 153 characters: the most one offer can carry.
    const fourteen = Array.from({length: 14}, (_, i) =>
      `2026-11-${String(i + 1).padStart(2, '0')}`).join(',');

    it('accepts the dates each way at the largest offer', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'day_swap_group_offered', actorName: 'Alice', date: '2026-11-01',
        dayCount: '14', datesToYou: fourteen,
      })));
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'day_swap_group_offered', actorName: 'Alice', date: '2026-11-01',
        dayCount: '14', datesToSender: fourteen,
      })));
    });

    it('bounds each list like every key a client writes', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'day_swap_group_offered', datesToYou: 'x'.repeat(161),
      })));
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered', datesToSender: 'x'.repeat(161),
      })));
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'day_swap_offered', datesToSender: 'x'.repeat(160),
      })));
    });
  });

  describe('a new expense\'s figures (21 October 2026)', () => {
    it('accepts the amount, the share and the currency, and bounds each', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      const expense = {
        type: 'expense_added', actorName: 'Alice', subject: 'School trip',
        amount: '1000.00', share: '300.00', currency: 'CZK',
      };
      await assertSucceeds(db.collection('notification_queue').add(queued(expense)));
      await assertFails(db.collection('notification_queue').add(queued(
          Object.assign({}, expense, {amount: '9'.repeat(25)}))));
      await assertFails(db.collection('notification_queue').add(queued(
          Object.assign({}, expense, {share: '9'.repeat(25)}))));
      await assertFails(db.collection('notification_queue').add(queued(
          Object.assign({}, expense, {currency: 'X'.repeat(9)}))));
    });
  });

  describe('what a client may claim to be', () => {
    it('refuses a forged pairing notification', async () => {
      // Only `acceptPairingInvitation` produces this, and it writes as admin.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'pairing_accepted',
        actorName: 'Alice',
      })));
    });

    it('refuses a forged unpair notification', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'pairing_removed',
        actorName: 'Alice',
      })));
    });

    it('refuses a forged chat message', async () => {
      // The one type whose text the app still relays rather than composes — which is exactly
      // why a client must not be able to produce it.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'chat_message',
        conversationId: 'c1',
        actorName: 'CoPlanly Support',
        preview: 'Confirm your password',
      })));
    });

    it('refuses a type nobody defines', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'account_suspended',
      })));
    });

    it('refuses a payload with no type at all', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        eventId: 'event-1',
      })));
    });

    it('accepts each of the other client types', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      const types = [
        'event_updated', 'event_deleted', 'child_info_updated',
        'change_request_created', 'change_request_accepted',
        'change_request_declined', 'change_request_cancelled',
        'custody_proposal_proposed', 'custody_proposal_accepted',
        'custody_proposal_declined',
        'day_swap_offered', 'day_swap_accepted', 'day_swap_declined',
        'day_swap_group_offered', 'day_swap_group_accepted', 'day_swap_group_declined',
        'split_ratio_proposed', 'split_ratio_accepted', 'split_ratio_declined',
        // UX-18. The pair's first agreement, carried over from before they paired — its own
        // type because it is not a proposal and there is nothing to confirm or decline.
        'split_ratio_agreed',
        // Phase 3a. The proposer's phone queues it for the parent who did not answer in time.
        'request_last_chance',
        // 21 October 2026. A new shared expense, with its figures.
        'expense_added',
        // 21 October 2026. An important event: on both calendars at once, presence required.
        'event_important',
        // October 2026. The co-parent sent a sticker.
        'sticker_received',
      ];
      for (const type of types) {
        await assertSucceeds(
            db.collection('notification_queue').add(queued({type: type})));
      }
    });
  });

  describe('which family a push names (M-8)', () => {
    // The receiving device switches to this family before opening the tap target, so the id
    // is bounded like every other key a client writes, and must name a family the sender and
    // the addressee are both in.
    it('accepts the family the two of them share', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: 'alice-uid__bob-uid',
      })));
    });

    it('accepts a payload with no family, which is what an older build sends', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: '',
      })));
    });

    it('refuses a family the sender is not in', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: 'bob-uid__carol-uid',
      })));
    });

    it('refuses a family the addressee is not in', async () => {
      // Alice is in it, Bob is not: steering Bob's app at a family of Alice's with somebody
      // else is not something Alice's push may do.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: 'alice-uid__carol-uid',
      })));
    });

    it('bounds the family id like every other key', async () => {
      // A key the rule does not know about is a key with no bound. Membership alone would let
      // an id carry the two uids plus any amount of padding in a third part.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: 'alice-uid__bob-uid__' + 'x'.repeat(300),
      })));
    });

    it('refuses a family id that is not a string', async () => {
      const db = env.authenticatedContext(ALICE).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
        familyId: 42,
      })));
    });

    it('accepts a last chance that names the family and when it ends', async () => {
      // What the proposer's phone queues (phase 3a): a type, the family the request belongs to
      // and the end of the last chance. Nothing else rides along; the words are the receiving
      // device's own.
      const db = env.authenticatedContext(ALICE).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'request_last_chance',
        familyId: 'alice-uid__bob-uid',
        answerBy: '1787000000000',
      })));
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'request_last_chance',
        familyId: 'alice-uid__carol-uid',
        answerBy: '1787000000000',
      })));
    });
  });

  describe('who a client may notify', () => {
    it('still refuses a stranger', async () => {
      // Unchanged by SEC-3, and re-pinned here because the rule was rewritten around it.
      const db = env.authenticatedContext(CAROL).firestore();
      await assertFails(db.collection('notification_queue').add(queued({
        type: 'event_created',
      })));
    });

    it('still lets a user queue one for themselves', async () => {
      const db = env.authenticatedContext(CAROL).firestore();
      await assertSucceeds(db.collection('notification_queue').add(queued({
        type: 'event_created',
      }, CAROL)));
    });
  });
});
