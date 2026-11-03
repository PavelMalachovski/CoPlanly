package com.coparently.app.domain.export

import com.coparently.app.domain.export.RecordFixtures.RECORD_ID
import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an exported file says about verifying it (MON-16).
 *
 * The rule under test is design item 8's: a file names a record id — and a place to check it —
 * only when it was registered, says "not registered" in the same places when it was not, and
 * never leaves a reader to guess. Both formats, every PDF page.
 */
class RecordVerificationTest {

    private val measure: (String, LineStyle) -> Float = { text, _ -> text.length * CHAR_WIDTH }

    private fun record(verification: RecordVerification) =
        CommunicationRecordBuilder.build(sources(), scope()).copy(verification = verification)

    private val registered = record(RecordVerification.Registered(RECORD_ID, URL))

    // ---- The fingerprint ------------------------------------------------------------------

    @Test
    fun `the fingerprint is SHA-256 in lowercase hex, as the callable requires`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ExportFingerprint.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `one changed byte is a different fingerprint`() {
        val a = ExportFingerprint.sha256Hex("Pickup at six".toByteArray())
        val b = ExportFingerprint.sha256Hex("Pickup at sex".toByteArray())

        assertFalse(a == b)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(a))
    }

    @Test
    fun `a record id is printed in groups of four`() {
        assertEquals("7K3Q-0ABC-DEFG-HJKM", RecordId.display(RECORD_ID))
    }

    @Test
    fun `a record is unregistered unless the export flow says otherwise`() {
        assertEquals(RecordVerification.Unregistered, CommunicationRecordBuilder.build(sources(), scope()).verification)
    }

    // ---- CSV ------------------------------------------------------------------------------

    @Test
    fun `a registered CSV prints the id, the address and the instruction before the table`() {
        val lines = csvLines(registered)
        val header = lines.indexOfFirst { it.startsWith("Section,") }

        val id = lines.indexOf("Record ID,7K3Q-0ABC-DEFG-HJKM" + ",".repeat(WIDTH - 2))
        val url = lines.indexOfFirst { it.startsWith("Verify at,$URL") }
        val instruction = lines.indexOfFirst { it.startsWith("CHECK IT AT THE ADDRESS ABOVE") }
        assertTrue(id in 0 until url && url < instruction && instruction < header, "order: $lines")
        // Still after the statement, which stays the first thing on the file's face.
        assertTrue(lines.indexOfFirst { it.startsWith("NOT A TRUTH RECORD") } < id)
    }

    @Test
    fun `with no verification page hosted, the CSV prints no address`() {
        val lines = csvLines(record(RecordVerification.Registered(RECORD_ID, "")))

        assertFalse(lines.any { it.startsWith("Verify at") })
        assertTrue(lines.any { it.startsWith("REGISTERED UNDER THIS ID") })
        assertFalse(lines.any { it.startsWith("CHECK IT AT THE ADDRESS ABOVE") })
    }

    @Test
    fun `an unregistered CSV says it cannot be verified, and names no id`() {
        val csv = CommunicationRecordCsv.render(record(RecordVerification.Unregistered), labels())

        assertTrue(csv.contains("NOT REGISTERED, CANNOT BE VERIFIED"))
        assertFalse(csv.contains("Record ID"))
    }

    // ---- PDF ------------------------------------------------------------------------------

    @Test
    fun `a registered PDF names the id and the address on its first page`() {
        val texts = RecordLayout.blocks(registered, labels()).map { it.text }

        assertTrue("Record ID: 7K3Q-0ABC-DEFG-HJKM" in texts)
        assertTrue("Verify at: $URL" in texts)
        assertTrue("CHECK IT AT THE ADDRESS ABOVE" in texts)
    }

    @Test
    fun `an unregistered PDF says so on its first page`() {
        val texts = RecordLayout.blocks(record(RecordVerification.Unregistered), labels()).map { it.text }

        assertTrue("NOT REGISTERED, CANNOT BE VERIFIED" in texts)
        assertFalse(texts.any { it.startsWith("Record ID") })
    }

    @Test
    fun `every page's footer names the record, so a loose page still does`() {
        assertEquals(
            listOf("TITLE · Page 3 / 7", "Record ID: 7K3Q-0ABC-DEFG-HJKM · Verify at: $URL"),
            RecordVerificationLayout.footerTexts(registered, labels(), 3, 7)
        )
        assertEquals(
            listOf("TITLE · Page 1 / 1", "NOT REGISTERED"),
            RecordVerificationLayout.footerTexts(record(RecordVerification.Unregistered), labels(), 1, 1)
        )
    }

    @Test
    fun `the footer stays below the body, however far it wraps`() {
        val narrow = PageGeometry(width = 260f)
        val geometry = RecordVerificationLayout.withFooterRoom(registered, labels(), narrow, measure)
        val blocks = List(200) { RecordBlock("line $it", LineStyle.BODY) }

        val pages = RecordLayout.paginate(blocks, geometry, measure)
        val footer = RecordVerificationLayout.footer(registered, labels(), 1, pages.size, geometry, measure)

        assertTrue(footer.size > 2, "the narrow page should wrap the footer")
        val bodyBottom = pages.flatten().maxOf { it.baseline }
        val footerTop = footer.minOf { it.baseline } - LineStyle.SMALL.size
        assertTrue(bodyBottom < footerTop, "body at $bodyBottom runs into the footer at $footerTop")
        assertTrue(footer.all { it.baseline <= geometry.height })
    }

    private fun csvLines(record: CommunicationRecord): List<String> =
        CommunicationRecordCsv.render(record, labels())
            .removePrefix(CommunicationRecordCsv.BOM)
            .split("\r\n")
            .filter { it.isNotEmpty() }

    private companion object {
        const val URL = "https://coplanly.example/verify/"
        const val CHAR_WIDTH = 5f
        val WIDTH = labels().columns.all().size
    }
}
