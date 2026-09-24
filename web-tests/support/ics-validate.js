/**
 * A strict RFC 5545 check of one iCalendar document, as a calendar app would meet it.
 *
 * Two independent halves, because each catches what the other lets through:
 *  - **The wire, octet by octet** — our own reading of RFC 5545 §3.1 (content lines, CRLF,
 *    folding at 75 octets never inside a UTF-8 sequence, the content-line grammar) and §3.3.11
 *    (TEXT escaping). Parsers are lenient here on purpose, so a lenient parser alone would pass a
 *    feed a stricter client (Outlook, some CalDAV servers) rejects.
 *  - **The meaning**, through Mozilla's `ical.js` — the parser Thunderbird's lineage uses, written
 *    independently of `functions/calendar-feed.js`: it must parse, and every VEVENT must carry
 *    UID, DTSTAMP and DTSTART, a DTEND after its DTSTART and of the same value type, a DTSTAMP in
 *    UTC, a readable RRULE whose UNTIL matches DTSTART's type (§3.3.10), and a unique UID.
 *
 * `validateIcs` returns the problems as sentences rather than throwing at the first, so a failing
 * test lists everything wrong with a feed at once.
 */
'use strict';

const ICAL = require('ical.js');

/** RFC 5545 §3.1 — name, parameters, value. Parameter values may be quoted. */
const CONTENT_LINE =
  /^([A-Za-z0-9-]+)((?:;[A-Za-z0-9-]+=(?:"[^"\x00-\x1F]*"|[^";:,\x00-\x1F]*)(?:,(?:"[^"\x00-\x1F]*"|[^";:,\x00-\x1F]*))*)*):(.*)$/;

/** Properties whose value type is TEXT in what the feed writes (RFC 5545 §3.8.1, RFC 7986). */
const TEXT_PROPERTIES = new Set(['SUMMARY', 'DESCRIPTION', 'LOCATION', 'COMMENT', 'X-WR-CALNAME',
  'X-WR-CALDESC']);

const utf8 = new TextDecoder('utf-8', {fatal: true});

// ical.js types a property it has no definition for as UNKNOWN and leaves its value escaped. RFC
// 7986 and the de-facto X-WR-* extensions the feed writes are TEXT (RFC 5545 §3.8.8.2 gives an
// X- property TEXT unless it says otherwise), which is how Apple Calendar and Outlook read them.
for (const name of ['x-wr-calname', 'x-wr-caldesc']) {
  ICAL.design.icalendar.property[name] = {defaultType: 'text'};
}

/**
 * Splits bytes at CRLF, reporting any bare CR or LF.
 *
 * @param {!Buffer} bytes The document.
 * @param {!Array<string>} problems Collects what is wrong.
 * @return {!Array<!Buffer>} The physical lines, without their CRLF.
 */
function physicalLines(bytes, problems) {
  const lines = [];
  let start = 0;
  for (let i = 0; i < bytes.length; i++) {
    if (bytes[i] === 0x0d) {
      if (bytes[i + 1] !== 0x0a) problems.push(`bare CR at byte ${i}`);
      else {
        lines.push(bytes.subarray(start, i));
        start = i + 2;
        i++;
      }
    } else if (bytes[i] === 0x0a) {
      problems.push(`bare LF at byte ${i} (line endings must be CRLF)`);
      lines.push(bytes.subarray(start, i));
      start = i + 1;
    }
  }
  if (start !== bytes.length) problems.push('the last line does not end with CRLF');
  return lines;
}

/**
 * Whether a TEXT value is escaped as §3.3.11 requires: no bare `,` or `;`, and a backslash only
 * before `\`, `;`, `,`, `n` or `N`.
 *
 * @param {string} value The raw value.
 * @return {boolean} True when valid.
 */
function textEscapedCorrectly(value) {
  for (let i = 0; i < value.length; i++) {
    const c = value[i];
    if (c === ',' || c === ';') return false;
    if (c === '\\') {
      if (!'\\;,nN'.includes(value[i + 1] || '')) return false;
      i++;
    }
  }
  return true;
}

/**
 * @param {ICAL.Component} component A component.
 * @param {string} name A property name, lower case.
 * @return {number} How many times it occurs.
 */
const count = (component, name) => component.getAllProperties(name).length;

/**
 * Validates one iCalendar document.
 *
 * @param {!Buffer|string} input The document as served (a string is taken as UTF-8).
 * @return {{problems: !Array<string>, events: !Array<{uid: string, summary: string,
 *   description: ?string, start: ICAL.Time, end: ?ICAL.Time, rrule: ?ICAL.Recur,
 *   transp: ?string}>, calendar: ?ICAL.Component}} What was found.
 */
function validateIcs(input) {
  const bytes = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  const problems = [];

  // ── The wire ──
  const lines = physicalLines(bytes, problems);
  const logical = [];
  lines.forEach((line, i) => {
    if (line.length > 75) problems.push(`line ${i + 1} is ${line.length} octets (max 75)`);
    let text;
    try {
      text = utf8.decode(line);
    } catch (e) {
      problems.push(`line ${i + 1} is not valid UTF-8 on its own (folded inside a character?)`);
      text = line.toString('utf8');
    }
    if (/[\x00-\x08\x0A-\x1F\x7F]/.test(text)) problems.push(`line ${i + 1} holds a control character`);
    if (text.startsWith(' ') || text.startsWith('\t')) {
      if (logical.length === 0) problems.push('the document starts with a continuation line');
      else logical[logical.length - 1] += text.slice(1);
    } else {
      if (text === '') problems.push(`line ${i + 1} is empty`);
      logical.push(text);
    }
  });

  const stack = [];
  logical.forEach((line, i) => {
    const match = CONTENT_LINE.exec(line);
    if (!match) {
      problems.push(`content line ${i + 1} does not match the RFC 5545 grammar: ${line.slice(0, 60)}`);
      return;
    }
    const name = match[1].toUpperCase();
    const value = match[3];
    if (name === 'BEGIN') stack.push(value);
    if (name === 'END') {
      const open = stack.pop();
      if (open !== value) problems.push(`END:${value} closes ${open ? `BEGIN:${open}` : 'nothing'}`);
    }
    if (TEXT_PROPERTIES.has(name) && !textEscapedCorrectly(value)) {
      problems.push(`${name} is not escaped per RFC 5545 §3.3.11: ${value.slice(0, 60)}`);
    }
    if (name === 'DTSTAMP' && !/^\d{8}T\d{6}Z$/.test(value)) problems.push(`DTSTAMP is not UTC: ${value}`);
  });
  if (stack.length) problems.push(`unclosed components: ${stack.join(', ')}`);
  if (logical[0] !== 'BEGIN:VCALENDAR') problems.push('the document does not start with BEGIN:VCALENDAR');
  if (logical[logical.length - 1] !== 'END:VCALENDAR') problems.push('the document does not end with END:VCALENDAR');

  // ── The meaning ──
  let calendar = null;
  try {
    calendar = new ICAL.Component(ICAL.parse(bytes.toString('utf8')));
  } catch (e) {
    problems.push(`ical.js could not parse it: ${e.message}`);
    return {problems, events: [], calendar};
  }
  if (calendar.name !== 'vcalendar') problems.push(`the root component is ${calendar.name}`);
  if (count(calendar, 'version') !== 1 || calendar.getFirstPropertyValue('version') !== '2.0') {
    problems.push('VCALENDAR needs exactly one VERSION:2.0');
  }
  if (count(calendar, 'prodid') !== 1) problems.push('VCALENDAR needs exactly one PRODID');

  const events = [];
  const uids = new Set();
  for (const vevent of calendar.getAllSubcomponents('vevent')) {
    const uid = vevent.getFirstPropertyValue('uid');
    const where = `VEVENT ${uid || '(no UID)'}`;
    for (const required of ['uid', 'dtstamp', 'dtstart']) {
      if (count(vevent, required) !== 1) problems.push(`${where} needs exactly one ${required.toUpperCase()}`);
    }
    for (const once of ['dtend', 'summary', 'description', 'rrule', 'transp']) {
      if (count(vevent, once) > 1) problems.push(`${where} has more than one ${once.toUpperCase()}`);
    }
    if (count(vevent, 'dtend') && count(vevent, 'duration')) problems.push(`${where} has both DTEND and DURATION`);
    if (uid) {
      if (uids.has(uid)) problems.push(`UID ${uid} occurs twice`);
      uids.add(uid);
    }
    const start = vevent.getFirstPropertyValue('dtstart');
    const end = vevent.getFirstPropertyValue('dtend');
    if (start && end) {
      if (start.isDate !== end.isDate) problems.push(`${where}: DTSTART and DTEND differ in value type`);
      if (end.compare(start) <= 0) problems.push(`${where}: DTEND ${end} is not after DTSTART ${start}`);
    }
    const rrule = vevent.getFirstPropertyValue('rrule');
    if (rrule) {
      if (!(rrule instanceof ICAL.Recur) || !rrule.freq) problems.push(`${where}: RRULE has no FREQ`);
      if (rrule.until && start) {
        if (rrule.until.isDate !== start.isDate) problems.push(`${where}: UNTIL and DTSTART differ in value type`);
        const floating = start.zone === ICAL.Timezone.localTimezone;
        if (floating && rrule.until.zone === ICAL.Timezone.utcTimezone) {
          problems.push(`${where}: DTSTART is floating, so UNTIL must be too (RFC 5545 §3.3.10)`);
        }
      }
      try {
        // A client expands the rule; so do we, a few occurrences in.
        const it = new ICAL.Event(vevent).iterator();
        for (let i = 0; i < 5 && it.next(); i++);
      } catch (e) {
        problems.push(`${where}: RRULE does not expand: ${e.message}`);
      }
    }
    events.push({
      uid,
      summary: vevent.getFirstPropertyValue('summary'),
      description: vevent.getFirstPropertyValue('description'),
      start,
      end,
      rrule,
      transp: vevent.getFirstPropertyValue('transp'),
    });
  }
  return {problems, events, calendar};
}

module.exports = {validateIcs, textEscapedCorrectly};
