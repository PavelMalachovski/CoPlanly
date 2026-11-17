/**
 * The pure half of the server's request clocks (MON-25): the arithmetic of a request's deadline,
 * ported from the Kotlin `RequestClock` (`shared/.../domain/changerequests/RequestClock.kt`).
 *
 * Nothing in this file touches Firestore or reads the time: `nowMillis` is always passed in. A
 * clock here is a plain object with the stored field names of `RequestDeadlineDocument`
 * (`answerByMillis`, `capMillis`, `lastChanceUntilMillis`, `outcome`, ...), so a raw document's
 * `data()` can be handed over as it is.
 *
 * **Kotlin is the authority.** `test/fixtures/request-clock-cases.json` is read by both
 * `test/request-clocks.test.js` and the Kotlin `RequestClockFixtureTest`, so the two cannot drift:
 * change the arithmetic in both, and add a case to the fixture.
 */

/** How long a last chance runs when the cap does not shorten it: 24 hours. */
const LAST_CHANCE_MILLIS = 86400000;

/** How long after the deadline the proposer can still give a last chance: 24 hours. */
const BUTTON_MILLIS = 86400000;

/** A last chance ends this long before the cap: one hour. */
const CAP_MARGIN_MILLIS = 3600000;

/** A last chance shorter than this is not offered: one hour. */
const MIN_LAST_CHANCE_MILLIS = 3600000;

/** Kinds withdrawn when their clock ends (`RequestKind.withdrawnAtEnd`): the stored kind names. */
const WITHDRAWN_AT_END = new Set(['proposal', 'split']);

/**
 * Whether a field is present: neither undefined nor null.
 * @param {*} value the field.
 * @return {boolean} true when present.
 */
function has(value) {
  return value !== undefined && value !== null;
}

/**
 * The moment the button stops being offered: a day after the deadline, or two hours before the cap
 * if that is sooner (`RequestClock.buttonUntilMillis`).
 *
 * @param {object} clock the clock, with the stored field names.
 * @return {number} epoch millis
 */
function buttonUntilMillis(clock) {
  const byTime = clock.answerByMillis + BUTTON_MILLIS;
  if (!has(clock.capMillis)) return byTime;
  return Math.min(byTime, clock.capMillis - CAP_MARGIN_MILLIS - MIN_LAST_CHANCE_MILLIS);
}

/**
 * What pressing the last-chance button at `nowMillis` would give, or null when the button is not
 * offered (`RequestClock.lastChanceWindowMillis`).
 *
 * @param {object} clock the clock, with the stored field names.
 * @param {number} nowMillis the moment to judge at, in epoch millis.
 * @return {number|null} milliseconds, or null when not offered
 */
function lastChanceWindowMillis(clock, nowMillis) {
  const unused = !has(clock.outcome) && !has(clock.lastChanceUntilMillis);
  const inButtonTime = nowMillis >= clock.answerByMillis && nowMillis < buttonUntilMillis(clock);
  if (!unused || !inButtonTime) return null;
  if (!has(clock.capMillis)) return LAST_CHANCE_MILLIS;
  return Math.min(LAST_CHANCE_MILLIS, clock.capMillis - CAP_MARGIN_MILLIS - nowMillis);
}

/**
 * The moment the request is over if nobody answers: where the last chance ends when one was given,
 * otherwise the later of the deadline and the button's end (`RequestClock.endMillis`).
 *
 * @param {object} clock the clock, with the stored field names.
 * @return {number} epoch millis
 */
function endMillis(clock) {
  if (has(clock.lastChanceUntilMillis)) return clock.lastChanceUntilMillis;
  return Math.max(clock.answerByMillis, buttonUntilMillis(clock));
}

/**
 * Where the request stands at `nowMillis`: `'OPEN'`, `'NO_ANSWER_LAST_CHANCE_POSSIBLE'`,
 * `'LAST_CHANCE'` or `'NO_ANSWER'` (`RequestClock.stateOf`; the first check that applies decides).
 *
 * @param {object} clock the clock, with the stored field names.
 * @param {number} nowMillis the moment to judge at, in epoch millis.
 * @return {string} the state name
 */
function stateOf(clock, nowMillis) {
  if (has(clock.outcome)) return 'NO_ANSWER';
  if (nowMillis < clock.answerByMillis) return 'OPEN';
  if (has(clock.lastChanceUntilMillis)) {
    return nowMillis < clock.lastChanceUntilMillis ? 'LAST_CHANCE' : 'NO_ANSWER';
  }
  if (lastChanceWindowMillis(clock, nowMillis) !== null) return 'NO_ANSWER_LAST_CHANCE_POSSIBLE';
  return 'NO_ANSWER';
}

/**
 * Whether a clock of this stored kind is withdrawn at its end: a custody proposal or a split.
 *
 * @param {string|undefined} kind the stored kind name.
 * @return {boolean} whether it is withdrawn at its end
 */
function withdrawnAtEnd(kind) {
  return WITHDRAWN_AT_END.has(kind);
}

/**
 * A Firestore `Timestamp` (anything with `toMillis()`) or a number, as epoch millis; null if neither.
 *
 * @param {number|object|undefined} value a number or something with `toMillis()`.
 * @return {number|null} epoch millis, or null
 */
function toMillis(value) {
  if (typeof value === 'number') return value;
  if (value && typeof value.toMillis === 'function') return value.toMillis();
  return null;
}

/**
 * When the sweep should next look at a clock: the table of the spec's §5.1, over a raw clock
 * document, or null for "no field" (the clock needs nothing more from the server).
 *
 * Every value is a fixed moment taken from the document, never "now", and the document alone
 * decides it, so no time is passed:
 * - `serverSettledAt` set, or `serverToldNoAnswerAt` set (the server closed it): null;
 * - `outcome` set (a phone closed it): `closedAt` when the kind is withdrawn at its end, so the
 *   server checks the withdrawal once; otherwise null;
 * - a last chance given: where it ends;
 * - the deadline push already sent: the end;
 * - otherwise the deadline, which covers an open clock and a deadline not yet told. A clock that
 *   is past its end and was never told is therefore due at its deadline, also in the past.
 *
 * @param {object} doc the clock document's data. `closedAt` may be epoch millis or an object with
 *   `toMillis()` (a Firestore `Timestamp`); a missing or unreadable one counts as absent.
 * @return {number|null}
 */
function nextStepAt(doc) {
  if (has(doc.serverSettledAt) || has(doc.serverToldNoAnswerAt)) return null;
  if (has(doc.outcome)) return withdrawnAtEnd(doc.kind) ? toMillis(doc.closedAt) : null;
  if (has(doc.lastChanceUntilMillis)) return doc.lastChanceUntilMillis;
  if (has(doc.serverToldDeadlineAt)) return endMillis(doc);
  return doc.answerByMillis;
}

module.exports = {
  LAST_CHANCE_MILLIS,
  BUTTON_MILLIS,
  CAP_MARGIN_MILLIS,
  MIN_LAST_CHANCE_MILLIS,
  stateOf,
  lastChanceWindowMillis,
  buttonUntilMillis,
  endMillis,
  nextStepAt,
  withdrawnAtEnd,
};
