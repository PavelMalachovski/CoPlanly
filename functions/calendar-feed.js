/**
 * The pure half of the calendar feed (MON-17): tokens, the custody port, and the iCalendar text.
 *
 * Nothing in this file touches Firestore or the network. `index.js` owns the callables and the
 * HTTPS handler, reads the documents, and hands them to the functions here; keeping the two
 * apart is what lets the RFC 5545 output and the custody arithmetic be tested against fixtures
 * without a fake database.
 *
 * **The custody port is deliberately small.** The Android app answers "whose day is this" in
 * `CustodyResolver.custodyFor` and `CustodyModel.getCustodyFor`, and "which afternoons does it
 * carry" in `CustodyResolver.contactWindowsResolver` and `ContactWindowCodec`. What is ported is
 * exactly that and nothing else: an accepted one-off swap first, then the highest-priority
 * seasonal layer covering the date (MON-14, `SeasonalLayerCodec` strings, ordered by
 * `SeasonalLayer.PRECEDENCE`), then the whole-day pattern
 * (`floorMod(days since startDate, patternDays)` in `momDayIndices` is slot 1), and contact
 * windows decoded from their wire strings, dropped when they name the parent who already has the
 * day. A pending or declined swap moves nothing, a pending proposal is not the schedule, and the
 * legacy per-parent schedule never reached Firestore, so none of those is read. If this file and
 * the Kotlin ever disagree, the Kotlin is right and `test/calendar-feed.test.js` is where the
 * fixture goes.
 */

const crypto = require('crypto');

/** Where feed records live. Keyed by the SHA-256 of the token; no client may read or write it. */
const FEED_COLLECTION = 'calendar_feeds';

/** A feed nobody has fetched for this long is deleted rather than served. */
const FEED_IDLE_EXPIRY_DAYS = 90;

/** How far back the feed reaches, in days, so last month's handovers are still there. */
const WINDOW_BACK_DAYS = 30;

/** How far ahead the feed reaches, in days — a school year and a summer. */
const WINDOW_AHEAD_DAYS = 365;

/** How long a rendered feed is reused for the same token, per function instance. */
const CACHE_TTL_MS = 15 * 60 * 1000;

/** Requests one token may make per [RATE_WINDOW_MS] before it is answered 429. */
const RATE_LIMIT = 30;

/** The rate-limit window. Calendar clients poll hourly; thirty in ten minutes is a loop. */
const RATE_WINDOW_MS = 10 * 60 * 1000;

/** `lastUsedAtMillis` is refreshed at most this often, so a poll is not a write. */
const LAST_USED_WRITE_INTERVAL_MS = 24 * 60 * 60 * 1000;

/** Live feeds one parent may hold. Each is a separate subscriber; a hundred is a leak. */
const MAX_FEEDS_PER_OWNER = 10;

/** Bytes of randomness in a token: 256 bits, comfortably above the ≥128 MON-17 requires. */
const TOKEN_BYTES = 32;

/** What a token looks like on the wire: 32 bytes in unpadded base64url. */
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * A new feed token. Returned to the caller once and never stored — only [feedTokenHash] is.
 *
 * @return {string} 43 characters of base64url.
 */
function newFeedToken() {
  return crypto.randomBytes(TOKEN_BYTES).toString('base64url');
}

/**
 * The document id a token is stored under.
 *
 * A plain SHA-256 and not a keyed or slow hash: the token already carries 256 random bits, so
 * there is nothing to brute-force, and the digest exists so that reading the collection — a
 * backup, an export, an operator in the console — yields nothing that fetches a feed.
 *
 * @param {string} token The token from the URL.
 * @return {string} Hex digest.
 */
function feedTokenHash(token) {
  return crypto.createHash('sha256').update(String(token)).digest('hex');
}

/**
 * The token a request path names, or null.
 *
 * Accepts the path with or without the function's own name in front (`/<token>.ics` is what an
 * `onRequest` handler sees; a Hosting rewrite would pass `/calendarFeed/<token>.ics`).
 *
 * @param {*} path The request path.
 * @return {?string} The token, or null when the path is not a feed URL.
 */
function tokenFromPath(path) {
  if (typeof path !== 'string') return null;
  const match = /(?:^|\/)([^/]+)\.ics$/.exec(path);
  if (!match || !TOKEN_PATTERN.test(match[1])) return null;
  return match[1];
}

// ── Dates ──────────────────────────────────────────────────────────────────────────────────

/**
 * An ISO date's day number (days since 1970-01-01), or NaN when it is not a real date.
 *
 * @param {*} iso `YYYY-MM-DD`.
 * @return {number} The day number.
 */
function dayNumber(iso) {
  const match = typeof iso === 'string' ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso) : null;
  if (!match) return NaN;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const millis = Date.UTC(year, month - 1, day);
  const back = new Date(millis);
  // Date.UTC rolls 2026-02-30 over into March; a date that does not round-trip is not one.
  if (back.getUTCMonth() !== month - 1 || back.getUTCDate() !== day) return NaN;
  return Math.round(millis / DAY_MS);
}

/**
 * The ISO date for a day number.
 *
 * @param {number} day Days since 1970-01-01.
 * @return {string} `YYYY-MM-DD`.
 */
function isoOfDay(day) {
  return new Date(day * DAY_MS).toISOString().slice(0, 10);
}

/**
 * `a mod n` that is never negative, as Kotlin's `Math.floorMod`.
 *
 * @param {number} a Dividend.
 * @param {number} n Divisor, positive.
 * @return {number} In `0 until n`.
 */
function floorMod(a, n) {
  return ((a % n) + n) % n;
}

// ── Custody ────────────────────────────────────────────────────────────────────────────────

const SLOT_ONE = 'mom';
const SLOT_TWO = 'dad';

/**
 * One contact-window wire string decoded, or null — the port of `ContactWindowCodec.decode`.
 *
 * `"<dayIndex>|<HH:mm>|<HH:mm>|<slot>"`. An entry this cannot read is dropped, never repaired:
 * guessing a slot or a time would put a child with the wrong parent on the calendar.
 *
 * @param {*} value The stored string.
 * @return {?{dayIndex: number, start: string, end: string, parent: string}} The window.
 */
function decodeContactWindow(value) {
  if (typeof value !== 'string') return null;
  const parts = value.split('|');
  if (parts.length !== 4) return null;
  const [index, start, end, parent] = parts;
  if (!/^\+?\d+$/.test(index)) return null;
  const dayIndex = Number(index);
  if (!Number.isSafeInteger(dayIndex)) return null;
  const time = /^([01]\d|2[0-3]):([0-5]\d)$/;
  if (!time.test(start) || !time.test(end) || start >= end) return null;
  if (parent !== SLOT_ONE && parent !== SLOT_TWO) return null;
  return {dayIndex, start, end, parent};
}

/** Longest range, and longest cycle, a seasonal layer may have — `SeasonalLayer.MAX_LAYER_DAYS`. */
const MAX_LAYER_DAYS = 366;

/** How many layers are read before the rest count as unreadable — `SeasonalLayerCodec.MAX_LAYERS`. */
const MAX_LAYERS = 32;

/**
 * One seasonal-layer wire string decoded, or null — the port of `SeasonalLayerCodec.decode`.
 *
 * `L1;<id>;<priority>;<from>;<to>;<anchor>;<patternDays>;<slot-1 days>;<windows>;<name>`. An
 * entry this cannot read — another version, a field that does not validate — is ignored here,
 * exactly as the app ignores it for resolving a date (it keeps it only to write it back, which
 * the feed never does). The name is not needed and is not decoded.
 *
 * @param {*} value The stored string.
 * @return {?{id: string, priority: number, from: number, to: number, start: number,
 *   patternDays: number, momDays: !Set<number>, windows: !Array<!Object>}} The layer.
 */
function decodeSeasonalLayer(value) {
  if (typeof value !== 'string') return null;
  const parts = value.split(';');
  if (parts.length !== 10 || parts[0] !== 'L1') return null;
  const [, id, priorityText, fromIso, toIso, anchorIso, cycleText, daysText, windowsText, name] = parts;
  if (!/^[A-Za-z0-9-]{1,64}$/.test(id)) return null;
  // The name is not shown, but an entry the app cannot read must not be read here either.
  if (!/^(?:[A-Za-z0-9\-_.~]|%[0-9A-Fa-f]{2})*$/.test(name)) return null;
  let decodedName;
  try {
    decodedName = decodeURIComponent(name);
  } catch (e) {
    return null;
  }
  if (decodedName.length > 80) return null;
  if (!/^-?\d+$/.test(priorityText) || !/^\d+$/.test(cycleText)) return null;
  const priority = Number(priorityText);
  const patternDays = Number(cycleText);
  const [from, to, start] = [dayNumber(fromIso), dayNumber(toIso), dayNumber(anchorIso)];
  if ([from, to, start].some(Number.isNaN) || to < from || to - from + 1 > MAX_LAYER_DAYS) return null;
  if (patternDays < 1 || patternDays > MAX_LAYER_DAYS || priority < -1000 || priority > 1000) return null;
  const dayTexts = daysText === '' ? [] : daysText.split(',');
  if (dayTexts.some((d) => !/^\d+$/.test(d) || Number(d) >= patternDays)) return null;
  const windows = (windowsText === '' ? [] : windowsText.split(',')).map(decodeContactWindow);
  if (windows.some((w) => w === null || w.dayIndex >= patternDays)) return null;
  return {id, priority, from, to, start, patternDays, momDays: new Set(dayTexts.map(Number)), windows};
}

/**
 * The layer that decides [day], or null — the port of `CustodyModel.layerOn` and
 * `SeasonalLayer.PRECEDENCE`: highest priority, then the later start, then the id.
 *
 * @param {!Object} model From [parseCustodyModel].
 * @param {number} day Day number.
 * @return {?Object} The layer.
 */
function layerOn(model, day) {
  let best = null;
  (model.layers || []).forEach((layer) => {
    if (day < layer.from || day > layer.to) return;
    if (best === null || layer.priority > best.priority ||
        (layer.priority === best.priority && (layer.from > best.from ||
          (layer.from === best.from && layer.id < best.id)))) {
      best = layer;
    }
  });
  return best;
}

/**
 * The parts of a `custody_models` document the feed needs, or null when it describes no pattern.
 *
 * Mirrors `FirestoreCustodyDataSource.toSharedCustody`: without a parseable `startDate` and a
 * `patternDays` there is no schedule, and a half-parsed one would assign the wrong days. Only
 * `ACCEPTED` overrides are kept (see the file KDoc).
 *
 * @param {?Object} doc The document's data.
 * @return {?{start: number, patternDays: number, momDays: !Set<number>,
 *   swaps: !Object<string, string>, windows: !Array<!Object>, layers: !Array<!Object>}} The model.
 */
function parseCustodyModel(doc) {
  if (!doc) return null;
  const start = dayNumber(doc.startDate);
  const patternDays = typeof doc.patternDays === 'number' ? Math.trunc(doc.patternDays) : NaN;
  if (Number.isNaN(start) || !(patternDays > 0)) return null;

  const momDays = new Set((Array.isArray(doc.momDayIndices) ? doc.momDayIndices : [])
      .filter((i) => typeof i === 'number')
      .map((i) => Math.trunc(i)));

  const swaps = {};
  const overrides = doc.dayOverrides && typeof doc.dayOverrides === 'object' ? doc.dayOverrides : {};
  Object.keys(overrides).forEach((iso) => {
    const entry = overrides[iso] || {};
    if (entry.status === 'ACCEPTED' && (entry.toParent === SLOT_ONE || entry.toParent === SLOT_TWO) &&
        !Number.isNaN(dayNumber(iso))) {
      swaps[iso] = entry.toParent;
    }
  });

  const windows = (Array.isArray(doc.contactWindows) ? doc.contactWindows : [])
      .map(decodeContactWindow)
      .filter((w) => w !== null);

  // `SeasonalLayerCodec.decodeAll`: distinct, sorted, readable ones up to MAX_LAYERS.
  const layerStrings = [...new Set((Array.isArray(doc.seasonalLayers) ? doc.seasonalLayers : [])
      .filter((v) => typeof v === 'string'))].sort();
  const layers = layerStrings.map(decodeSeasonalLayer).filter((l) => l !== null).slice(0, MAX_LAYERS);

  return {start, patternDays, momDays, swaps, windows, layers};
}

/**
 * Whose day [day] is — the port of `CustodyResolver.custodyFor` over a model.
 *
 * @param {!Object} model From [parseCustodyModel].
 * @param {number} day Day number.
 * @return {string} `'mom'` or `'dad'`.
 */
function custodyOn(model, day) {
  const swap = model.swaps[isoOfDay(day)];
  if (swap) return swap;
  const layer = layerOn(model, day);
  if (layer) return layer.momDays.has(floorMod(day - layer.start, layer.patternDays)) ? SLOT_ONE : SLOT_TWO;
  return model.momDays.has(floorMod(day - model.start, model.patternDays)) ? SLOT_ONE : SLOT_TWO;
}

/**
 * The contact windows worth showing on [day], earliest first — the port of
 * `CustodyResolver.contactWindowsResolver`: a window naming the parent who already has the day
 * (by the pattern or by an accepted swap) is dropped.
 *
 * @param {!Object} model From [parseCustodyModel].
 * @param {number} day Day number.
 * @return {!Array<!Object>} The windows.
 */
function contactWindowsOn(model, day) {
  // Inside a layer the layer's own windows answer, in its own cycle (`CustodyModel.contactWindowsOn`).
  const layer = layerOn(model, day);
  const source = layer ? layer : model;
  if (source.windows.length === 0) return [];
  const index = floorMod(day - source.start, source.patternDays);
  const owner = custodyOn(model, day);
  return source.windows
      .filter((w) => w.dayIndex === index && w.parent !== owner)
      .sort((a, b) => (a.start < b.start ? -1 : a.start > b.start ? 1 : 0));
}

/**
 * Consecutive days with the same parent, as `[start, endExclusive)` runs of day numbers.
 *
 * One all-day event per run rather than per day: Apple Calendar draws a run as one bar, and a
 * week-on-week-off family would otherwise see seven identical entries a week.
 *
 * @param {!Object} model From [parseCustodyModel].
 * @param {number} from First day, inclusive.
 * @param {number} to Last day, exclusive.
 * @return {!Array<{slot: string, start: number, end: number}>} The runs.
 */
function custodyRuns(model, from, to) {
  const runs = [];
  for (let day = from; day < to; day++) {
    const slot = custodyOn(model, day);
    const last = runs[runs.length - 1];
    if (last && last.slot === slot && last.end === day) {
      last.end = day + 1;
    } else {
      runs.push({slot, start: day, end: day + 1});
    }
  }
  return runs;
}

// ── iCalendar text ─────────────────────────────────────────────────────────────────────────

/**
 * A TEXT value escaped per RFC 5545 §3.3.11: backslash, semicolon and comma escaped, every
 * newline shape written as `\n`, and other control characters (which TEXT forbids) removed.
 *
 * @param {*} value The text.
 * @return {string} The escaped value.
 */
function escapeText(value) {
  return String(value === null || value === undefined ? '' : value)
      .replace(/\\/g, '\\\\')
      .replace(/;/g, '\\;')
      .replace(/,/g, '\\,')
      .replace(/\r\n|\r|\n/g, '\\n')
      // eslint-disable-next-line no-control-regex
      .replace(/[\u0000-\u0008\u000B-\u001F\u007F]/g, '');
}

/**
 * One content line folded at 75 octets (RFC 5545 §3.1), never inside a UTF-8 sequence.
 *
 * A continuation line starts with a single space, which counts toward its own 75.
 *
 * @param {string} line An unfolded content line.
 * @return {string} The folded line, CRLF between the parts, no trailing CRLF.
 */
function foldLine(line) {
  const parts = [];
  let current = '';
  let currentBytes = 0;
  let limit = 75;
  for (const char of line) {
    const bytes = Buffer.byteLength(char, 'utf8');
    if (currentBytes + bytes > limit) {
      parts.push(current);
      current = ' ';
      currentBytes = 1;
      limit = 75;
    }
    current += char;
    currentBytes += bytes;
  }
  parts.push(current);
  return parts.join('\r\n');
}

/**
 * A day number as an iCalendar DATE.
 *
 * @param {number} day Day number.
 * @return {string} `YYYYMMDD`.
 */
function icsDate(day) {
  return isoOfDay(day).replace(/-/g, '');
}

/**
 * A stored naive date-time as a *floating* iCalendar DATE-TIME, or null.
 *
 * Floating (no `Z`, no `TZID`) on purpose: events are stored as naive local date-times
 * (CLAUDE.md item 13 keeps them so), and a floating time is exactly that — 15:00 wherever the
 * reader is, which is what the app itself shows.
 *
 * @param {*} value `YYYY-MM-DDTHH:mm[:ss[.fff]]`, as `ISO_LOCAL_DATE_TIME` writes.
 * @return {?{day: number, text: string}} The day number and the DATE-TIME text.
 */
function icsLocalDateTime(value) {
  const match = typeof value === 'string' ?
    /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/.exec(value) : null;
  if (!match) return null;
  const day = dayNumber(match[1]);
  if (Number.isNaN(day) || Number(match[2]) > 23 || Number(match[3]) > 59) return null;
  const seconds = match[4] || '00';
  return {day, text: `${match[1].replace(/-/g, '')}T${match[2]}${match[3]}${seconds}`};
}

/**
 * Epoch millis as a UTC DATE-TIME, for DTSTAMP.
 *
 * @param {number} millis Epoch millis.
 * @return {string} `YYYYMMDDTHHMMSSZ`.
 */
function icsUtc(millis) {
  return new Date(millis).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

/** The app's recurrence patterns as RRULEs. Anything else is emitted as its first occurrence. */
const RRULES = {
  daily: 'FREQ=DAILY',
  weekly: 'FREQ=WEEKLY',
  biweekly: 'FREQ=WEEKLY;INTERVAL=2',
  monthly: 'FREQ=MONTHLY',
};

// ── Localised labels ───────────────────────────────────────────────────────────────────────

/**
 * The few words the feed writes itself, in the five app languages. Taken from the app's own
 * `calendar_strings.xml` wording where one exists, so the iPhone and the app read alike.
 */
const LABELS = {
  en: {
    custody: 'With {name}',
    contact: 'Contact with {name}',
    parent: 'Parent',
    description: 'Read-only copy of your CoPlanly calendar. Changes are made in the CoPlanly app.',
    custodyUnknown: 'Custody days are not shown: the two parents are not told apart yet. ' +
      'Open CoPlanly on both phones to finish pairing.',
  },
  cs: {
    custody: 'V péči: {name}',
    contact: 'Styk s: {name}',
    parent: 'Rodič',
    description: 'Kopie kalendáře CoPlanly jen pro čtení. Změny se dělají v aplikaci CoPlanly.',
    custodyUnknown: 'Dny péče se nezobrazují: rodiče zatím nejsou rozlišeni. ' +
      'Otevřete CoPlanly na obou telefonech a dokončete propojení.',
  },
  de: {
    custody: 'Bei: {name}',
    contact: 'Umgang bei {name}',
    parent: 'Elternteil',
    description: 'Schreibgeschützte Kopie deines CoPlanly-Kalenders. Änderungen nimmst du in der CoPlanly-App vor.',
    custodyUnknown: 'Betreuungstage werden nicht angezeigt: Die beiden Eltern sind noch nicht ' +
      'unterschieden. Öffne CoPlanly auf beiden Handys, um die Verknüpfung abzuschließen.',
  },
  ru: {
    custody: 'С кем: {name}',
    contact: 'Общение: {name}',
    parent: 'Родитель',
    description: 'Копия календаря CoPlanly только для чтения. Изменения вносятся в приложении CoPlanly.',
    custodyUnknown: 'Дни опеки не показаны: родители пока не различены. ' +
      'Откройте CoPlanly на обоих телефонах, чтобы завершить связывание.',
  },
  uk: {
    custody: 'З ким: {name}',
    contact: 'Спілкування: {name}',
    parent: 'Один із батьків',
    description: 'Копія календаря CoPlanly лише для читання. Зміни вносяться в застосунку CoPlanly.',
    custodyUnknown: 'Дні опіки не показано: батьків ще не розрізнено. ' +
      'Відкрийте CoPlanly на обох телефонах, щоб завершити зв’язування.',
  },
};

/**
 * One of the five app languages for a stored or requested tag; English otherwise.
 *
 * @param {*} tag A language tag such as `cs` or `de-AT`.
 * @return {string} `en`, `cs`, `de`, `ru` or `uk`.
 */
function feedLocale(tag) {
  const language = typeof tag === 'string' ? tag.toLowerCase().split(/[-_]/)[0] : '';
  return Object.prototype.hasOwnProperty.call(LABELS, language) ? language : 'en';
}

/**
 * A label with `{name}` filled in.
 *
 * @param {string} locale From [feedLocale].
 * @param {string} key A key of [LABELS].
 * @param {string=} name The name to insert.
 * @return {string} The label.
 */
function label(locale, key, name) {
  return LABELS[feedLocale(locale)][key].replace('{name}', name || '');
}

// ── Building the feed ──────────────────────────────────────────────────────────────────────

/**
 * A short, stable, non-reversible tag for a family, for custody UIDs. Custody entries have no
 * record id of their own, and two families' feeds must not mint the same UID.
 *
 * @param {string} familyId The family.
 * @return {string} 16 hex characters.
 */
function familyTag(familyId) {
  return feedTokenHash(`family:${familyId}`).slice(0, 16);
}

/**
 * The VEVENT lines for one stored event, or `[]` when it must not or cannot be served.
 *
 * **Never served:** a private event (item 3 — one should never reach Firestore, and this is the
 * second lock), a tombstoned one (item 14), and anything not stamped with this family's id or
 * not created by one of its two parents (the M-6 rule for calendar friends, for the same
 * reason: a feed names one family). Also refused: an event the feed's owner could not read in
 * the app — neither its creator nor in its `sharedWith`.
 *
 * @param {!Object} doc The event document.
 * @param {!{familyId: string, members: !Array<string>, ownerUid: string,
 *   windowStart: number, windowEnd: number, stamp: string}} ctx Context.
 * @return {!Array<string>} Unfolded content lines.
 */
function eventLines(doc, ctx) {
  if (!doc || doc.isPrivate === true) return [];
  if (doc.deletedAtMillis !== undefined && doc.deletedAtMillis !== null) return [];
  if (doc.familyId !== ctx.familyId || !ctx.members.includes(doc.createdByFirebaseUid)) return [];
  const audience = Array.isArray(doc.sharedWith) ? doc.sharedWith : [];
  if (doc.createdByFirebaseUid !== ctx.ownerUid && !audience.includes(ctx.ownerUid)) return [];
  if (typeof doc.id !== 'string' || !doc.id) return [];

  const start = icsLocalDateTime(doc.startDateTime);
  if (!start) return [];
  const end = icsLocalDateTime(doc.endDateTime);
  const lastDay = end && end.text >= start.text ? end.day : start.day;

  const rrule = doc.isRecurring === true ? RRULES[doc.recurrencePattern] : undefined;
  const until = rrule ? dayNumber(doc.recurrenceEndDate) : NaN;
  if (rrule) {
    if (start.day > ctx.windowEnd || (!Number.isNaN(until) && until < ctx.windowStart)) return [];
  } else if (start.day > ctx.windowEnd || lastDay < ctx.windowStart) {
    return [];
  }

  const lines = [
    'BEGIN:VEVENT',
    `UID:${doc.id}@coplanly.app`,
    `DTSTAMP:${ctx.stamp}`,
    `DTSTART:${start.text}`,
  ];
  if (end && end.text > start.text) lines.push(`DTEND:${end.text}`);
  if (rrule) {
    // DTSTART is floating, so UNTIL must be floating too (RFC 5545 §3.3.10).
    lines.push(Number.isNaN(until) ? `RRULE:${rrule}` : `RRULE:${rrule};UNTIL=${icsDate(until)}T235959`);
  }
  lines.push(`SUMMARY:${escapeText(doc.title)}`);
  if (typeof doc.description === 'string' && doc.description.trim()) {
    lines.push(`DESCRIPTION:${escapeText(doc.description)}`);
  }
  lines.push('TRANSP:OPAQUE', 'END:VEVENT');
  return lines;
}

/**
 * The VEVENT lines for the custody layer: one all-day event per run of days with one parent,
 * and one timed event per contact window.
 *
 * @param {!Object} model From [parseCustodyModel].
 * @param {!{familyId: string, names: !Object<string, string>, locale: string,
 *   windowStart: number, windowEnd: number, stamp: string}} ctx Context; `names` maps a slot to
 *   the display name of the parent holding it.
 * @return {!Array<string>} Unfolded content lines.
 */
function custodyLines(model, ctx) {
  const tag = familyTag(ctx.familyId);
  const lines = [];
  custodyRuns(model, ctx.windowStart, ctx.windowEnd + 1).forEach((run) => {
    lines.push(
        'BEGIN:VEVENT',
        `UID:custody-${tag}-${isoOfDay(run.start)}@coplanly.app`,
        `DTSTAMP:${ctx.stamp}`,
        `DTSTART;VALUE=DATE:${icsDate(run.start)}`,
        // All-day DTEND is exclusive: a run of the 1st to the 7th ends on the 8th.
        `DTEND;VALUE=DATE:${icsDate(run.end)}`,
        `SUMMARY:${escapeText(label(ctx.locale, 'custody', ctx.names[run.slot]))}`,
        // Transparent: a custody day is not a meeting, and must not mark the reader busy.
        'TRANSP:TRANSPARENT',
        'END:VEVENT');
  });
  for (let day = ctx.windowStart; day <= ctx.windowEnd; day++) {
    contactWindowsOn(model, day).forEach((w) => {
      const date = icsDate(day);
      const from = w.start.replace(':', '');
      const to = w.end.replace(':', '');
      lines.push(
          'BEGIN:VEVENT',
          `UID:contact-${tag}-${date}-${from}-${w.parent}@coplanly.app`,
          `DTSTAMP:${ctx.stamp}`,
          `DTSTART:${date}T${from}00`,
          `DTEND:${date}T${to}00`,
          `SUMMARY:${escapeText(label(ctx.locale, 'contact', ctx.names[w.parent]))}`,
          'TRANSP:TRANSPARENT',
          'END:VEVENT');
    });
  }
  return lines;
}

/**
 * The whole feed as `text/calendar`: CRLF line endings, every line folded.
 *
 * @param {!{familyId: string, members: !Array<string>, ownerUid: string, locale: string,
 *   nowMillis: number, custody: ?Object, names: ?Object<string, string>,
 *   events: !Array<!Object>}} input `custody` is a parsed model or null; `names` maps slot to
 *   name, or is null when the two parents cannot be told apart (the custody layer is then
 *   omitted and the calendar says why).
 * @return {string} The iCalendar document.
 */
function buildFeed(input) {
  const today = Math.floor(input.nowMillis / DAY_MS);
  const ctx = {
    familyId: input.familyId,
    members: input.members,
    ownerUid: input.ownerUid,
    locale: feedLocale(input.locale),
    names: input.names || {},
    windowStart: today - WINDOW_BACK_DAYS,
    windowEnd: today + WINDOW_AHEAD_DAYS,
    stamp: icsUtc(input.nowMillis),
  };

  const description = input.custody && !input.names ?
    `${label(ctx.locale, 'description')} ${label(ctx.locale, 'custodyUnknown')}` :
    label(ctx.locale, 'description');

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CoPlanly//Calendar feed//EN',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'X-WR-CALNAME:CoPlanly',
    `X-WR-CALDESC:${escapeText(description)}`,
    'REFRESH-INTERVAL;VALUE=DURATION:PT1H',
    'X-PUBLISHED-TTL:PT1H',
  ];
  if (input.custody && input.names) {
    lines.push(...custodyLines(input.custody, ctx));
  }
  input.events
      .slice()
      .sort((a, b) => String(a.startDateTime).localeCompare(String(b.startDateTime)))
      .forEach((doc) => lines.push(...eventLines(doc, ctx)));
  lines.push('END:VCALENDAR');
  return lines.map(foldLine).join('\r\n') + '\r\n';
}

// ── Per-instance cache and rate limit ──────────────────────────────────────────────────────

/**
 * A small TTL map. Per function instance, which is the point: it spares Firestore the fan-out
 * of a render when a calendar client re-polls, without being a store anyone must invalidate —
 * the feed record itself is still read on every request, so a revoked link stops at once.
 *
 * @param {number} ttlMs Entry lifetime.
 * @param {number=} maxEntries Oldest entries are dropped past this.
 * @return {{get: function(string, number): *, set: function(string, *, number): void,
 *   delete: function(string): void}} The cache.
 */
function ttlCache(ttlMs, maxEntries) {
  const cap = maxEntries || 500;
  const entries = new Map();
  return {
    get(key, now) {
      const entry = entries.get(key);
      if (!entry) return undefined;
      if (entry.expires <= now) {
        entries.delete(key);
        return undefined;
      }
      return entry.value;
    },
    set(key, value, now) {
      entries.delete(key);
      entries.set(key, {value, expires: now + ttlMs});
      while (entries.size > cap) entries.delete(entries.keys().next().value);
    },
    delete(key) {
      entries.delete(key);
    },
  };
}

/**
 * A fixed-window counter per key: `allow(key, now)` is false once a key has spent [limit]
 * requests in the current window. Per instance, like [ttlCache] — a brake on a runaway client,
 * not a quota.
 *
 * @param {number} limit Requests per window.
 * @param {number} windowMs Window length.
 * @return {{allow: function(string, number): boolean}} The limiter.
 */
function rateLimiter(limit, windowMs) {
  const windows = new Map();
  return {
    allow(key, now) {
      const current = windows.get(key);
      if (!current || current.until <= now) {
        windows.set(key, {until: now + windowMs, count: 1});
        if (windows.size > 5000) {
          for (const [k, v] of windows) if (v.until <= now) windows.delete(k);
        }
        return true;
      }
      current.count += 1;
      return current.count <= limit;
    },
  };
}

module.exports = {
  FEED_COLLECTION,
  FEED_IDLE_EXPIRY_DAYS,
  WINDOW_BACK_DAYS,
  WINDOW_AHEAD_DAYS,
  CACHE_TTL_MS,
  RATE_LIMIT,
  RATE_WINDOW_MS,
  LAST_USED_WRITE_INTERVAL_MS,
  MAX_FEEDS_PER_OWNER,
  DAY_MS,
  newFeedToken,
  feedTokenHash,
  tokenFromPath,
  dayNumber,
  isoOfDay,
  decodeContactWindow,
  decodeSeasonalLayer,
  layerOn,
  parseCustodyModel,
  custodyOn,
  contactWindowsOn,
  custodyRuns,
  escapeText,
  foldLine,
  icsLocalDateTime,
  feedLocale,
  label,
  buildFeed,
  ttlCache,
  rateLimiter,
};
