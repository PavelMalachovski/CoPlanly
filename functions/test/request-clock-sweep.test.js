const test = require('firebase-functions-test')();
const assert = require('assert');
const {fakeDb} = require('./support/clock-fake-db');
const index = require('../index');
const {execFileSync} = require('child_process');
const path = require('path');

/**
 * Tests for the request clocks' schedule on the server (MON-25, spec §5.1, §5.2 and §5.6): the
 * trigger that keeps `serverNextStepAt`, the five-minute sweep that steps the due clocks, and the
 * operator backfill for the clocks open at deploy. The steps themselves are
 * `request-clock-steps.test.js`'s; here they are reached only through the sweep.
 */

const HOUR = 3600000;
const DAY = 24 * HOUR;
/** The deadline of every clock here. */
const T = 1800000000000;
/** Where a clock with no last chance and no cap ends: the button's day after the deadline. */
const END = T + DAY;
const FAMILY = 'alice__bob';
const PROPOSED_AT = '2026-11-17T10:00:00';
const deps = {serverTimestamp: () => 'SERVER_TIME'};

/**
 * A change request clock from Alice to Bob, with no schedule yet.
 * @param {string} ref the change request's id.
 * @param {!Object=} extra fields to add or replace.
 * @return {!Object}
 */
function changeClock(ref, extra) {
  return Object.assign({
    kind: 'change', ref, familyId: FAMILY, proposerUid: 'alice', responderUid: 'bob',
    participants: ['alice', 'bob'], answerByMillis: T,
  }, extra);
}

/**
 * Alice's custody proposal clock.
 * @param {!Object=} extra fields to add or replace.
 * @return {!Object}
 */
function proposalClock(extra) {
  return Object.assign({
    kind: 'proposal', ref: PROPOSED_AT, familyId: FAMILY, proposerUid: 'alice', responderUid: 'bob',
    participants: ['alice', 'bob'], answerByMillis: T,
  }, extra);
}

/**
 * The family's custody document, with Alice's proposal still waiting unless told otherwise.
 * @param {boolean=} waiting whether the proposal still waits.
 * @return {!Object}
 */
function custody(waiting) {
  const base = {participants: ['alice', 'bob'], startDate: '2026-01-05', patternDays: 14, momDayIndices: [0, 1]};
  if (waiting === false) return base;
  return Object.assign(base, {proposal: {
    startDate: '2026-12-07', patternDays: 14, momDayIndices: [0], proposedBy: 'alice', proposedAt: PROPOSED_AT,
  }});
}

/**
 * A database with the pair's profiles and thread, plus the given documents.
 * @param {!Object<string, !Object>} seed documents by path.
 * @return {!Object} the fake.
 */
function setUp(seed) {
  return fakeDb(Object.assign({
    'users/alice': {name: 'Alice Smith', partnerId: 'bob', partnerIds: ['bob']},
    'users/bob': {name: 'Bob Jones', partnerId: 'alice', partnerIds: ['alice']},
    [`conversations/${FAMILY}`]: {participants: ['alice', 'bob'], lastMessageAt: 1},
  }, seed));
}

/**
 * Writes a clock as a phone or a step would, then runs the trigger over that write.
 * @param {!Object} db the fake.
 * @param {string} id the clock's id.
 * @param {?Object} after the clock's data after the write, or null for a deletion.
 * @return {!Promise<{action: string}>} the trigger's answer.
 */
async function writeAndTrigger(db, id, after) {
  const path = `request_deadlines/${id}`;
  const before = db.store[path] === undefined ? null : JSON.parse(JSON.stringify(db.store[path]));
  if (after === null) delete db.store[path];
  else db.store[path] = JSON.parse(JSON.stringify(after));
  return index.onRequestClockWrittenImpl(db, {clockId: id, before, after});
}

describe('request clock schedule', () => {
  describe('the trigger', () => {
    /**
     * Each row of the spec's §5.1, as a clock a phone writes and the field the trigger keeps.
     * @const {!Array<{name: string, clock: !Object, expected: ?number}>}
     */
    const ROWS = [
      {name: 'an open clock is due at its deadline', clock: changeClock('cr1'), expected: T},
      {
        name: 'a clock never told and past its end is due at its deadline, already past',
        clock: changeClock('cr1', {answerByMillis: 50}),
        expected: 50,
      },
      {
        name: 'a deadline already told is due at the end',
        clock: changeClock('cr1', {serverToldDeadlineAt: T + 1, serverNextStepAt: T}),
        expected: END,
      },
      {
        name: 'a last chance is due where it ends',
        clock: changeClock('cr1', {lastChanceUntilMillis: T + 30 * HOUR, lastChanceAt: 5}),
        expected: T + 30 * HOUR,
      },
      {
        name: 'a custody clock a phone closed is due at its closing, to check the withdrawal',
        clock: proposalClock({outcome: 'no_answer', closedAt: END + 7}),
        expected: END + 7,
      },
      {
        name: 'a split clock a phone closed is due at its closing',
        clock: Object.assign(proposalClock({outcome: 'no_answer', closedAt: END + 9}), {kind: 'split', ref: '17'}),
        expected: END + 9,
      },
      {
        name: 'a change clock a phone closed needs nothing',
        clock: changeClock('cr1', {outcome: 'no_answer', closedAt: END + 7, serverNextStepAt: END}),
        expected: null,
      },
      {
        name: 'a settled clock needs nothing',
        clock: changeClock('cr1', {serverSettledAt: T + 2, serverNextStepAt: T, seenAt: T + 3}),
        expected: null,
      },
    ];

    ROWS.forEach((row) => {
      it(row.name, async () => {
        const db = setUp({});
        await writeAndTrigger(db, 'c1', row.clock);
        const stored = db.store['request_deadlines/c1'];
        if (row.expected === null) assert.ok(!('serverNextStepAt' in stored), JSON.stringify(stored));
        else assert.strictEqual(stored.serverNextStepAt, row.expected);
      });
    });

    it('theTriggerIgnoresAWriteOfOnlyServerFields', async () => {
      const db = setUp({'request_deadlines/c1': changeClock('cr1', {serverNextStepAt: T})});
      // A step settled the clock while the trigger's own value would still be the deadline.
      const result = await writeAndTrigger(db, 'c1', changeClock('cr1', {serverSettledAt: T + 5}));
      assert.deepStrictEqual(result, {action: 'ignored'});
      assert.ok(!('serverNextStepAt' in db.store['request_deadlines/c1']));
      // The four fields together are still only the server's.
      const all = await writeAndTrigger(db, 'c1', changeClock('cr1', {
        serverSettledAt: T + 5, serverToldDeadlineAt: T + 1, serverToldNoAnswerAt: T + 2, serverNextStepAt: 1,
      }));
      assert.deepStrictEqual(all, {action: 'ignored'});
      assert.strictEqual(db.store['request_deadlines/c1'].serverNextStepAt, 1);
    });

    it('theTriggerDoesNotLoop', async () => {
      const db = setUp({});
      const first = await writeAndTrigger(db, 'c1', changeClock('cr1'));
      assert.deepStrictEqual(first, {action: 'scheduled'});
      // Its own write fires it again, with only `serverNextStepAt` changed: nothing more is written.
      const before = changeClock('cr1');
      const after = JSON.parse(JSON.stringify(db.store['request_deadlines/c1']));
      const second = await index.onRequestClockWrittenImpl(db, {clockId: 'c1', before, after});
      assert.deepStrictEqual(second, {action: 'ignored'});
      // A phone's write that does not move the moment writes nothing either.
      const seen = await writeAndTrigger(db, 'c1', Object.assign({}, after, {seenAt: T - HOUR}));
      assert.deepStrictEqual(seen, {action: 'unchanged'});
    });

    it('leaves a clock the server closed but could not settle to the step', async () => {
      const db = setUp({'request_deadlines/c1': proposalClock({serverNextStepAt: END})});
      // The close changes `outcome` and `closedAt` with the server's fields; the step kept it due.
      const result = await writeAndTrigger(db, 'c1', proposalClock({
        outcome: 'no_answer', closedAt: END + 1, serverToldNoAnswerAt: END + 1, serverNextStepAt: END,
      }));
      assert.deepStrictEqual(result, {action: 'ownedByStep'});
      assert.strictEqual(db.store['request_deadlines/c1'].serverNextStepAt, END);
      // A phone's later write does not clear it either.
      const later = await writeAndTrigger(db, 'c1', Object.assign({}, db.store['request_deadlines/c1'], {seenAt: 3}));
      assert.deepStrictEqual(later, {action: 'ownedByStep'});
      assert.strictEqual(db.store['request_deadlines/c1'].serverNextStepAt, END);
    });

    it('writes nothing after a close the step settled', async () => {
      const db = setUp({'request_deadlines/c1': changeClock('cr1', {serverNextStepAt: END})});
      const result = await writeAndTrigger(db, 'c1', changeClock('cr1', {
        outcome: 'no_answer', closedAt: END + 1, serverToldNoAnswerAt: END + 1, serverSettledAt: END + 1,
      }));
      assert.deepStrictEqual(result, {action: 'unchanged'});
      assert.ok(!('serverNextStepAt' in db.store['request_deadlines/c1']));
    });

    it('decides from the clock as it stands, not as the write left it', async () => {
      const db = setUp({});
      const after = changeClock('cr1', {seenAt: 1});
      db.store['request_deadlines/c1'] = changeClock('cr1', {seenAt: 1, serverSettledAt: T + 5});
      const result = await index.onRequestClockWrittenImpl(db, {clockId: 'c1', before: changeClock('cr1'), after});
      assert.deepStrictEqual(result, {action: 'unchanged'});
      assert.ok(!('serverNextStepAt' in db.store['request_deadlines/c1']));
    });

    it('does nothing for a deleted clock', async () => {
      const db = setUp({'request_deadlines/c1': changeClock('cr1', {serverNextStepAt: T})});
      const result = await writeAndTrigger(db, 'c1', null);
      assert.deepStrictEqual(result, {action: 'deleted'});
      assert.ok(!('request_deadlines/c1' in db.store));
    });

    it('is wired to the deployed trigger', async () => {
      const wrapped = test.wrap(index.onRequestClockWritten);
      const change = test.makeChange(
          test.firestore.makeDocumentSnapshot(changeClock('cr1', {serverNextStepAt: 1}), 'request_deadlines/c1'),
          test.firestore.makeDocumentSnapshot(changeClock('cr1', {serverNextStepAt: 2}), 'request_deadlines/c1'));
      // A write of only the server's field: answered without reading anything.
      assert.strictEqual(await wrapped(change, {params: {id: 'c1'}}), null);
    });
  });

  describe('the sweep', () => {
    /**
     * A database of open change request clocks that a stale schedule left due, each with its
     * pending record, so a step only reschedules it to its deadline.
     * @param {number} count how many.
     * @return {!Object} the fake.
     */
    function dueOpenClocks(count) {
      const seed = {};
      for (let i = 0; i < count; i++) {
        seed[`request_deadlines/c${i}`] = changeClock(`cr${i}`, {serverNextStepAt: 100 + i});
        seed[`change_requests/cr${i}`] = {requestedBy: 'alice', requestedTo: 'bob', status: 'PENDING'};
      }
      return setUp(seed);
    }

    it('steps every due clock across pages, and nothing else', async () => {
      const db = dueOpenClocks(5);
      db.store['request_deadlines/later'] = changeClock('crL', {serverNextStepAt: T - HOUR});
      db.store['request_deadlines/none'] = changeClock('crN');
      const result = await index.sweepRequestClocksImpl(db, 1000, Object.assign({pageSize: 2}, deps));
      assert.deepStrictEqual(result, {stepped: 5, left: 0});
      for (let i = 0; i < 5; i++) assert.strictEqual(db.store[`request_deadlines/c${i}`].serverNextStepAt, T);
      assert.strictEqual(db.store['request_deadlines/later'].serverNextStepAt, T - HOUR);
      assert.ok(!('serverNextStepAt' in db.store['request_deadlines/none']));
    });

    it('moves past a clock whose record could not be read, leaving it due', async () => {
      const db = dueOpenClocks(4);
      // Past its end, so the step must read the record; the read fails.
      db.store['request_deadlines/c1'] = changeClock('cr1', {serverNextStepAt: 101, answerByMillis: 50});
      db.failRead('change_requests/cr1');
      const quiet = console.warn;
      console.warn = () => {};
      let result;
      try {
        result = await index.sweepRequestClocksImpl(db, T + 2 * DAY, Object.assign({pageSize: 1}, deps));
      } finally {
        console.warn = quiet;
      }
      assert.deepStrictEqual(result, {stepped: 4, left: 0});
      assert.strictEqual(db.store['request_deadlines/c1'].serverNextStepAt, 101);
      assert.ok(!('outcome' in db.store['request_deadlines/c1']));
    });

    it('stops at its time budget and leaves the rest due', async () => {
      const db = dueOpenClocks(5);
      let now = 0;
      const elapsed = () => {
        now += 10;
        return now;
      };
      const result = await index.sweepRequestClocksImpl(db, 1000,
          Object.assign({pageSize: 10, budgetMillis: 35, elapsedMillis: elapsed}, deps));
      assert.strictEqual(result.stepped + result.left, 5);
      assert.ok(result.stepped >= 1 && result.left >= 1, JSON.stringify(result));
      const stillDue = Object.keys(db.docsOf('request_deadlines'))
          .filter((id) => db.store[`request_deadlines/${id}`].serverNextStepAt <= 1000);
      assert.strictEqual(stillDue.length, result.left);
      // The next run takes them.
      const next = await index.sweepRequestClocksImpl(db, 1000, Object.assign({pageSize: 10}, deps));
      assert.deepStrictEqual(next, {stepped: result.left, left: 0});
    });

    it('aClockAnOldPhoneClosedOnlyChecksTheWithdrawal', async () => {
      const db = setUp({[`custody_models/${FAMILY}`]: Object.assign(custody(), {futureKey: 'kept'})});
      // An older build's end job closes the clock (and failed to withdraw the proposal).
      await writeAndTrigger(db, 'proposal_alice_x', proposalClock({outcome: 'no_answer', closedAt: END + 5}));
      assert.strictEqual(db.store['request_deadlines/proposal_alice_x'].serverNextStepAt, END + 5);

      const result = await index.sweepRequestClocksImpl(db, END + HOUR, deps);
      assert.deepStrictEqual(result, {stepped: 1, left: 0});
      const custodyDoc = db.store[`custody_models/${FAMILY}`];
      assert.ok(!('proposal' in custodyDoc));
      assert.strictEqual(custodyDoc.futureKey, 'kept');
      const stored = db.store['request_deadlines/proposal_alice_x'];
      assert.ok(!('serverNextStepAt' in stored));
      assert.ok(!('serverToldNoAnswerAt' in stored));
      // Nothing told twice: no push and no card from the server.
      assert.deepStrictEqual(db.docsOf('notification_queue'), {});
      assert.deepStrictEqual(db.docsOf('messages'), {});
    });

    it('aLastChanceGivenAfterTheDeadlinePushMovesTheEnd', async () => {
      const db = setUp({'change_requests/cr1': {requestedBy: 'alice', requestedTo: 'bob', status: 'PENDING'}});
      await writeAndTrigger(db, 'change_alice_cr1', changeClock('cr1'));
      // The deadline passes: the server tells Alice and waits for the end.
      await index.sweepRequestClocksImpl(db, T + HOUR, deps);
      const told = db.store['request_deadlines/change_alice_cr1'];
      assert.strictEqual(told.serverNextStepAt, END);
      // Alice gives a last chance two hours after the deadline, running past the old end.
      const until = T + 26 * HOUR;
      await writeAndTrigger(db, 'change_alice_cr1', Object.assign({}, told, {lastChanceUntilMillis: until}));
      assert.strictEqual(db.store['request_deadlines/change_alice_cr1'].serverNextStepAt, until);
      // At the old end nothing is due any more.
      assert.deepStrictEqual(await index.sweepRequestClocksImpl(db, END + HOUR, deps), {stepped: 0, left: 0});
      assert.ok(!('outcome' in db.store['request_deadlines/change_alice_cr1']));
      // At the last chance's end the request closes.
      await index.sweepRequestClocksImpl(db, until + 1, deps);
      assert.strictEqual(db.store['request_deadlines/change_alice_cr1'].outcome, 'no_answer');
    });
  });

  describe('the backfill', () => {
    /**
     * The clocks open at deploy, one of each case the backfill decides.
     * @return {!Object} the fake.
     */
    function atDeploy() {
      return setUp({
        [`custody_models/${FAMILY}`]: custody(),
        'request_deadlines/open': changeClock('cr1'),
        'request_deadlines/told': changeClock('cr2', {serverToldDeadlineAt: T + 1}),
        'request_deadlines/scheduled': changeClock('cr3', {serverNextStepAt: T}),
        'request_deadlines/settled': changeClock('cr4', {serverSettledAt: T + 1}),
        'request_deadlines/closedWaiting': proposalClock({outcome: 'no_answer', closedAt: END + 1}),
        'request_deadlines/closedAnswered': proposalClock({
          ref: '2026-11-01T10:00:00', outcome: 'no_answer', closedAt: END + 1,
        }),
        'request_deadlines/closedChange': changeClock('cr5', {outcome: 'no_answer', closedAt: END + 1}),
        'request_deadlines/serverClosed': proposalClock({
          outcome: 'no_answer', closedAt: END + 1, serverToldNoAnswerAt: END + 1, serverSettledAt: END + 1,
        }),
      });
    }

    it('schedules the open clocks and the closed proposals that still wait, and reruns safely', async () => {
      const db = atDeploy();
      const first = await index.backfillRequestClockScheduleImpl(db, END + 2 * HOUR, {pageSize: 3});
      assert.deepStrictEqual(first, {scanned: 8, scheduled: 3, unchanged: 1, skipped: 4, failed: 0});
      const at = (id) => db.store[`request_deadlines/${id}`].serverNextStepAt;
      assert.strictEqual(at('open'), T);
      assert.strictEqual(at('told'), END);
      assert.strictEqual(at('scheduled'), T);
      assert.strictEqual(at('closedWaiting'), END + 1);
      ['settled', 'closedAnswered', 'closedChange', 'serverClosed'].forEach((id) => {
        assert.strictEqual(at(id), undefined, id);
      });

      const again = await index.backfillRequestClockScheduleImpl(db, END + 3 * HOUR, {pageSize: 3});
      assert.deepStrictEqual(again, {scanned: 8, scheduled: 0, unchanged: 4, skipped: 4, failed: 0});
    });

    it('schedules a closed proposal with no readable closing at once', async () => {
      const db = setUp({
        [`custody_models/${FAMILY}`]: custody(),
        'request_deadlines/p': proposalClock({outcome: 'no_answer'}),
      });
      const result = await index.backfillRequestClockScheduleImpl(db, END + 2 * HOUR);
      assert.strictEqual(result.scheduled, 1);
      assert.strictEqual(db.store['request_deadlines/p'].serverNextStepAt, END + 2 * HOUR);
    });

    it('counts a closed proposal whose record could not be read as failed', async () => {
      const db = setUp({
        [`custody_models/${FAMILY}`]: custody(),
        'request_deadlines/p': proposalClock({outcome: 'no_answer', closedAt: END + 1}),
      });
      db.failRead(`custody_models/${FAMILY}`);
      const quiet = console.warn;
      console.warn = () => {};
      try {
        const result = await index.backfillRequestClockScheduleImpl(db, END + 2 * HOUR);
        assert.deepStrictEqual(result, {scanned: 1, scheduled: 0, unchanged: 0, skipped: 0, failed: 1});
      } finally {
        console.warn = quiet;
      }
      assert.ok(!('serverNextStepAt' in db.store['request_deadlines/p']));
    });

    it('refuses a caller who is not an operator', async () => {
      const saved = process.env.BACKFILL_ADMIN_UIDS;
      process.env.BACKFILL_ADMIN_UIDS = 'op-uid';
      try {
        const wrapped = test.wrap(index.backfillRequestClockSchedule);
        await assert.rejects(
            () => wrapped({}, {auth: {uid: 'someone-else'}}), (err) => err.code === 'permission-denied');
        await assert.rejects(() => wrapped({}, {}), (err) => err.code === 'permission-denied');
      } finally {
        if (saved === undefined) delete process.env.BACKFILL_ADMIN_UIDS;
        else process.env.BACKFILL_ADMIN_UIDS = saved;
      }
    });
  });

  describe('the emulator-only sweep', () => {
    /**
     * Whether `index.js`, loaded in a fresh process with [env], exports the emulator-only sweep.
     * A fresh process because the module, once loaded here, keeps what it exported.
     * @param {!Object} env variables to add to this process's environment.
     * @return {string} `function` or `undefined`.
     */
    function exportedWith(env) {
      const base = Object.assign({}, process.env);
      delete base.FUNCTIONS_EMULATOR;
      delete base.FIRESTORE_EMULATOR_HOST;
      return execFileSync(process.execPath, ['-e',
        'process.stdout.write(typeof require("./index").sweepRequestClocksOnEmulator)'],
      {cwd: path.join(__dirname, '..'), env: Object.assign(base, {GCLOUD_PROJECT: 'demo-coplanly'}, env)},
      ).toString();
    }

    it('is not exported where a deploy loads the code', () => {
      assert.strictEqual(index.sweepRequestClocksOnEmulator, undefined);
      assert.strictEqual(exportedWith({}), 'undefined');
    });

    it('needs both the Functions and the Firestore emulator', () => {
      assert.strictEqual(exportedWith({FUNCTIONS_EMULATOR: 'true'}), 'undefined');
      assert.strictEqual(exportedWith({FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080'}), 'undefined');
      assert.strictEqual(
          exportedWith({FUNCTIONS_EMULATOR: 'true', FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080'}), 'function');
    });
  });
});
