package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What an export holds, before it is a CSV or a PDF (MON-3).
 *
 * **A communication record, not a truth record** — the owner's answer to MON-4
 * (`docs/DESIGN-court-record.md` §9). Everything here is something a parent recorded or wrote in
 * the app, with the time it was recorded; nothing here asserts that it happened. Both renderers
 * print the statement that says so before anything else, and neither may drop it.
 *
 * Built by [CommunicationRecordBuilder] from plain inputs, so the whole of what the export
 * contains is decided in code the JVM tests can reach; `CommunicationRecordCsv` and the PDF
 * renderer only lay it out.
 *
 * @property from First day covered, inclusive, in [zone].
 * @property to Last day covered, inclusive, in [zone].
 * @property zone The zone every instant is printed in — the reader's.
 * @property generatedAtMillis When the export was made.
 * @property parents The two parents' names, as the app shows them — never "Mom" or "Dad".
 * @property complete False when the server could not be reached — for any part of the record, the
 *   parenting plan included — and the record holds only what this phone had. Printed on the
 *   document's face, never hidden.
 * @property events Calendar entries whose dates touch the range, each with its whole history.
 * @property messages Messages sent in the range, oldest first.
 * @property expenses Expenses dated in the range, oldest first.
 * @property plan The family's parenting plan as it stands at export time, or null when the parent
 *   chose to leave it out. Not bound to [from]…[to]: a plan has no period.
 * @property journal The exporting parent's own private journal entries about days in the period,
 *   or null when they did not ask for it — which is the default (MON-22).
 * @property verification Whether the file carries a registered record id (MON-16). The builder
 *   never decides this — it is set by the export flow once the server has reserved an id, and a
 *   record is [RecordVerification.Unregistered] until then, so nothing claims verifiability by
 *   default.
 */
data class CommunicationRecord(
    val from: LocalDate,
    val to: LocalDate,
    val zone: ZoneId,
    val generatedAtMillis: Long,
    val parents: List<String>,
    val complete: Boolean,
    val events: List<RecordEvent>,
    val messages: List<RecordMessage>,
    val expenses: List<RecordExpense>,
    val plan: RecordPlan? = null,
    val journal: RecordJournal? = null,
    val verification: RecordVerification = RecordVerification.Unregistered
)

/**
 * One calendar entry and every revision of it the export may show.
 *
 * @property eventId The event's id, so a reader can match revisions across the document.
 * @property revisions Oldest first, numbered from 1. For an entry saved before revisions were
 *   kept, a single entry with [RecordRevision.kind] null — its current state, labelled as such.
 */
data class RecordEvent(
    val eventId: String,
    val revisions: List<RecordRevision>
) {
    /** Whether this entry has a kept history, or only its current state. */
    val hasHistory: Boolean get() = revisions.any { it.kind != null }
}

/**
 * One saved revision of a calendar entry, with both clocks.
 *
 * @property number 1…n within its event, in the order the server received them. Not stored
 *   anywhere — see `docs/DESIGN-court-record.md` §4 for why.
 * @property kind What the revision recorded, or null for an entry's current state when it has no
 *   kept history.
 * @property byName Who saved it, by name.
 * @property deviceTimeMillis When their device says they saved it; null for a current state.
 * @property recordedAtMillis When the server received it; null when it has not.
 * @property delivered False for a revision still waiting on this phone to be uploaded.
 * @property parentName Whose custody day the entry was on, by name — [EventFacts.parentSlot]
 *   resolved through `ParentLabels`, never printed as a slot.
 */
data class RecordRevision(
    val number: Int,
    val kind: EventVersionKind?,
    val byName: String,
    val deviceTimeMillis: Long?,
    val recordedAtMillis: Long?,
    val delivered: Boolean,
    val facts: EventFacts,
    val parentName: String
)

/**
 * The parts of an event a record prints.
 *
 * Start and end are the wall-clock times the parent entered, with no zone — which is what the
 * events schema stores, and the export says so rather than inventing an offset.
 *
 * @property parentSlot The custody slot the event belongs to (`"mom"`/`"dad"`); printed only as
 *   that parent's name.
 */
data class EventFacts(
    val title: String,
    val description: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val parentSlot: String,
    val recurrence: String,
    val recurrenceEnd: LocalDate?
) {
    /** Whether any day of this entry falls in [from]…[to], a repeating one included. */
    fun touches(from: LocalDate, to: LocalDate): Boolean {
        val first = start?.toLocalDate() ?: return false
        val last = when {
            recurrence.isNotBlank() -> recurrenceEnd ?: LocalDate.MAX
            else -> end?.toLocalDate() ?: first
        }
        return !first.isAfter(to) && !last.isBefore(from)
    }
}

/**
 * One chat message.
 *
 * @property sentAtMillis The sender's device time — messages carry no server stamp, and the
 *   record's statement says so.
 * @property delivered False for a message this phone never managed to send.
 * @property citedPlanQuestionId For a schedule-proposal card built from the parenting plan
 *   (MON-21), the id of the plan question whose agreed answer the proposal cited **when it was
 *   made**; null otherwise. A fact about the proposal, not about the plan today: the record does
 *   not claim the answer still reads the same.
 */
data class RecordMessage(
    val messageId: String,
    val senderName: String,
    val sentAtMillis: Long,
    val text: String,
    val delivered: Boolean,
    val citedPlanQuestionId: String? = null
)

/**
 * One expense, as it stands today — expenses are not versioned (design §4), and the statement
 * says that too.
 */
data class RecordExpense(
    val expenseId: String,
    val date: LocalDate,
    val title: String,
    val amount: Double,
    val currency: String,
    val paidByName: String,
    val recordedByName: String,
    val notes: String
)
