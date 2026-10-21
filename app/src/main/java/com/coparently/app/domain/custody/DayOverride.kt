package com.coparently.app.domain.custody

/**
 * Where a single day's override stands.
 *
 * Only [ACCEPTED] changes whose day it is. [PENDING], [DECLINED] and [CANCELLED] are markers — the
 * calendar draws a pending one, but `CustodyResolver` steps straight past them to the agreed pattern.
 * Letting a pending swap move a day would show both parents a day neither of them agreed to.
 */
enum class DayOverrideStatus {
    /** Offered, and waiting for the other parent's answer. */
    PENDING,

    /** Agreed. This is the only status that moves a day. */
    ACCEPTED,

    /** Turned down. Kept so the parent who offered learns the outcome. */
    DECLINED,

    /**
     * Withdrawn by the parent who offered it, before it was answered (October 2026). Moves nothing:
     * a day whose earlier offer was agreed stays where that agreement put it
     * ([DayOverride.agreedToParent]). Kept, not deleted, so History says it was taken back and the
     * agreement it would have replaced stays on the record.
     *
     * An older build reads an entry in this state as unknown and drops it, as it drops any status it
     * cannot name — and then cannot write a swap while the document holds one (CLAUDE.md item 44).
     */
    CANCELLED
}

/**
 * One day offered to the other parent, outside the agreed pattern.
 *
 * Deliberately not a `ChangeRequest` and not a `CustodyProposal`. A `ChangeRequest` points at an
 * `eventId`, and a custody day is a property of the pattern with no row to point at. A
 * `CustodyProposal` replaces the whole pattern, so expressing "just next Saturday" as one would
 * mean inventing a pattern that differs in a single day and never repeats — and accepting it
 * would overwrite the arrangement the parents agreed.
 *
 * Carried in a map keyed by ISO date on the pair's shared `custody_models` document. A map rather
 * than a list because one override per date *is* the semantics: "we swap next Saturday" cannot
 * mean two things at once, and a map makes that structurally true instead of a rule some caller
 * has to remember. It also makes the calendar's lookup an O(1) read on a date it already holds,
 * for every cell it draws.
 *
 * @property toParent The slot taking the day — `"mom"` or `"dad"`, the two schema slot ids that
 *   are never renamed. Never a display name: `ParentLabels` resolves those.
 * @property requestedBy Firebase UID of the parent who offered the day. `firestore.rules` requires
 *   this to be the caller, and it is what stops either parent deciding their own offer.
 * @property requestedAt ISO date-time string, as everywhere else in this Firestore schema — dates
 *   cross the wire as strings, not as Firestore timestamps.
 * @property status Where the offer stands; see [DayOverrideStatus].
 * @property decidedBy Firebase UID of whoever answered — or, for [DayOverrideStatus.CANCELLED], of
 *   the parent who offered and withdrew it — or null while [status] is [DayOverrideStatus.PENDING].
 * @property decidedAt ISO date-time of that answer or withdrawal, or null while pending.
 * @property note Optional free text from either side. Blank is stored as null rather than as an
 *   empty string, so the UI has one thing to test for.
 * @property groupId Ties the days of one offer together, or null for a single day an older build
 *   offered and for every entry written before groups existed. Since phase 3a a single day offered
 *   by this build is a group of one, because the offer's clock (`request_deadlines`) is named by
 *   its group id.
 *
 *   Grouping is a **client-side** fact and deliberately so. `firestore.rules` can validate a diff
 *   that names exactly one date (Rules cannot iterate a map), so a run of days is still written
 *   one date at a time and stays one entry per date — which is the semantics the map exists to
 *   make structurally true. What the group id buys is the half the reporter actually asked for:
 *   the co-parent gets one card, one dialog and one push saying "5 days", instead of five of
 *   each.
 *
 *   A null id means "its own group of one", which is what makes every pre-existing entry read
 *   correctly with no migration and no backfill.
 * @property earlier The answers this day was given before it was offered again, as
 *   [DayOverrideDecisionCodec] strings, oldest first; null for none. Nullable rather than an empty
 *   default because Room's Gson reads an entry written before the field existed without running
 *   the constructor. Carried verbatim by every answer (`copy()`), and by a new offer of the day,
 *   which appends the answer it replaces ([DayOverrideTransition.offer]).
 */
data class DayOverride(
    val toParent: String,
    val requestedBy: String,
    val requestedAt: String,
    val status: DayOverrideStatus = DayOverrideStatus.PENDING,
    val decidedBy: String? = null,
    val decidedAt: String? = null,
    val note: String? = null,
    val groupId: String? = null,
    val earlier: List<String>? = null
) {
    /**
     * The answers this day was given before it was offered again, oldest first, decoded from
     * [earlier]. An entry this build cannot read is left out here and kept verbatim on the wire.
     */
    val earlierDecisions: List<DayOverrideDecision>
        get() = earlier.orEmpty().mapNotNull(DayOverrideDecisionCodec::decode)

    /** True while this offer is still waiting for an answer. */
    val isPending: Boolean get() = status == DayOverrideStatus.PENDING

    /** True when this offer actually moves the day. */
    val isAccepted: Boolean get() = status == DayOverrideStatus.ACCEPTED

    /**
     * The slot the day goes to by what the two parents last agreed: this offer's own [toParent]
     * once accepted, otherwise the latest accepted answer in [earlier], otherwise null.
     *
     * An agreement stands until another one replaces it. Asking to change an agreed swap writes a
     * new pending offer over the entry, and while it waits — or once it is declined or withdrawn —
     * the day stays where the parents agreed it would be, not where the pattern would put it.
     * This is what [CustodyResolver] answers from.
     */
    val agreedToParent: String?
        get() = if (isAccepted) {
            toParent
        } else {
            earlierDecisions.lastOrNull { it.status == DayOverrideStatus.ACCEPTED }?.toParent
        }
}
