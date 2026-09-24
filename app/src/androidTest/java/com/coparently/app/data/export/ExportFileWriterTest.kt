package com.coparently.app.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.R
import com.coparently.app.data.versions.EventVersionKind
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.CommunicationRecordBuilder
import com.coparently.app.domain.export.CommunicationRecordCsv
import com.coparently.app.domain.export.CurrentEventInput
import com.coparently.app.domain.export.EventFacts
import com.coparently.app.domain.export.EventRevisionInput
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.MessageInput
import com.coparently.app.domain.export.PlanLabels
import com.coparently.app.domain.export.RecordActions
import com.coparently.app.domain.export.RecordColumns
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.export.RecordScope
import com.coparently.app.domain.export.RecordSources
import com.coparently.app.domain.export.VerificationLabels
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.presentation.export.recordShareIntent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Writes a communication record through the real [ExportFileWriter] on the device (MON-3) and
 * reads the files back the way their recipient would.
 *
 * The JVM suite already pins what the record contains and how the CSV and the page layout look;
 * what only a device can show is that the files are really written where the `FileProvider`
 * serves them, that `PdfDocument` produces something `PdfRenderer` opens, and that the share
 * intent carries a URI the receiving app can read. The fixture goes through
 * [CommunicationRecordBuilder] rather than being built by hand, so a private event handed to the
 * source is dropped by the same code that drops it in production.
 */
@RunWith(AndroidJUnit4::class)
class ExportFileWriterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val writer = ExportFileWriter(context)
    private val labels = recordLabels(context)
    private val record = fixtureRecord()

    @After
    fun clearExports() {
        File(context.cacheDir, "exports").deleteRecursively()
    }

    @Test
    fun csv_isWrittenWhereTheProviderServesIt_andParsesAsRfc4180() {
        val exported = runBlocking { writer.write(record, labels, ExportFormat.CSV) }

        val file = File(context.cacheDir, "exports/coplanly-record-$FROM-$TO.csv")
        assertTrue("CSV file exists", file.exists())
        val text = file.readText(Charsets.UTF_8)
        assertTrue("starts with a byte-order mark", text.startsWith(CommunicationRecordCsv.BOM))
        assertTrue("ends with CRLF", text.endsWith("\r\n"))

        val rows = parseRfc4180(text.removePrefix(CommunicationRecordCsv.BOM))
        val width = labels.columns.all().size
        rows.forEachIndexed { index, row -> assertEquals("fields in record $index", width, row.size) }

        // The face: the title, then the statement, before anything else.
        assertEquals(labels.title, rows[0][0])
        labels.statement.forEachIndexed { index, paragraph -> assertEquals(paragraph, rows[index + 1][0]) }
        assertTrue("the column header is a record of its own", rows.any { it == labels.columns.all() })

        assertEquals(exported.mimeType, "text/csv")
        assertEquals(text, readThroughProvider(exported.uri))
    }

    @Test
    fun csv_holdsBothRevisionsWithBothClocks_guardsFormulas_andLeavesOutThePrivateEvent() {
        runBlocking { writer.write(record, labels, ExportFormat.CSV) }
        val text = File(context.cacheDir, "exports/coplanly-record-$FROM-$TO.csv").readText(Charsets.UTF_8)
        val rows = parseRfc4180(text.removePrefix(CommunicationRecordCsv.BOM))

        val revisions = rows.filter { it[0] == labels.sectionEvents && it[1] == SHARED_EVENT }
        assertEquals(listOf("1", "2"), revisions.map { it[2] })
        assertEquals(listOf(labels.actions.created, labels.actions.updated), revisions.map { it[COL_ACTION] })
        revisions.forEach { row ->
            assertTrue("device time printed", row[COL_DEVICE_TIME].isNotBlank())
            assertTrue("server time printed", row[COL_SERVER_TIME].isNotBlank())
            assertNotEquals(labels.notYetOnServer, row[COL_SERVER_TIME])
            assertNotEquals("two clocks, two values", row[COL_DEVICE_TIME], row[COL_SERVER_TIME])
        }

        assertFalse("a private event never reaches an export", text.contains(PRIVATE_TITLE))

        val messageTexts = rows.filter { it[0] == labels.sectionMessages }.map { it[COL_TEXT] }
        assertEquals(listOf("'$FORMULA_MESSAGE", QUOTED_MESSAGE), messageTexts)

        val expense = rows.single { it[0] == labels.sectionExpenses }
        assertEquals("'$FORMULA_EXPENSE", expense[COL_TEXT])
        assertEquals("12.50", expense[COL_AMOUNT])
    }

    @Test
    fun pdf_opensInPdfRenderer_withAtLeastOnePage() {
        val exported = runBlocking { writer.write(record, labels, ExportFormat.PDF) }

        assertTrue(File(context.cacheDir, "exports/coplanly-record-$FROM-$TO.pdf").exists())
        assertEquals("application/pdf", exported.mimeType)
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(exported.uri, "r"))
        PdfRenderer(descriptor).use { renderer ->
            assertTrue("page count", renderer.pageCount >= 1)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.recycle()
            }
        }
    }

    @Test
    fun shareIntent_carriesTheProviderUri_withAOneOffReadGrant() {
        val exported = runBlocking { writer.write(record, labels, ExportFormat.CSV) }
        val intent = recordShareIntent(exported)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/csv", intent.type)
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(exported.uri, stream)
        assertEquals("content", exported.uri.scheme)
        assertEquals("${context.packageName}.fileprovider", exported.uri.authority)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertFalse((intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
        assertEquals(exported.uri, requireNotNull(intent.clipData).getItemAt(0).uri)
    }

    private fun readThroughProvider(uri: Uri): String =
        requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun fixtureRecord(): CommunicationRecord {
        val facts = EventFacts(
            title = "School play",
            description = "Bring the costume",
            start = LocalDateTime.of(2026, 3, 12, 17, 0),
            end = LocalDateTime.of(2026, 3, 12, 18, 0),
            parentSlot = "mom",
            recurrence = "",
            recurrenceEnd = null
        )
        val created = at(2026, 3, 5, 9)
        val updated = at(2026, 3, 6, 20)
        val sources = RecordSources(
            revisions = listOf(
                revision("v1", EventVersionKind.CREATED, ALICE, created, facts),
                revision("v2", EventVersionKind.UPDATED, BOB, updated, facts.copy(start = facts.start?.plusHours(1)))
            ),
            currentEvents = listOf(
                CurrentEventInput(
                    eventId = "private-event",
                    familyId = FAMILY,
                    isPrivate = true,
                    creatorUid = ALICE,
                    facts = facts.copy(title = PRIVATE_TITLE)
                )
            ),
            messages = listOf(
                MessageInput("m1", BOB, at(2026, 3, 7, 10), FORMULA_MESSAGE, delivered = true),
                MessageInput("m2", ALICE, at(2026, 3, 7, 11), QUOTED_MESSAGE, delivered = true)
            ),
            expenses = listOf(
                Expense(
                    id = "x1",
                    title = FORMULA_EXPENSE,
                    amount = 12.5,
                    currency = "CZK",
                    category = ExpenseCategory.FOOD,
                    paidBy = ALICE,
                    date = LocalDate.of(2026, 3, 14),
                    createdByFirebaseUid = ALICE,
                    familyId = FAMILY
                )
            ),
            serverReached = true
        )
        val scope = RecordScope(
            from = FROM,
            to = TO,
            zone = ZONE,
            generatedAtMillis = at(2026, 3, 31, 12),
            families = setOf(FAMILY),
            parents = listOf("Alice", "Bob"),
            nameForUid = { uid -> if (uid == ALICE) "Alice" else "Bob" },
            nameForSlot = { slot -> if (slot == "mom") "Alice" else "Bob" }
        )
        return CommunicationRecordBuilder.build(sources, scope)
    }

    private fun revision(
        id: String,
        kind: EventVersionKind,
        editor: String,
        deviceTime: Long,
        facts: EventFacts
    ) = EventRevisionInput(
        versionId = id,
        eventId = SHARED_EVENT,
        kind = kind,
        editorUid = editor,
        deviceTimeMillis = deviceTime,
        recordedAtMillis = deviceTime + SERVER_LAG_MILLIS,
        familyId = FAMILY,
        facts = facts
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZONE).toInstant().toEpochMilli()

    /**
     * A strict RFC 4180 reader: records end in CRLF, a quoted field may hold commas, CR, LF and
     * doubled quotes, and a bare CR or LF outside quotes — or a quote inside an unquoted field — is
     * an error rather than something to be forgiving about.
     */
    private fun parseRfc4180(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                quoted && char == '"' -> quoted = false
                quoted -> field.append(char)
                char == '"' -> {
                    check(field.isEmpty()) { "quote inside an unquoted field at $index" }
                    quoted = true
                }
                char == ',' -> {
                    fields.add(field.toString())
                    field.clear()
                }
                char == '\r' -> {
                    check(text.getOrNull(index + 1) == '\n') { "bare CR at $index" }
                    fields.add(field.toString())
                    field.clear()
                    records.add(fields)
                    fields = mutableListOf()
                    index++
                }
                char == '\n' -> error("bare LF at $index")
                else -> field.append(char)
            }
            index++
        }
        check(!quoted) { "unterminated quoted field" }
        check(field.isEmpty() && fields.isEmpty()) { "last record does not end in CRLF" }
        return records
    }

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val FAMILY = "alice-uid__bob-uid"
        const val SHARED_EVENT = "event-1"
        const val PRIVATE_TITLE = "PRIVATE-MUST-NOT-BE-EXPORTED"
        const val FORMULA_MESSAGE = "=HYPERLINK(\"http://example.invalid\",\"open\")"
        const val QUOTED_MESSAGE = "Fine, \"see you\" then\r\nBye"
        const val FORMULA_EXPENSE = "-5 for lunch, @school"
        const val SERVER_LAG_MILLIS = 2_000L

        const val COL_ACTION = 3
        const val COL_DEVICE_TIME = 5
        const val COL_SERVER_TIME = 6
        const val COL_TEXT = 7
        const val COL_AMOUNT = 12

        val ZONE: ZoneId = ZoneId.of("Europe/Prague")
        val FROM: LocalDate = LocalDate.of(2026, 3, 1)
        val TO: LocalDate = LocalDate.of(2026, 3, 31)

        /** Every word the record prints, from the app's own resources, as `ExportScreen` builds it. */
        fun recordLabels(context: Context) = RecordLabels(
            title = context.getString(R.string.export_record_title),
            statement = listOf(
                context.getString(R.string.export_statement_what),
                context.getString(R.string.export_statement_messages),
                context.getString(R.string.export_statement_revisions),
                context.getString(R.string.export_statement_limits)
            ),
            period = context.getString(R.string.export_record_period),
            generated = context.getString(R.string.export_record_generated),
            timeZone = context.getString(R.string.export_record_time_zone),
            parents = context.getString(R.string.export_record_parents),
            incomplete = context.getString(R.string.export_record_incomplete),
            sectionEvents = context.getString(R.string.export_record_section_events),
            sectionMessages = context.getString(R.string.export_record_section_messages),
            sectionExpenses = context.getString(R.string.export_record_section_expenses),
            nothingInPeriod = context.getString(R.string.export_record_nothing),
            columns = RecordColumns(
                section = context.getString(R.string.export_col_section),
                item = context.getString(R.string.export_col_item),
                revision = context.getString(R.string.export_col_revision),
                action = context.getString(R.string.export_col_action),
                by = context.getString(R.string.export_col_by),
                deviceTime = context.getString(R.string.export_col_device_time),
                serverTime = context.getString(R.string.export_col_server_time),
                text = context.getString(R.string.export_col_text),
                starts = context.getString(R.string.export_col_starts),
                ends = context.getString(R.string.export_col_ends),
                parent = context.getString(R.string.export_col_parent),
                notes = context.getString(R.string.export_col_notes),
                amount = context.getString(R.string.export_col_amount),
                currency = context.getString(R.string.export_col_currency)
            ),
            actions = RecordActions(
                created = context.getString(R.string.export_action_created),
                updated = context.getString(R.string.export_action_updated),
                deleted = context.getString(R.string.export_action_deleted),
                currentState = context.getString(R.string.export_action_current),
                sent = context.getString(R.string.export_action_sent),
                notSent = context.getString(R.string.export_action_not_sent),
                recorded = context.getString(R.string.export_action_recorded)
            ),
            notYetOnServer = context.getString(R.string.export_record_not_on_server),
            noServerTime = context.getString(R.string.export_record_no_server_time),
            revision = context.getString(R.string.export_record_revision),
            page = context.getString(R.string.export_record_page),
            verification = VerificationLabels(
                recordId = context.getString(R.string.export_verify_record_id),
                verifyAt = context.getString(R.string.export_verify_at),
                instruction = context.getString(R.string.export_verify_instruction),
                instructionNoUrl = context.getString(R.string.export_verify_instruction_no_url),
                notRegistered = context.getString(R.string.export_verify_not_registered),
                notRegisteredShort = context.getString(R.string.export_verify_not_registered_short)
            ),
            plan = PlanLabels(
                section = context.getString(R.string.parenting_plan_title),
                disclaimer = context.getString(R.string.parenting_plan_disclaimer),
                currentState = context.getString(R.string.export_plan_current_state),
                notFromServer = context.getString(R.string.export_plan_not_from_server),
                unsentHere = context.getString(R.string.export_plan_unsent_here),
                noPlan = context.getString(R.string.export_plan_none),
                lastChanged = context.getString(R.string.export_plan_last_changed),
                agreed = context.getString(R.string.parenting_plan_status_agreed),
                notAgreed = context.getString(R.string.export_plan_not_agreed),
                notAnswered = context.getString(R.string.parenting_plan_not_answered),
                retired = context.getString(R.string.export_plan_retired),
                questions = emptyMap(),
                cited = context.getString(R.string.export_plan_cited)
            )
        )
    }
}

/** Renders then saves: the two steps `ExportViewModel` runs with a hash and a registration between. */
private suspend fun ExportFileWriter.write(
    record: CommunicationRecord,
    labels: RecordLabels,
    format: ExportFormat
): ExportedFile = save(render(record, labels, format), record, format)
