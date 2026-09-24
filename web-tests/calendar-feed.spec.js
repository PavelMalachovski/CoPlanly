/**
 * The calendar feed (MON-17) as a calendar app meets it: every `.ics` below goes through
 * `support/ics-validate.js` — our octet-level reading of RFC 5545 plus Mozilla's `ical.js` —
 * rather than through our own expectations of what `calendar-feed.js` writes, which is what
 * `functions/test/calendar-feed.test.js` already checks.
 *
 * Two sources:
 *  - **`buildFeed` directly** (no emulator, always runs): a representative family in each of the
 *    five app languages — shared, overnight, open-ended, recurring and all-day custody events,
 *    contact windows, a seasonal layer and a swap, names in every script the app ships, titles
 *    needing escaping and folding (including 4-byte characters at the fold) — plus the events the
 *    feed must never serve.
 *  - **The real `calendarFeed` HTTPS function on the emulator**: a pair made through
 *    `acceptPairingInvitation`, events and a custody model seeded, a link minted by
 *    `createCalendarFeed`, and the served body fetched twice over HTTP.
 */
'use strict';

const {test, expect} = require('@playwright/test');
const feed = require('../functions/calendar-feed');
const {validateIcs} = require('./support/ics-validate');
const emu = require('./support/emulators');

const ALICE = 'uidAlice';
const BOB = 'uidBob';
const CAROL = 'uidCarol';
const FAMILY = [ALICE, BOB].sort().join('__');
const NOW = Date.parse('2026-09-23T10:00:00Z');
const DAY = 24 * 60 * 60 * 1000;

/** Parent names in the scripts and with the characters each app language brings. */
const NAMES = {
  en: {mom: 'Zoë O\'Brien-Smith', dad: 'Sam, Jr.; "Dad"'},
  cs: {mom: 'Zdeňka Čermáková', dad: 'Jiří Řezníček'},
  de: {mom: 'Jördis Müßig', dad: 'Björn Größer'},
  ru: {mom: 'Алёна Жукова', dad: 'Фёдор Щербаков'},
  uk: {mom: 'Ґалина Їжакевич', dad: 'Євген Ярошенко'},
};

/** Titles a real family writes, and ones built to break escaping and folding. */
const TITLES = {
  commas: 'Pick-up, school; then dentist \\ bring card',
  newlines: 'Swim practice\nbring towel\nand goggles',
  longCyrillic: 'Родительское собрание в школе — обсуждение расписания на следующую четверть, ' +
    'кружков, питания и поездки класса в музей; взять с собой документы, форму и сменную обувь',
  emoji: '🎂 Birthday party 🎈🎉 at grandma\'s — bring the 🎁 and the 📷 '.repeat(3).trim(),
  german: 'Elternabend: Übergabe am Bahnhof, Straße „Am Größten Weg“ Nr. 5',
  czech: 'Předání dětí u školy – nezapomenout přezůvky, svačinu a úkoly z češtiny',
  ukrainian: 'Зустріч із психологом; взяти щоденник, ґудзики й їжу',
};

/**
 * A stored event document, as `EventRepositoryImpl.toFirestoreMap()` writes one.
 *
 * @param {!Object} over Fields to set.
 * @return {!Object} The document.
 */
function event(over) {
  return Object.assign({
    familyId: FAMILY,
    createdByFirebaseUid: ALICE,
    sharedWith: [ALICE, BOB],
    isPrivate: false,
    isRecurring: false,
    description: '',
  }, over);
}

const EVENTS = [
  event({id: 'ev-commas', title: TITLES.commas, startDateTime: '2026-09-24T15:00:00',
    endDateTime: '2026-09-24T16:30:00', description: 'Room 12, 2nd floor; ask for Mrs. Nováková\nThanks!'}),
  event({id: 'ev-newlines', title: TITLES.newlines, startDateTime: '2026-09-25T17:00:00',
    endDateTime: '2026-09-25T18:00:00', createdByFirebaseUid: BOB}),
  event({id: 'ev-overnight', title: 'School trip (overnight)', startDateTime: '2026-10-02T22:00:00',
    endDateTime: '2026-10-03T07:30:00'}),
  event({id: 'ev-open', title: 'Call the lawyer', startDateTime: '2026-10-05T09:00:00', endDateTime: ''}),
  event({id: 'ev-cyr', title: TITLES.longCyrillic, startDateTime: '2026-10-06T18:00:00',
    endDateTime: '2026-10-06T19:30:00'}),
  event({id: 'ev-emoji', title: TITLES.emoji, startDateTime: '2026-10-10T14:00:00',
    endDateTime: '2026-10-10T17:00:00'}),
  event({id: 'ev-de', title: TITLES.german, startDateTime: '2026-10-11T08:00:00',
    endDateTime: '2026-10-11T08:30:00'}),
  event({id: 'ev-cs', title: TITLES.czech, startDateTime: '2026-10-12T07:45:00',
    endDateTime: '2026-10-12T08:00:00'}),
  event({id: 'ev-uk', title: TITLES.ukrainian, startDateTime: '2026-10-13T16:00:00.000',
    endDateTime: '2026-10-13T17:00:00.000'}),
  event({id: 'ev-weekly', title: 'Football training', startDateTime: '2026-09-01T16:00:00',
    endDateTime: '2026-09-01T17:30:00', isRecurring: true, recurrencePattern: 'weekly',
    recurrenceEndDate: '2026-12-15'}),
  event({id: 'ev-biweekly', title: 'Piano', startDateTime: '2026-09-02T15:00:00',
    endDateTime: '2026-09-02T15:45:00', isRecurring: true, recurrencePattern: 'biweekly'}),
  event({id: 'ev-daily', title: 'Medicine', startDateTime: '2026-09-20T08:00:00',
    endDateTime: '2026-09-20T08:05:00', isRecurring: true, recurrencePattern: 'daily',
    recurrenceEndDate: '2026-09-30'}),
  event({id: 'ev-monthly', title: 'Pocket money', startDateTime: '2026-09-01T12:00:00',
    endDateTime: '2026-09-01T12:15:00', isRecurring: true, recurrencePattern: 'monthly'}),
];

/** What must never be served: private, tombstoned, another family's, unreadable by the owner. */
const NEVER = [
  event({id: 'no-private', title: 'PRIVATE therapy', startDateTime: '2026-09-26T10:00:00',
    endDateTime: '2026-09-26T11:00:00', isPrivate: true}),
  event({id: 'no-tomb', title: 'DELETED concert', startDateTime: '2026-09-27T10:00:00',
    endDateTime: '2026-09-27T11:00:00', deletedAtMillis: NOW - DAY, deletedBy: ALICE}),
  event({id: 'no-other', title: 'OTHER family dinner', startDateTime: '2026-09-28T10:00:00',
    endDateTime: '2026-09-28T11:00:00', familyId: [ALICE, CAROL].sort().join('__'),
    sharedWith: [ALICE, CAROL]}),
  event({id: 'no-carol', title: 'CAROL wrote this', startDateTime: '2026-09-29T10:00:00',
    endDateTime: '2026-09-29T11:00:00', createdByFirebaseUid: CAROL, sharedWith: [CAROL, ALICE]}),
];

/**
 * The custody document: a fortnight, a contact window, a summer layer and an accepted swap —
 * one of each thing `custodyLines` turns into VEVENTs.
 */
const CUSTODY_DOC = {
  startDate: '2026-09-07',
  patternDays: 14,
  momDayIndices: [0, 1, 2, 3, 4, 5, 6],
  contactWindows: ['9|15:00|19:00|mom', '2|16:30|18:00|dad'],
  seasonalLayers: ['L1;summer;0;2027-07-01;2027-08-31;2027-07-01;14;0,1,2,3,4,5,6;;Summer%20holidays'],
  // 2026-10-05..11 is slot 1's week: the accepted swap cuts one day out of it, the pending one not.
  dayOverrides: {'2026-10-07': {status: 'ACCEPTED', toParent: 'dad'},
    '2026-10-08': {status: 'PENDING', toParent: 'dad'}},
};

/**
 * @param {string} locale An app language.
 * @param {number=} nowMillis Now.
 * @return {string} The feed.
 */
function build(locale, nowMillis = NOW) {
  return feed.buildFeed({
    familyId: FAMILY,
    members: [ALICE, BOB],
    ownerUid: ALICE,
    locale,
    nowMillis,
    custody: feed.parseCustodyModel(CUSTODY_DOC),
    names: NAMES[locale],
    events: EVENTS.concat(NEVER),
  });
}

/**
 * Checks everything a calendar app would, and returns the parsed events.
 *
 * @param {string} ics The document.
 * @return {!Array<!Object>} Its events.
 */
function expectValid(ics) {
  const {problems, events} = validateIcs(ics);
  expect(problems).toEqual([]);
  return events;
}

test.describe('buildFeed through an independent RFC 5545 parser', () => {
  for (const locale of Object.keys(NAMES)) {
    test(`a family's feed in ${locale} is valid iCalendar and says what it was given`, () => {
      const ics = build(locale);
      const events = expectValid(ics);
      const bySummary = new Map(events.map((e) => [e.summary, e]));

      // Every title comes back exactly, whatever it had to be escaped or folded for.
      for (const doc of EVENTS) {
        expect(bySummary.has(doc.title), `"${doc.title}" round-trips`).toBe(true);
        expect(bySummary.get(doc.title).uid).toBe(`${doc.id}@coplanly.app`);
      }
      const commas = bySummary.get(TITLES.commas);
      expect(commas.description).toBe('Room 12, 2nd floor; ask for Mrs. Nováková\nThanks!');

      // Folding was exercised, not merely allowed.
      expect(ics).toMatch(/\r\n /);

      // Nothing the feed must not serve.
      for (const doc of NEVER) expect(ics).not.toContain(doc.id);
      for (const e of events) expect(e.summary).not.toMatch(/PRIVATE|DELETED|OTHER|CAROL/);

      // Overnight: floating times, ends the next morning.
      const overnight = events.find((e) => e.uid === 'ev-overnight@coplanly.app');
      expect(overnight.start.toString()).toBe('2026-10-02T22:00:00');
      expect(overnight.end.toString()).toBe('2026-10-03T07:30:00');
      expect(overnight.start.zone).toBe(require('ical.js').Timezone.localTimezone);
      // No end: no DTEND, not a zero-length or negative one.
      expect(events.find((e) => e.uid === 'ev-open@coplanly.app').end).toBeNull();

      // Recurrence, as a client expands it.
      const weekly = events.find((e) => e.uid === 'ev-weekly@coplanly.app');
      expect(weekly.rrule.freq).toBe('WEEKLY');
      expect(weekly.rrule.until.toString()).toBe('2026-12-15T23:59:59');
      expect(events.find((e) => e.uid === 'ev-biweekly@coplanly.app').rrule.interval).toBe(2);

      // Custody: all-day runs naming the parent in this language, transparent, never "Mom"/"Dad".
      const custody = events.filter((e) => e.uid.startsWith('custody-'));
      expect(custody.length).toBeGreaterThan(20);
      for (const run of custody) {
        expect(run.start.isDate).toBe(true);
        expect(run.end.isDate).toBe(true);
        expect(run.transp).toBe('TRANSPARENT');
        expect([feed.label(locale, 'custody', NAMES[locale].mom),
          feed.label(locale, 'custody', NAMES[locale].dad)]).toContain(run.summary);
      }
      // The runs tile the window: each starts where the last ended, no gaps, no overlaps.
      const sorted = custody.slice().sort((a, b) => a.start.compare(b.start));
      for (let i = 1; i < sorted.length; i++) expect(sorted[i].start.compare(sorted[i - 1].end)).toBe(0);
      // The accepted swap is its own one-day run with the other parent; the pending one is not.
      const swap = custody.find((e) => e.start.toString() === '2026-10-07');
      expect(swap.end.toString()).toBe('2026-10-08');
      expect(swap.summary).toBe(feed.label(locale, 'custody', NAMES[locale].dad));
      const after = custody.find((e) => e.start.toString() === '2026-10-08');
      expect(after.end.toString()).toBe('2026-10-12');
      expect(after.summary).toBe(feed.label(locale, 'custody', NAMES[locale].mom));
      // Contact windows are timed, on the day of the parent who does not have it.
      const windows = events.filter((e) => e.uid.startsWith('contact-'));
      expect(windows.length).toBeGreaterThan(0);
      for (const w of windows) expect(w.start.isDate).toBe(false);
    });
  }

  test('with the parents not told apart, no custody layer and a description saying why', () => {
    const ics = feed.buildFeed({familyId: FAMILY, members: [ALICE, BOB], ownerUid: ALICE, locale: 'de',
      nowMillis: NOW, custody: feed.parseCustodyModel(CUSTODY_DOC), names: null, events: EVENTS});
    const {problems, events, calendar} = validateIcs(ics);
    expect(problems).toEqual([]);
    expect(events.some((e) => e.uid.startsWith('custody-'))).toBe(false);
    expect(calendar.getFirstPropertyValue('x-wr-caldesc')).toBe(
        `${feed.label('de', 'description')} ${feed.label('de', 'custodyUnknown')}`);
  });

  test('an empty family is still a valid calendar', () => {
    expectValid(feed.buildFeed({familyId: FAMILY, members: [ALICE, BOB], ownerUid: ALICE, locale: 'en',
      nowMillis: NOW, custody: null, names: null, events: []}));
  });

  test('UIDs are stable between two fetches — only DTSTAMP moves', () => {
    const morning = build('cs', NOW);
    const evening = build('cs', NOW + 8 * 60 * 60 * 1000);
    const uids = (ics) => validateIcs(ics).events.map((e) => e.uid);
    expect(uids(evening)).toEqual(uids(morning));
    const withoutStamp = (ics) => ics.replace(/^DTSTAMP:.*$/gm, '');
    expect(withoutStamp(evening)).toBe(withoutStamp(morning));
    // A day later the window moves, but an event keeps its UID — that is what stops a
    // subscribed calendar duplicating it.
    const tomorrow = validateIcs(build('cs', NOW + DAY)).events;
    for (const doc of EVENTS) expect(tomorrow.map((e) => e.uid)).toContain(`${doc.id}@coplanly.app`);
  });

  test('the validator is not a rubber stamp', () => {
    // Every rule it enforces, broken once — so a pass above means something.
    const good = build('en');
    const bytes = Buffer.from(good, 'utf8');
    // "Р" is two octets in UTF-8; a fold between them splits the character across two lines.
    const cyr = bytes.indexOf(Buffer.from('Родительское', 'utf8'));
    const broken = {
      'LF line endings': good.replace(/\r\n/g, '\n'),
      'an unfolded long line': good.replace(/\r\n /g, ''),
      'a fold inside a character': Buffer.concat(
          [bytes.subarray(0, cyr + 1), Buffer.from('\r\n '), bytes.subarray(cyr + 1)]),
      'a bare comma in a SUMMARY': good.replace('SUMMARY:Piano', 'SUMMARY:Piano, grade 2'),
      'a stray backslash in a SUMMARY': good.replace('SUMMARY:Piano', 'SUMMARY:Piano \\q'),
      'a missing DTSTAMP': good.replace(/DTSTAMP:[^\r]*\r\n/, ''),
      'a DTSTAMP not in UTC': good.replace(/(DTSTAMP:\d{8}T\d{6})Z/, '$1'),
      'DTEND before DTSTART': good.replace('DTEND:20260924T163000', 'DTEND:20260924T140000'),
      'a date DTEND on a timed DTSTART': good.replace('DTEND:20260924T163000', 'DTEND;VALUE=DATE:20260925'),
      'a UTC UNTIL on a floating DTSTART': good.replace('UNTIL=20261215T235959', 'UNTIL=20261215T235959Z'),
      'a duplicate UID': good.replace('UID:ev-open@coplanly.app', 'UID:ev-commas@coplanly.app'),
      'no VERSION': good.replace('VERSION:2.0\r\n', ''),
      'an unclosed VEVENT': good.replace('END:VEVENT\r\n', ''),
    };
    for (const [what, ics] of Object.entries(broken)) {
      expect(Buffer.from(ics).equals(bytes), `${what} changed nothing`).toBe(false);
      expect(validateIcs(Buffer.isBuffer(ics) ? ics : Buffer.from(ics, 'utf8')).problems.length, what)
          .toBeGreaterThan(0);
    }
  });
});

test.describe('the real calendarFeed function on the emulator', () => {
  test.beforeEach(() => emu.requireEmulators(test));

  test('a link minted by createCalendarFeed serves a valid, stable, private-free feed', async () => {
    const {a, b, familyId} = await emu.pairedParents('Ганна Їжак', 'Jiří Řezníček');
    const doc = (id, over) => Object.assign({
      id, familyId, createdByFirebaseUid: a.uid, sharedWith: [a.uid, b.uid], isPrivate: false,
      isRecurring: false, description: '', startDateTime: '2026-10-01T15:00:00',
      endDateTime: '2026-10-01T16:00:00',
    }, over);
    const today = new Date().toISOString().slice(0, 10);
    const inWindow = (days) => new Date(Date.now() + days * DAY).toISOString().slice(0, 10);
    const seeded = [
      doc('web-commas', {title: TITLES.commas, startDateTime: `${inWindow(1)}T15:00:00`,
        endDateTime: `${inWindow(1)}T16:30:00`}),
      doc('web-cyr', {title: TITLES.longCyrillic, startDateTime: `${inWindow(2)}T18:00:00`,
        endDateTime: `${inWindow(2)}T19:00:00`, createdByFirebaseUid: b.uid}),
      doc('web-overnight', {title: 'Overnight at grandma\'s', startDateTime: `${inWindow(3)}T20:00:00`,
        endDateTime: `${inWindow(4)}T09:00:00`}),
      doc('web-weekly', {title: 'Swimming', startDateTime: `${today}T17:00:00`,
        endDateTime: `${today}T18:00:00`, isRecurring: true, recurrencePattern: 'weekly'}),
      doc('web-private', {title: 'PRIVATE note', startDateTime: `${inWindow(5)}T10:00:00`,
        endDateTime: `${inWindow(5)}T11:00:00`, isPrivate: true}),
      doc('web-tomb', {title: 'DELETED plan', startDateTime: `${inWindow(6)}T10:00:00`,
        endDateTime: `${inWindow(6)}T11:00:00`, deletedAtMillis: Date.now(), deletedBy: a.uid}),
    ];
    for (const e of seeded) await emu.seed(`events/${e.id}`, e);
    await emu.seed(`custody_models/${familyId}`, Object.assign({}, CUSTODY_DOC,
        {startDate: today, participants: [a.uid, b.uid]}));

    const created = await emu.callable('createCalendarFeed', {familyId, locale: 'uk'}, a.token);
    // The URL names production; the test only takes the token from it and asks the emulator.
    const token = /\/calendarFeed\/([A-Za-z0-9_-]{43})\.ics$/.exec(created.url)[1];
    expect(created.webcalUrl.startsWith('webcal:')).toBe(true);
    const url = `${await emu.functionsBase()}/calendarFeed/${token}.ics`;

    const fetchFeed = async () => {
      const response = await fetch(url);
      expect(response.status).toBe(200);
      expect(response.headers.get('content-type')).toBe('text/calendar; charset=utf-8');
      return Buffer.from(await response.arrayBuffer());
    };
    const first = await fetchFeed();
    const second = await fetchFeed();

    const one = validateIcs(first);
    expect(one.problems).toEqual([]);
    const two = validateIcs(second);
    expect(two.problems).toEqual([]);
    expect(two.events.map((e) => e.uid)).toEqual(one.events.map((e) => e.uid));

    const summaries = one.events.map((e) => e.summary);
    for (const e of seeded.slice(0, 4)) expect(summaries).toContain(e.title);
    expect(first.toString('utf8')).not.toMatch(/PRIVATE|DELETED|web-private|web-tomb/);
    // Named, in the feed's language, and never a slot word.
    const custody = one.events.filter((e) => e.uid.startsWith('custody-'));
    expect(custody.length).toBeGreaterThan(0);
    for (const run of custody) {
      expect([feed.label('uk', 'custody', a.name), feed.label('uk', 'custody', b.name)]).toContain(run.summary);
    }
    // (The slot words may appear inside a UID, which no calendar shows; never in text it does.)
    for (const e of one.events) expect(`${e.summary} ${e.description || ''}`).not.toMatch(/\b(mom|dad)\b/i);

    // Revoked, it is gone at once — the same 404 as a token that never existed.
    await emu.callable('revokeCalendarFeed', {feedId: created.feedId}, a.token);
    expect((await fetch(url)).status).toBe(404);
  });
});
