/**
 * MON-16 — a verifiable export, without anybody's affidavit.
 *
 * An export (MON-3) is made on the phone. What the server adds is a **receipt**: a record id the
 * file prints on its face, and the SHA-256 of the file's exact bytes, registered under that id at a
 * time the server's clock chose. Anyone holding the file can later hash it in their own browser
 * and ask whether that hash was registered, and when. A single altered byte gives a different
 * hash, and the answer is "not found".
 *
 * **Two calls, because the id has to be inside the bytes it vouches for.** The hash must cover the
 * record id (otherwise the id could be printed on any file), so the id exists before the file is
 * rendered: [reserveImpl] mints it, the phone renders the file with it, hashes the result, and
 * [registerImpl] records the hash under the reserved id — once. A reservation also proves the
 * server was reachable *before* the file said anything about verification; a phone that cannot
 * reserve prints "not registered" instead, and one that reserved but then failed to register
 * renders the file again without the id. Nothing ever prints an id the server does not hold a
 * hash for.
 *
 * `export_receipts/{recordId}`:
 *
 *   state          'reserved' | 'registered'
 *   generatorUid   who made the export; '' once their account is erased (see [scrubReceipts])
 *   familyId       `FamilyKey.of` of the pair, '' for an account with no co-parent (or erased)
 *   fromDate       'YYYY-MM-DD', first day covered
 *   toDate         'YYYY-MM-DD', last day covered
 *   format         'pdf' | 'csv'
 *   reservedAt     server time the id was minted
 *   sha256         lowercase hex, 64 characters — only once registered
 *   byteLength     the file's length in bytes — only once registered
 *   recordedAt     server time the hash was registered — only once registered
 *   formatVersion  1
 *
 * No client reads or writes the collection (`firestore.rules`): every path goes through the
 * callables here, which is what lets [verifyImpl] answer a lawyer with no account while
 * disclosing nothing but the receipt itself.
 */

const crypto = require('crypto');

/** The collection. */
const RECEIPTS = 'export_receipts';

/** How long a reserved id may wait for its hash. An export takes seconds; this is generous. */
const RESERVATION_TTL_MS = 60 * 60 * 1000;

/** Random bytes per record id: 80 bits, 16 Crockford base-32 characters. */
const RECORD_ID_BYTES = 10;

/** Crockford's base 32: no I, L, O or U, so an id read aloud or retyped cannot be misread. */
const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** The formats an export comes in. */
const FORMATS = ['pdf', 'csv'];

/** Refuses a byte length no export plausibly has; the PDF of a busy year is a few megabytes. */
const MAX_BYTE_LENGTH = 200 * 1024 * 1024;

/** Verification lookups one address may make per window, per function instance. */
const VERIFY_RATE_LIMIT = 30;

/** Reservations one account may make per window, per function instance. */
const RESERVE_RATE_LIMIT = 20;

/** The rate-limit window. */
const RATE_WINDOW_MS = 10 * 60 * 1000;

/**
 * The instance cap the two limited callables are deployed with. The per-instance limiters are
 * only a bound on the whole service because the number of instances is bounded too: one address
 * can make at most `VERIFY_RATE_LIMIT × MAX_INSTANCES` lookups per window, whatever it does.
 */
const MAX_INSTANCES = 10;

/**
 * The fixed wording [verifyImpl] returns for who made a file. Deliberately not a name — see
 * `docs/DESIGN-court-record.md` §10.
 */
const GENERATED_BY = 'one of the family\'s parents';

/**
 * Encodes bytes as Crockford base 32, most significant bit first, without padding.
 *
 * @param {!Buffer} bytes The bytes.
 * @return {string} The encoding.
 */
function crockford(bytes) {
  let bits = 0;
  let value = 0;
  let out = '';
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      out += CROCKFORD[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
    value &= (1 << bits) - 1;
  }
  if (bits > 0) out += CROCKFORD[(value << (5 - bits)) & 31];
  return out;
}

/**
 * A fresh record id: 16 characters of Crockford base 32 from a CSPRNG. No uid, no family, no
 * date in it — the id is printed on a document that travels, and it must say nothing by itself.
 *
 * @param {function(number): !Buffer=} random Source of random bytes; `crypto.randomBytes` by
 *     default, replaceable in tests.
 * @return {string} The id.
 */
function newRecordId(random) {
  return crockford((random || crypto.randomBytes)(RECORD_ID_BYTES));
}

/**
 * The canonical form of a record id somebody typed or pasted: separators and spaces removed,
 * upper case, and Crockford's look-alikes folded (O→0, I and L→1). Returns '' for anything that
 * is not 16 characters of the alphabet afterwards.
 *
 * @param {*} input Whatever the caller sent — never trusted.
 * @return {string} The canonical id, or ''.
 */
function normalizeRecordId(input) {
  if (typeof input !== 'string' || input.length > 64) return '';
  const folded = input.toUpperCase()
      .replace(/[\s-]/g, '')
      .replace(/O/g, '0')
      .replace(/[IL]/g, '1');
  if (folded.length !== 16) return '';
  for (const ch of folded) {
    if (!CROCKFORD.includes(ch)) return '';
  }
  return folded;
}

/**
 * Whether [value] is a SHA-256 digest in lowercase hex.
 *
 * @param {*} value Candidate.
 * @return {boolean} True for exactly 64 characters of `[0-9a-f]`.
 */
function isSha256Hex(value) {
  return typeof value === 'string' && /^[0-9a-f]{64}$/.test(value);
}

/**
 * Whether [value] is a real calendar date written `YYYY-MM-DD`.
 *
 * @param {*} value Candidate.
 * @return {boolean} True for a valid ISO date; false for `2026-02-30`.
 */
function isIsoDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

/**
 * The other member of [familyId], seen from [uid] — or '' when the id does not name [uid] and one
 * other person. The same derivation `partnerFromFamilyId` in `index.js` performs, repeated here so
 * this module stands alone.
 *
 * @param {*} familyId Candidate family id.
 * @param {string} uid The caller.
 * @return {string} The co-parent's uid, or ''.
 */
function coParentIn(familyId, uid) {
  if (typeof familyId !== 'string' || !familyId) return '';
  const members = familyId.split('__');
  if (members.length !== 2 || members[0] === members[1] || !members.includes(uid)) return '';
  return members[0] === uid ? members[1] : members[0];
}

/**
 * A per-instance fixed-window counter. Best-effort by construction: it forgets on a cold start
 * and each instance counts alone, which [MAX_INSTANCES] turns into a bound on the service. It
 * stores nothing anywhere else — in particular no address reaches Firestore or a log.
 *
 * @param {number} limit Calls allowed per window per key.
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

/**
 * An error the callable wrapper turns into an `HttpsError`. Kept free of `firebase-functions` so
 * the implementations run in a plain mocha process.
 */
class ReceiptError extends Error {
  /**
   * @param {string} code An `HttpsError` code.
   * @param {string} reason A stable machine-readable reason the client can switch on.
   * @param {string} message For logs and developers; never shown to a user.
   */
  constructor(code, reason, message) {
    super(message);
    this.code = code;
    this.reason = reason;
  }
}

/**
 * Mints a record id for an export the caller is about to render.
 *
 * The caller must be a parent of [data.familyId] — decided from the id itself, like the rules'
 * `isFamilyMember`, and **not** from a live pairing: a parent who has unpaired is exactly the
 * parent most likely to need the record of what the two of them wrote. A blank family is an
 * account exporting with no co-parent; it vouches for nobody but its author.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore.
 * @param {string} uid The caller.
 * @param {!Object} data `{familyId, fromDate, toDate, format}`.
 * @param {{now: function(): !Object, random: (function(number): !Buffer|undefined)}} deps The
 *     server clock (a Firestore `Timestamp` factory) and the random source.
 * @return {Promise<{recordId: string}>} The reserved id.
 */
async function reserveImpl(db, uid, data, deps) {
  const input = data || {};
  const familyId = typeof input.familyId === 'string' ? input.familyId : '';
  if (familyId && !coParentIn(familyId, uid)) {
    throw new ReceiptError('permission-denied', 'not-a-parent', 'Caller is not a parent of that family');
  }
  if (!isIsoDate(input.fromDate) || !isIsoDate(input.toDate) || input.fromDate > input.toDate) {
    throw new ReceiptError('invalid-argument', 'bad-range', 'fromDate/toDate must be ISO dates, from <= to');
  }
  if (!FORMATS.includes(input.format)) {
    throw new ReceiptError('invalid-argument', 'bad-format', 'format must be pdf or csv');
  }
  // Collisions at 80 bits are not a practical concern; `create` refuses one anyway, and a retry
  // with a fresh id is cheaper than reasoning about it.
  for (let attempt = 0; attempt < 3; attempt++) {
    const recordId = newRecordId(deps.random);
    try {
      await db.collection(RECEIPTS).doc(recordId).create({
        state: 'reserved',
        generatorUid: uid,
        familyId,
        fromDate: input.fromDate,
        toDate: input.toDate,
        format: input.format,
        reservedAt: deps.now(),
        formatVersion: 1,
      });
      return {recordId};
    } catch (err) {
      if (err && (err.code === 6 || err.code === 'already-exists')) continue;
      throw err;
    }
  }
  throw new ReceiptError('internal', 'id-collision', 'Could not mint a unique record id');
}

/**
 * Registers the hash of the rendered file under a reserved id. **Create-once**: a receipt that
 * already holds a hash is never changed. Repeating the same hash — a retry after a lost
 * acknowledgement — returns the original registration rather than failing, so the phone can tell
 * "it landed" from "it was refused".
 *
 * `recordedAt` is the function's clock, never the caller's; it is what the verification page
 * prints as "registered at".
 *
 * @param {FirebaseFirestore.Firestore} db Firestore.
 * @param {string} uid The caller.
 * @param {!Object} data `{recordId, sha256, byteLength}`.
 * @param {{now: function(): !Object}} deps The server clock.
 * @return {Promise<{recordId: string, recordedAtMillis: number}>} The registration.
 */
async function registerImpl(db, uid, data, deps) {
  const input = data || {};
  const recordId = normalizeRecordId(input.recordId);
  if (!recordId) {
    throw new ReceiptError('invalid-argument', 'bad-record-id', 'recordId is malformed');
  }
  if (!isSha256Hex(input.sha256)) {
    throw new ReceiptError('invalid-argument', 'bad-sha256', 'sha256 must be 64 lowercase hex characters');
  }
  const byteLength = input.byteLength;
  if (!Number.isInteger(byteLength) || byteLength <= 0 || byteLength > MAX_BYTE_LENGTH) {
    throw new ReceiptError('invalid-argument', 'bad-length', 'byteLength must be a positive integer');
  }
  const ref = db.collection(RECEIPTS).doc(recordId);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const receipt = snap.exists ? snap.data() : null;
    if (!receipt || receipt.generatorUid !== uid) {
      // One answer for "no such id" and "somebody else's id": the second must not be probeable.
      throw new ReceiptError('not-found', 'no-reservation', 'No reservation of this id by the caller');
    }
    if (receipt.state === 'registered') {
      if (receipt.sha256 === input.sha256 && receipt.byteLength === byteLength) {
        return {recordId, recordedAtMillis: receipt.recordedAt.toMillis()};
      }
      throw new ReceiptError('already-exists', 'already-registered', 'This id already holds another hash');
    }
    const now = deps.now();
    if (now.toMillis() - receipt.reservedAt.toMillis() > RESERVATION_TTL_MS) {
      throw new ReceiptError('failed-precondition', 'reservation-expired', 'The reservation has expired');
    }
    tx.update(ref, {state: 'registered', sha256: input.sha256, byteLength, recordedAt: now});
    return {recordId, recordedAtMillis: now.toMillis()};
  });
}

/**
 * What a verifier is told about a registered receipt — and everything they are not.
 *
 * @param {string} recordId The id.
 * @param {!Object} receipt The stored receipt.
 * @return {!Object} The public view.
 */
function publicView(recordId, receipt) {
  return {
    found: true,
    recordId,
    recordedAtMillis: receipt.recordedAt.toMillis(),
    recordedAt: new Date(receipt.recordedAt.toMillis()).toISOString(),
    fromDate: receipt.fromDate,
    toDate: receipt.toDate,
    format: receipt.format,
    byteLength: receipt.byteLength,
    generatedBy: GENERATED_BY,
  };
}

/**
 * Answers a verifier, who has no account: by the file's hash, or by the record id printed on it.
 *
 * Returns only what [publicView] builds — never a uid, a family id, a name, or whether the
 * generating account still exists. A reservation that never received a hash is "not found": it
 * vouches for no file. See `docs/DESIGN-court-record.md` §10 for why each field is or is not here.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore.
 * @param {!Object} data `{sha256}` or `{recordId}`.
 * @return {Promise<!Object>} `{found: false}` or the public view.
 */
async function verifyImpl(db, data) {
  const input = data || {};
  if (input.sha256 !== undefined) {
    const sha256 = typeof input.sha256 === 'string' ? input.sha256.toLowerCase() : '';
    if (!isSha256Hex(sha256)) {
      throw new ReceiptError('invalid-argument', 'bad-sha256', 'sha256 must be 64 hex characters');
    }
    const snap = await db.collection(RECEIPTS)
        .where('sha256', '==', sha256)
        .limit(1)
        .get();
    const doc = snap.docs.find((d) => d.data().state === 'registered');
    return doc ? publicView(doc.id, doc.data()) : {found: false};
  }
  const recordId = normalizeRecordId(input.recordId);
  if (!recordId) {
    throw new ReceiptError('invalid-argument', 'bad-record-id', 'Send a sha256 or a recordId');
  }
  const snap = await db.collection(RECEIPTS).doc(recordId).get();
  const receipt = snap.exists ? snap.data() : null;
  return receipt && receipt.state === 'registered' ? publicView(recordId, receipt) : {found: false};
}

/**
 * What account deletion does to receipts: **scrub, never delete** a registered one.
 *
 * A receipt holds no content — a hash, a range, a format and two times — but `generatorUid` is
 * the departing parent's personal data, and so is a `familyId` that spells their uid. Both are
 * blanked. The hash stays, because the file it vouches for may already be in front of a court, and
 * erasing the parent's account must not un-verify the other parent's evidence. Reservations that
 * never received a hash vouch for nothing and are deleted.
 *
 * The co-parent's receipts are reached through [familyIds] — every family the departing account
 * was ever in that the caller can still name (live partners, surviving conversation threads, and
 * the families on the departing parent's own receipts) — because a receipt stores the family as
 * its id, not as an array a query could search for one uid.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore.
 * @param {string} uid The departing account.
 * @param {!Array<string>} familyIds Candidate families the account belonged to.
 * @return {Promise<{scrubbed: number, deleted: number}>} What changed.
 */
async function scrubReceipts(db, uid, familyIds) {
  const own = await db.collection(RECEIPTS).where('generatorUid', '==', uid).get();
  const families = new Set(familyIds.filter((id) => coParentIn(id, uid)));
  let scrubbed = 0;
  let deleted = 0;
  for (const doc of own.docs) {
    const receipt = doc.data();
    if (receipt.familyId) families.add(receipt.familyId);
    if (receipt.state === 'registered') {
      await db.collection(RECEIPTS).doc(doc.id).update({generatorUid: '', familyId: ''});
      scrubbed++;
    } else {
      await db.collection(RECEIPTS).doc(doc.id).delete();
      deleted++;
    }
  }
  for (const familyId of families) {
    const theirs = await db.collection(RECEIPTS).where('familyId', '==', familyId).get();
    for (const doc of theirs.docs) {
      await db.collection(RECEIPTS).doc(doc.id).update({familyId: ''});
      scrubbed++;
    }
  }
  return {scrubbed, deleted};
}

/**
 * The caller's address for rate limiting, from the callable's raw request. Best-effort: behind
 * Google's front end `req.ip` is the client, and a missing value falls into one shared bucket
 * rather than an unlimited one.
 *
 * @param {?Object} context The `onCall` context.
 * @return {string} The key.
 */
function clientKey(context) {
  const req = context && context.rawRequest;
  const forwarded = req && req.headers && req.headers['x-forwarded-for'];
  const first = typeof forwarded === 'string' ? forwarded.split(',')[0].trim() : '';
  return (req && req.ip) || first || 'unknown';
}

module.exports = {
  RECEIPTS,
  RESERVATION_TTL_MS,
  VERIFY_RATE_LIMIT,
  RESERVE_RATE_LIMIT,
  RATE_WINDOW_MS,
  MAX_INSTANCES,
  GENERATED_BY,
  ReceiptError,
  crockford,
  newRecordId,
  normalizeRecordId,
  isSha256Hex,
  isIsoDate,
  coParentIn,
  rateLimiter,
  reserveImpl,
  registerImpl,
  verifyImpl,
  scrubReceipts,
  clientKey,
};
