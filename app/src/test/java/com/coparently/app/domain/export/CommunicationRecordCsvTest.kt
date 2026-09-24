package com.coparently.app.domain.export

import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.export.RecordFixtures.ALICE
import com.coparently.app.domain.export.RecordFixtures.BOB
import com.coparently.app.domain.export.RecordFixtures.MARCH_10_0900
import com.coparently.app.domain.export.RecordFixtures.expense
import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.revision
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CommunicationRecordCsv] — the CSV a lawyer opens (MON-3).
 *
 * Three things are pinned: RFC 4180 (quoting, doubled quotes, CRLF, one width for every record),
 * the formula-injection guard (half of this file is written by the other parent), and the
 * statement on the file's face, before any data.
 */
class CommunicationRecordCsvTest {

    // ---- RFC 4180 -------------------------------------------------------------------------

    @Test
    fun `a plain field is written as it is`() {
        assertEquals("Dentist", CommunicationRecordCsv.escape("Dentist"))
    }

    @Test
    fun `a comma, a quote or a line break wraps the field in quotes and doubles inner quotes`() {
        assertEquals("\"a,b\"", CommunicationRecordCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CommunicationRecordCsv.escape("say \"hi\""))
        assertEquals("\"line one\nline two\"", CommunicationRecordCsv.escape("line one\nline two"))
        assertEquals("\"cr\rhere\"", CommunicationRecordCsv.escape("cr\rhere"))
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", CommunicationRecordCsv.escape(""))
    }

    // ---- Formula injection ----------------------------------------------------------------

    @Test
    fun `a cell a spreadsheet would evaluate is turned into text`() {
        assertEquals("'=1+1", CommunicationRecordCsv.escape("=1+1"))
        assertEquals("'+420 123", CommunicationRecordCsv.escape("+420 123"))
        assertEquals("'-5", CommunicationRecordCsv.escape("-5"))
        assertEquals("'@SUM(A1)", CommunicationRecordCsv.escape("@SUM(A1)"))
        assertEquals("'\tx", CommunicationRecordCsv.escape("\tx"))
    }

    @Test
    fun `the guard goes inside the quotes, where a spreadsheet reads it`() {
        assertEquals(
            "\"'=HYPERLINK(\"\"http://x\"\",\"\"click\"\")\"",
            CommunicationRecordCsv.escape("=HYPERLINK(\"http://x\",\"click\")")
        )
    }

    @Test
    fun `a sign in the middle of a cell is left alone`() {
        assertEquals("pick-up at 5", CommunicationRecordCsv.escape("pick-up at 5"))
    }

    // ---- The file -------------------------------------------------------------------------

    @Test
    fun `the file starts with the byte-order mark and the title, and ends every record in CRLF`() {
        val csv = render()

        assertTrue(csv.startsWith(CommunicationRecordCsv.BOM + "TITLE"))
        assertTrue(csv.endsWith("\r\n"))
        assertFalse(csv.replace("\r\n", "").contains('\n'), "a bare LF outside a quoted field")
    }

    @Test
    fun `the statement comes before the header row, on the face of the file`() {
        val lines = records(render())

        val statement = lines.indexOfFirst { it.startsWith("NOT A TRUTH RECORD") }
        val header = lines.indexOfFirst { it.startsWith("Section,") }
        assertTrue(statement in 0 until header)
    }

    @Test
    fun `every record has the same number of fields, the preamble included`() {
        val width = labels().columns.all().size

        records(render()).forEach { line ->
            assertEquals(width, fields(line).size, "record: $line")
        }
    }

    @Test
    fun `a revision row carries both clocks, labelled by its columns`() {
        val row = records(render()).first { it.startsWith("Event,") }.let(::fields)
        val columns = labels().columns.all()

        assertEquals("2026-03-10 09:00:00 +01:00", row[columns.indexOf("Device time")])
        assertEquals("2026-03-10 09:00:02 +01:00", row[columns.indexOf("Server time")])
        assertEquals("Changed", row[columns.indexOf("Action")])
        assertEquals("1", row[columns.indexOf("Rev")])
        assertEquals("Alice", row[columns.indexOf("Parent")])
    }

    @Test
    fun `a revision still on this phone says so in the server column`() {
        val csv = CommunicationRecordCsv.render(
            CommunicationRecordBuilder.build(sources(revisions = listOf(revision("v1", recordedAt = null))), scope()),
            labels()
        )

        val row = fields(records(csv).first { it.startsWith("Event,") })
        assertEquals("Not yet on server", row[labels().columns.all().indexOf("Server time")])
    }

    @Test
    fun `a revision the server recorded says so, and has no device time`() {
        val server = revision("srv_e1_1", deviceTime = null, byServer = true, writeKey = "saved|2026-03-10T08:00:00")
        val csv = CommunicationRecordCsv.render(
            CommunicationRecordBuilder.build(sources(revisions = listOf(server)), scope()),
            labels()
        )

        val row = fields(records(csv).first { it.startsWith("Event,") })
        val columns = labels().columns.all()
        assertEquals("Changed — RECORDED BY THE SERVER", row[columns.indexOf("Action")])
        assertEquals("", row[columns.indexOf("Device time")])
        assertEquals("2026-03-10 09:00:02 +01:00", row[columns.indexOf("Server time")])
    }

    @Test
    fun `a message the other parent wrote as a formula arrives as text`() {
        val csv = CommunicationRecordCsv.render(
            CommunicationRecordBuilder.build(
                sources(messages = listOf(MessageInput("m1", BOB, MARCH_10_0900, "=cmd|' /C calc'!A0", true))),
                scope()
            ),
            labels()
        )

        assertTrue(csv.contains(",'=cmd|' /C calc'!A0,"))
    }

    @Test
    fun `an amount has two decimals and a point in every locale`() {
        val row = fields(records(render()).first { it.startsWith("Expense,") })
        val columns = labels().columns.all()

        assertEquals("42.50", row[columns.indexOf("Amount")])
        assertEquals("CZK", row[columns.indexOf("Currency")])
    }

    @Test
    fun `an incomplete record says so before the table`() {
        val csv = CommunicationRecordCsv.render(
            CommunicationRecordBuilder.build(sources(serverReached = false), scope()),
            labels()
        )

        val lines = records(csv)
        val warning = lines.indexOfFirst { it.startsWith("INCOMPLETE") }
        assertTrue(warning in 0 until lines.indexOfFirst { it.startsWith("Section,") })
    }

    private fun render(): String = CommunicationRecordCsv.render(
        CommunicationRecordBuilder.build(
            sources(
                revisions = listOf(revision("v1", kind = EventVersionKind.UPDATED, editor = ALICE)),
                messages = listOf(MessageInput("m1", BOB, MARCH_10_0900, "Pickup at six, \"not\" five", true)),
                expenses = listOf(expense("x1", LocalDate.of(2026, 3, 3)))
            ),
            scope()
        ),
        labels()
    )

    /** Records, split on CRLF outside quotes — the test fixtures hold no CRLF inside one. */
    private fun records(csv: String): List<String> =
        csv.removePrefix(CommunicationRecordCsv.BOM).split("\r\n").filter { it.isNotEmpty() }

    /** A minimal RFC 4180 field splitter, enough to read back what the renderer wrote. */
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
}
