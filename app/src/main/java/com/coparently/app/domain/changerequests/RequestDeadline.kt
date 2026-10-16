package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * The kinds of request that get a clock (phase 3a, spec §5.1).
 *
 * [wire] is what `request_deadlines/{id}.kind` stores and what the first part of the clock's id
 * spells, so it is part of the stored schema and is never renamed.
 *
 * @property wire The name stored in the clock and spelled in its id.
 */
enum class RequestKind(val wire: String) {
    /** A day-swap offer, however many days it covers. */
    SWAP("swap"),

    /** A change request: a new time for an event. */
    CHANGE("change"),

    /** An event created for the other parent's days, waiting for their acceptance. */
    EVENT("event"),

    /** A custody proposal: a new schedule. */
    PROPOSAL("proposal");

    companion object {
        /**
         * The kind stored as [value]; null for a missing one, and for one this build does not know:
         * a newer build may time kinds of request that this one cannot show.
         */
        fun fromWire(value: String?): RequestKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * How a clock was closed (spec §4.4, §5.1). This is the only state a clock stores: every other
 * state is derived from the times, so it cannot disagree with them.
 *
 * @property wire The name stored in the clock's `outcome`.
 */
enum class RequestClockOutcome(val wire: String) {
    /** Nobody answered: the last chance ran out, or the button's own time ran out unused. */
    NO_ANSWER("no_answer");

    companion object {
        /**
         * The outcome stored as [value]; null for a missing one, and for one this build does not
         * know. A clock read that way is judged by its times alone.
         */
        fun fromWire(value: String?): RequestClockOutcome? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One request's clock: the document `request_deadlines/{id}` as this build reads it (phase 3a,
 * spec §5.1).
 *
 * The clock is a document beside the request rather than a field on it, because the request's own
 * record cannot take one: a change request's update rule admits three fields, the custody
 * document's swap and proposal writes are closed lists, and an older build rewrites a whole change
 * request when it answers, so a new key there would make that answer fail or be dropped. An older
 * build never reads or writes this document, and a request with no clock — sent before this
 * feature, or by an older build — behaves exactly as it always did: open until answered.
 *
 * Only the end is stored ([outcome]). Whether the request is open, past its deadline, in its last
 * chance or over is derived on each phone by [RequestClock] from these fields and the phone's
 * clock. A clock whose request is gone and which has no [outcome] shows nothing: its request was
 * replaced.
 *
 * @property id The document id, [RequestDeadlineId.of], so both phones find it without asking.
 * @property kind What kind of request the clock times.
 * @property ref The request it times: a swap's group id, a change request's id, an event's id, or a
 *   proposal's `proposedAt`.
 * @property familyId The family the request was made in. Whatever works from the clock — a reminder,
 *   the end — acts in this family, never in the one on screen.
 * @property proposerUid The parent who made the request and set the deadline.
 * @property responderUid The parent whose answer is due.
 * @property answerByMillis The deadline, in epoch millis.
 * @property capMillis The first moment the request would change something — a swap's first day, an
 *   event's start, the first changed day of a proposal — or null when nothing ahead would change.
 *   A last chance ends before it (see [RequestClock]).
 * @property createdAtMillis The server's time when the clock was created. Read with the server's
 *   estimate, so on the proposer's phone it is that phone's estimate, not null, until the server
 *   has answered; null only for a document that carries no such field.
 * @property firstDate A swap's first day, so a "No answer" row can still say which days the offer
 *   covered after the offer itself has been replaced; null for every other kind.
 * @property dayCount How many days a swap's offer covered; null for every other kind.
 * @property deliveredAtMillis When the answering parent's phone first received the clock. Written
 *   once, together with [remindersPlanned] and [reminderTimesMillis]; null until then.
 * @property remindersPlanned How many reminders that phone planned, none to three; null until it
 *   has planned.
 * @property reminderTimesMillis When that phone planned the reminders for, worked out in its own
 *   time zone; empty until it has planned.
 * @property seenAtMillis When the answering parent first opened the request; written once.
 * @property remindersShown How many of the planned reminders that phone has shown.
 * @property lastChanceAtMillis The server's time when the proposer gave a last chance; null until
 *   they do. On the proposer's phone it is that phone's estimate until the server has answered.
 * @property lastChanceUntilMillis When the last chance ends. Its presence is what says a last chance
 *   was given: the proposer's phone chooses it itself, so it is the one field that never depends
 *   on the server's stamp.
 * @property outcome How the clock was closed, or null while it has not been.
 * @property closedAtMillis The server's time when the proposer's phone closed the clock; null before
 *   then. On the proposer's phone it is that phone's estimate until the server has answered.
 */
data class RequestDeadline(
    val id: String,
    val kind: RequestKind,
    val ref: String,
    val familyId: String,
    val proposerUid: String,
    val responderUid: String,
    val answerByMillis: Long,
    val capMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val firstDate: LocalDate? = null,
    val dayCount: Int? = null,
    val deliveredAtMillis: Long? = null,
    val remindersPlanned: Int? = null,
    val reminderTimesMillis: List<Long> = emptyList(),
    val seenAtMillis: Long? = null,
    val remindersShown: Int = 0,
    val lastChanceAtMillis: Long? = null,
    val lastChanceUntilMillis: Long? = null,
    val outcome: RequestClockOutcome? = null,
    val closedAtMillis: Long? = null
)

/**
 * What a sender knows when it starts a clock: the request, the family it is made in, who must
 * answer, and the two times that come from the sender's choice and the request's own dates.
 *
 * It carries no proposer: that is whoever writes the clock, the signed-in parent, and the rules
 * bind the clock to the caller (spec §5.1, §5.2).
 *
 * @property kind What kind of request the clock times.
 * @property ref The request it times, as [RequestDeadline.ref] says.
 * @property familyId The family on screen when the request was made (spec §5.6).
 * @property responderUid The co-parent whose answer is due.
 * @property answerByMillis The deadline the sender chose.
 * @property capMillis The first moment the request would change something, or null when nothing
 *   ahead would.
 * @property firstDate A swap's first day; null for every other kind.
 * @property dayCount How many days a swap covers; null for every other kind.
 */
data class RequestStart(
    val kind: RequestKind,
    val ref: String,
    val familyId: String,
    val responderUid: String,
    val answerByMillis: Long,
    val capMillis: Long?,
    val firstDate: LocalDate? = null,
    val dayCount: Int? = null
)

/**
 * The id of a request's clock, derived from the request so that both phones find the document
 * without asking (phase 3a, spec §5.1):
 *
 * - `swap_<proposerUid>_<groupId>`
 * - `change_<proposerUid>_<changeRequestId>`
 * - `event_<proposerUid>_<eventId>`
 * - `proposal_<proposerUid>_<familyId>_<proposedAt>`
 *
 * The proposer's uid is in the id because the other parent can see the rest of it. With it the
 * security rules can bind the id to the caller, so the other parent can neither create a request's
 * clock first and strip its deadline, nor fake an early "No answer". The parts are joined with
 * `_`, which a Firebase uid never contains (see [com.coparently.app.domain.family.FamilyKey]), so
 * no two proposers can spell the same id; the rules rebuild the id the same way.
 *
 * A proposal's id also carries its family, because its reference is a timestamp, unique only
 * within one family's custody document. The other kinds' references are random ids.
 */
object RequestDeadlineId {

    private const val SEPARATOR = "_"

    /** The family stands in the id of a proposal only; the other kinds pass this. */
    private const val NO_FAMILY = ""

    /**
     * The id for [kind] made by [proposerUid] about [ref], as [RequestDeadline.ref] defines it.
     *
     * @param familyId Used by a [RequestKind.PROPOSAL] only; the other kinds ignore it.
     */
    fun of(kind: RequestKind, proposerUid: String, ref: String, familyId: String): String {
        val parts = when (kind) {
            RequestKind.PROPOSAL -> listOf(kind.wire, proposerUid, familyId, ref)
            RequestKind.SWAP, RequestKind.CHANGE, RequestKind.EVENT -> listOf(kind.wire, proposerUid, ref)
        }
        return parts.joinToString(SEPARATOR)
    }

    /**
     * The id of an offer's clock. Every day of an offer shares its author and its group id, so the
     * first day speaks for all of them.
     *
     * @return Null for an offer with no group id — one sent before a one-day swap carried one — which
     *   has no clock.
     */
    fun forSwap(group: DaySwapGroup): String? {
        val offer = group.swaps.first().override
        val groupId = offer.groupId ?: return null
        return of(RequestKind.SWAP, offer.requestedBy, groupId, NO_FAMILY)
    }

    /** The id of a change request's clock; the parent who asked is the proposer. */
    fun forChange(request: ChangeRequest): String =
        of(RequestKind.CHANGE, request.requestedBy, request.id, NO_FAMILY)

    /**
     * The id of the clock of an event waiting for an answer; its creator is the proposer.
     *
     * @return Null for an event with no creator, which no clock can name.
     */
    fun forEvent(event: Event): String? {
        val creator = event.createdByFirebaseUid?.takeIf { it.isNotBlank() } ?: return null
        return of(RequestKind.EVENT, creator, event.id, NO_FAMILY)
    }

    /**
     * The id of a custody proposal's clock in [familyId]; the proposer is
     * [CustodyProposal.proposedBy] and the reference its `proposedAt`.
     */
    fun forProposal(proposal: CustodyProposal, familyId: String): String =
        of(RequestKind.PROPOSAL, proposal.proposedBy, proposal.proposedAt, familyId)
}
