const test = require('firebase-functions-test')();
const assert = require('assert');

const feed = require('../calendar-feed');
const {
  createCalendarFeedImpl,
  listCalendarFeedsImpl,
  revokeCalendarFeedImpl,
  serveCalendarFeedImpl,
  sweepIdleCalendarFeedsImpl,
  calendarFeedNames,
} = require('../index');

/**
 * The calendar feed (MON-17): the custody port against fixtures the Kotlin answers, the RFC 5545
 * text, and the four callables' security properties over an in-memory Firestore.
 */

const ALICE = 'uidAlice';
const BOB = 'uidBob';
const CAROL = 'uidCarol';
const FAMILY = [ALICE, BOB].sort().join('__');
const OTHER_FAMILY = [ALICE, CAROL].sort().join('__');
const NOW = Date.parse('2026-09-23T10:00:00Z');
const DAY = 24 * 60 * 60 * 1000;
const TOKEN = 'A'.repeat(43);

/**
 * In-memory Firestore with documents keyed by collection then id. Supports what the feed code
 * uses: doc get/set/update/delete, `==`, `<` and `array-contains` queries (chained), batches, and
 * a per-collection read counter so the cache can be observed.
 *
 * @param {!Object<string, !Object<string, !Object>>} seed Documents.
 * @return {!Object} The fake.
 */
function fakeDb(seed) {
  const docs = JSON.parse(JSON.stringify(seed));
  const reads = {};
  const bucket = (name) => (docs[name] = docs[name] || {});
  const count = (name) => {
    reads[name] = (reads[name] || 0) + 1;
  };
  const ref = (name, id) => ({
    _name: name,
    id,
    async get() {
      count(name);
      const data = bucket(name)[id];
      return {id, exists: data !== undefined, data: () => data, ref: ref(name, id)};
    },
    async set(value) {
      bucket(name)[id] = JSON.parse(JSON.stringify(value));
    },
    async update(value) {
      bucket(name)[id] = Object.assign({}, bucket(name)[id], value);
    },
    async delete() {
      delete bucket(name)[id];
    },
  });
  const test = (doc, [field, op, value]) => {
    const actual = doc[field];
    if (op === '==') return actual === value;
    if (op === '<') return typeof actual === 'number' && actual < value;
    if (op === 'array-contains') return Array.isArray(actual) && actual.includes(value);
    throw new Error(`unsupported ${op}`);
  };
  const query = (name, predicates) => ({
    where: (f, o, v) => query(name, predicates.concat([[f, o, v]])),
    async get() {
      count(name);
      const found = Object.keys(bucket(name))
          .filter((id) => predicates.every((p) => test(bucket(name)[id], p)))
          .map((id) => ({id, exists: true, data: () => bucket(name)[id], ref: ref(name, id)}));
      return {docs: found, size: found.length};
    },
  });
  return {
    _docs: docs,
    _reads: reads,
    collection: (name) => ({
      doc: (id) => ref(name, id),
      where: (f, o, v) => query(name, [[f, o, v]]),
    }),
    batch() {
      const ops = [];
      return {
        delete: (r) => ops.push(r),
        async commit() {
          for (const r of ops) await r.delete();
        },
      };
    },
  };
}

/**
 * Two live parents, a family document with separated slots, and names.
 *
 * @return {!Object} Seed documents for [fakeDb].
 */
function pairedUsers() {
  return {
    users: {
      [ALICE]: {name: 'Alice', role: 'mom', partnerIds: [BOB], partnerId: BOB},
      [BOB]: {name: 'Bob', role: 'dad', partnerIds: [ALICE], partnerId: ALICE},
      [CAROL]: {name: 'Carol', role: 'dad', partnerIds: [ALICE]},
    },
    families: {[FAMILY]: {members: [ALICE, BOB], slots: {[ALICE]: 'mom', [BOB]: 'dad'}}},
  };
}

/** Week on, week off from Monday 2026-09-07, Alice (slot 1) first. */
const WEEK_ON_WEEK_OFF = {
  startDate: '2026-09-07',
  patternDays: 14,
  momDayIndices: [0, 1, 2, 3, 4, 5, 6],
};

/**
 * An event document as `EventRepositoryImpl.toFirestoreMap` writes one.
 *
 * @param {!Object=} overrides Fields to change.
 * @return {!Object} The document.
 */
function event(overrides) {
  return Object.assign({
    id: 'ev-1',
    title: 'Dentist',
    description: '',
    startDateTime: '2026-09-25T15:00:00',
    endDateTime: '2026-09-25T16:00:00',
    parentOwner: 'mom',
    isRecurring: false,
    recurrencePattern: '',
    recurrenceEndDate: '',
    createdByFirebaseUid: ALICE,
    sharedWith: [ALICE, BOB],
    familyId: FAMILY,
  }, overrides);
}

describe('calendar feed: tokens', () => {
  it('mints 256-bit base64url tokens and stores a 64-hex SHA-256', () => {
    const token = feed.newFeedToken();
    assert.match(token, /^[A-Za-z0-9_-]{43}$/);
    assert.notStrictEqual(token, feed.newFeedToken());
    assert.match(feed.feedTokenHash(token), /^[0-9a-f]{64}$/);
  });

  it('reads the token from either path shape and nothing else', () => {
    assert.strictEqual(feed.tokenFromPath(`/${TOKEN}.ics`), TOKEN);
    assert.strictEqual(feed.tokenFromPath(`/calendarFeed/${TOKEN}.ics`), TOKEN);
    assert.strictEqual(feed.tokenFromPath(`/${TOKEN}`), null);
    assert.strictEqual(feed.tokenFromPath('/short.ics'), null);
    assert.strictEqual(feed.tokenFromPath(`/${TOKEN.slice(1)}!.ics`), null);
    assert.strictEqual(feed.tokenFromPath(undefined), null);
  });
});

describe('calendar feed: the custody port', () => {
  const day = (iso) => feed.dayNumber(iso);

  it('decodes contact windows as ContactWindowCodec does, dropping what it cannot read', () => {
    assert.deepStrictEqual(feed.decodeContactWindow('9|15:00|19:00|dad'),
        {dayIndex: 9, start: '15:00', end: '19:00', parent: 'dad'});
    ['9|15:00|19:00', '-1|15:00|19:00|dad', '9|19:00|15:00|dad', '9|15:00|15:00|mom',
      '9|9:00|19:00|dad', '9|15:00|24:00|dad', '9|15:00|19:00|father', 'x|15:00|19:00|dad', 7]
        .forEach((bad) => assert.strictEqual(feed.decodeContactWindow(bad), null, String(bad)));
  });

  it('assigns week on, week off from the start date, before it too', () => {
    const model = feed.parseCustodyModel(WEEK_ON_WEEK_OFF);
    assert.strictEqual(feed.custodyOn(model, day('2026-09-07')), 'mom');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-13')), 'mom');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-14')), 'dad');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-21')), 'mom');
    // floorMod(-7, 14) = 7: the week before the start is slot 2's.
    assert.strictEqual(feed.custodyOn(model, day('2026-08-31')), 'dad');
  });

  it('matches the every-other-weekend preset (fortnight from a Monday, weekend 5–6)', () => {
    const resident = [0, 1, 2, 3, 4, 7, 8, 9, 10, 11, 12, 13];
    const model = feed.parseCustodyModel(
        {startDate: '2026-09-07', patternDays: 14, momDayIndices: resident});
    assert.strictEqual(feed.custodyOn(model, day('2026-09-12')), 'dad');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-13')), 'dad');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-19')), 'mom');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-26')), 'dad');
  });

  it('moves a day only for an ACCEPTED swap', () => {
    const model = feed.parseCustodyModel(Object.assign({}, WEEK_ON_WEEK_OFF, {
      dayOverrides: {
        '2026-09-08': {toParent: 'dad', requestedBy: BOB, status: 'ACCEPTED'},
        '2026-09-09': {toParent: 'dad', requestedBy: BOB, status: 'PENDING'},
        '2026-09-10': {toParent: 'dad', requestedBy: BOB, status: 'DECLINED'},
      },
    }));
    assert.strictEqual(feed.custodyOn(model, day('2026-09-08')), 'dad');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-09')), 'mom');
    assert.strictEqual(feed.custodyOn(model, day('2026-09-10')), 'mom');
  });

  it('shows a contact window only when it names the parent who does not have the day', () => {
    const model = feed.parseCustodyModel(Object.assign({}, WEEK_ON_WEEK_OFF, {
      contactWindows: ['2|15:00|19:00|dad', '3|10:00|12:00|mom', 'garbage'],
      dayOverrides: {'2026-09-23': {toParent: 'dad', requestedBy: BOB, status: 'ACCEPTED'}},
    }));
    // Index 2 is Wednesday 2026-09-09, a slot-1 day: Bob's afternoon shows.
    assert.deepStrictEqual(feed.contactWindowsOn(model, day('2026-09-09')).map((w) => w.parent), ['dad']);
    // Index 3 names slot 1 on slot 1's own day: nothing new, dropped.
    assert.deepStrictEqual(feed.contactWindowsOn(model, day('2026-09-10')), []);
    // Index 2 again a fortnight later, but a swap already gave Bob the day.
    assert.deepStrictEqual(feed.contactWindowsOn(model, day('2026-09-23')), []);
  });

  it('refuses a document without a pattern rather than guessing one', () => {
    assert.strictEqual(feed.parseCustodyModel({patternDays: 14}), null);
    assert.strictEqual(feed.parseCustodyModel({startDate: '2026-02-30', patternDays: 14}), null);
    assert.strictEqual(feed.parseCustodyModel({startDate: '2026-09-07', patternDays: 0}), null);
  });

  it('merges consecutive days with one parent into a run', () => {
    const model = feed.parseCustodyModel(WEEK_ON_WEEK_OFF);
    const runs = feed.custodyRuns(model, day('2026-09-07'), day('2026-09-28'));
    assert.deepStrictEqual(runs.map((r) => [r.slot, feed.isoOfDay(r.start), feed.isoOfDay(r.end)]), [
      ['mom', '2026-09-07', '2026-09-14'],
      ['dad', '2026-09-14', '2026-09-21'],
      ['mom', '2026-09-21', '2026-09-28'],
    ]);
  });
});

describe('calendar feed: RFC 5545 text', () => {
  it('escapes backslash, semicolon, comma and every newline shape', () => {
    assert.strictEqual(feed.escapeText('a\\b;c,d\ne\r\nf\rg'), 'a\\\\b\\;c\\,d\\ne\\nf\\ng');
    assert.strictEqual(feed.escapeText('bell\u0007tab\t'), 'belltab\t');
  });

  it('folds at 75 octets without splitting a UTF-8 character', () => {
    const line = 'SUMMARY:' + 'Příliš žluťoučký kůň úpěl ďábelské ódy – '.repeat(6);
    const folded = feed.foldLine(line);
    folded.split('\r\n').forEach((part, i) => {
      assert.ok(Buffer.byteLength(part, 'utf8') <= 75, `line ${i} is too long`);
      if (i > 0) assert.strictEqual(part[0], ' ');
    });
    assert.strictEqual(folded.replace(/\r\n /g, ''), line);
  });

  it('reads ISO_LOCAL_DATE_TIME in both of its shapes as floating time', () => {
    assert.strictEqual(feed.icsLocalDateTime('2026-09-25T15:00:00').text, '20260925T150000');
    assert.strictEqual(feed.icsLocalDateTime('2026-09-25T15:00:00.123').text, '20260925T150000');
    assert.strictEqual(feed.icsLocalDateTime('2026-09-25T15:00').text, '20260925T150000');
    assert.strictEqual(feed.icsLocalDateTime(''), null);
  });

  /**
   * A whole feed for Alice's family.
   *
   * @param {!Array<!Object>} events Event documents.
   * @param {?Object=} names Slot names; defaults to Alice/Bob.
   * @return {string} The iCalendar body.
   */
  function build(events, names) {
    return feed.buildFeed({
      familyId: FAMILY,
      members: [ALICE, BOB],
      ownerUid: ALICE,
      locale: 'en',
      nowMillis: NOW,
      custody: feed.parseCustodyModel(Object.assign({}, WEEK_ON_WEEK_OFF, {
        contactWindows: ['2|15:00|19:00|dad'],
      })),
      names: names === undefined ? {mom: 'Alice', dad: 'Bob'} : names,
      events,
    });
  }

  /**
   * The VEVENT blocks of a body, unfolded.
   *
   * @param {string} body An iCalendar body.
   * @return {!Array<string>} One string per VEVENT.
   */
  function vevents(body) {
    return body.replace(/\r\n /g, '').split('BEGIN:VEVENT').slice(1);
  }

  it('uses CRLF everywhere and wraps everything in one VCALENDAR', () => {
    const body = build([event()]);
    assert.ok(body.startsWith('BEGIN:VCALENDAR\r\nVERSION:2.0\r\n'));
    assert.ok(body.endsWith('END:VCALENDAR\r\n'));
    assert.ok(!/[^\r]\n/.test(body), 'a bare LF');
    body.split('\r\n').forEach((line) => assert.ok(Buffer.byteLength(line) <= 75));
  });

  it('names parents by name, never by slot', () => {
    const body = build([]);
    assert.ok(body.includes('SUMMARY:With Alice'));
    assert.ok(body.includes('SUMMARY:With Bob'));
    assert.ok(body.includes('SUMMARY:Contact with Bob'));
    assert.ok(!/\b(Mom|Dad|mom|dad)\b/.test(body.replace(/UID:[^\r]*/g, '')));
  });

  it('writes custody runs as all-day events with an exclusive DTEND', () => {
    const run = vevents(build([])).find((v) => v.includes('DTSTART;VALUE=DATE:20260921'));
    assert.ok(run.includes('DTEND;VALUE=DATE:20260928'));
    assert.ok(run.includes('SUMMARY:With Alice'));
    assert.ok(run.includes('TRANSP:TRANSPARENT'));
    const window = vevents(build([])).find((v) => v.includes('DTSTART:20260923T150000'));
    assert.ok(window.includes('DTEND:20260923T190000'));
  });

  it('keeps UIDs stable between two renders', () => {
    const uids = (body) => body.split('\r\n').filter((l) => l.startsWith('UID:'));
    assert.deepStrictEqual(uids(build([event()])), uids(build([event()])));
  });

  it('never serves a private, tombstoned, foreign or unreadable event', () => {
    const body = build([
      event({id: 'private', isPrivate: true}),
      event({id: 'tombstoned', deletedAtMillis: 1, deletedBy: ALICE}),
      event({id: 'other-family', familyId: OTHER_FAMILY, createdByFirebaseUid: ALICE}),
      event({id: 'unstamped', familyId: ''}),
      event({id: 'stranger', createdByFirebaseUid: CAROL}),
      event({id: 'not-shared', createdByFirebaseUid: BOB, sharedWith: [BOB]}),
      event({id: 'ok', title: 'School play; bring snacks, please'}),
    ]);
    ['private', 'tombstoned', 'other-family', 'unstamped', 'stranger', 'not-shared']
        .forEach((id) => assert.ok(!body.includes(`UID:${id}@`), id));
    assert.ok(body.includes('UID:ok@coplanly.app'));
    assert.ok(body.includes('SUMMARY:School play\\; bring snacks\\, please'));
  });

  it('leaves out events wholly outside the window', () => {
    const body = build([
      event({id: 'old', startDateTime: '2025-01-01T10:00:00', endDateTime: '2025-01-01T11:00:00'}),
      event({id: 'far', startDateTime: '2028-01-01T10:00:00', endDateTime: '2028-01-01T11:00:00'}),
    ]);
    assert.ok(!body.includes('UID:old@'));
    assert.ok(!body.includes('UID:far@'));
  });

  it('maps the app recurrence patterns to RRULE, with a floating UNTIL', () => {
    const body = build([
      event({id: 'weekly', isRecurring: true, recurrencePattern: 'weekly',
        startDateTime: '2026-01-05T17:00:00', endDateTime: '2026-01-05T18:00:00',
        recurrenceEndDate: '2026-12-20'}),
      event({id: 'biweekly', isRecurring: true, recurrencePattern: 'biweekly'}),
      event({id: 'ended', isRecurring: true, recurrencePattern: 'daily',
        startDateTime: '2025-01-01T08:00:00', endDateTime: '', recurrenceEndDate: '2025-02-01'}),
      event({id: 'unknown', isRecurring: true, recurrencePattern: 'yearly'}),
    ]);
    const weekly = vevents(body).find((v) => v.includes('UID:weekly@'));
    assert.ok(weekly.includes('RRULE:FREQ=WEEKLY;UNTIL=20261220T235959'));
    assert.ok(weekly.includes('DTSTART:20260105T170000'));
    assert.ok(vevents(body).find((v) => v.includes('UID:biweekly@')).includes('RRULE:FREQ=WEEKLY;INTERVAL=2'));
    assert.ok(!body.includes('UID:ended@'));
    const unknown = vevents(body).find((v) => v.includes('UID:unknown@'));
    assert.ok(!unknown.includes('RRULE'), 'an unknown pattern is its first occurrence, as in the app');
  });

  it('omits the custody layer, and says why, when the parents share a slot', () => {
    const body = build([], null);
    assert.ok(!body.includes('SUMMARY:With'));
    assert.ok(body.replace(/\r\n /g, '').includes('Custody days are not shown'));
  });

  it('writes its own words in the feed language', () => {
    const body = feed.buildFeed({
      familyId: FAMILY, members: [ALICE, BOB], ownerUid: ALICE, locale: 'cs-CZ', nowMillis: NOW,
      custody: feed.parseCustodyModel(WEEK_ON_WEEK_OFF), names: {mom: 'Alice', dad: 'Bob'}, events: [],
    });
    assert.ok(body.includes('SUMMARY:V péči: Alice'));
    assert.strictEqual(feed.feedLocale('pt'), 'en');
  });
});

describe('calendar feed: names', () => {
  const live = {members: [ALICE, BOB], profiles: {[ALICE]: {name: ' ', role: 'mom'}, [BOB]: {name: 'Bob'}}};

  it('takes the slot from the family document and falls back to a neutral word', () => {
    const names = calendarFeedNames({slots: {[ALICE]: 'dad', [BOB]: 'mom'}}, live, 'de');
    assert.deepStrictEqual(names, {dad: 'Elternteil', mom: 'Bob'});
  });

  it('refuses to name days when both parents read one slot', () => {
    assert.strictEqual(calendarFeedNames(null, live, 'en'), null);
  });
});

describe('calendar feed: callables and serving', () => {
  const noLimit = {allow: () => true};

  it('refuses a caller who is not live in the family', async () => {
    const db = fakeDb(pairedUsers());
    await assert.rejects(createCalendarFeedImpl(db, CAROL, FAMILY, 'en', NOW, TOKEN),
        (err) => err.code === 'permission-denied');
    // Carol names Alice, but Alice does not name Carol back.
    await assert.rejects(createCalendarFeedImpl(db, CAROL, OTHER_FAMILY, 'en', NOW, TOKEN),
        (err) => err.code === 'permission-denied');
    assert.deepStrictEqual(db._docs.calendar_feeds || {}, {});
  });

  it('stores the hash and never the token, and returns the token once in the URL', async () => {
    const db = fakeDb(pairedUsers());
    const created = await createCalendarFeedImpl(db, ALICE, FAMILY, 'de-AT', NOW, TOKEN);
    assert.ok(created.url.endsWith(`/${TOKEN}.ics`));
    assert.ok(created.webcalUrl.startsWith('webcal://'));
    const stored = db._docs.calendar_feeds;
    assert.deepStrictEqual(Object.keys(stored), [feed.feedTokenHash(TOKEN)]);
    const record = stored[feed.feedTokenHash(TOKEN)];
    assert.ok(!JSON.stringify(record).includes(TOKEN));
    assert.strictEqual(record.locale, 'de');
    assert.deepStrictEqual(record.familyMembers, [ALICE, BOB].sort());

    const listed = await listCalendarFeedsImpl(db, ALICE, NOW);
    assert.strictEqual(listed.feeds.length, 1);
    assert.ok(!JSON.stringify(listed).includes(TOKEN));
    assert.ok(!JSON.stringify(listed).includes(feed.feedTokenHash(TOKEN)));
    assert.deepStrictEqual((await listCalendarFeedsImpl(db, BOB, NOW)).feeds, []);
  });

  it('serves the family calendar, then 404s once the owner revokes it', async () => {
    const seed = pairedUsers();
    seed.custody_models = {[FAMILY]: WEEK_ON_WEEK_OFF};
    seed.events = {'ev-1': event(), 'ev-private': event({id: 'ev-private', isPrivate: true})};
    const db = fakeDb(seed);
    const {feedId} = await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN);

    const served = await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(60000), noLimit);
    assert.strictEqual(served.status, 200);
    assert.ok(served.body.includes('UID:ev-1@coplanly.app'));
    assert.ok(!served.body.includes('ev-private'));
    assert.ok(served.body.includes('SUMMARY:With Alice'));

    assert.deepStrictEqual(await revokeCalendarFeedImpl(db, BOB, feedId), {revoked: 0});
    assert.deepStrictEqual(await revokeCalendarFeedImpl(db, ALICE, feedId), {revoked: 1});
    const after = await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(60000), noLimit);
    assert.strictEqual(after.status, 404);
  });

  it('answers an unknown token 404 with one read and nothing else', async () => {
    const db = fakeDb(pairedUsers());
    const result = await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(60000), noLimit);
    assert.strictEqual(result.status, 404);
    assert.deepStrictEqual(db._reads, {calendar_feeds: 1});
    assert.strictEqual((await serveCalendarFeedImpl(db, null, NOW, feed.ttlCache(1), noLimit)).status, 404);
  });

  it('reuses a render from the cache, but still reads the record every time', async () => {
    const seed = pairedUsers();
    seed.events = {'ev-1': event()};
    const db = fakeDb(seed);
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN);
    const cache = feed.ttlCache(60000);
    await serveCalendarFeedImpl(db, TOKEN, NOW, cache, noLimit);
    await serveCalendarFeedImpl(db, TOKEN, NOW + 1000, cache, noLimit);
    assert.strictEqual(db._reads.events, 1);
    assert.strictEqual(db._reads.calendar_feeds, 3); // the create's count query, then two gets
  });

  it('expires a link unused for 90 days, and deletes it', async () => {
    const db = fakeDb(pairedUsers());
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN);
    const later = NOW + 91 * DAY;
    const result = await serveCalendarFeedImpl(db, TOKEN, later, feed.ttlCache(60000), noLimit);
    assert.strictEqual(result.status, 404);
    assert.deepStrictEqual(db._docs.calendar_feeds, {});
  });

  it('refreshes lastUsedAtMillis at most daily', async () => {
    const db = fakeDb(pairedUsers());
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN);
    const record = () => db._docs.calendar_feeds[feed.feedTokenHash(TOKEN)];
    await serveCalendarFeedImpl(db, TOKEN, NOW + 60000, feed.ttlCache(1), noLimit);
    assert.strictEqual(record().lastUsedAtMillis, NOW);
    await serveCalendarFeedImpl(db, TOKEN, NOW + 2 * DAY, feed.ttlCache(1), noLimit);
    assert.strictEqual(record().lastUsedAtMillis, NOW + 2 * DAY);
  });

  it('stops serving, and deletes the link, once the family has ended', async () => {
    const db = fakeDb(pairedUsers());
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN);
    db._docs.users[BOB].partnerIds = [];
    db._docs.users[BOB].partnerId = '';
    const result = await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(60000), noLimit);
    assert.strictEqual(result.status, 404);
    assert.deepStrictEqual(db._docs.calendar_feeds, {});
  });

  it('rate-limits one token before touching Firestore', async () => {
    const db = fakeDb(pairedUsers());
    const limiter = feed.rateLimiter(2, 60000);
    await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(1), limiter);
    await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(1), limiter);
    const third = await serveCalendarFeedImpl(db, TOKEN, NOW, feed.ttlCache(1), limiter);
    assert.strictEqual(third.status, 429);
    assert.strictEqual(db._reads.calendar_feeds, 2);
    assert.ok(limiter.allow(TOKEN, NOW + 60001), 'the window resets');
  });

  it('caps the live links one parent may hold', async () => {
    const db = fakeDb(pairedUsers());
    for (let i = 0; i < feed.MAX_FEEDS_PER_OWNER; i++) {
      await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, `${i}`.padStart(43, 'x'));
    }
    await assert.rejects(createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, TOKEN),
        (err) => err.code === 'resource-exhausted');
  });

  it('sweeps only idle links', async () => {
    const db = fakeDb(pairedUsers());
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW - 100 * DAY, TOKEN);
    await createCalendarFeedImpl(db, ALICE, FAMILY, 'en', NOW, 'B'.repeat(43));
    assert.strictEqual(await sweepIdleCalendarFeedsImpl(db, NOW), 1);
    assert.deepStrictEqual(Object.keys(db._docs.calendar_feeds), [feed.feedTokenHash('B'.repeat(43))]);
  });
});

after(() => test.cleanup());
