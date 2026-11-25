@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.export

import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.CommunicationRecordCsv
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.JournalLabels
import com.coparently.app.domain.export.PlanLabels
import com.coparently.app.domain.export.RecordActions
import com.coparently.app.domain.export.RecordColumns
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordMessage
import com.coparently.app.domain.export.RecordVerification
import com.coparently.app.domain.export.VerificationLabels
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [IosRecordFileWriter] and [InfoPlistExportVerifyAddress] (MON-24 phase 3, part C, Task 12): the CSV
 * is the common writer's bytes, the PDF is the layout's pages with the statement first and the footer
 * on each, and a save writes exactly the bytes it is handed under `exports/`. Each test works in a
 * cache folder of its own under the temporary directory.
 */
class IosRecordFileWriterTest {

    private val caches = NSTemporaryDirectory().trimEnd('/') + "/coplanly-export-" + NSUUID().UUIDString

    private val writer = IosRecordFileWriter(caches)

    @AfterTest
    fun removeTheFolder() {
        NSFileManager.defaultManager.removeItemAtPath(caches, error = null)
    }

    @Test
    fun `the csv is byte for byte the common writer's`() {
        val record = record()

        val bytes = runBlocking { writer.render(record, LABELS, ExportFormat.CSV) }

        assertContentEquals(CommunicationRecordCsv.render(record, LABELS).encodeToByteArray(), bytes)
    }

    @Test
    fun `the pdf starts with the PDF marker and has one page per layout page`() {
        val record = record()

        val bytes = runBlocking { writer.render(record, LABELS, ExportFormat.PDF) }

        assertEquals("%PDF", bytes.copyOfRange(0, 4).decodeToString())
        val document = assertNotNull(PDFDocument(data = bytes.toNSData()), "PDFKit could not open the file")
        val expected = writer.pages(record, LABELS).size
        assertTrue(expected > 1, "The fixture should span more than one page, not $expected")
        assertEquals(expected.toULong(), document.pageCount)
        for (index in 0 until expected) {
            val text = document.pageAtIndex(index.toULong())?.string.orEmpty()
            assertTrue(LABELS.verification.notRegisteredShort in text, "Page ${index + 1} has no footer: $text")
            assertTrue("${LABELS.page} ${index + 1} / $expected" in text, "Page ${index + 1} is not numbered: $text")
        }
    }

    @Test
    fun `the statement is the first text on page one`() {
        val bytes = runBlocking { writer.render(record(), LABELS, ExportFormat.PDF) }

        val text = PDFDocument(data = bytes.toNSData()).pageAtIndex(0u)?.string.orEmpty().trim()
        // The title heads the page; the statement is the first text under it, before anything else.
        assertTrue(text.startsWith(LABELS.title), "Page one does not start with the title: $text")
        val afterTitle = text.removePrefix(LABELS.title).trimStart()
        assertTrue(afterTitle.startsWith(LABELS.statement.first()), "The statement is not first: $text")
        val statementEnds = text.indexOf(LABELS.statement.last())
        assertTrue(statementEnds in 0 until text.indexOf(LABELS.period), "The statement is not first: $text")
    }

    @Test
    fun `a registered record's id is in every page's footer`() {
        val record = record().copy(verification = RecordVerification.Registered("7K3Q0ABCDEFGHJKM", ""))

        val bytes = runBlocking { writer.render(record, LABELS, ExportFormat.PDF) }

        val document = PDFDocument(data = bytes.toNSData())
        for (index in 0uL until document.pageCount) {
            val text = document.pageAtIndex(index)?.string.orEmpty()
            assertTrue(LABELS.verification.recordId in text, "Page ${index + 1u} names no record id: $text")
            assertFalse(LABELS.verification.notRegisteredShort in text, "Page ${index + 1u} says not registered")
        }
    }

    @Test
    fun `a save writes the bytes under exports and replaces the last export`() {
        val record = record()
        val old = "$caches/${IosRecordFileWriter.EXPORT_DIRECTORY}/old.pdf"
        SystemFileSystem.createDirectories(Path(caches, IosRecordFileWriter.EXPORT_DIRECTORY))
        SystemFileSystem.sink(Path(old)).buffered().use { it.write(byteArrayOf(1)) }
        val bytes = "a,b\r\n".encodeToByteArray()

        val file = runBlocking { writer.save(bytes, record, ExportFormat.CSV) }

        assertEquals("$caches/exports/coplanly-record-2026-03-01-2026-03-31.csv", file.uri)
        assertEquals(ExportFormat.CSV.mimeType, file.mimeType)
        assertContentEquals(bytes, SystemFileSystem.source(Path(file.uri)).buffered().use { it.readByteArray() })
        assertFalse(SystemFileSystem.exists(Path(old)), "The last export was kept")
    }

    @Test
    fun `the verification address is blank unless the build sets one`() {
        assertEquals("", InfoPlistExportVerifyAddress.addressFrom(null))
        assertEquals("", InfoPlistExportVerifyAddress.addressFrom(""))
        assertEquals("", InfoPlistExportVerifyAddress.addressFrom("$(COPLANLY_EXPORT_VERIFY_URL)"))
        val padded = " https://example.org/verify/ "
        assertEquals("https://example.org/verify/", InfoPlistExportVerifyAddress.addressFrom(padded))
        // The test runner's bundle has no such key.
        assertEquals("", InfoPlistExportVerifyAddress.configured())
    }

    private fun record() = CommunicationRecord(
        from = LocalDate(2026, 3, 1),
        to = LocalDate(2026, 3, 31),
        zone = TimeZone.of("Europe/Prague"),
        generatedAtMillis = MARCH_10_0900,
        parents = listOf("Alice", "Bob"),
        complete = true,
        events = emptyList(),
        // Enough messages to fill more than one page.
        messages = (1..MESSAGES).map { index ->
            RecordMessage(
                messageId = "m$index",
                senderName = if (index % 2 == 0) "Alice" else "Bob",
                sentAtMillis = MARCH_10_0900 + index * MINUTE,
                text = "Message number $index about the weekend, the school bag and who drives on Friday.",
                delivered = true
            )
        },
        expenses = emptyList()
    )

    private fun ByteArray.toNSData(): NSData = usePinned {
        NSData.create(bytes = it.addressOf(0), length = size.toULong())
    }

    private companion object {
        const val MARCH_10_0900 = 1_773_129_600_000L
        const val MINUTE = 60_000L
        const val MESSAGES = 60

        val LABELS = RecordLabels(
            title = "COMMUNICATION RECORD",
            statement = listOf("NOT A TRUTH RECORD", "MESSAGES ARE FIXED"),
            period = "Period",
            generated = "Generated",
            timeZone = "Zone",
            parents = "Parents",
            incomplete = "INCOMPLETE",
            sectionEvents = "Events",
            sectionMessages = "Messages",
            sectionExpenses = "Expenses",
            nothingInPeriod = "Nothing",
            inReplyTo = "In reply to %1\$s's message of %2\$s: \"%3\$s\"",
            replySticker = "a sticker",
            replyNotInRecord = "In reply to an earlier message not in this record",
            columns = RecordColumns(
                section = "Section",
                item = "Item",
                revision = "Rev",
                action = "Action",
                by = "By",
                deviceTime = "Device time",
                serverTime = "Server time",
                text = "Text",
                starts = "Starts",
                ends = "Ends",
                parent = "Parent",
                notes = "Notes",
                amount = "Amount",
                currency = "Currency",
                inReplyTo = "In reply to"
            ),
            actions = RecordActions(
                created = "Created",
                updated = "Changed",
                deleted = "Deleted",
                currentState = "Current state",
                sent = "Sent",
                notSent = "Not sent",
                recorded = "Recorded",
                serverRecorded = "RECORDED BY THE SERVER"
            ),
            notYetOnServer = "Not yet on server",
            noServerTime = "None kept",
            revision = "Revision",
            page = "Page",
            verification = VerificationLabels(
                recordId = "Record ID",
                verifyAt = "Verify at",
                instruction = "CHECK IT AT THE ADDRESS ABOVE",
                instructionNoUrl = "REGISTERED UNDER THIS ID",
                notRegistered = "NOT REGISTERED, CANNOT BE VERIFIED",
                notRegisteredShort = "NOT REGISTERED"
            ),
            plan = PlanLabels(
                section = "Plan",
                disclaimer = "NOT THE MINISTRY FORM",
                currentState = "CURRENT STATE AT EXPORT",
                notFromServer = "PLAN FROM THIS PHONE",
                unsentHere = "UNSENT EDITS NOT SHOWN",
                noPlan = "NO PLAN RECORDED",
                lastChanged = "Last changed",
                agreed = "Agreed",
                notAgreed = "Not agreed",
                notAnswered = "Not answered",
                retired = "NO LONGER ASKED",
                questions = emptyMap(),
                cited = "FROM THE PLAN ANSWER TO"
            ),
            journal = JournalLabels(
                section = "Journal",
                privateNote = "PRIVATE NOTES",
                clockNote = "THAT PHONE'S CLOCK",
                none = "NO JOURNAL ENTRIES",
                written = "Written",
                edited = "Last edited"
            )
        )
    }
}
