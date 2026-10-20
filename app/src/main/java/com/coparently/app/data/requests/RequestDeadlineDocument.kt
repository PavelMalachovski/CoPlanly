package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What a request's clock looks like on the wire, defined once (phase 3a, spec §5.1).
 *
 * `request_deadlines/{id}` is one small document beside every day swap, change request, event
 * awaiting acceptance and custody proposal. `firestore.rules` is the other half of this definition:
 * its create set, its five update shapes and the field each phone writes are spelled out there.
 * This file is the vocabulary the client shares between its writers and its reader, so the field
 * names cannot drift between them.
 *
 * **The wire form is hand-written maps, not a reflected model.** A Gson model would need a keep
 * rule and a case in the R8 probe; a map needs neither, and the one place that reads it
 * ([parse]) is defensive about every value.
 *
 * **Server times are not in the maps.** `createdAt`, `lastChanceAt` and `closedAt` are Firestore
 * server timestamps, pinned by the rules to `request.time`. This file stays free of Firebase so it
 * can be tested on the JVM, so the data source stamps them on the way out
 * (`FieldValue.serverTimestamp()`) and reads them on the way in ([ServerTimes]).
 */
object RequestDeadlineDocument {

    /** The collection. Top-level, so a request's own record is never touched to carry a clock. */
    const val COLLECTION = "request_deadlines"

    /** `swap`, `change`, `event`, `proposal` or `split`: [RequestKind.wire]. Written by the proposer, at create. */
    const val KIND = "kind"

    /**
     * The request the clock times: a swap's group id, a change request's or event's id, a proposal's
     * time, or a split proposal's time in epoch millis as decimal text.
     */
    const val REF = "ref"

    /** The family the request was made in. Whatever works from the clock acts in this family. */
    const val FAMILY_ID = "familyId"

    /** The parent who made the request and chose the deadline. */
    const val PROPOSER_UID = "proposerUid"

    /** The parent whose answer is due. */
    const val RESPONDER_UID = "responderUid"

    /** Both parents, sorted: what the list query and the read rule key on. */
    const val PARTICIPANTS = "participants"

    /** A server timestamp: the rule requires it to equal `request.time`. */
    const val CREATED_AT = "createdAt"

    /** The deadline, in epoch millis. */
    const val ANSWER_BY_MILLIS = "answerByMillis"

    /** The first moment the request would change something, in epoch millis. Absent when nothing would. */
    const val CAP_MILLIS = "capMillis"

    /** A swap's first day, ISO. Absent for every other kind. */
    const val FIRST_DATE = "firstDate"

    /** How many days a swap covers. Absent for every other kind. */
    const val DAY_COUNT = "dayCount"

    /** When the answering parent's phone first received the clock. Written once, with the plan. */
    const val DELIVERED_AT_MILLIS = "deliveredAtMillis"

    /** How many reminders that phone planned, none to three. Written once, with the plan. */
    const val REMINDERS_PLANNED = "remindersPlanned"

    /** When it planned them for, in epoch millis. Written once, with the plan. */
    const val REMINDER_TIMES_MILLIS = "reminderTimesMillis"

    /** When the answering parent first opened the request. Written once. */
    const val SEEN_AT_MILLIS = "seenAtMillis"

    /** How many of the planned reminders that phone has shown. Raised by one each time. */
    const val REMINDERS_SHOWN = "remindersShown"

    /** A server timestamp: when the proposer gave a last chance. Written once, with [LAST_CHANCE_UNTIL_MILLIS]. */
    const val LAST_CHANCE_AT = "lastChanceAt"

    /** Where the last chance ends, in epoch millis. Its presence is what says one was given. */
    const val LAST_CHANCE_UNTIL_MILLIS = "lastChanceUntilMillis"

    /** How the clock was closed: [RequestClockOutcome.wire]. Written once, with [CLOSED_AT]. */
    const val OUTCOME = "outcome"

    /** A server timestamp: when the proposer closed the clock. Written once, with [OUTCOME]. */
    const val CLOSED_AT = "closedAt"

    /**
     * The three server timestamps of a stored clock, already converted to epoch millis by the
     * caller (a Firestore `Timestamp` is not available on the JVM this is tested on).
     *
     * @property createdAtMillis When the server created the clock.
     * @property lastChanceAtMillis When the server recorded the last chance.
     * @property closedAtMillis When the server recorded the closure.
     */
    data class ServerTimes(
        val createdAtMillis: Long? = null,
        val lastChanceAtMillis: Long? = null,
        val closedAtMillis: Long? = null
    )

    /**
     * The fields the proposer writes when it creates the clock for [start]: the keys the rule
     * requires and the optional ones that apply, and never [CREATED_AT], which only the data source
     * can stamp.
     *
     * An optional field that does not apply is **absent, not null**: the rule refuses a key it did
     * not expect. [PARTICIPANTS] is the two parents, sorted.
     *
     * @param start What the sender knows when it starts the clock.
     * @param proposerUid The signed-in parent, who writes the clock; the rule binds it to the caller.
     */
    fun createFields(start: RequestStart, proposerUid: String): Map<String, Any> = buildMap {
        put(KIND, start.kind.wire)
        put(REF, start.ref)
        put(FAMILY_ID, start.familyId)
        put(PROPOSER_UID, proposerUid)
        put(RESPONDER_UID, start.responderUid)
        put(PARTICIPANTS, listOf(proposerUid, start.responderUid).sorted())
        put(ANSWER_BY_MILLIS, start.answerByMillis)
        start.capMillis?.let { put(CAP_MILLIS, it) }
        start.firstDate?.let { put(FIRST_DATE, DateTimeFormatter.ISO_LOCAL_DATE.format(it)) }
        start.dayCount?.let { put(DAY_COUNT, it) }
    }

    /**
     * The one write in which the answering parent's phone records that it received the clock and
     * what it planned: all three keys together, as the rule requires, and written once.
     *
     * [REMINDERS_PLANNED] is the length of [reminderTimesMillis], so the two cannot disagree. A clock
     * received too late to plan anything still records its delivery, with none planned.
     *
     * @param deliveredAtMillis When the phone received the clock, by its own clock.
     * @param reminderTimesMillis The reminders it planned, in epoch millis; empty for none.
     */
    fun deliveredFields(deliveredAtMillis: Long, reminderTimesMillis: List<Long>): Map<String, Any> = mapOf(
        DELIVERED_AT_MILLIS to deliveredAtMillis,
        REMINDERS_PLANNED to reminderTimesMillis.size,
        REMINDER_TIMES_MILLIS to reminderTimesMillis
    )

    /**
     * Reads a stored clock, or null when this build cannot use it.
     *
     * Null rather than a guess, for a kind this build does not know and for a clock missing a field
     * nothing can be done without (who made it, who must answer, in which family, until when): a
     * request this build cannot time is shown as it always was. Everything optional that cannot be
     * read reads as absent, and a field this build does not know is ignored.
     *
     * Numbers come back from the store as whatever type it chooses, so each is read through
     * [Number]. This runs inside a snapshot listener, over a document the other parent can write to
     * within the rules: a cast that threw there would be a crash the caller's retry cannot see.
     *
     * Times are read **as they are**. The rules let a phone whose clock runs slow stamp its delivery
     * or opening up to five minutes before [CREATED_AT], and nothing here corrects that.
     *
     * An outcome this build does not know is not a closure: the clock reads as unclosed, with no
     * [RequestDeadline.closedAtMillis] either, and is judged by its times alone.
     *
     * @param id The document id.
     * @param document The raw document. The three server timestamps in it are not read here.
     * @param times The server timestamps, converted by the caller.
     */
    fun parse(id: String, document: Map<String, Any?>, times: ServerTimes): RequestDeadline? {
        val kind = RequestKind.fromWire(document.text(KIND))
        val answerBy = document.long(ANSWER_BY_MILLIS)
        val ref = document.text(REF)
        if (kind == null || answerBy == null || ref == null) return null
        val familyId = document.text(FAMILY_ID)
        val proposerUid = document.text(PROPOSER_UID)
        val responderUid = document.text(RESPONDER_UID)
        if (familyId == null || proposerUid == null || responderUid == null) return null
        val outcome = RequestClockOutcome.fromWire(document.text(OUTCOME))
        return RequestDeadline(
            id = id,
            kind = kind,
            ref = ref,
            familyId = familyId,
            proposerUid = proposerUid,
            responderUid = responderUid,
            answerByMillis = answerBy,
            capMillis = document.long(CAP_MILLIS),
            createdAtMillis = times.createdAtMillis,
            firstDate = document.date(FIRST_DATE),
            dayCount = document.int(DAY_COUNT),
            deliveredAtMillis = document.long(DELIVERED_AT_MILLIS),
            remindersPlanned = document.int(REMINDERS_PLANNED),
            reminderTimesMillis = (document[REMINDER_TIMES_MILLIS] as? List<*>).orEmpty()
                .mapNotNull { (it as? Number)?.toLong() },
            seenAtMillis = document.long(SEEN_AT_MILLIS),
            remindersShown = document.int(REMINDERS_SHOWN) ?: 0,
            lastChanceAtMillis = times.lastChanceAtMillis,
            lastChanceUntilMillis = document.long(LAST_CHANCE_UNTIL_MILLIS),
            outcome = outcome,
            closedAtMillis = times.closedAtMillis.takeIf { outcome != null }
        )
    }

    /** [key] as text, or null when it is absent, blank or not a string. */
    private fun Map<String, Any?>.text(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

    /** [key] as a whole number, whichever number type the store handed over; null when it is not one. */
    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    /** [key] as a small whole number; null when it is not one. */
    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    /** [key] as an ISO date; null when it is absent or does not parse. */
    private fun Map<String, Any?>.date(key: String): LocalDate? =
        text(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
