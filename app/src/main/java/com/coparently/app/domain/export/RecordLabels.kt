package com.coparently.app.domain.export

/**
 * Every word an export prints, already in the reader's language.
 *
 * Resolved in composition from `export_strings.xml` and handed down, so the renderers stay pure
 * Kotlin and no `Context` reaches a ViewModel (CLAUDE.md, CQ-14). No templates: a word that needs
 * a number beside it ("Revision", "Page") is printed with the number after it, which reads right in
 * all five languages and keeps `String.format` out of code that runs off the main thread.
 *
 * @property statement The paragraphs that say what the record is and is not — printed first, on
 *   the face of both formats, and never shortened (`docs/DESIGN-court-record.md` §3).
 */
data class RecordLabels(
    val title: String,
    val statement: List<String>,
    val period: String,
    val generated: String,
    val timeZone: String,
    val parents: String,
    val incomplete: String,
    val sectionEvents: String,
    val sectionMessages: String,
    val sectionExpenses: String,
    val nothingInPeriod: String,
    val columns: RecordColumns,
    val actions: RecordActions,
    val notYetOnServer: String,
    val noServerTime: String,
    val revision: String,
    val page: String
)

/** The column headers, which the PDF reuses as field labels so the two formats read alike. */
data class RecordColumns(
    val section: String,
    val item: String,
    val revision: String,
    val action: String,
    val by: String,
    val deviceTime: String,
    val serverTime: String,
    val text: String,
    val starts: String,
    val ends: String,
    val parent: String,
    val notes: String,
    val amount: String,
    val currency: String
) {
    /** In the order the CSV prints them. */
    fun all(): List<String> = listOf(
        section, item, revision, action, by, deviceTime, serverTime, text, starts, ends, parent, notes,
        amount, currency
    )
}

/**
 * What each row records.
 *
 * @property currentState An entry saved before revisions were kept: its state today, not a
 *   revision — worded so nobody reads it as the moment the entry was made.
 * @property notSent A message this phone never managed to send; it is in the record because the
 *   parent wrote it, and marked because the other parent never received it.
 */
data class RecordActions(
    val created: String,
    val updated: String,
    val deleted: String,
    val currentState: String,
    val sent: String,
    val notSent: String,
    val recorded: String
)
