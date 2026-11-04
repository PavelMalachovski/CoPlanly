package com.coparently.app.data.export

import com.coparently.app.data.remote.firebase.CallableCode
import com.coparently.app.data.remote.firebase.RecordingCallableFunctions
import com.coparently.app.domain.export.ExportFormat
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ExportReceipts] on [com.coparently.app.data.remote.firebase.CallableFunctions] (MON-16; MON-24
 * phase 2, Task 8j): the payloads `functions/export-receipts.js` reads, the answers it counts as a
 * registration, and every failure an answer rather than an exception (CLAUDE.md item 26).
 */
class ExportReceiptsTest {

    private val functions = RecordingCallableFunctions()
    private val receipts = ExportReceipts(functions) { "  https://coplanly.web.app/verify/ " }

    @Test
    fun aReservationSendsThePeriodAndFormatAndReturnsTheId() = runTest {
        functions.answer = { _, _ -> mapOf("recordId" to "ABCDEFGHJKMNPQRS") }

        val id = receipts.reserve("a__b", LocalDate(2026, 1, 5), LocalDate(2026, 3, 31), ExportFormat.PDF)

        assertEquals("ABCDEFGHJKMNPQRS", id)
        val (name, payload) = functions.calls.single()
        assertEquals("reserveExportRecordId", name)
        assertEquals(
            mapOf("familyId" to "a__b", "fromDate" to "2026-01-05", "toDate" to "2026-03-31", "format" to "pdf"),
            payload
        )
    }

    @Test
    fun aRegistrationCountsOnlyWhenTheServerNamesTheIdAndATime() = runTest {
        functions.answer = { _, _ -> mapOf("recordId" to "ID1", "recordedAtMillis" to 1_778_000_000_000L) }
        assertTrue(receipts.register("ID1", "f".repeat(64), 1234))
        val expected = mapOf<String, Any>("recordId" to "ID1", "sha256" to "f".repeat(64), "byteLength" to 1234)
        assertEquals("registerExportReceipt" to expected, functions.calls.single())

        functions.answer = { _, _ -> mapOf("recordId" to "OTHER", "recordedAtMillis" to 1L) }
        assertFalse(receipts.register("ID1", "f".repeat(64), 1234))
        functions.answer = { _, _ -> mapOf("recordId" to "ID1") }
        assertFalse(receipts.register("ID1", "f".repeat(64), 1234))
    }

    @Test
    fun aRefusalOrNoAnswerIsNotRegistered() = runTest {
        functions.answer = { _, _ -> throw RecordingCallableFunctions.refusal(CallableCode.UNAVAILABLE) }
        assertNull(receipts.reserve("", LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), ExportFormat.CSV))
        assertFalse(receipts.register("ID1", "0".repeat(64), 1))

        functions.answer = { _, _ -> null }
        assertNull(receipts.reserve("", LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), ExportFormat.CSV))
    }

    @Test
    fun theVerificationAddressIsThePlatformsTrimmed() {
        assertEquals("https://coplanly.web.app/verify/", receipts.verifyUrl)
        assertEquals("", ExportReceipts(functions) { "" }.verifyUrl)
    }
}
