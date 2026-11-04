package com.coparently.app.domain.export

import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.versions.EventVersionKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * How a record prints its values — one definition, shared by the CSV and the PDF so the two
 * formats can never disagree about a time.
 *
 * Every format is fixed and locale-independent (`Locale.ROOT`): this is evidence, and a date a
 * reader in another country could misread (`03/04`) is worse than one that looks foreign.
 *
 * The times are printed by [RecordTimeText] (`yyyy-MM-dd HH:mm:ss xxx` and `yyyy-MM-dd HH:mm`,
 * the bytes `java.time` printed for those patterns) and the amount by [DecimalText], in common
 * code, so a record made on an iPhone reads exactly as one made on an Android phone (MON-24).
 */
object RecordFormat {

    private const val MONEY_SCALE = 2

    /** An instant in [zone], with its offset printed, so it cannot be read in the wrong zone. */
    fun instant(millis: Long, zone: TimeZone): String = RecordTimeText.instant(millis, zone)

    /** A wall-clock time as the parent entered it — no zone, because none was stored. */
    fun wallClock(value: LocalDateTime?): String =
        value?.let { RecordTimeText.wallClock(it) }.orEmpty()

    /** A date, ISO. */
    fun date(value: LocalDate?): String = value?.let { RecordTimeText.date(it) }.orEmpty()

    /**
     * An amount with two decimals and a point, never a grouping separator:
     * `BigDecimal.valueOf(value).setScale(2, HALF_UP).toPlainString()`, which [DecimalText.halfUp] is.
     */
    fun amount(value: Double): String = DecimalText.halfUp(value, MONEY_SCALE)

    /** What a revision recorded, in the reader's words. */
    fun action(kind: EventVersionKind?, actions: RecordActions): String = when (kind) {
        EventVersionKind.CREATED -> actions.created
        EventVersionKind.UPDATED -> actions.updated
        EventVersionKind.DELETED -> actions.deleted
        null -> actions.currentState
    }

    /**
     * The action cell of a revision: what it recorded, and — for one the server recorded because the
     * editing phone did not — that it was, so nobody reads the name beside it as a phone's claim.
     */
    fun revisionAction(revision: RecordRevision, actions: RecordActions): String {
        val action = action(revision.kind, actions)
        return if (revision.recordedByServer) "$action — ${actions.serverRecorded}" else action
    }

    /** The server-time cell: the time, "not yet received", or "none kept" for a current state. */
    fun serverTime(revision: RecordRevision, zone: TimeZone, labels: RecordLabels): String = when {
        revision.recordedAtMillis != null -> instant(revision.recordedAtMillis, zone)
        revision.kind == null -> labels.noServerTime
        else -> labels.notYetOnServer
    }

    /** The notes cell for an entry: its description, and its repetition when it has one. */
    fun eventNotes(facts: EventFacts): String = listOf(
        facts.description,
        facts.recurrence.takeIf { it.isNotBlank() }?.let { pattern ->
            listOfNotNull(pattern, facts.recurrenceEnd?.let { "→ $it" }).joinToString(" ")
        }.orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" · ")

    /**
     * A message's text, followed by one line per file it carried: the name and the SHA-256 of the
     * bytes (MON-23). The digest is what lets a reader holding a copy of the file show it is the
     * one that was sent; the bytes themselves never go into the record.
     *
     * A message sent with a file and no words carries the file's name as its text (the fallback an
     * older build shows), so that text is dropped rather than printed twice. "SHA-256" is the
     * algorithm's name in every language, which is why this line needs no label.
     */
    fun messageText(text: String, attachments: List<ChatAttachment>): String {
        val words = text.takeUnless { attachments.size == 1 && it == attachments.first().fileName }
        val files = attachments.map { "${it.fileName} (SHA-256 ${it.sha256})" }
        return (listOfNotNull(words?.takeIf { it.isNotBlank() }) + files).joinToString("\n")
    }

    /**
     * "Proposed from the parenting plan's answer to: <question>" for a schedule-proposal message
     * that cited the plan (MON-21), or empty. The question is printed in its current wording, or as
     * its id once the plan no longer asks it — never dropped.
     */
    fun planCitation(message: RecordMessage, words: PlanLabels): String =
        message.citedPlanQuestionId?.let { "${words.cited}: ${words.question(it)}" }.orEmpty()
}
