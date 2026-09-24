package com.coparently.app.domain.export

import java.time.ZoneId

/**
 * Renders a [CommunicationRecord] as CSV (MON-3).
 *
 * **RFC 4180**: records end in CRLF; a field holding a comma, a double quote, CR or LF is wrapped
 * in double quotes with every inner quote doubled; every record has the same number of fields —
 * the preamble rows too, padded — so a strict parser reads the whole file as one table.
 *
 * **Formula injection is refused.** A cell a spreadsheet would evaluate — one starting with `=`,
 * `+`, `-`, `@`, a tab or a carriage return — is prefixed with an apostrophe (OWASP's advice).
 * The other parent writes half of what is in this file, and a message that reads
 * `=HYPERLINK(...)` must arrive in a lawyer's spreadsheet as text, not as a link.
 *
 * **The statement comes first**, as rows of its own, before the table: a CSV has no "face"
 * otherwise, and the owner's answer to MON-4 is that the export says what it is on its face.
 * The record id and how to verify the file follow it (MON-16) — or, for a file that could not be
 * registered, the sentence saying it cannot be verified.
 *
 * **The parenting plan, when the record carries one, comes last** — one row per parent per
 * question, then the rows for answers under questions the plan no longer asks, preceded by the
 * section's own notes (the disclaimer, "current state at export time", and whether the server was
 * read). Every one of those rows is padded to the header's width like any other.
 *
 * Starts with a UTF-8 byte-order mark. Outside RFC 4180, harmless to a parser that follows it,
 * and the difference between Excel showing "Čeština" and "ÄŒeÅ¡tina" to the lawyer who opens it.
 */
object CommunicationRecordCsv {

    /** The UTF-8 byte-order mark the file starts with. */
    const val BOM = "\uFEFF"

    private const val CRLF = "\r\n"
    private val formulaStarts = setOf('=', '+', '-', '@', '\t', '\r')

    /** The whole file. */
    fun render(record: CommunicationRecord, labels: RecordLabels): String {
        val width = labels.columns.all().size
        val rows = preamble(record, labels) + listOf(emptyList(), labels.columns.all()) + body(record, labels)
        return BOM + rows.joinToString(separator = CRLF, postfix = CRLF) { row ->
            row.padded(width).joinToString(",") { escape(it) }
        }
    }

    /**
     * One field, guarded and escaped.
     *
     * Guarded first, then escaped, so the apostrophe sits inside the quotes where a spreadsheet
     * reads it as the start of text.
     */
    fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && value.first() in formulaStarts) "'$value" else value
        val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
    }

    private fun preamble(record: CommunicationRecord, labels: RecordLabels): List<List<String>> {
        val meta = listOf(
            listOf(labels.period, "${RecordFormat.date(record.from)} – ${RecordFormat.date(record.to)}"),
            listOf(labels.generated, RecordFormat.instant(record.generatedAtMillis, record.zone)),
            listOf(labels.timeZone, record.zone.id),
            listOf(labels.parents, record.parents.joinToString(", "))
        )
        val warning = if (record.complete) emptyList() else listOf(listOf(labels.incomplete))
        return listOf(listOf(labels.title)) + labels.statement.map { listOf(it) } + meta +
            verification(record, labels.verification) + warning
    }

    /**
     * The record id, where to check it and how — or the file's statement that it cannot be
     * checked (MON-16). After the statement, which stays first, and before the table.
     */
    private fun verification(record: CommunicationRecord, words: VerificationLabels): List<List<String>> =
        when (val verification = record.verification) {
            is RecordVerification.Registered -> listOfNotNull(
                listOf(words.recordId, RecordId.display(verification.recordId)),
                verification.verifyUrl.takeIf { it.isNotBlank() }?.let { listOf(words.verifyAt, it) },
                listOf(if (verification.verifyUrl.isBlank()) words.instructionNoUrl else words.instruction)
            )
            RecordVerification.Unregistered -> listOf(listOf(words.notRegistered))
        }

    private fun body(record: CommunicationRecord, labels: RecordLabels): List<List<String>> =
        record.events.flatMap { event -> event.revisions.map { eventRow(event, it, record.zone, labels) } } +
            record.messages.map { messageRow(it, record.zone, labels) } +
            record.expenses.map { expenseRow(it, labels) } +
            record.plan?.let { PlanCsvRows.rows(it, record.zone, labels.plan) }.orEmpty()

    private fun eventRow(
        event: RecordEvent,
        revision: RecordRevision,
        zone: ZoneId,
        labels: RecordLabels
    ): List<String> = listOf(
        labels.sectionEvents,
        event.eventId,
        revision.number.toString(),
        RecordFormat.action(revision.kind, labels.actions),
        revision.byName,
        revision.deviceTimeMillis?.let { RecordFormat.instant(it, zone) }.orEmpty(),
        RecordFormat.serverTime(revision, zone, labels),
        revision.facts.title,
        RecordFormat.wallClock(revision.facts.start),
        RecordFormat.wallClock(revision.facts.end),
        revision.parentName,
        RecordFormat.eventNotes(revision.facts),
        "",
        ""
    )

    private fun messageRow(message: RecordMessage, zone: ZoneId, labels: RecordLabels): List<String> = listOf(
        labels.sectionMessages,
        message.messageId,
        "",
        if (message.delivered) labels.actions.sent else labels.actions.notSent,
        message.senderName,
        RecordFormat.instant(message.sentAtMillis, zone),
        "",
        message.text,
        "",
        "",
        "",
        RecordFormat.planCitation(message, labels.plan),
        "",
        ""
    )

    private fun expenseRow(expense: RecordExpense, labels: RecordLabels): List<String> = listOf(
        labels.sectionExpenses,
        expense.expenseId,
        "",
        labels.actions.recorded,
        expense.recordedByName,
        "",
        "",
        expense.title,
        RecordFormat.date(expense.date),
        "",
        expense.paidByName,
        expense.notes,
        RecordFormat.amount(expense.amount),
        expense.currency
    )

    private fun List<String>.padded(width: Int): List<String> =
        if (size >= width) this else this + List(width - size) { "" }
}
