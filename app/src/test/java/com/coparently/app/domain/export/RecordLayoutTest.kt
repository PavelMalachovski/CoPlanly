package com.coparently.app.domain.export

import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.revision
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [RecordLayout] — how the PDF reads, decided where the JVM can see it (MON-3).
 *
 * The renderer only draws what this returns, so the order of the document, the statement on its
 * first page and the page breaks are all pinned here with a fake font five points per character.
 */
class RecordLayoutTest {

    private val measure: (String, LineStyle) -> Float = { text, _ -> text.length * CHAR_WIDTH }

    @Test
    fun `the title and the statement are the first things on the first page`() {
        val record = CommunicationRecordBuilder.build(sources(), scope())

        val first = RecordLayout.paginate(RecordLayout.blocks(record, labels()), PageGeometry(), measure).first()

        assertEquals("TITLE", first[0].text)
        assertEquals("NOT A TRUTH RECORD", first[1].text)
        assertEquals("MESSAGES ARE FIXED", first[2].text)
    }

    @Test
    fun `sections come in reading order, and an empty one says so`() {
        val record = CommunicationRecordBuilder.build(sources(revisions = listOf(revision("v1"))), scope())

        val texts = RecordLayout.blocks(record, labels()).map { it.text }

        val events = texts.indexOf("Event")
        val messages = texts.indexOf("Message")
        val expenses = texts.indexOf("Expense")
        assertTrue(events in 0 until messages && messages < expenses)
        assertEquals("Nothing", texts[messages + 1])
    }

    @Test
    fun `a revision prints both clocks under their labels`() {
        val record = CommunicationRecordBuilder.build(sources(revisions = listOf(revision("v1"))), scope())

        val texts = RecordLayout.blocks(record, labels()).map { it.text }

        assertTrue("Device time: 2026-03-10 09:00:00 +01:00" in texts)
        assertTrue("Server time: 2026-03-10 09:00:02 +01:00" in texts)
        assertTrue(texts.any { it.startsWith("Revision 1 — Changed — Alice") })
    }

    @Test
    fun `an incomplete record says so on the first page`() {
        val record = CommunicationRecordBuilder.build(sources(serverReached = false), scope())

        val first = RecordLayout.paginate(RecordLayout.blocks(record, labels()), PageGeometry(), measure).first()

        assertTrue(first.any { it.text == "INCOMPLETE" })
    }

    @Test
    fun `text wraps at spaces, and a word longer than the line is broken rather than lost`() {
        val lines = RecordLayout.wrap("aaaa bbbb cccccccccccc", width = 5 * CHAR_WIDTH) { it.length * CHAR_WIDTH }

        assertEquals(listOf("aaaa", "bbbb", "ccccc", "ccccc", "cc"), lines)
    }

    @Test
    fun `a long record breaks across pages and no line leaves the printable area`() {
        val geometry = PageGeometry()
        val blocks = List(200) { RecordBlock("line $it", LineStyle.BODY) }

        val pages = RecordLayout.paginate(blocks, geometry, measure)

        assertTrue(pages.size > 1)
        val bottom = geometry.height - geometry.margin - geometry.footerHeight
        pages.flatten().forEach { assertTrue(it.baseline <= bottom, "line below the footer: ${it.text}") }
        assertEquals(200, pages.flatten().size)
    }

    private companion object {
        const val CHAR_WIDTH = 5f
    }
}
