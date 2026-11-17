/**
 * When the answering parent is reminded of a request that waits on their answer: a port of the
 * Kotlin `ReminderPlan` (`shared/.../domain/changerequests/ReminderPlan.kt`), for the server's own
 * plan (iPhone phase 4 part C, spec §7.3), made when no phone delivered the clock, in the zone the
 * answering parent's profile states (`users/{uid}.timeZone`).
 *
 * Nothing here reads the time: every moment is passed in, in epoch millis. A zone is an IANA name,
 * read through `Intl.DateTimeFormat`, so the wall clock and its daylight-saving changes are the
 * zone's own. Whether a moment is at night, and where a reminder moves to, is read off the zone's
 * calendar and converted back to an instant, never found by adding hours, as in Kotlin.
 *
 * **Kotlin is the authority.** `test/fixtures/reminder-plan-cases.json` is read by both
 * `test/reminder-plan.test.js` and the Kotlin `ReminderPlanFixtureTest`, so the two cannot drift:
 * change the arithmetic in both, and add a case to the fixture.
 */

const HOUR_MILLIS = 3600000;
const DAY_MILLIS = 24 * HOUR_MILLIS;

/** A window of this length or more gets three reminders: 24 hours. */
const FULL_PLAN_FROM_MILLIS = DAY_MILLIS;

/** A window of this length or more, and under a day, gets two: 6 hours. */
const SHORT_PLAN_FROM_MILLIS = 6 * HOUR_MILLIS;

/** Where the night begins on the answering parent's clock: 21:00. */
const QUIET_FROM_HOUR = 21;

/** Where the night ends, and where a reminder moved out of it goes: 08:00. */
const QUIET_UNTIL_HOUR = 8;

/** Where a reminder goes when the next 08:00 is at or after the deadline: 20:30 the evening before. */
const EVENING_BEFORE = {hour: 20, minute: 30};

/** One formatter per zone, made once: building one is the costly part. */
const formatters = new Map();

/**
 * The formatter that reads a moment's wall clock in [zone].
 * @param {string} zone an IANA name.
 * @return {!Intl.DateTimeFormat}
 */
function formatterFor(zone) {
  let formatter = formatters.get(zone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat('en-US', {
      timeZone: zone,
      hourCycle: 'h23',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    });
    formatters.set(zone, formatter);
  }
  return formatter;
}

/**
 * Whether [zone] is a time zone this runtime knows by its IANA name.
 * @param {*} zone the stored value.
 * @return {boolean}
 */
function isZone(zone) {
  if (typeof zone !== 'string' || zone.trim() === '') return false;
  try {
    formatterFor(zone);
    return true;
  } catch (e) {
    return false;
  }
}

/**
 * The wall clock of [millis] in [zone]: year, month (1-12), day, hour, minute and second.
 * @param {number} millis epoch millis.
 * @param {string} zone an IANA name.
 * @return {{year: number, month: number, day: number, hour: number, minute: number, second: number}}
 */
function wallClock(millis, zone) {
  const parts = {};
  for (const part of formatterFor(zone).formatToParts(new Date(millis))) {
    if (part.type !== 'literal') parts[part.type] = Number(part.value);
  }
  return {
    year: parts.year, month: parts.month, day: parts.day,
    hour: parts.hour, minute: parts.minute, second: parts.second,
  };
}

/**
 * The zone's offset from UTC at [millis], in millis (seconds' precision, as the formatter prints).
 * @param {number} millis epoch millis.
 * @param {string} zone an IANA name.
 * @return {number}
 */
function offsetAt(millis, zone) {
  const w = wallClock(millis, zone);
  const asUtc = Date.UTC(w.year, w.month - 1, w.day, w.hour, w.minute, w.second);
  return asUtc - (millis - (((millis % 1000) + 1000) % 1000));
}

/**
 * The instant of a wall clock in [zone], placed as java.time places it (`LocalDateTime.toInstant`,
 * which kotlinx-datetime calls on the JVM): in an overlap the earlier offset, in a gap the time
 * moved forward by the gap's length (the offset before the gap).
 *
 * @param {{year: number, month: number, day: number}} day the calendar day (month 1-12).
 * @param {number} hour the hour.
 * @param {number} minute the minute.
 * @param {string} zone an IANA name.
 * @return {number} epoch millis
 */
function atZone(day, hour, minute, zone) {
  const local = Date.UTC(day.year, day.month - 1, day.day, hour, minute);
  const before = offsetAt(local - DAY_MILLIS, zone);
  const after = offsetAt(local + DAY_MILLIS, zone);
  const valid = [before, after]
      .map((offset) => local - offset)
      .filter((instant) => local - offsetAt(instant, zone) === instant);
  if (valid.length > 0) return Math.min(...valid);
  // A gap: the wall clock does not exist, so it is read with the offset before the change.
  return local - before;
}

/**
 * The calendar day [days] after [day].
 * @param {{year: number, month: number, day: number}} day the day.
 * @param {number} days how many days to add; negative goes back.
 * @return {{year: number, month: number, day: number}}
 */
function plusDays(day, days) {
  const date = new Date(Date.UTC(day.year, day.month - 1, day.day + days));
  return {year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate()};
}

/**
 * Whether an hour of the day is in the quiet hours: from 21:00 through midnight up to 08:00.
 * @param {number} hour the hour, 0-23.
 * @return {boolean}
 */
function isNightHour(hour) {
  return hour >= QUIET_FROM_HOUR || hour < QUIET_UNTIL_HOUR;
}

/**
 * Whether [millis] falls in the quiet hours of [zone]'s clock (`ReminderPlan.isQuietAt`).
 * @param {number} millis epoch millis.
 * @param {string} zone an IANA name.
 * @return {boolean}
 */
function isQuietAt(millis, zone) {
  return isNightHour(wallClock(millis, zone).hour);
}

/**
 * The next 08:00 on [zone]'s calendar after [millis]: its own day's while still ahead, else the
 * next day's (`ReminderPlan.nextMorning`).
 *
 * @param {number} millis epoch millis.
 * @param {string} zone an IANA name.
 * @return {number} epoch millis
 */
function nextMorning(millis, zone) {
  const w = wallClock(millis, zone);
  const day = w.hour < QUIET_UNTIL_HOUR ? w : plusDays(w, 1);
  return atZone(day, QUIET_UNTIL_HOUR, 0, zone);
}

/**
 * How many reminders a window gets (`ReminderPlan.countFor`).
 * @param {number} windowMillis from the sending to the deadline.
 * @return {number}
 */
function countFor(windowMillis) {
  if (windowMillis >= FULL_PLAN_FROM_MILLIS) return 3;
  if (windowMillis >= SHORT_PLAN_FROM_MILLIS) return 2;
  if (windowMillis > 0) return 1;
  return 0;
}

/**
 * The reminders a window gets, where they fall before the night is considered. Integer division
 * truncates, as Kotlin's `Long` division does.
 *
 * @param {number} sentAtMillis the start of the window.
 * @param {number} answerByMillis the end of the window.
 * @return {!Array<number>}
 */
function placed(sentAtMillis, answerByMillis) {
  const window = answerByMillis - sentAtMillis;
  const midpoint = sentAtMillis + Math.trunc(window / 2);
  switch (countFor(window)) {
    case 3:
      return [
        sentAtMillis + Math.trunc(window / 3),
        sentAtMillis + Math.trunc(window * 2 / 3),
        answerByMillis - 2 * HOUR_MILLIS,
      ];
    case 2:
      return [midpoint, answerByMillis - HOUR_MILLIS];
    case 1:
      return [midpoint];
    default:
      return [];
  }
}

/**
 * [millis] where it is outside the night; otherwise the next 08:00, or 20:30 the evening before it
 * when that morning is at or after the deadline.
 *
 * @param {number} millis the reminder.
 * @param {number} answerByMillis the deadline.
 * @param {string} zone an IANA name.
 * @return {number}
 */
function outOfTheNight(millis, answerByMillis, zone) {
  if (!isQuietAt(millis, zone)) return millis;
  const morning = nextMorning(millis, zone);
  if (morning < answerByMillis) return morning;
  const eveningDay = plusDays(wallClock(morning, zone), -1);
  return atZone(eveningDay, EVENING_BEFORE.hour, EVENING_BEFORE.minute, zone);
}

/**
 * The times at which to remind the answering parent, in epoch millis, earliest first
 * (`ReminderPlan.plan`): placed over the window, moved out of the night of [zone], dropped at or
 * before the sending and before [notBeforeMillis], and merged where they landed on one time.
 *
 * @param {number} sentAtMillis when the request was sent.
 * @param {number} answerByMillis the deadline.
 * @param {number} notBeforeMillis the moment of planning; a reminder before it is not planned.
 * @param {string} zone the answering parent's IANA zone.
 * @return {!Array<number>}
 */
function plan(sentAtMillis, answerByMillis, notBeforeMillis, zone) {
  const times = placed(sentAtMillis, answerByMillis)
      .map((millis) => outOfTheNight(millis, answerByMillis, zone))
      .filter((millis) => millis > sentAtMillis && millis >= notBeforeMillis);
  return Array.from(new Set(times)).sort((a, b) => a - b);
}

module.exports = {
  plan,
  isQuietAt,
  nextMorning,
  isZone,
};
