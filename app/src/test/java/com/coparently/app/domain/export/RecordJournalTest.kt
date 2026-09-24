package com.coparently.app.domain.export

import com.coparently.app.domain.export.RecordFixtures.ALICE
import com.coparently.app.domain.export.RecordFixtures.FAMILY
import com.coparently.app.domain.export.RecordFixtures.FROM
import com.coparently.app.domain.export.RecordFixtures.MARCH_10_0900
import com.coparently.app.domain.export.RecordFixtures.TO
import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.planOf
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import com.coparently.app.domain.journal.JournalEntry
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The private journal in the communication record (MON-22 in MON-3's export).
 *
 * Pinned in the three layers the export has, like [RecordPlanTest]: what the builder keeps (the
 * period, the family, the author's name), how the CSV carries it (the header's width, the formula
 * guard, the notes before any entry), and how the PDF lays it out (after the expenses, before the
 * plan, labelled as one parent's private notes before the first entry).
 */
class RecordJournalTest {

    private val march12 = entry("j2", LocalDate.of(2026, 3, 12), "Handover was an hour late")
    private val march5 = entry("j1", LocalDate.of(2026, 3, 5), "Child came back without the inhaler")

    private fun recordWith(journal: List<JournalEntry>?, plan: PlanSource? = null) =
        CommunicationRecordBuilder.build(sources().copy(journal = journal, plan = plan), scope())

    // ---- The builder ----------------------------------------------------------------------

    @Test
    fun `entries about days in the period are kept, oldest first, each by its author's name`() {
        val journal = assertNotNull(recordWith(listOf(march12, march5)).journal)

        assertEquals(listOf("j1", "j2"), journal.entries.map { it.entryId })
        assertTrue(journal.entries.all { it.authorName == "Alice" })
    }

    @Test
    fun `an entry about a day outside the period, or written in another family, is not in it`() {
        val before = entry("before", FROM.minusDays(1), "February")
        val after = entry("after", TO.plusDays(1), "April")
        val elsewhere = entry("elsewhere", LocalDate.of(2026, 3, 20), "Other family").copy(familyId = "alice__carol")
        val unpaired = entry("unpaired", LocalDate.of(2026, 3, 21), "Before pairing").copy(familyId = null)

        val journal = assertNotNull(recordWith(listOf(before, after, elsewhere, unpaired, march5)).journal)

        assertEquals(listOf("j1", "unpaired"), journal.entries.map { it.entryId })
    }

    @Test
    fun `a journal left out is absent, and one asked for with no entries is an empty section`() {
        assertNull(recordWith(null).journal)
        assertTrue(assertNotNull(recordWith(emptyList()).journal).entries.isEmpty())
        assertTrue(recordWith(emptyList()).complete, "a journal read from this phone cannot make a record incomplete")
    }

    @Test
    fun `an entry changed after it was written says so, and one that was not does not`() {
        val edited = march5.copy(updatedAtMillis = march5.createdAtMillis + HOUR)
        val journal = assertNotNull(recordWith(listOf(edited, march12)).journal)

        assertTrue(journal.entries.first { it.entryId == "j1" }.edited)
        assertFalse(journal.entries.first { it.entryId == "j2" }.edited)
    }

    // ---- The CSV --------------------------------------------------------------------------

    @Test
    fun `journal rows have the header's width, the notes first, and the day in the starts column`() {
        val edited = march5.copy(updatedAtMillis = march5.createdAtMillis + HOUR)
        val lines = records(CommunicationRecordCsv.render(recordWith(listOf(edited)), labels()))
        val columns = labels().columns.all()

        lines.forEach { assertEquals(columns.size, fields(it).size, "record: $it") }
        val note = lines.indexOfFirst { it.contains("ONE PARENT'S PRIVATE NOTES, NEVER SHARED") }
        val row = lines.indexOfFirst { it.startsWith("Journal,j1,") }
        assertTrue(note in 0 until row)
        val fields = fields(lines[row])
        assertEquals("Written", fields[columns.indexOf("Action")])
        assertEquals("Alice", fields[columns.indexOf("By")])
        assertEquals("2026-03-10 09:00:00 +01:00", fields[columns.indexOf("Device time")])
        assertEquals("Child came back without the inhaler", fields[columns.indexOf("Text")])
        assertEquals("2026-03-05", fields[columns.indexOf("Starts")])
        assertEquals("Last edited: 2026-03-10 10:00:00 +01:00", fields[columns.indexOf("Notes")])
    }

    @Test
    fun `an entry written as a formula arrives as text`() {
        val formula = entry("f", LocalDate.of(2026, 3, 8), "=HYPERLINK(\"http://x\")")

        val csv = CommunicationRecordCsv.render(recordWith(listOf(formula)), labels())

        assertTrue(csv.contains(",\"'=HYPERLINK(\"\"http://x\"\")\","))
    }

    @Test
    fun `an empty journal is its notes and one line saying so`() {
        val lines = records(CommunicationRecordCsv.render(recordWith(emptyList()), labels()))

        assertTrue(lines.any { fields(it)[7] == "NO JOURNAL ENTRIES" })
        assertTrue(lines.any { fields(it)[7] == "ONE PARENT'S PRIVATE NOTES, NEVER SHARED" })
        assertTrue(lines.none { it.startsWith("Journal,j") })
    }

    @Test
    fun `a left-out journal prints no journal rows at all`() {
        val csv = CommunicationRecordCsv.render(recordWith(null), labels())

        assertFalse(csv.contains("Journal"))
        assertFalse(csv.contains("NEVER SHARED"))
    }

    // ---- The PDF --------------------------------------------------------------------------

    @Test
    fun `the journal follows the expenses, precedes the plan, and says whose notes these are first`() {
        val texts = RecordLayout.blocks(recordWith(listOf(march5), plan = planOf()), labels()).map { it.text }

        val expenses = texts.indexOf("Expense")
        val section = texts.indexOf("Journal")
        val plan = texts.indexOf("Plan")
        val firstEntry = texts.indexOf("2026-03-05")
        assertTrue(expenses in 0 until section)
        assertTrue(section < plan)
        assertTrue(texts.indexOf("ONE PARENT'S PRIVATE NOTES, NEVER SHARED") in section until firstEntry)
        assertTrue(texts.indexOf("THAT PHONE'S CLOCK") in section until firstEntry)
        assertEquals(
            listOf("By: Alice", "Written: 2026-03-10 09:00:00 +01:00", "Child came back without the inhaler"),
            texts.subList(firstEntry + 1, firstEntry + 4)
        )
    }

    @Test
    fun `an edited entry prints when, and an empty journal says there is nothing`() {
        val edited = march5.copy(updatedAtMillis = march5.createdAtMillis + HOUR)
        val withEdit = RecordLayout.blocks(recordWith(listOf(edited)), labels()).map { it.text }
        val empty = RecordLayout.blocks(recordWith(emptyList()), labels()).map { it.text }

        assertTrue("Last edited: 2026-03-10 10:00:00 +01:00" in withEdit)
        assertTrue("NO JOURNAL ENTRIES" in empty)
        assertFalse(RecordLayout.blocks(recordWith(null), labels()).any { it.text == "Journal" })
    }

    private fun entry(id: String, date: LocalDate, text: String) = JournalEntry(
        id = id,
        entryDate = date,
        text = text,
        createdAtMillis = MARCH_10_0900,
        updatedAtMillis = MARCH_10_0900,
        familyId = FAMILY,
        createdByFirebaseUid = ALICE
    )

    /** Records, split on CRLF outside quotes — the fixtures hold no CRLF inside one. */
    private fun records(csv: String): List<String> =
        csv.removePrefix(CommunicationRecordCsv.BOM).split("\r\n").filter { it.isNotEmpty() }

    /** A minimal RFC 4180 field splitter, as in `CommunicationRecordCsvTest`. */
    private fun fields(record: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < record.length) {
            val c = record[i]
            when {
                quoted && c == '"' && i + 1 < record.length && record[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += field.toString()
                    field.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        out += field.toString()
        return out
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
