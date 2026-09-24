package com.coparently.app.domain.export

import com.coparently.app.domain.journal.JournalEntry
import java.time.LocalDate
import java.time.ZoneId

/**
 * The exporting parent's private journal in a record (MON-22 in MON-3's export).
 *
 * Only ever the entries of the parent who made the export — the source reads them from this phone,
 * where nobody else's can be — and only when that parent ticked the box, which is off by default.
 * Both formats label the section, before any entry, as one parent's own private notes that the
 * other parent never saw: an entry placed among the messages without that label would read as
 * something that was said to them.
 *
 * @property entries Entries about a day in the record's period, oldest first. Empty is printed as
 *   "no journal entries in this period", never left out: the parent asked for the section.
 */
data class RecordJournal(val entries: List<RecordJournalEntry>)

/**
 * One journal entry as the record prints it.
 *
 * @property entryDate The day the entry is about, as the parent chose it.
 * @property authorName Who wrote it, by name — never a role.
 * @property createdAtMillis When it was first written, by that phone's clock; no server ever saw it.
 * @property updatedAtMillis When it was last edited, by that phone's clock.
 */
data class RecordJournalEntry(
    val entryId: String,
    val entryDate: LocalDate,
    val authorName: String,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    /** Whether the entry was changed after it was first written — printed, so nobody assumes not. */
    val edited: Boolean get() = updatedAtMillis != createdAtMillis
}

/**
 * Builds the journal section from the entries the export was handed.
 *
 * Pure, like [RecordPlanBuilder]. The period and family filters are applied here again whatever the
 * source did, the same way [CommunicationRecordBuilder] re-applies them to expenses: an entry
 * written in another family's time, or about a day outside the period, is not in this record.
 */
object RecordJournalBuilder {

    /** The section for [entries] within [scope]. */
    fun build(entries: List<JournalEntry>, scope: RecordScope): RecordJournal = RecordJournal(
        entries = entries
            .filter { it.familyId.isNullOrEmpty() || it.familyId in scope.families }
            .filter { !it.entryDate.isBefore(scope.from) && !it.entryDate.isAfter(scope.to) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<JournalEntry> { it.entryDate }.thenBy { it.createdAtMillis }.thenBy { it.id }
            )
            .map {
                RecordJournalEntry(
                    entryId = it.id,
                    entryDate = it.entryDate,
                    authorName = scope.nameForUid(it.createdByFirebaseUid),
                    text = it.text,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            }
    )
}

/**
 * How both formats lay the journal section out.
 *
 * The section's two notes come first — whose notes these are and that the other parent never saw
 * them, then that the times are that phone's clock — and then each entry: the day it is about, who
 * wrote it, when, whether it was edited since, and the text.
 */
object RecordJournalLayout {

    /** The section as PDF paragraphs; [byLabel] is the record's own "By" column header. */
    fun blocks(journal: RecordJournal, zone: ZoneId, words: JournalLabels, byLabel: String): List<RecordBlock> {
        val heading = RecordBlock(words.section, LineStyle.HEADING, spaceBefore = RecordLayout.SECTION_GAP)
        val notes = listOf(
            RecordBlock(words.privateNote, LineStyle.EMPHASIS),
            RecordBlock(words.clockNote, LineStyle.SMALL, spaceBefore = RecordLayout.SMALL_GAP)
        )
        if (journal.entries.isEmpty()) {
            val none = RecordBlock(words.none, LineStyle.BODY, spaceBefore = RecordLayout.ITEM_GAP)
            return listOf(heading) + notes + none
        }
        return listOf(heading) + notes + journal.entries.flatMap { entry ->
            listOfNotNull(
                RecordBlock(
                    RecordFormat.date(entry.entryDate),
                    LineStyle.SUBHEADING,
                    spaceBefore = RecordLayout.ITEM_GAP
                ),
                RecordBlock("$byLabel: ${entry.authorName}", LineStyle.SMALL, indent = 1),
                RecordBlock(
                    "${words.written}: ${RecordFormat.instant(entry.createdAtMillis, zone)}",
                    LineStyle.SMALL,
                    indent = 1
                ),
                editedLine(entry, zone, words)?.let { RecordBlock(it, LineStyle.SMALL, indent = 1) },
                RecordBlock(entry.text, LineStyle.BODY, indent = 1, spaceBefore = RecordLayout.SMALL_GAP)
            )
        }
    }

    /**
     * The section as CSV rows, in the record's fourteen columns: the notes in the text column, then
     * one row per entry — its id, "written", the author, when it was written, the text, the day it
     * is about in the "starts" column, and when it was last edited in the notes.
     */
    fun csvRows(journal: RecordJournal, zone: ZoneId, words: JournalLabels): List<List<String>> {
        val note = { text: String -> listOf(words.section, "", "", "", "", "", "", text) }
        val notes = listOf(note(words.privateNote), note(words.clockNote))
        if (journal.entries.isEmpty()) return notes + listOf(note(words.none))
        return notes + journal.entries.map { entry ->
            listOf(
                words.section,
                entry.entryId,
                "",
                words.written,
                entry.authorName,
                RecordFormat.instant(entry.createdAtMillis, zone),
                "",
                entry.text,
                RecordFormat.date(entry.entryDate),
                "",
                "",
                editedLine(entry, zone, words).orEmpty()
            )
        }
    }

    private fun editedLine(entry: RecordJournalEntry, zone: ZoneId, words: JournalLabels): String? =
        if (entry.edited) "${words.edited}: ${RecordFormat.instant(entry.updatedAtMillis, zone)}" else null
}
