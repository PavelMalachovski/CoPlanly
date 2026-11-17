const assert = require('assert');
const fs = require('fs');
const path = require('path');
const {fakeDb} = require('./support/clock-fake-db');
const steps = require('../request-clock-steps');

const HOUR = 3600000;
const DAY = 24 * HOUR;
/** The deadline of every clock here. */
const T = 1800000000000;
/** A moment past the deadline, inside the day a last chance can still be given. */
const AT_DEADLINE = T + HOUR;
/** A moment past the end: no last chance was given, and the button's day is over. */
const AT_END = T + DAY + HOUR;
const FAMILY = 'alice__bob';
const deps = {serverTimestamp: () => 'SERVER_TIME'};

const PROPOSED_AT = '2026-11-17T10:00:00';
const SPLIT_AT = 1763373600000;

/**
 * What each kind's record looks like pending, answered and gone, and where it lives.
 * @const {!Object<string, !Object>}
 */
const KINDS = {
  change: {
    ref: 'cr1',
    path: 'change_requests/cr1',
    card: {entityType: 'CHANGE_REQUEST', entityId: 'ev1', title: 'Dentist'},
    pending: {'change_requests/cr1': changeRequest('PENDING')},
    answered: {'change_requests/cr1': changeRequest('ACCEPTED')},
    gone: {},
  },
  event: {
    ref: 'ev2',
    path: 'events/ev2',
    card: {entityType: 'EVENT', entityId: 'ev2', title: 'School play'},
    pending: {'events/ev2': event({acceptance: 'PENDING'})},
    answered: {'events/ev2': event({acceptance: 'ACCEPTED'})},
    gone: {'events/ev2': event({acceptance: 'PENDING', deletedAtMillis: 5})},
  },
  swap: {
    ref: 'g1',
    path: `custody_models/${FAMILY}`,
    card: {entityType: 'DAY_SWAP', entityId: '2026-12-01', title: '2026-12-01'},
    pending: {[`custody_models/${FAMILY}`]: custody({dayOverrides: {'2026-12-01': swapDay('PENDING', 'g1')}})},
    answered: {[`custody_models/${FAMILY}`]: custody({dayOverrides: {'2026-12-01': swapDay('ACCEPTED', 'g1')}})},
    gone: {[`custody_models/${FAMILY}`]: custody({dayOverrides: {'2026-12-01': swapDay('PENDING', 'other')}})},
  },
  proposal: {
    ref: PROPOSED_AT,
    path: `custody_models/${FAMILY}`,
    card: {entityType: 'CUSTODY_PROPOSAL', entityId: FAMILY, title: ''},
    pending: {[`custody_models/${FAMILY}`]: custody({
      proposal: custodyProposal(PROPOSED_AT), proposalPlanCitation: 'p1|care_weekday|0123456789abcdef',
    })},
    answered: {[`custody_models/${FAMILY}`]: custody({
      lastDecision: {outcome: 'ACCEPTED', by: 'bob', at: '2026-11-18T09:00:00', proposalAt: PROPOSED_AT},
    })},
    gone: {[`custody_models/${FAMILY}`]: custody({})},
  },
  split: {
    ref: String(SPLIT_AT),
    path: `family_settings/${FAMILY}`,
    card: {entityType: 'SPLIT_PROPOSAL', entityId: FAMILY, title: ''},
    pending: {[`family_settings/${FAMILY}`]: settings({proposal: splitProposal(SPLIT_AT)})},
    answered: {[`family_settings/${FAMILY}`]: settings({
      lastDecision: {outcome: 'DECLINED', by: 'bob', atMillis: SPLIT_AT + 5, proposalAtMillis: SPLIT_AT},
    })},
    gone: {[`family_settings/${FAMILY}`]: settings({})},
  },
};

/**
 * A change request from Alice to Bob.
 * @param {string} status its status.
 * @return {!Object}
 */
function changeRequest(status) {
  return {requestedBy: 'alice', requestedTo: 'bob', status, eventId: 'ev1', eventTitle: 'Dentist'};
}

/**
 * An event Alice created.
 * @param {!Object} extra fields to add.
 * @return {!Object}
 */
function event(extra) {
  return Object.assign({createdByFirebaseUid: 'alice', title: 'School play'}, extra);
}

/**
 * A day of a swap Alice offered.
 * @param {string} status the day's status.
 * @param {string} groupId its group.
 * @return {!Object}
 */
function swapDay(status, groupId) {
  return {toParent: 'dad', requestedBy: 'alice', requestedAt: '2026-11-17T10:00:00', status, groupId};
}

/**
 * The family's custody document, with keys the server does not know beside the pattern.
 * @param {!Object} extra fields to add.
 * @return {!Object}
 */
function custody(extra) {
  return Object.assign({
    participants: ['alice', 'bob'], startDate: '2026-01-05', patternDays: 14, momDayIndices: [0, 1],
    contactWindows: ['9|15:00|19:00|dad'], seasonalLayers: ['L1;summer'], futureKey: 'kept',
  }, extra);
}

/**
 * Alice's custody proposal.
 * @param {string} proposedAt its time.
 * @return {!Object}
 */
function custodyProposal(proposedAt) {
  return {startDate: '2026-12-07', patternDays: 14, momDayIndices: [0], proposedBy: 'alice', proposedAt};
}

/**
 * The family's money agreement.
 * @param {!Object} extra fields to add.
 * @return {!Object}
 */
function settings(extra) {
  return Object.assign({participants: ['alice', 'bob'], momShareBasisPoints: 5000, futureKey: 'kept'}, extra);
}

/**
 * Alice's split proposal.
 * @param {number} at its time.
 * @return {!Object}
 */
function splitProposal(at) {
  return {momShareBasisPoints: 6000, proposedBy: 'alice', proposedAtMillis: at};
}

/**
 * The clock's id, as the phone derives it.
 * @param {string} kind the clock's kind.
 * @return {string}
 */
function clockIdOf(kind) {
  return `${kind}_alice_${KINDS[kind].ref}`;
}

/**
 * A clock of the given kind, due at its deadline.
 * @param {string} kind the clock's kind.
 * @param {!Object=} extra fields to add.
 * @return {!Object}
 */
function clock(kind, extra) {
  return Object.assign({
    kind, ref: KINDS[kind].ref, familyId: FAMILY, proposerUid: 'alice', responderUid: 'bob',
    participants: ['alice', 'bob'], answerByMillis: T, serverNextStepAt: T,
  }, kind === 'swap' ? {firstDate: '2026-12-01', dayCount: 1} : {}, extra);
}

/**
 * A database with the pair's profiles, their thread, a clock and a record.
 * @param {string} kind the clock's kind.
 * @param {string} record `pending`, `answered`, `gone` or `unknown`.
 * @param {!Object=} options `clock` fields to add, `seed` documents to add or replace.
 * @return {!Object} the fake.
 */
function setUp(kind, record, options) {
  const opts = options || {};
  const recordSeed = record === 'unknown' ? KINDS[kind].pending : KINDS[kind][record];
  const db = fakeDb(Object.assign({
    'users/alice': {name: 'Alice Smith', partnerId: 'bob', partnerIds: ['bob']},
    'users/bob': {name: 'Bob Jones', partnerId: 'alice', partnerIds: ['alice']},
    [`conversations/${FAMILY}`]: {participants: ['alice', 'bob'], lastMessageAt: 1},
    [`request_deadlines/${clockIdOf(kind)}`]: clock(kind, opts.clock),
  }, recordSeed, opts.seed));
  if (record === 'unknown') db.failRead(KINDS[kind].path);
  return db;
}

/**
 * The clock's stored document.
 * @param {!Object} db the fake.
 * @param {string} kind the clock's kind.
 * @return {!Object}
 */
function storedClock(db, kind) {
  return db.store[`request_deadlines/${clockIdOf(kind)}`];
}

describe('request clock steps', () => {
  Object.keys(KINDS).forEach((kind) => {
    describe(`a ${kind} clock at its deadline`, () => {
      it('tells the proposer once while the record waits, and moves to the end', async () => {
        const db = setUp(kind, 'pending');
        const result = await steps.stepClock(db, clockIdOf(kind), AT_DEADLINE, deps);
        assert.deepStrictEqual(result, {action: 'toldDeadline'});
        const stored = storedClock(db, kind);
        assert.strictEqual(stored.serverToldDeadlineAt, AT_DEADLINE);
        assert.strictEqual(stored.serverNextStepAt, T + DAY);
        assert.strictEqual(stored.outcome, undefined);
        assert.deepStrictEqual(db.docsOf('notification_queue'), {
          [`srv_request_deadline_passed_${clockIdOf(kind)}`]: {
            targetUserId: 'alice',
            data: {type: 'request_deadline_passed', actorName: 'Bob', familyId: FAMILY},
            status: 'pending',
            createdAt: 'SERVER_TIME',
          },
        });
        assert.deepStrictEqual(db.docsOf('messages'), {});
      });

      ['answered', 'gone'].forEach((record) => {
        it(`settles without a push when the record is ${record}`, async () => {
          const db = setUp(kind, record);
          const result = await steps.stepClock(db, clockIdOf(kind), AT_DEADLINE, deps);
          assert.deepStrictEqual(result, {action: 'settled'});
          const stored = storedClock(db, kind);
          assert.strictEqual(stored.serverSettledAt, AT_DEADLINE);
          assert.strictEqual('serverNextStepAt' in stored, false);
          assert.deepStrictEqual(db.docsOf('notification_queue'), {});
        });
      });

      it('leaves the clock due when the record cannot be read', async () => {
        const db = setUp(kind, 'unknown');
        const before = JSON.parse(JSON.stringify(storedClock(db, kind)));
        const result = await steps.stepClock(db, clockIdOf(kind), AT_DEADLINE, deps);
        assert.deepStrictEqual(result, {action: 'skipped'});
        assert.deepStrictEqual(storedClock(db, kind), before);
        assert.deepStrictEqual(db.docsOf('notification_queue'), {});
      });
    });

    describe(`a ${kind} clock at its end`, () => {
      it('closes it, posts the card, pushes the proposer and withdraws what waits', async () => {
        const db = setUp(kind, 'pending', {clock: {serverNextStepAt: T + DAY, serverToldDeadlineAt: AT_DEADLINE}});
        const result = await steps.stepClock(db, clockIdOf(kind), AT_END, deps);
        assert.deepStrictEqual(result, {action: 'closed'});
        const stored = storedClock(db, kind);
        assert.strictEqual(stored.outcome, 'no_answer');
        assert.strictEqual(stored.closedAt, 'SERVER_TIME');
        assert.strictEqual(stored.serverToldNoAnswerAt, AT_END);
        assert.strictEqual(stored.serverSettledAt, AT_END);
        assert.strictEqual('serverNextStepAt' in stored, false);

        const card = KINDS[kind].card;
        const messageId = `srv_unanswered_${clockIdOf(kind)}`;
        assert.deepStrictEqual(db.docsOf('messages'), {
          [messageId]: {
            id: messageId,
            conversationId: FAMILY,
            senderId: 'alice',
            senderName: 'Alice Smith',
            content: card.title ? `No answer: ${card.title}` : 'No answer',
            timestamp: AT_END,
            messageType: 'ACTIVITY',
            attachments: [card.entityType, card.entityId],
            isRead: false,
            replyToMessageId: '',
            activity: Object.assign({kind: 'REQUEST_UNANSWERED'}, card),
          },
        });
        assert.strictEqual(db.store[`conversations/${FAMILY}`].lastMessageAt, AT_END);
        assert.deepStrictEqual(db.docsOf('notification_queue'), {
          [`srv_request_no_answer_${clockIdOf(kind)}`]: {
            targetUserId: 'alice',
            data: {type: 'request_no_answer', actorName: 'Bob', familyId: FAMILY},
            status: 'pending',
            createdAt: 'SERVER_TIME',
          },
        });

        if (kind === 'proposal') {
          const doc = db.store[`custody_models/${FAMILY}`];
          assert.strictEqual('proposal' in doc, false);
          assert.strictEqual('proposalPlanCitation' in doc, false);
        }
        if (kind === 'split') {
          assert.strictEqual('proposal' in db.store[`family_settings/${FAMILY}`], false);
        }
      });

      ['answered', 'gone'].forEach((record) => {
        it(`settles without closing when the record is ${record}`, async () => {
          const db = setUp(kind, record, {clock: {serverNextStepAt: T + DAY, serverToldDeadlineAt: AT_DEADLINE}});
          const result = await steps.stepClock(db, clockIdOf(kind), AT_END, deps);
          assert.deepStrictEqual(result, {action: 'settled'});
          const stored = storedClock(db, kind);
          assert.strictEqual(stored.outcome, undefined);
          assert.strictEqual(stored.serverSettledAt, AT_END);
          assert.deepStrictEqual(db.docsOf('messages'), {});
          assert.deepStrictEqual(db.docsOf('notification_queue'), {});
        });
      });

      it('leaves the clock due when the record cannot be read', async () => {
        const db = setUp(kind, 'unknown', {clock: {serverNextStepAt: T + DAY, serverToldDeadlineAt: AT_DEADLINE}});
        const before = JSON.parse(JSON.stringify(storedClock(db, kind)));
        const result = await steps.stepClock(db, clockIdOf(kind), AT_END, deps);
        assert.deepStrictEqual(result, {action: 'skipped'});
        assert.deepStrictEqual(storedClock(db, kind), before);
        assert.deepStrictEqual(db.docsOf('messages'), {});
      });
    });

    it(`lookRecord reads a ${kind} record as the phone does`, async () => {
      for (const record of ['pending', 'answered', 'gone', 'unknown']) {
        const db = setUp(kind, record);
        const look = await steps.lookRecord(db, clock(kind));
        assert.strictEqual(look.status, record, record);
        // A proposal's card names the family, so the phone hands it back even when the proposal is gone.
        const named = record === 'pending' || record === 'answered' ||
          (record === 'gone' && (kind === 'proposal' || kind === 'split'));
        if (named) assert.deepStrictEqual(look.card, KINDS[kind].card);
        else assert.strictEqual(look.card, null);
      }
    });
  });

  describe('timing', () => {
    it('reschedules a clock that fired before its deadline', async () => {
      const db = setUp('change', 'pending', {clock: {serverNextStepAt: T - DAY}});
      assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), T - HOUR, deps), {action: 'rescheduled'});
      assert.strictEqual(storedClock(db, 'change').serverNextStepAt, T);
      assert.deepStrictEqual(db.docsOf('notification_queue'), {});
    });

    it('reschedules to the end of a last chance', async () => {
      const until = AT_DEADLINE + DAY;
      const db = setUp('change', 'pending', {clock: {lastChanceUntilMillis: until}});
      const result = await steps.stepClock(db, clockIdOf('change'), AT_DEADLINE, deps);
      assert.deepStrictEqual(result, {action: 'rescheduled'});
      assert.strictEqual(storedClock(db, 'change').serverNextStepAt, until);
    });

    it('does not tell the deadline twice', async () => {
      const db = setUp('change', 'pending', {clock: {serverToldDeadlineAt: T}});
      const result = await steps.stepClock(db, clockIdOf('change'), AT_DEADLINE, deps);
      assert.deepStrictEqual(result, {action: 'rescheduled'});
      assert.strictEqual(storedClock(db, 'change').serverNextStepAt, T + DAY);
      assert.deepStrictEqual(db.docsOf('notification_queue'), {});
    });

    it('goes straight to the end for a clock past it that was never told', async () => {
      const db = setUp('change', 'pending');
      assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), AT_END, deps), {action: 'closed'});
      assert.deepStrictEqual(Object.keys(db.docsOf('notification_queue')),
          [`srv_request_no_answer_${clockIdOf('change')}`]);
    });

    it('does nothing for a clock that is not due', async () => {
      const db = setUp('change', 'pending', {clock: {serverNextStepAt: AT_DEADLINE + 1}});
      assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), AT_DEADLINE, deps), {action: 'notDue'});
      const noField = setUp('change', 'pending', {clock: {serverNextStepAt: null}});
      assert.deepStrictEqual(await steps.stepClock(noField, clockIdOf('change'), AT_END, deps), {action: 'notDue'});
      assert.deepStrictEqual(db.docsOf('notification_queue'), {});
    });

    it('settles a clock it cannot time', async () => {
      const db = setUp('change', 'pending', {clock: {kind: 'future_kind'}});
      assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), AT_END, deps), {action: 'settled'});
      assert.strictEqual(storedClock(db, 'change').outcome, undefined);
    });

    it('stamps the server\'s own time when no clock is passed', async () => {
      const db = setUp('change', 'pending');
      await steps.stepClock(db, clockIdOf('change'), AT_END);
      assert.deepStrictEqual(storedClock(db, 'change').closedAt, {__serverTimestamp: true});
    });
  });

  it('doesNotCloseWhenTheRecordWasAnsweredInsideTheTransaction', async () => {
    const db = setUp('change', 'pending');
    // The co-parent answers after the sweep found the clock due, before the step reads the record.
    db.beforeTransaction((store) => {
      store['change_requests/cr1'].status = 'ACCEPTED';
    });
    assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), AT_END, deps), {action: 'settled'});
    assert.strictEqual(storedClock(db, 'change').outcome, undefined);
    assert.deepStrictEqual(db.docsOf('messages'), {});
    assert.deepStrictEqual(db.docsOf('notification_queue'), {});
  });

  it('aClockOfAnEndedFamilyIsSettledWithoutAPush', async () => {
    for (const at of [AT_DEADLINE, AT_END]) {
      const db = setUp('proposal', 'pending', {seed: {'users/alice': {name: 'Alice', partnerId: '', partnerIds: []}}});
      assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('proposal'), at, deps), {action: 'settled'});
      assert.strictEqual(storedClock(db, 'proposal').outcome, undefined);
      assert.deepStrictEqual(db.docsOf('messages'), {});
      assert.deepStrictEqual(db.docsOf('notification_queue'), {});
      assert.ok('proposal' in db.store[`custody_models/${FAMILY}`]);
    }
    // A deleted proposer has no live family either.
    const deleted = setUp('change', 'pending');
    delete deleted.store['users/alice'];
    assert.deepStrictEqual(await steps.stepClock(deleted, clockIdOf('change'), AT_END, deps), {action: 'settled'});
    assert.deepStrictEqual(deleted.docsOf('notification_queue'), {});
  });

  it('actorNameIsAFirstNameNeverAnAddress', async () => {
    const db = setUp('change', 'pending', {seed: {'users/bob': {name: 'bob@example.com', partnerIds: ['alice']}}});
    await steps.stepClock(db, clockIdOf('change'), AT_DEADLINE, deps);
    const push = db.docsOf('notification_queue')[`srv_request_deadline_passed_${clockIdOf('change')}`];
    assert.strictEqual(push.data.actorName, '');

    const named = setUp('change', 'pending', {seed: {'users/bob': {name: '  Bob   Jones ', partnerIds: ['alice']}}});
    await steps.stepClock(named, clockIdOf('change'), AT_END, deps);
    const end = named.docsOf('notification_queue')[`srv_request_no_answer_${clockIdOf('change')}`];
    assert.strictEqual(end.data.actorName, 'Bob');
  });

  it('twoOverlappingStepsActOnce', async () => {
    const db = setUp('proposal', 'pending');
    const results = await Promise.all([
      steps.stepClock(db, clockIdOf('proposal'), AT_END, deps),
      steps.stepClock(db, clockIdOf('proposal'), AT_END, deps),
    ]);
    assert.deepStrictEqual(results.map((r) => r.action).sort(), ['closed', 'notDue']);
    assert.strictEqual(Object.keys(db.docsOf('messages')).length, 1);
    assert.strictEqual(Object.keys(db.docsOf('notification_queue')).length, 1);
  });

  it('aRetryAfterTheCloseSendsNoSecondCardOrPush', async () => {
    const db = setUp('change', 'pending');
    await steps.stepClock(db, clockIdOf('change'), AT_END, deps);
    const card = JSON.parse(JSON.stringify(db.docsOf('messages')));
    // Whatever makes the clock due again (a re-armed field), the server's close is not repeated.
    const stored = storedClock(db, 'change');
    stored.serverNextStepAt = AT_END;
    delete stored.serverSettledAt;
    assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('change'), AT_END + HOUR, deps), {action: 'settled'});
    assert.deepStrictEqual(db.docsOf('messages'), card);
    assert.strictEqual(Object.keys(db.docsOf('notification_queue')).length, 1);

    // An older phone closed a proposal and its withdrawal failed: the server withdraws, and tells nobody.
    const old = setUp('proposal', 'pending', {clock: {outcome: 'no_answer', closedAt: 'PHONE_TIME'}});
    const retried = await steps.stepClock(old, clockIdOf('proposal'), AT_END, deps);
    assert.deepStrictEqual(retried, {action: 'withdrawalRetried'});
    assert.strictEqual('proposal' in old.store[`custody_models/${FAMILY}`], false);
    assert.strictEqual(storedClock(old, 'proposal').serverSettledAt, AT_END);
    assert.deepStrictEqual(old.docsOf('messages'), {});
    assert.deepStrictEqual(old.docsOf('notification_queue'), {});

    // A closed change request needs nothing from the server.
    const closed = setUp('change', 'pending', {clock: {outcome: 'no_answer'}});
    assert.deepStrictEqual(await steps.stepClock(closed, clockIdOf('change'), AT_END, deps), {action: 'settled'});
    assert.deepStrictEqual(closed.docsOf('messages'), {});
  });

  it('aDifferentProposedAtIsNotWithdrawn', async () => {
    const custodyPath = `custody_models/${FAMILY}`;
    const settingsPath = `family_settings/${FAMILY}`;
    const newer = {
      [custodyPath]: custody({proposal: custodyProposal('2026-11-18T08:00:00')}),
      [settingsPath]: settings({proposal: splitProposal(SPLIT_AT + 1000)}),
    };
    const closedOf = (kind, seed) => setUp(kind, 'pending', {clock: {outcome: 'no_answer'}, seed});
    const db = closedOf('proposal', newer);
    assert.deepStrictEqual(await steps.stepClock(db, clockIdOf('proposal'), AT_END, deps), {action: 'settled'});
    assert.strictEqual(db.store[custodyPath].proposal.proposedAt, '2026-11-18T08:00:00');
    const splitDb = closedOf('split', newer);
    assert.deepStrictEqual(await steps.stepClock(splitDb, clockIdOf('split'), AT_END, deps), {action: 'settled'});
    assert.strictEqual(splitDb.store[settingsPath].proposal.proposedAtMillis, SPLIT_AT + 1000);

    // Nor by the step that retries a phone's withdrawal.
    const step = setUp('proposal', 'pending', {clock: {outcome: 'no_answer'}, seed: newer});
    assert.deepStrictEqual(await steps.stepClock(step, clockIdOf('proposal'), AT_END, deps), {action: 'settled'});
    assert.strictEqual(step.store[custodyPath].proposal.proposedAt, '2026-11-18T08:00:00');

    // A proposal by somebody else under the same time is not the clock's either.
    const other = closedOf('proposal', {[custodyPath]: custody({
      proposal: Object.assign(custodyProposal(PROPOSED_AT), {proposedBy: 'bob'}),
    })});
    assert.deepStrictEqual(await steps.stepClock(other, clockIdOf('proposal'), AT_END, deps), {action: 'settled'});
    assert.strictEqual(other.store[custodyPath].proposal.proposedBy, 'bob');
  });

  it('withdrawalKeepsKeysTheServerDoesNotKnow', async () => {
    const custodyDb = setUp('proposal', 'pending', {clock: {outcome: 'no_answer'}});
    assert.deepStrictEqual(
        await steps.stepClock(custodyDb, clockIdOf('proposal'), AT_END, deps), {action: 'withdrawalRetried'});
    const doc = custodyDb.store[`custody_models/${FAMILY}`];
    assert.deepStrictEqual(doc, {
      participants: ['alice', 'bob'], startDate: '2026-01-05', patternDays: 14, momDayIndices: [0, 1],
      contactWindows: ['9|15:00|19:00|dad'], seasonalLayers: ['L1;summer'], futureKey: 'kept',
    });

    const splitDb = setUp('split', 'pending', {
      clock: {outcome: 'no_answer'},
      seed: {[`family_settings/${FAMILY}`]: settings({
        proposal: splitProposal(SPLIT_AT),
        lastDecision: {outcome: 'ACCEPTED', by: 'bob', atMillis: 1, proposalAtMillis: 1},
      })},
    });
    assert.deepStrictEqual(
        await steps.stepClock(splitDb, clockIdOf('split'), AT_END, deps), {action: 'withdrawalRetried'});
    assert.deepStrictEqual(splitDb.store[`family_settings/${FAMILY}`], {
      participants: ['alice', 'bob'], momShareBasisPoints: 5000, futureKey: 'kept',
      lastDecision: {outcome: 'ACCEPTED', by: 'bob', atMillis: 1, proposalAtMillis: 1},
    });
  });

  it('theCardMatchesTheMessagesWireFixture', () => {
    const fixturePath = path.join(__dirname, '..', '..', 'app', 'src', 'test', 'resources', 'wire', 'current',
        'messages', 'activity-custody-proposed.json');
    const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8')).document;
    const {id, data} = steps.unansweredCardFor(
        Object.assign({id: clockIdOf('change')}, clock('change')), KINDS.change.card, 'Alice Smith', AT_END);
    assert.strictEqual(id, data.id);
    assert.deepStrictEqual(Object.keys(data).sort(), Object.keys(fixture).sort());
    Object.keys(fixture).forEach((key) => {
      assert.strictEqual(Array.isArray(data[key]), Array.isArray(fixture[key]), key);
      assert.strictEqual(typeof data[key], typeof fixture[key], key);
    });
    data.attachments.forEach((value) => assert.strictEqual(typeof value, 'string'));
    Object.keys(data.activity).forEach((key) => {
      assert.ok(key in fixture.activity, key);
      assert.strictEqual(typeof data.activity[key], typeof fixture.activity[key], key);
    });
    assert.strictEqual(data.messageType, fixture.messageType);
    // The phone's base-English fallback for the kind (`ActivityFallbackText`).
    assert.strictEqual(data.content, 'No answer: Dentist');
    assert.strictEqual(steps.unansweredCardFor(
        Object.assign({id: 'x'}, clock('proposal')), KINDS.proposal.card, '', AT_END).data.content, 'No answer');
    // Deterministic: the same clock is the same card.
    assert.strictEqual(steps.unansweredCardFor({id: 'abc', familyId: FAMILY, proposerUid: 'alice'},
        KINDS.change.card, '').id, 'srv_unanswered_abc');
  });
});
