'use strict';

/**
 * The bound on how many wrong invitation codes one account may try.
 *
 * An invitation code is six characters of a 31-character alphabet — about 8.9×10⁸ values — and
 * a bearer credential: a co-parent code makes whoever redeems it a parent of the family at once,
 * with the children's records, medical profiles, chat and money, and a guest code opens one
 * child's record. Before this, the four redeem callables (`acceptPairingInvitation`,
 * `acceptGuestInvitation`, `acceptCalendarFriendInvitation`, `acceptProfessionalInvitation`)
 * had no limit at all, and the preview's was in memory, per instance: a signed-in account could
 * loop guesses for the price of an invocation and a read each.
 *
 * So every miss is counted on the server, per account, in `redeem_attempts/{uid}` — closed to
 * every client (no `match` in `firestore.rules`, so Firestore's default denies), one window and
 * one count, deleted with the account. A caller over the limit is refused before any invitation
 * is read, with `resource-exhausted` and the reason `too-many-attempts`, by all five callables:
 * a code found through one of them redeems through another, so one counter serves all.
 *
 * Only a miss counts — an unknown code (`not-found`). A code that exists but is refused for
 * another reason (expired, wrong kind, already paired) was a hit, and refusing a parent who
 * retries a real code would punish the wrong person. A parent who mistypes their co-parent's
 * code a few times is far inside the limit; a sweep is not: at ten misses an hour, finding one
 * of a hundred live codes takes about a thousand account-years.
 *
 * What this does not stop is a sweep spread over many accounts. That is App Check's job (a
 * console step, `docs/SECURITY-AUDIT-2026-10.md`), and a longer code's for the link and QR paths.
 */

/** The server-only collection holding each account's count of wrong codes. */
const REDEEM_ATTEMPTS_COLLECTION = 'redeem_attempts';

/** Wrong codes one account may try per window. */
const REDEEM_FAILURE_LIMIT = 10;

/** The window the limit counts over. */
const REDEEM_FAILURE_WINDOW_MS = 60 * 60 * 1000;

/** The `reason` of the refusal, which the app may map to its own words. */
const TOO_MANY_ATTEMPTS = 'too-many-attempts';

/**
 * The count that applies at [nowMillis]: the stored one while its window is open, else none.
 *
 * @param {?Object} stored `redeem_attempts/{uid}`'s data, or null.
 * @param {number} nowMillis The server's clock.
 * @return {{windowStartMillis: number, failures: number}} The live window.
 */
function liveWindow(stored, nowMillis) {
  const start = stored && Number.isFinite(stored.windowStartMillis) ? stored.windowStartMillis : 0;
  const failures = stored && Number.isInteger(stored.failures) ? stored.failures : 0;
  if (start <= 0 || start > nowMillis || nowMillis - start >= REDEEM_FAILURE_WINDOW_MS) {
    return {windowStartMillis: nowMillis, failures: 0};
  }
  return {windowStartMillis: start, failures};
}

/**
 * Whether [error] says the code named no invitation — a guess that missed.
 *
 * @param {*} error What the redeem threw.
 * @return {boolean} True for a `not-found` refusal.
 */
function isMiss(error) {
  if (!error) return false;
  const reason = error.details && error.details.reason;
  return error.code === 'not-found' && (reason === undefined || reason === 'not-found');
}

/**
 * Runs one redeem for [uid] under the limit: refuses it when the account already missed too
 * often, and counts it when it misses.
 *
 * @param {FirebaseFirestore.Firestore} db Firestore instance (admin).
 * @param {string} uid The signed-in caller.
 * @param {function(): Promise<*>} redeem The callable's body.
 * @param {{HttpsError: Function}} https `functions.https`, for the refusal.
 * @param {number=} nowMillis The server's clock; injectable for tests.
 * @return {Promise<*>} What [redeem] returned.
 */
async function guardRedeem(db, uid, redeem, https, nowMillis = Date.now()) {
  const ref = db.collection(REDEEM_ATTEMPTS_COLLECTION).doc(uid);
  const snap = await ref.get();
  const window = liveWindow(snap.exists ? snap.data() : null, nowMillis);
  if (window.failures >= REDEEM_FAILURE_LIMIT) {
    throw new https.HttpsError(
        'resource-exhausted', 'Too many wrong codes; try again later', {reason: TOO_MANY_ATTEMPTS});
  }
  try {
    return await redeem();
  } catch (error) {
    if (isMiss(error)) {
      try {
        // A transaction, so two misses at once both count. The window is re-read inside it.
        await db.runTransaction(async (tx) => {
          const current = await tx.get(ref);
          const live = liveWindow(current.exists ? current.data() : null, nowMillis);
          tx.set(ref, {windowStartMillis: live.windowStartMillis, failures: live.failures + 1});
        });
      } catch (countError) {
        // The caller is owed the redeem's own answer, not the counter's failure.
        console.error('redeem-guard: could not count a miss', {uid, error: String(countError)});
      }
    }
    throw error;
  }
}

module.exports = {
  REDEEM_ATTEMPTS_COLLECTION,
  REDEEM_FAILURE_LIMIT,
  REDEEM_FAILURE_WINDOW_MS,
  TOO_MANY_ATTEMPTS,
  guardRedeem,
  isMiss,
  liveWindow,
};
