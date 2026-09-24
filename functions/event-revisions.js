/**
 * MON-4 — the revisions a phone did not record, recorded by the server.
 *
 * Every build since MON-4 queues a revision of each create, update and delete of a non-private
 * event and uploads it to `event_versions` (CLAUDE.md item 25). An **older build** does not, and
 * the events rule cannot demand it: an `existsAfter` check there would refuse every edit from a
 * co-parent who has not updated. So the gap is closed on this side instead. A trigger on
 * `events/{eventId}` reads each write and, when no phone has recorded a revision of that exact
 * write, records one from the saved document.
 *
 * **How "this exact write" is recognised — the write key.** A client revision embeds the event
 * document its save uploaded (`EventVersionDocument.EVENT`), so the two sides can be matched by
 * content without any new field on the client:
 *
 *   - a tombstone:     `deleted|<deletedAtMillis>` — the client's delete revision carries the same
 *                      `Tombstone.fields` the tombstone write does;
 *   - anything else:   `saved|<updatedAt>` — every save stamps `updatedAt`, and the revision's
 *                      snapshot is the very map that save uploaded.
 *
 * `EventVersionDocument.writeKey` on the phone is the other copy of [writeKey]; change both.
 *
 * **Why a write of the same key is not recorded.** The server rewrites event documents it does not
 * author — the unpair sweep narrows `sharedWith`, the family backfill stamps `familyId`, account
 * deletion scrubs an audience — and a phone's sync re-uploads a document unchanged. None of those
 * is a parent saving anything; none changes the key.
 *
 * **The race, and why it is settled by the reader.** A phone uploads its revision from an outbox,
 * independently of the event write and possibly much later (offline). A trigger that waited for it
 * would have to wait for ever. So the trigger checks once, again after a short grace, and then
 * writes; a late client revision then exists beside a server one for the same write, and the
 * export drops the server one (`CommunicationRecordBuilder`). The server revision is marked
 * `recordedBy: 'server'`, which no client may write (`firestore.rules`), and its id starts with
 * `srv_`, which no client may use — so a client cannot pre-empt it by squatting on the id.
 *
 * `event_versions/{srv_<eventId>_<updateTime>}`, beside the client's fields:
 *
 *   recordedBy        'server'
 *   editorUid         whom the saved document names: `deletedBy` on a tombstone, else
 *                     `lastModifiedBy`, else `createdByFirebaseUid`. A claim of the document, not
 *                     proven — the events rule does not pin `lastModifiedBy`
 *   editorField       which of those three it came from
 *   deviceTimeMillis  null — no device reported one. An older export build, which requires a
 *                     number here, therefore skips a server revision rather than mislabelling it
 *   recordedAt        the server time of this write
 *   event             the saved document, as the trigger read it
 */

const crypto = require('crypto');

/** The collection. */
const VERSIONS = 'event_versions';

/** What `recordedBy` says on a revision this module wrote. */
const RECORDED_BY_SERVER = 'server';

/** Every server revision id starts with this; `firestore.rules` refuses it to clients. */
const SERVER_ID_PREFIX = 'srv_';

/** The revision document's shape — the client's `EventVersionDocument.CURRENT_FORMAT`. */
const FORMAT_VERSION = 1;

/** How long to wait for a phone's own revision before recording one from the document. */
const CLIENT_GRACE_MS = 5000;

/** Firestore's gRPC code for a create over an existing document. */
const ALREADY_EXISTS = 6;

/**
 * The write key of an event document — see the file comment. Null for a document with neither a
 * tombstone nor an `updatedAt`, which cannot be matched and is not recorded.
 *
 * @param {?Object} doc An event document, or a revision's embedded `event`.
 * @return {?string} The key.
 */
function writeKey(doc) {
  if (!doc || typeof doc !== 'object') {
    return null;
  }
  const deletedAt = doc.deletedAtMillis;
  if (typeof deletedAt === 'number' && Number.isFinite(deletedAt) && Math.trunc(deletedAt) > 0) {
    return `deleted|${Math.trunc(deletedAt)}`;
  }
  if (typeof doc.updatedAt === 'string' && doc.updatedAt !== '') {
    return `saved|${doc.updatedAt}`;
  }
  return null;
}

/**
 * Who the saved document says made this write, and which field says so.
 *
 * @param {!Object} after The event document after the write.
 * @param {string} kind The revision's kind.
 * @return {?{uid: string, field: string}} The editor, or null when the document names nobody.
 */
function editorOf(after, kind) {
  const fields = kind === 'deleted' ?
    ['deletedBy', 'lastModifiedBy', 'createdByFirebaseUid'] :
    ['lastModifiedBy', 'createdByFirebaseUid'];
  for (const field of fields) {
    const value = after[field];
    if (typeof value === 'string' && value !== '') {
      return {uid: value, field};
    }
  }
  return null;
}

/**
 * Whether this write deserves a server revision, and if so of which kind.
 *
 * @param {?Object} before The document before the write, or null when it was created.
 * @param {?Object} after The document after the write, or null when it was removed.
 * @return {{skip: string}|{kind: string, key: string, editor: {uid: string, field: string},
 *   audience: !Array<string>}} A reason to record nothing, or what to record.
 */
function decide(before, after) {
  if (!after) {
    // A document removed outright: an event turned private (the one legitimate client removal),
    // the 90-day sweep, or account deletion. None is a parent's save to record.
    return {skip: 'removed'};
  }
  if (after.isPrivate === true) {
    // Never reached: a private event is never written to Firestore (CLAUDE.md item 3). Asserted
    // anyway, because a revision is kept for ever and this is the one field that must never be.
    return {skip: 'private'};
  }
  const key = writeKey(after);
  if (key === null) {
    return {skip: 'undated'};
  }
  if (before && writeKey(before) === key) {
    return {skip: 'unchanged'};
  }
  // A document that was absent or a tombstone and is now live was (re-)created — an Undo is a
  // `set()` over the tombstone.
  const beforeKey = before ? writeKey(before) : null;
  const beforeLive = before !== null && before !== undefined &&
    !(beforeKey !== null && beforeKey.startsWith('deleted|'));
  const kind = key.startsWith('deleted|') ? 'deleted' : (beforeLive ? 'updated' : 'created');
  const editor = editorOf(after, kind);
  if (!editor) {
    return {skip: 'noEditor'};
  }
  const audience = Array.isArray(after.sharedWith) ?
    after.sharedWith.filter((uid) => typeof uid === 'string' && uid !== '') : [];
  if (audience.length === 0) {
    // Readable by nobody: the revision rule admits only members of its own audience.
    return {skip: 'noAudience'};
  }
  return {kind, key, editor, audience};
}

/**
 * The server revision's id: fixed by the write it records, so a retried trigger lands on the same
 * document rather than a second one.
 *
 * @param {string} eventId The event.
 * @param {?{seconds: number, nanoseconds: number}} updateTime The write's commit time.
 * @param {string} key The write key, used when there is no commit time.
 * @return {string} The id.
 */
function serverRevisionId(eventId, updateTime, key) {
  const stamp = updateTime && typeof updateTime.seconds === 'number' ?
    `${updateTime.seconds}${String(updateTime.nanoseconds || 0).padStart(9, '0')}` :
    crypto.createHash('sha256').update(key).digest('hex').slice(0, 24);
  return `${SERVER_ID_PREFIX}${eventId}_${stamp}`;
}

/**
 * Whether a phone has already recorded a revision of the write [key] names.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore instance.
 * @param {string} eventId The event.
 * @param {string} key The write key.
 * @return {Promise<boolean>} True when a client revision of this write exists.
 */
async function clientRevisionExists(db, eventId, key) {
  const snap = await db.collection(VERSIONS).where('eventId', '==', eventId).get();
  return snap.docs.some((doc) => {
    const data = doc.data() || {};
    return data.recordedBy !== RECORDED_BY_SERVER && writeKey(data.event) === key;
  });
}

/**
 * Body of the `recordServerEventRevision` trigger.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore instance.
 * @param {{eventId: string, before: ?Object, after: ?Object,
 *   updateTime: ?{seconds: number, nanoseconds: number}}} write The write the trigger saw.
 * @param {{serverTimestamp: function(): *, graceMs: (number|undefined),
 *   sleep: (function(number): !Promise|undefined)}} deps The `recordedAt` sentinel, and the
 *   grace before the second check (tests pass 0).
 * @return {Promise<{recorded: boolean, reason: string, id: (string|undefined)}>} What happened.
 */
async function recordServerRevisionImpl(db, write, deps) {
  const decision = decide(write.before, write.after);
  if (decision.skip) {
    return {recorded: false, reason: decision.skip};
  }
  const {eventId} = write;
  if (await clientRevisionExists(db, eventId, decision.key)) {
    return {recorded: false, reason: 'clientRecorded'};
  }
  const graceMs = deps.graceMs === undefined ? CLIENT_GRACE_MS : deps.graceMs;
  if (graceMs > 0) {
    const sleep = deps.sleep || ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
    await sleep(graceMs);
    if (await clientRevisionExists(db, eventId, decision.key)) {
      return {recorded: false, reason: 'clientRecorded'};
    }
  }

  const id = serverRevisionId(eventId, write.updateTime, decision.key);
  try {
    await db.collection(VERSIONS).doc(id).create({
      eventId,
      kind: decision.kind,
      editorUid: decision.editor.uid,
      editorField: decision.editor.field,
      deviceTimeMillis: null,
      recordedAt: deps.serverTimestamp(),
      sharedWith: decision.audience,
      familyId: typeof write.after.familyId === 'string' ? write.after.familyId : '',
      event: write.after,
      formatVersion: FORMAT_VERSION,
      recordedBy: RECORDED_BY_SERVER,
    });
  } catch (err) {
    if (err && err.code === ALREADY_EXISTS) {
      // A retried trigger: the first attempt already wrote it.
      return {recorded: false, reason: 'alreadyRecorded', id};
    }
    throw err;
  }
  return {recorded: true, reason: decision.kind, id};
}

module.exports = {
  VERSIONS,
  RECORDED_BY_SERVER,
  SERVER_ID_PREFIX,
  CLIENT_GRACE_MS,
  writeKey,
  decide,
  serverRevisionId,
  recordServerRevisionImpl,
};
