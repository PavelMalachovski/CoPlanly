#!/usr/bin/env node
/**
 * The iPhone's write gate, its second half (MON-24 phase 3, Task 7).
 *
 * `IosWriteGateTest` (shared-data's iosTest) runs two parents' production data layers on the
 * iPhone simulator against the Firebase emulators and lists, in `build/ios-gate/written.txt`, every
 * document their Firestore client wrote. This script reads each of those documents back from the
 * Firestore emulator's REST API and compares its *shape* — the key set, each value's Firestore type
 * (integer, double, string, boolean, null, timestamp, array, map) and the shape of every nested
 * map and array element — with what Android writes to the same collection:
 *
 *  - the `app/src/test/resources/wire/current/<collection>/` fixture of the same writer path, which
 *    `CurrentWireFixturesTest` holds to what the Android build writes (CLAUDE.md, "Things that are
 *    easy to get wrong", item 5), chosen by what the writer leaves in the document (`RULES`);
 *  - for a collection with no fixture, a literal shape below, each citing the writer it was
 *    derived from. Those writers are shared code, so what the gate catches there is the encoder's
 *    drift on the iPhone: a Long written as a double, a map as a string, a timestamp for ISO text,
 *    a key missing or added.
 *
 * Since part C's Task 10 the gate also uploads files to Cloud Storage, listed in the same file as
 * `storage:<object path>` lines. Each is read back from the Storage emulator (`--storage`, the
 * emulators' admin token too) and its path, content type, size and custom metadata are held to what
 * Android's `SharedFileStorage` writes (`FILE_RULES`): the path shape `storage.rules` keys on, and
 * exactly the `uploader` and `sha256` keys, the uploader one of the path's family.
 *
 * Where a fixture's optional keys legitimately differ from what the gate writes, the rule says so
 * with its reason (`mayBeAbsent`, `mayBeEmpty`); keys a server or a later step adds to the document
 * are typed too (`serverAdded`, `laterSteps`). Nothing is skipped silently.
 *
 * It fails — one `::error::` line per mismatch — on any mismatch, on a listed document the
 * emulator does not have, on a collection with no known shape, on a required collection the gate
 * wrote nothing to, and when `written.txt` is missing or empty: a gate that wrote nothing cannot
 * pass. It reads only the emulator (`Authorization: Bearer owner`, the emulators' admin token) and
 * refuses any host that is not loopback, so it can never read production.
 *
 * Usage: node tools/ios/check-ios-writes.js --emulator 127.0.0.1:8080
 *          --fixtures app/src/test/resources/wire/current [--written build/ios-gate/written.txt]
 *          [--storage 127.0.0.1:9199]
 * No dependencies. `node --test tools/test/check-ios-writes.test.js` covers it.
 */
'use strict';

const fs = require('fs');
const path = require('path');

/** The emulators' project, as `firebase.json` and the iPhone's `IosFirebase` name it. */
const PROJECT = 'demo-coplanly';

/**
 * The collections the gate must have written at least one document to (the brief's list and what
 * pairing writes on the way), so a gate that stopped early cannot pass on the few it reached.
 */
const REQUIRED_COLLECTIONS = [
  'users', 'invitations', 'conversations', 'messages', 'events', 'event_versions', 'expenses',
  'child_info', 'pets', 'change_requests', 'custody_models', 'request_deadlines', 'notification_queue',
];

/** The emulators' default bucket, as `IosFirebase` and Android's e2e `EmulatorEnvironment` name it. */
const BUCKET = `${PROJECT}.appspot.com`;

/** How written.txt marks a Storage object's line (`WrittenDocuments.STORAGE_PREFIX`). */
const STORAGE_PREFIX = 'storage:';

/** The Storage prefixes the gate must have uploaded at least one object under. */
const REQUIRED_FILE_PREFIXES = ['family_documents', 'pet_photos'];

/** What `storage.rules`' `isAcceptableSharedFile` admits (`SharedFilePolicy` in the app). */
const SHARED_FILE_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/heic', 'image/heif', 'image/webp'];

/**
 * The Storage objects Android writes, by top-level prefix: the path's shape (its first group the
 * pair id `storage.rules`' `isOneOfPair` splits) and the content types the rule admits. Both are
 * `SharedFileStorage.upload`, whose custom metadata is `uploader` then `sha256`, nothing else.
 */
const FILE_RULES = {
  // FamilyDocumentPaths.storagePath: family_documents/{familyId}/{docId}/{fileName}.
  family_documents: {
    path: /^family_documents\/([^/]+)\/([^/]+)\/([^/]+)$/,
    contentTypes: SHARED_FILE_TYPES,
    source: 'SharedFileStorage.upload at FamilyDocumentPaths.storagePath',
  },
  // ChatAttachmentCodec.storagePath: chat_attachments/{conversationId}/{messageId}/{fileName}.
  chat_attachments: {
    path: /^chat_attachments\/([^/]+)\/([^/]+)\/([^/]+)$/,
    contentTypes: SHARED_FILE_TYPES,
    source: 'SharedFileStorage.upload at ChatAttachmentCodec.storagePath',
  },  // RecordPhotoPaths.build: pet_photos/{familyId | solo_{uid}}/{recordId}/{random uuid}.jpg, the
  // JPEG FirebaseImageStorage (and the iPhone's IosRecordPhotoStorage, part C, Task 11) re-encodes
  // every photo to, through SharedFileStorage.uploadBytes. Before the uploader has a family the
  // owner is their own solo_ folder, which only they may write.
  pet_photos: {
    path: /^pet_photos\/([^/]+)\/([^/]+)\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg)$/,
    contentTypes: ['image/jpeg'],
    soloOwner: true,
    source: 'SharedFileStorage.uploadBytes at RecordPhotoPaths.build',
  },
};

// ---- shapes -----------------------------------------------------------------------------------

/** A primitive shape. */
const t = (type) => ({ type });
const STRING = t('string');
const INTEGER = t('integer');
const BOOLEAN = t('boolean');
const TIMESTAMP = t('timestamp');
const NULL = t('null');
/** Either of [options]. */
const oneOf = (...options) => ({ type: 'oneOf', options });
/** A map with these keys, and these optional ones. */
const mapShape = (fields, optional = {}) => ({ type: 'map', fields, optional });
/** A map whose keys are data (uids, dates), every value of [values]'s shape; may be empty. */
const mapOf = (values) => ({ type: 'mapOf', values, nonEmpty: false });
/** An array of [items]; may be empty. */
const arrayOf = (items) => ({ type: 'array', items, nonEmpty: false });

/** A shape's name in a message. */
function nameOf(shape) {
  if (shape.type === 'oneOf') return shape.options.map(nameOf).join(' or ');
  if (shape.type === 'mapOf') return 'map';
  return shape.type;
}

/**
 * The shape of a JSON text's value, numbers typed by their lexeme: a number written with a
 * fraction or an exponent is a double (`1250.0` too, which `JSON.parse` would make an integer),
 * any other an integer — as the fixtures' Kotlin writer prints a Double and a Long.
 */
function shapeOfJson(text) {
  let at = 0;
  const fail = (what) => { throw new Error(`JSON: ${what} at ${at}`); };
  const space = () => { while (/\s/.test(text[at] || '')) at++; };
  const value = () => {
    space();
    const c = text[at];
    if (c === '{') {
      at++;
      const fields = {};
      space();
      if (text[at] === '}') { at++; return mapShape(fields); }
      for (;;) {
        space();
        const key = string();
        space();
        if (text[at++] !== ':') fail('":" expected');
        fields[key] = value();
        space();
        const next = text[at++];
        if (next === '}') return mapShape(fields);
        if (next !== ',') fail('"," or "}" expected');
      }
    }
    if (c === '[') {
      at++;
      const items = [];
      space();
      if (text[at] === ']') { at++; return { type: 'array', items: null, nonEmpty: false }; }
      for (;;) {
        items.push(value());
        space();
        const next = text[at++];
        if (next === ']') return { type: 'array', items: items[0], nonEmpty: true };
        if (next !== ',') fail('"," or "]" expected');
      }
    }
    if (c === '"') { string(); return STRING; }
    if (text.startsWith('true', at)) { at += 4; return BOOLEAN; }
    if (text.startsWith('false', at)) { at += 5; return BOOLEAN; }
    if (text.startsWith('null', at)) { at += 4; return NULL; }
    const number = /^-?\d+(\.\d+)?([eE][+-]?\d+)?/.exec(text.slice(at));
    if (!number) fail('a value expected');
    at += number[0].length;
    return number[1] || number[2] ? t('double') : INTEGER;
  };
  const string = () => {
    if (text[at] !== '"') fail('a string expected');
    const end = /^"(?:[^"\\]|\\.)*"/.exec(text.slice(at));
    if (!end) fail('an unterminated string');
    at += end[0].length;
    return JSON.parse(end[0]);
  };
  const shape = value();
  space();
  if (at !== text.length) fail('trailing text');
  return shape;
}

// ---- comparison -------------------------------------------------------------------------------

/** A Firestore REST value's type. */
function typeOf(value) {
  const key = Object.keys(value || {}).find((k) => k.endsWith('Value'));
  return {
    nullValue: 'null', booleanValue: 'boolean', integerValue: 'integer', doubleValue: 'double',
    timestampValue: 'timestamp', stringValue: 'string', bytesValue: 'bytes', referenceValue: 'reference',
    geoPointValue: 'geopoint', arrayValue: 'array', mapValue: 'map',
  }[key] || 'unknown';
}

const fieldsOf = (value) => (value.mapValue && value.mapValue.fields) || {};
const valuesOf = (value) => (value.arrayValue && value.arrayValue.values) || [];
const join = (parent, key) => (parent ? `${parent}.${key}` : key);

/** The mismatches of the REST [value] at [where] against [shape]. */
function compare(shape, value, where) {
  const actual = typeOf(value);
  if (shape.type === 'oneOf') {
    const fits = shape.options.find((option) => compare(option, value, where).length === 0);
    if (fits) return [];
    const sameType = shape.options.find((option) => option.type === actual);
    return sameType ? compare(sameType, value, where) : [`${where}: ${nameOf(shape)} expected, ${actual} found`];
  }
  const expected = shape.type === 'mapOf' ? 'map' : shape.type;
  if (expected !== actual) return [`${where}: ${expected} expected, ${actual} found`];
  if (shape.type === 'map') {
    const fields = fieldsOf(value);
    const problems = [];
    for (const [key, field] of Object.entries(shape.fields)) {
      if (key in fields) problems.push(...compare(field, fields[key], join(where, key)));
      else if (!(key in shape.optional)) problems.push(`${join(where, key)}: missing (${nameOf(field)} expected)`);
    }
    for (const [key, field] of Object.entries(fields)) {
      if (key in shape.fields) continue;
      if (key in shape.optional) problems.push(...compare(shape.optional[key], field, join(where, key)));
      else problems.push(`${join(where, key)}: unexpected key (${typeOf(field)} found)`);
    }
    return problems;
  }
  if (shape.type === 'mapOf') {
    const entries = Object.entries(fieldsOf(value));
    return elements(shape, entries.map(([key, entry]) => [join(where, key), entry]), where, '{}');
  }
  if (shape.type === 'array') {
    return elements(shape, valuesOf(value).map((entry, index) => [`${where}[${index}]`, entry]), where, '[]');
  }
  return [];
}

/** The mismatches of a collection's [entries] against [shape]'s element shape. */
function elements(shape, entries, where, mark) {
  if (entries.length === 0) {
    return shape.nonEmpty ? [`${where}: empty, so the fixture's elements are not compared`] : [];
  }
  const item = shape.type === 'mapOf' ? shape.values : shape.items;
  if (!item) return [`${where}${mark}: elements found where the fixture has none to compare`];
  return entries.flatMap(([at, entry]) => compare(item, entry, at));
}

/** The mismatches of a REST [document] against the map [shape]. */
function compareDocument(shape, document) {
  return compare(shape, { mapValue: { fields: document.fields || {} } }, '');
}

// ---- fixtures and their adjustments -----------------------------------------------------------

const fixtureCache = new Map();

/** The shape of `wire/current/[file]`'s `document`. */
function fixture(fixtures, file) {
  const key = path.join(fixtures, file);
  if (!fixtureCache.has(key)) {
    const whole = shapeOfJson(fs.readFileSync(key, 'utf8'));
    if (!whole.fields || !whole.fields.document) throw new Error(`${file} has no "document"`);
    fixtureCache.set(key, whole.fields.document);
  }
  return clone(fixtureCache.get(key));
}

const clone = (value) => JSON.parse(JSON.stringify(value));

/**
 * The shape at [where] (`a.b`, an array's elements as `a[]`, a keyed map's values as `a{}`) of
 * [shape], handed to [edit] with its parent and key; throws when the path is not in the shape, so a
 * rule that names a key the fixture does not have fails loudly instead of doing nothing.
 */
function at(shape, where, edit) {
  const steps = where.split('.');
  let node = shape;
  for (let index = 0; index < steps.length; index++) {
    const [, key, mark] = /^(.*?)(\[\]|\{\})?$/.exec(steps[index]);
    const holder = [node.fields, node.optional].find((fields) => fields && key in fields);
    if (!holder) throw new Error(`no "${key}" in the shape at ${where}`);
    const container = mark ? holder[key] : holder;
    const slot = mark === '[]' ? 'items' : mark === '{}' ? 'values' : key;
    if (index === steps.length - 1) return edit(container, slot);
    node = container[slot];
    if (!node) throw new Error(`nothing under "${steps[index]}" at ${where}`);
  }
  return undefined;
}

/**
 * [base] with a rule's adjustments, each with its reason in the rule:
 *  - `keyedMaps`: maps keyed by data (uids, dates), whose values are compared with the fixture's
 *    first entry rather than by key;
 *  - `mayBeEmpty`: arrays and keyed maps the gate's write leaves empty for the reason given;
 *  - `mayBeAbsent`: keys the fixture's writer path carries and the gate's does not;
 *  - `extraKeys`: keys the writer adds only when it has them (typed);
 *  - `replace`: a nested shape taken from the fixture of the writer that built it;
 *  - `serverAdded`: keys a server-side write adds to the document (always there);
 *  - `laterSteps`: keys a later step's write may add (typed, optional).
 */
function adjust(base, rule) {
  const shape = clone(base);
  for (const where of Object.keys(rule.keyedMaps || {})) {
    at(shape, where, (container, key) => {
      const entries = Object.values(container[key].fields);
      container[key] = { type: 'mapOf', values: entries[0] || null, nonEmpty: entries.length > 0 };
    });
  }
  for (const [where, replacement] of Object.entries(rule.replace || {})) {
    at(shape, where, (container, key) => { container[key] = clone(replacement); });
  }
  for (const where of Object.keys(rule.mayBeEmpty || {})) {
    at(shape, where, (container, key) => { container[key].nonEmpty = false; });
  }
  for (const where of Object.keys(rule.mayBeAbsent || {})) {
    const dot = where.lastIndexOf('.');
    const parent = dot < 0 ? shape : at(shape, where.slice(0, dot), (container, key) => container[key]);
    const key = where.slice(dot + 1);
    if (!(key in parent.fields)) throw new Error(`no "${key}" to make optional at ${where}`);
    parent.optional[key] = parent.fields[key];
    delete parent.fields[key];
  }
  for (const [where, added] of Object.entries(rule.extraKeys || {})) {
    const dot = where.lastIndexOf('.');
    const parent = dot < 0 ? shape : at(shape, where.slice(0, dot), (container, key) => container[key]);
    parent.optional[where.slice(dot + 1)] = added;
  }
  Object.assign(shape.fields, rule.serverAdded || {});
  Object.assign(shape.optional, rule.laterSteps || {});
  return shape;
}

// ---- the rules --------------------------------------------------------------------------------

/** The gate photographs a pet (part C, Task 11) and no child, so a child's photo lists stay empty. */
const NO_STORAGE = 'the gate uploads a pet\'s photo only (IosRecordPhotoStorage, part C): no medical photo to reference';

/** A rule: the fixture of this writer path, and its adjustments with their reasons. */
const RULES = {
  // EventRepositoryImpl.insertEvent → EventDocument.fromEvent; the gate runs no sync, so every event
  // it writes is a repository save (never `sync-upload.json`'s nulls), or one deleted afterwards
  // (EventRepositoryImpl.deleteEvent → FirestoreEventDataSource.tombstoneEvent: `Tombstone.fields`
  // merged in by update()). A deleted one is the save's shape plus the two keys, typed as
  // `tombstone.json` holds them: that fixture's event tags no member and sets no reminder, where the
  // gate's does both.
  events: (doc, fixtures) => ({
    fixture: 'events/repository-save.json',
    // FirestoreEventDataSource.stamped adds the change cursor, FieldValue.serverTimestamp(), to
    // every write after fromEvent built the document the fixture holds.
    serverAdded: Object.assign({ serverUpdatedAt: TIMESTAMP }, isTombstone(doc) ? tombstoneKeys(fixtures) : {}),
  }),

  // EventVersionRecorder; its `event` is the document `fromEvent` built for the same save, so it
  // is held to the repository save's fixture rather than `updated.json`'s own embedded copy (which
  // tags no member) — with the tombstone's two keys for a deletion, which EventRepositoryImpl.
  // deleteEvent records with `Tombstone.fields` added. `recordedAt` is
  // FieldValue.serverTimestamp(), added by the data source.
  event_versions: (doc, fixtures) => {
    const event = fixture(fixtures, 'events/repository-save.json');
    if (stringField(doc, 'kind') === 'deleted') Object.assign(event.fields, tombstoneKeys(fixtures));
    return {
      fixture: 'event_versions/updated.json',
      replace: { event },
      serverAdded: { recordedAt: TIMESTAMP },
    };
  },

  // Message.toFirestoreMap: a text message, or an activity card (ActivityAnnouncer).
  messages: (doc) => (stringField(doc, 'messageType') === 'ACTIVITY' ? {
    fixture: 'messages/activity-custody-proposed.json',
    // ActivityAnnouncer.announce writes [entityType, entityId] as the card's attachments, which
    // the fixture (a card built without the announcer) leaves empty: text, as a message's are.
    replace: { attachments: arrayOf(STRING) },
    mayBeAbsent: {
      'activity.planCitation': 'ActivityAnnouncement.toMap writes it only for a proposal citing the plan',
    },
    // ActivityAnnouncement.toMap writes each only when the card has one.
    extraKeys: { 'activity.whenIso': STRING, 'activity.amount': STRING, 'activity.currency': STRING },
  } : {
    // The gate's message carries one reference (to another message's file, so nothing is
    // uploaded), and its list is compared with the fixture's element by element.
    fixture: 'messages/text.json',
  }),

  // CustodyModelRepository: a pattern write alone (the gate's second pair), or (with
  // `dayOverrides`) the swap and proposal writes over one (the first pair).
  custody_models: (doc, fixtures) => (doc.fields && 'dayOverrides' in doc.fields ? {
    fixture: 'custody_models/swap-beside-proposal.json',
    keyedMaps: { dayOverrides: 'keyed by ISO date' },
    mayBeAbsent: {
      proposalPlanCitation: 'written only beside a proposal that cites an agreed parenting plan',
    },
    // The swap fixture's three codec lists are empty, top level and in the proposal, so they would
    // compare no element; the gate's schedule holds one of each, and a swap or proposal write
    // carries them verbatim (items 24, 30 and 33), so each element is held to the pattern write's.
    replace: codecLists(fixtures),
  } : { fixture: 'custody_models/pattern-write.json' }),

  // ChildInfoRepositoryImpl.upsertChildInfo → toFirestoreMap.
  child_info: () => ({
    fixture: 'child_info/repository-save.json',
    keyedMaps: { guests: 'keyed by the guest\'s uid' },
    // The portrait (schema 47): toFirestoreMap writes the key always, null for a child with none,
    // as Android does; the fixture's child has one, and the gate photographs no child.
    replace: { photo: oneOf(NULL, STRING) },
    mayBeEmpty: {
      guests: 'a guest is granted through the guest callable by a third account; ChildInfoGuests.encode is shared code',
      medicalPhotos: NO_STORAGE,
    },
  }),

  // PetRepositoryImpl.upsertPet; the gate's pet carries the reference to the photo it uploaded
  // (IosRecordPhotoStorage, part C, Task 11), compared with the fixture's element.
  pets: () => ({ fixture: 'pets/repository-save.json' }),

  // ExpenseRepositoryImpl.addExpense → expenseToFirestoreMap (an empty receipt is "").
  expenses: () => ({ fixture: 'expenses/repository-save.json' }),

  // RequestDeadlineRepositoryImpl.start → RequestDeadlineDocument.createFields; `createdAt` is the
  // data source's server timestamp, and the later steps each write their own keys
  // (RequestDeadlineDocument's delivered, seen, reminder, last-chance and closing fields). The
  // server's own four fields (MON-25: `onRequestClockWritten` and the sweep's steps in
  // functions/request-clock-steps.js) are epoch millis, present once the trigger or a step ran.
  request_deadlines: () => ({
    fixture: 'request_deadlines/create.json',
    serverAdded: { createdAt: TIMESTAMP },
    laterSteps: {
      deliveredAtMillis: INTEGER, remindersPlanned: INTEGER, reminderTimesMillis: arrayOf(INTEGER),
      seenAtMillis: INTEGER, remindersShown: INTEGER, lastChanceAt: TIMESTAMP,
      lastChanceUntilMillis: INTEGER, outcome: STRING, closedAt: TIMESTAMP,
      serverNextStepAt: INTEGER, serverToldDeadlineAt: INTEGER, serverToldNoAnswerAt: INTEGER,
      serverSettledAt: INTEGER,
    },
  }),

  // ---- no fixture: literal shapes, each from its writer ----

  // ChangeRequestRepositoryImpl.toFirestoreMap: every date ISO text, every absent one "".
  change_requests: () => literal('ChangeRequestRepositoryImpl.toFirestoreMap', mapShape({
    id: STRING, eventId: STRING, eventTitle: STRING, requestedBy: STRING, requestedTo: STRING,
    currentStartDateTime: STRING, currentEndDateTime: STRING, proposedStartDateTime: STRING,
    proposedEndDateTime: STRING, note: STRING, status: STRING, createdAt: STRING, respondedAt: STRING,
    familyId: STRING,
  })),

  // FirestorePushQueue.queueNotificationForUser, the four keys and types FcmService writes on
  // Android (`createdAt` epoch millis as a Long, `data` text only); `sendNotification` then adds
  // its outcome (functions/index.js).
  notification_queue: () => literal('FirestorePushQueue.queueNotificationForUser', mapShape({
    targetUserId: STRING, data: mapOf(STRING), createdAt: INTEGER, status: STRING,
  }, { error: STRING, sentAt: TIMESTAMP, messageId: STRING })),

  // MessageRepositoryImpl.ensureConversation (a merge), then FirestoreMessageDataSource's
  // bumpLastMessageAt, markDelivered and markRead: epoch millis, the marks keyed by uid.
  conversations: () => literal('MessageRepositoryImpl.ensureConversation', mapShape({
    id: STRING, participants: arrayOf(STRING), archived: BOOLEAN, createdAt: STRING,
  }, { lastMessageAt: INTEGER, lastReadAt: mapOf(INTEGER), lastDeliveredAt: mapOf(INTEGER) })),

  // PairingRepositoryImpl.writeNewInvite; `acceptPairingInvitation` then writes the status, the
  // accepter (where the client wrote null) and the time, as admin.
  invitations: () => literal('PairingRepositoryImpl.writeNewInvite', mapShape({
    id: STRING, code: STRING, fromUserId: STRING, fromUserName: STRING, fromUserEmail: STRING,
    toEmail: STRING, status: STRING, createdAt: INTEGER, expiresAt: INTEGER, acceptedBy: oneOf(NULL, STRING),
  }, { acceptedAt: INTEGER })),

  // UserRepositoryImpl.ensureProfile's merge (writeRemoteProfile; a picture only when there is
  // one); `acceptPairingInvitation` adds the pairing, as admin.
  users: () => literal('UserRepositoryImpl.ensureProfile', mapShape({
    name: STRING, email: STRING, id: STRING, firebaseUid: STRING,
  }, {
    profilePhotoUrl: STRING, partnerIds: arrayOf(STRING), partnerId: STRING, pairedAt: INTEGER, role: STRING,
    slotMovedAt: INTEGER,
  })),

  // A phone writes only `caresFor.<uid>` (FirestoreFamilyDataSource.setCaresFor, a merge); the
  // callable writes the rest when the pair forms.
  families: () => literal('FirestoreFamilyDataSource.setCaresFor', mapShape({
    members: arrayOf(STRING), slots: mapOf(STRING), caresFor: mapOf(STRING), createdAt: INTEGER,
  })),
};

/** The custody document's codec lists, as `pattern-write.json` holds them, also under `proposal`. */
const CODEC_LISTS = ['contactWindows', 'seasonalLayers', 'childOverrides'];

/**
 * [CODEC_LISTS], top level and under `proposal`, each a non-empty list of the element shape
 * `pattern-write.json` holds (a codec string; a string if that fixture ever held none).
 */
function codecLists(fixtures) {
  const pattern = fixture(fixtures, 'custody_models/pattern-write.json');
  const lists = {};
  for (const key of CODEC_LISTS) {
    const list = { type: 'array', items: pattern.fields[key].items || STRING, nonEmpty: true };
    lists[key] = list;
    lists[`proposal.${key}`] = list;
  }
  return lists;
}

/** A rule whose shape is [shape], derived from [writer]. */
function literal(writer, shape) {
  return { source: `literal from ${writer}`, shape };
}

/** `Tombstone.fields`' two keys, typed as `events/tombstone.json` holds them. */
function tombstoneKeys(fixtures) {
  const tombstone = fixture(fixtures, 'events/tombstone.json');
  return { deletedAtMillis: tombstone.fields.deletedAtMillis, deletedBy: tombstone.fields.deletedBy };
}

/** Whether a REST event document is a tombstone (`Tombstone.DELETED_AT_MILLIS` set). */
function isTombstone(doc) {
  const value = doc && doc.fields && doc.fields.deletedAtMillis;
  return Boolean(value) && typeOf(value) !== 'null';
}

/** A REST document's text field. */
function stringField(doc, key) {
  const value = doc.fields && doc.fields[key];
  return value && value.stringValue;
}

/** The rule for a [collection]'s REST [doc]: where its shape comes from, and the shape. */
function ruleFor(collection, doc, fixtures) {
  const make = RULES[collection];
  if (!make) throw new Error('no Android shape is known for this collection');
  const rule = make(doc, fixtures);
  if (rule.shape) return rule;
  return { source: `wire/current/${rule.fixture}`, shape: adjust(fixture(fixtures, rule.fixture), rule) };
}

// ---- the run ----------------------------------------------------------------------------------

/** The unique `collection/id` paths of written.txt's [text]; its `storage:` lines are [parseWrittenFiles]'. */
function parseWritten(text) {
  const paths = [];
  for (const line of text.split('\n')) {
    const p = line.trim();
    if (!p || p.startsWith(STORAGE_PREFIX)) continue;
    if (!/^[^/\s]+\/[^/\s]+$/.test(p)) throw new Error(`"${p}" is not a collection/document path`);
    if (!paths.includes(p)) paths.push(p);
  }
  return paths;
}

/** The unique Storage object paths of written.txt's `storage:` lines. */
function parseWrittenFiles(text) {
  const paths = [];
  for (const line of text.split('\n')) {
    const p = line.trim();
    if (!p.startsWith(STORAGE_PREFIX)) continue;
    const objectPath = p.slice(STORAGE_PREFIX.length);
    if (!objectPath || objectPath.startsWith('/') || objectPath.split('/').some((part) => part === '')) {
      throw new Error(`"${p}" is not a Storage object path`);
    }
    if (!paths.includes(objectPath)) paths.push(objectPath);
  }
  return paths;
}

/**
 * The mismatches of the Storage object at [objectPath], whose metadata the emulator answered as
 * [meta] (its `contentType`, `size` and custom `metadata`), with what Android writes there.
 */
function compareFile(objectPath, meta) {
  const prefix = objectPath.split('/')[0];
  const rule = FILE_RULES[prefix];
  if (!rule) return [`no Android shape is known for Storage prefix "${prefix}"`];
  const problems = [];
  const match = rule.path.exec(objectPath);
  // A record photo's owner may be its uploader's own folder, `solo_{uid}` (storage.rules'
  // isRecordPhotoOwner), which names one uid: the uploader's.
  const solo = match && rule.soloOwner && match[1].startsWith('solo_') ? match[1].slice('solo_'.length) : null;
  const pair = solo ? [solo, solo] : match ? match[1].split('__') : [];
  if (!match) {
    problems.push(`path: not the shape ${rule.source} writes (${rule.path})`);
  } else if (solo === '') {
    problems.push('path: "solo_" names no uid');
  } else if (!solo && (pair.length !== 2 || pair.some((uid) => uid === ''))) {
    problems.push(`path: "${match[1]}" is not a pair id of two uids, which storage.rules splits`);
  }
  if (!rule.contentTypes.includes(meta.contentType)) {
    problems.push(`contentType: "${meta.contentType}" is not one storage.rules admits`);
  }
  if (!(Number(meta.size) > 0)) problems.push(`size: ${meta.size}, where storage.rules requires more than 0`);
  const custom = meta.metadata || {};
  const keys = Object.keys(custom).sort();
  if (keys.join(',') !== 'sha256,uploader') {
    problems.push(`metadata: keys [${keys.join(', ')}] where ${rule.source} writes [sha256, uploader]`);
  }
  if (typeof custom.sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(custom.sha256)) {
    problems.push('metadata.sha256: not 64 lowercase hex digits');
  }
  if (typeof custom.uploader !== 'string' || custom.uploader === '') {
    problems.push('metadata.uploader: missing or empty');
  } else if (pair.length === 2 && !pair.includes(custom.uploader)) {
    problems.push('metadata.uploader: not one of the family the path names');
  }
  return problems;
}

/** `host:port` of a loopback emulator; anything else is refused. */
function parseEmulator(text) {
  const m = /^(\[::1\]|[^:[\]]+):(\d+)$/.exec(text || '');
  if (!m) throw new Error(`--emulator must be host:port, not "${text}"`);
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(m[1])) {
    throw new Error(`--emulator must be a loopback address, not "${m[1]}": this reads only the emulator`);
  }
  return { host: m[1], port: Number(m[2]) };
}

/** Reads one document from the emulator at [emulator] as its owner; null when there is none. */
function emulatorReader(emulator) {
  return async (documentPath) => {
    const url = `http://${emulator.host}:${emulator.port}/v1/projects/${PROJECT}/databases/(default)/documents/` +
      documentPath.split('/').map(encodeURIComponent).join('/');
    const response = await fetch(url, { headers: { Authorization: 'Bearer owner' } });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`${documentPath}: the emulator answered ${response.status}`);
    return response.json();
  };
}

/** Reads one object's metadata from the Storage emulator at [emulator] as its owner; null when there is none. */
function storageReader(emulator) {
  return async (objectPath) => {
    const url = `http://${emulator.host}:${emulator.port}/v0/b/${BUCKET}/o/${encodeURIComponent(objectPath)}`;
    const response = await fetch(url, { headers: { Authorization: 'Bearer owner' } });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`storage:${objectPath}: the Storage emulator answered ${response.status}`);
    return response.json();
  };
}

/** A shape as plain text, for the log of a document that did not match. */
function describe(value) {
  const type = typeOf(value);
  if (type === 'map') {
    return Object.fromEntries(Object.entries(fieldsOf(value)).sort().map(([k, v]) => [k, describe(v)]));
  }
  if (type === 'array') return valuesOf(value).map(describe);
  return type;
}

/**
 * Compares every document [written] lists. Resolves with every problem found; logs one line per
 * document compared through [log].
 */
async function run({
  written, fixtures, fetchDocument, fetchFile = null, required = REQUIRED_COLLECTIONS,
  requiredFiles = REQUIRED_FILE_PREFIXES, log = console.log,
}) {
  const problems = [];
  if (!fs.existsSync(written)) return { problems: [`${written} is missing: the gate wrote nothing`], checked: 0 };
  let paths;
  let files;
  try {
    const text = fs.readFileSync(written, 'utf8');
    paths = parseWritten(text);
    files = parseWrittenFiles(text);
  } catch (e) {
    return { problems: [`${written}: ${e.message}`], checked: 0 };
  }
  if (paths.length === 0) return { problems: [`${written} is empty: the gate wrote nothing`], checked: 0 };

  let checked = 0;
  for (const documentPath of paths) {
    const collection = documentPath.split('/')[0];
    const doc = await fetchDocument(documentPath);
    if (!doc) {
      problems.push(`${documentPath}: not found on the emulator`);
      continue;
    }
    let rule;
    try {
      rule = ruleFor(collection, doc, fixtures);
    } catch (e) {
      problems.push(`${documentPath}: ${e.message}`);
      continue;
    }
    const mismatches = compareDocument(rule.shape, doc);
    checked++;
    if (mismatches.length === 0) {
      log(`ok ${documentPath} against ${rule.source}`);
    } else {
      log(`MISMATCH ${documentPath} against ${rule.source}; the document's shape:`);
      log(JSON.stringify(describe({ mapValue: { fields: doc.fields || {} } })));
      problems.push(...mismatches.map((m) => `${documentPath}: ${m}`));
    }
  }
  for (const collection of required) {
    if (!paths.some((p) => p.startsWith(`${collection}/`))) {
      problems.push(`${collection}: the gate wrote no document to compare`);
    }
  }
  for (const objectPath of files) {
    if (!fetchFile) {
      problems.push(`storage:${objectPath}: no Storage emulator to read it from`);
      continue;
    }
    const meta = await fetchFile(objectPath);
    if (!meta) {
      problems.push(`storage:${objectPath}: not found on the Storage emulator`);
      continue;
    }
    const mismatches = compareFile(objectPath, meta);
    checked++;
    if (mismatches.length === 0) {
      log(`ok storage:${objectPath} against ${FILE_RULES[objectPath.split('/')[0]].source}`);
    } else {
      problems.push(...mismatches.map((m) => `storage:${objectPath}: ${m}`));
    }
  }
  for (const prefix of requiredFiles) {
    if (!files.some((p) => p.startsWith(`${prefix}/`))) {
      problems.push(`storage:${prefix}: the gate uploaded no file to compare`);
    }
  }
  return { problems, checked };
}

/** The value of `--name` in [argv], or [fallback]. */
function option(argv, name, fallback) {
  const index = argv.indexOf(`--${name}`);
  return index >= 0 ? argv[index + 1] : fallback;
}

async function main(argv) {
  let emulator;
  try {
    emulator = parseEmulator(option(argv, 'emulator'));
  } catch (e) {
    console.log(`::error::${e.message}`);
    return 2;
  }
  const fixtures = option(argv, 'fixtures', 'app/src/test/resources/wire/current');
  let storage;
  try {
    storage = parseEmulator(option(argv, 'storage', '127.0.0.1:9199'));
  } catch (e) {
    console.log(`::error::--storage: ${e.message}`);
    return 2;
  }
  const written = option(argv, 'written', 'build/ios-gate/written.txt');
  const { problems, checked } = await run({
    written, fixtures, fetchDocument: emulatorReader(emulator), fetchFile: storageReader(storage),
  });
  for (const problem of problems) console.log(`::error::${problem}`);
  console.log(`${checked} documents and files compared, ${problems.length} problems`);
  return problems.length === 0 ? 0 : 1;
}

if (require.main === module) {
  main(process.argv.slice(2)).then((code) => process.exit(code), (e) => {
    console.log(`::error::${e.stack || e}`);
    process.exit(1);
  });
}

module.exports = {
  shapeOfJson, compare, compareDocument, ruleFor, parseWritten, parseWrittenFiles, compareFile, parseEmulator, run,
  REQUIRED_COLLECTIONS, REQUIRED_FILE_PREFIXES,
};
