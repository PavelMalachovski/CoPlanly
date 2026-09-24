package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.parentingplan.PlanCitationCodec
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One saved revision as the export reads it — from the server, or still in this phone's outbox.
 *
 * @property versionId The revision's document id; the same revision seen from both places is
 *   printed once.
 * @property recordedAtMillis When the server received it, or null for one still in the outbox.
 * @property familyId The family the event belonged to, `""` for none.
 */
data class EventRevisionInput(
    val versionId: String,
    val eventId: String,
    val kind: EventVersionKind,
    val editorUid: String,
    val deviceTimeMillis: Long,
    val recordedAtMillis: Long?,
    val familyId: String,
    val facts: EventFacts
)

/**
 * An event as it stands on this phone, for entries saved before revisions were kept.
 *
 * Never a private event: the source leaves those out, and so does [CommunicationRecordBuilder].
 */
data class CurrentEventInput(
    val eventId: String,
    val familyId: String,
    val isPrivate: Boolean,
    val creatorUid: String?,
    val facts: EventFacts
)

/**
 * One chat message as the export reads it.
 *
 * @property attachments The files it carried (MON-23) — listed in the record by name and SHA-256,
 *   never by their bytes.
 * @property planCitation The `PlanCitationCodec` string a schedule-proposal card carried (MON-21),
 *   or null — every other message, and every proposal from an older build or not built from the plan.
 */
data class MessageInput(
    val messageId: String,
    val senderUid: String,
    val sentAtMillis: Long,
    val text: String,
    val delivered: Boolean,
    val attachments: List<ChatAttachment> = emptyList(),
    val planCitation: String? = null
)

/**
 * Everything [CommunicationRecordBuilder] draws from, gathered by the data layer.
 *
 * @property serverReached False when the server could not be asked, so the record is this phone's.
 * @property plan The parenting plan, or null when the parent chose to leave it out of the export.
 *   It carries its own [PlanSource.serverReached], which the record's completeness also reads.
 * @property journal The exporting parent's own journal entries, or null when they left the journal
 *   out (the default). Read from this phone, where it only ever exists, so it cannot make the
 *   record incomplete.
 */
data class RecordSources(
    val revisions: List<EventRevisionInput>,
    val currentEvents: List<CurrentEventInput>,
    val messages: List<MessageInput>,
    val expenses: List<Expense>,
    val serverReached: Boolean,
    val plan: PlanSource? = null,
    val journal: List<JournalEntry>? = null
)

/**
 * What the export covers, and in whose terms.
 *
 * @property families The family ids whose records belong in this export; `""` (no family) is
 *   always included, because it is what a record written before pairing carries.
 * @property nameForUid A parent's name for a uid, from `ParentLabels` — never a role.
 * @property nameForSlot A parent's name for a custody slot, from `ParentLabels`.
 */
data class RecordScope(
    val from: LocalDate,
    val to: LocalDate,
    val zone: ZoneId,
    val generatedAtMillis: Long,
    val families: Set<String>,
    val parents: List<String>,
    val nameForUid: (String) -> String,
    val nameForSlot: (String) -> String
)

/**
 * Decides what a communication record contains (MON-3).
 *
 * Pure: no Android, no Firestore, no clock. Every rule about *what* is exported lives here so the
 * JVM tests can pin it, and the renderers only decide *how* it looks.
 *
 * - **An event is in the record when any of its revisions touches the range**, and then its whole
 *   history comes with it. A parent who moved an appointment out of March changed March; showing
 *   March without that move would hide the one edit the reader is looking for.
 * - **Revisions are ordered by the server's clock**, the device's where the server has not seen
 *   one yet, and numbered 1…n when they are printed — see `docs/DESIGN-court-record.md` §4.
 * - **An event saved before revisions were kept is printed as its current state**, labelled as
 *   having no history, rather than dressed up as a "created" revision dated today.
 * - **Private events are never in it** (CLAUDE.md item 3), whatever the source hands over.
 * - **The child's medical profile is never in it** (design §4) — this type has no field for it.
 * - **The parenting plan is in it only when asked for**, whole and as it stands now (see
 *   [RecordPlanBuilder]); a plan that could not be read from the server makes the record incomplete.
 * - **The private journal is in it only when asked for**, and only entries about days in the period
 *   (see [RecordJournalBuilder]); it is off by default because nobody else has ever seen it.
 */
object CommunicationRecordBuilder {

    /** Builds the record for [scope] from [sources]. */
    fun build(sources: RecordSources, scope: RecordScope): CommunicationRecord =
        CommunicationRecord(
            from = scope.from,
            to = scope.to,
            zone = scope.zone,
            generatedAtMillis = scope.generatedAtMillis,
            parents = scope.parents,
            complete = sources.serverReached && sources.plan?.serverReached != false,
            events = events(sources, scope),
            messages = messages(sources.messages, scope),
            expenses = expenses(sources.expenses, scope),
            plan = sources.plan?.let { RecordPlanBuilder.build(it, scope.nameForUid) },
            journal = sources.journal?.let { RecordJournalBuilder.build(it, scope) }
        )

    private fun events(sources: RecordSources, scope: RecordScope): List<RecordEvent> {
        val inFamily = { familyId: String -> familyId.isEmpty() || familyId in scope.families }
        val byEvent = sources.revisions
            .filter { inFamily(it.familyId) }
            // The same revision from the server and from the outbox: the server's copy wins,
            // because it carries the time the server received it.
            .groupBy { it.versionId }
            .map { (_, copies) -> copies.firstOrNull { it.recordedAtMillis != null } ?: copies.first() }
            .groupBy { it.eventId }
        val withHistory = byEvent.values
            .filter { revisions -> revisions.any { it.facts.touches(scope.from, scope.to) } }
            .map { revisions -> historyOf(revisions, scope) }
        val withoutHistory = sources.currentEvents
            .filter { !it.isPrivate && it.eventId !in byEvent && inFamily(it.familyId) }
            .filter { it.facts.touches(scope.from, scope.to) }
            .map { current ->
                RecordEvent(
                    eventId = current.eventId,
                    revisions = listOf(
                        RecordRevision(
                            number = 1,
                            kind = null,
                            byName = current.creatorUid?.let(scope.nameForUid).orEmpty(),
                            deviceTimeMillis = null,
                            recordedAtMillis = null,
                            delivered = true,
                            facts = current.facts,
                            parentName = scope.nameForSlot(current.facts.parentSlot)
                        )
                    )
                )
            }
        return (withHistory + withoutHistory).sortedWith(
            compareBy<RecordEvent> { it.revisions.last().facts.start ?: LocalDateTime.MAX }
                .thenBy { it.eventId }
        )
    }

    private fun historyOf(revisions: List<EventRevisionInput>, scope: RecordScope): RecordEvent {
        val ordered = revisions.sortedWith(
            compareBy<EventRevisionInput> { it.recordedAtMillis ?: Long.MAX_VALUE }
                .thenBy { it.deviceTimeMillis }
                .thenBy { it.versionId }
        )
        return RecordEvent(
            eventId = ordered.first().eventId,
            revisions = ordered.mapIndexed { index, revision ->
                RecordRevision(
                    number = index + 1,
                    kind = revision.kind,
                    byName = scope.nameForUid(revision.editorUid),
                    deviceTimeMillis = revision.deviceTimeMillis,
                    recordedAtMillis = revision.recordedAtMillis,
                    delivered = revision.recordedAtMillis != null,
                    facts = revision.facts,
                    parentName = scope.nameForSlot(revision.facts.parentSlot)
                )
            }
        )
    }

    private fun messages(messages: List<MessageInput>, scope: RecordScope): List<RecordMessage> {
        val start = scope.from.atStartOfDay(scope.zone).toInstant().toEpochMilli()
        val end = scope.to.plusDays(1).atStartOfDay(scope.zone).toInstant().toEpochMilli()
        return messages
            .filter { it.sentAtMillis in start until end }
            .distinctBy { it.messageId }
            .sortedWith(compareBy<MessageInput> { it.sentAtMillis }.thenBy { it.messageId })
            .map {
                RecordMessage(
                    messageId = it.messageId,
                    senderName = scope.nameForUid(it.senderUid),
                    sentAtMillis = it.sentAtMillis,
                    text = RecordFormat.messageText(it.text, it.attachments),
                    delivered = it.delivered,
                    citedPlanQuestionId = PlanCitationCodec.questionIdOf(it.planCitation)
                )
            }
    }

    private fun expenses(expenses: List<Expense>, scope: RecordScope): List<RecordExpense> = expenses
        .filter { it.familyId.isNullOrEmpty() || it.familyId in scope.families }
        .filter { !it.date.isBefore(scope.from) && !it.date.isAfter(scope.to) }
        .sortedWith(compareBy<Expense> { it.date }.thenBy { it.id })
        .map {
            RecordExpense(
                expenseId = it.id,
                date = it.date,
                title = it.title,
                amount = it.amount,
                currency = it.currency,
                paidByName = scope.nameForUid(it.paidBy),
                recordedByName = it.createdByFirebaseUid?.let(scope.nameForUid).orEmpty(),
                notes = it.notes.orEmpty()
            )
        }

    /**
     * Reads the parts of an event document a record prints.
     *
     * The document is the wire form `EventRepositoryImpl.toFirestoreMap()` writes, where every
     * absent value is an empty string. A field that does not parse is left empty rather than
     * guessed: an export that invented a time would be the one thing it must never do.
     */
    fun factsOf(document: Map<String, Any?>): EventFacts {
        val recurring = document["isRecurring"] as? Boolean ?: false
        return EventFacts(
            title = (document["title"] as? String).orEmpty(),
            description = (document["description"] as? String).orEmpty(),
            start = dateTime(document["startDateTime"]),
            end = dateTime(document["endDateTime"]),
            parentSlot = (document["parentOwner"] as? String).orEmpty(),
            recurrence = if (recurring) (document["recurrencePattern"] as? String).orEmpty() else "",
            recurrenceEnd = (document["recurrenceEndDate"] as? String)?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )
    }

    private fun dateTime(value: Any?): LocalDateTime? = (value as? String)?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() }
}
