package com.coparently.app.presentation.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.coparently.app.R
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.ExportFileWriter
import com.coparently.app.data.export.ExportReceipts
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.data.export.OptionalRecordSections
import com.coparently.app.data.export.ParentingPlanRecordSource
import com.coparently.app.domain.chat.DepartedThread
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.ExportFingerprint
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.PlanSource
import com.coparently.app.domain.export.RecordFixtures
import com.coparently.app.domain.export.RecordSources
import com.coparently.app.domain.export.RecordVerification
import com.coparently.app.domain.journal.JournalEntry
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [ExportViewModel] — the export's one screen (MON-3).
 *
 * What is pinned is what the screen cannot check for itself: the record is built for the pair on
 * screen (their thread, their names, never a role), the range cannot be turned inside out, and a
 * failure is a sentence rather than a file that never arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val me = User(id = "u1", email = "alice@example.com", name = "Alice", role = "mom", colorCode = "#FF4081")
    private val partner = PartnerSummary(
        id = "u2",
        name = "Bob",
        email = "bob@example.com",
        pairedSinceMillis = 1L,
        role = "dad"
    )
    private val source = mockk<CommunicationRecordSource>()
    private val planSource = mockk<ParentingPlanRecordSource>()
    private val journal = mockk<JournalRepository>()
    private val writer = mockk<ExportFileWriter>()
    private val receipts = mockk<ExportReceipts>()
    private val userRepository = mockk<UserRepository>()
    private val departedThreads = mockk<DepartedThreadSource>()
    private val fallbacks = NameFallbacks(you = "You", coParent = "Co-parent", unknown = "Parent")

    /** Every ViewModel a test made, so tear-down can stop what it left running. */
    private val viewModels = mutableListOf<ExportViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        coEvery { source.gather(any(), any(), any(), any(), any()) } returns RecordSources(
            revisions = emptyList(),
            currentEvents = emptyList(),
            messages = emptyList(),
            expenses = emptyList(),
            serverReached = true
        )
        coEvery { planSource.read(any(), any()) } returns PLAN
        every { receipts.verifyUrl } returns VERIFY_URL
        // Offline by default: the tests that are about something else must not depend on a server.
        coEvery { receipts.reserve(any(), any(), any(), any()) } returns null
        coEvery { writer.render(any(), any(), any()) } answers { bytesFor(firstArg()) }
        coEvery { departedThreads.current(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        // Nothing calls onCleared in a test, so an export still suspended when its test returns
        // would outlive it. `ParentsSource` shares on the real Dispatchers.Default, so such an
        // export can be resumed from that thread at any moment — and once `resetMain` has run,
        // that resumption dispatches to the absent Android main looper and throws, reported by
        // the *next* `runTest` as UncaughtExceptionsBeforeTest. Cancel while Main is still the
        // test dispatcher, so the cancellation completes here and a late resume is a no-op.
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        partnerOnScreen: PartnerSummary? = partner,
        thread: String? = null
    ) = ExportViewModel(
        source,
        OptionalRecordSections(planSource, journal),
        writer,
        receipts,
        testParentsSource(me, partnerOnScreen),
        userRepository,
        departedThreads,
        SavedStateHandle(listOfNotNull(thread?.let { ExportViewModel.ARG_THREAD to it }).toMap())
    ).also { viewModels += it }

    /** Distinct bytes per verification state, so a test can tell which rendering was saved. */
    private fun bytesFor(record: CommunicationRecord): ByteArray = when (val v = record.verification) {
        is RecordVerification.Registered -> "registered ${v.recordId} ${v.verifyUrl}".toByteArray()
        RecordVerification.Unregistered -> "not registered".toByteArray()
    }

    @Test
    fun `the range starts as the last three months, ending today`() {
        val state = viewModel().state.value
        val today = LocalDate.now(ZoneId.systemDefault())

        assertEquals(today, state.to)
        assertEquals(today.minusMonths(3), state.from)
    }

    @Test
    fun `a start moved past the end takes the end with it, and the other way round`() {
        val vm = viewModel()

        vm.setTo(LocalDate.of(2026, 3, 31))
        vm.setFrom(LocalDate.of(2026, 4, 10))
        assertEquals(LocalDate.of(2026, 4, 10), vm.state.value.to)

        vm.setTo(LocalDate.of(2026, 1, 5))
        assertEquals(LocalDate.of(2026, 1, 5), vm.state.value.from)
    }

    @Test
    fun `a CSV is built for the pair on screen and handed to the share sheet`() = runTest {
        val record = slot<CommunicationRecord>()
        val file = ExportedFile(mockk<Uri>(), "text/csv")
        coEvery { writer.save(any(), capture(record), ExportFormat.CSV) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            assertEquals(file, awaitItem().file)
        }

        coVerify {
            source.gather(
                myUid = "u1",
                conversationId = "u1__u2",
                from = any(),
                to = any(),
                zone = any()
            )
        }
        // Names, never roles: the slot identifiers "mom"/"dad" must not reach a court document.
        assertEquals(listOf("Alice", "Bob"), record.captured.parents)
        vm.state.first { it.working == null }
    }

    @Test
    fun `a PDF goes through the PDF writer`() = runTest {
        val file = ExportedFile(mockk<Uri>(), "application/pdf")
        coEvery { writer.save(any(), any(), ExportFormat.PDF) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)
            assertEquals(file, awaitItem().file)
        }
        coVerify { writer.render(any(), any(), ExportFormat.PDF) }
    }

    @Test
    fun `a failure is a sentence, and the screen is usable again`() = runTest {
        coEvery { writer.save(any(), any(), any()) } throws IllegalStateException("disk full")
        val vm = viewModel()

        vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
        val state = vm.state.first { it.error != null && it.working == null }

        assertEquals(UiText.Res(R.string.export_error_failed), state.error)
        vm.errorShown()
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `signed out, nothing is gathered and the parent is told why`() = runTest {
        coEvery { userRepository.getCurrentUserId() } returns null
        val vm = viewModel()

        vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)
        val state = vm.state.first { it.error != null && it.working == null }

        assertEquals(UiText.Res(R.string.export_error_signed_out), state.error)
        coVerify(exactly = 0) { source.gather(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a second tap while one export runs does not start another`() = runTest {
        val saving = CompletableDeferred<Unit>()
        coEvery { writer.save(any(), any(), any()) } coAnswers {
            saving.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val vm = viewModel()

        vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
        // `working` is set before the export's coroutine starts, so waiting on it alone let this
        // test finish while the export still waited for the parents' names on another thread.
        // Wait until it is really running — parked in the save — before the second tap.
        saving.await()
        vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)

        assertEquals(ExportFormat.CSV, vm.state.value.working)
        assertFalse(vm.state.value.error != null)
    }

    // ---- MON-5 in the record: the parenting plan is in by default, and can be left out --------

    @Test
    fun `the plan is included by default, read for the pair on screen`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel()
        assertEquals(true, vm.state.value.includePlan)

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify { planSource.read("u1", "u2") }
        assertEquals(listOf("Alice", "Bob"), record.captured.plan?.questions?.first()?.answers?.map { it.parentName })
    }

    @Test
    fun `a plan left out is not read, and the record carries none`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel()

        vm.setIncludePlan(false)
        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify(exactly = 0) { planSource.read(any(), any()) }
        assertEquals(null, record.captured.plan)
    }

    // ---- MON-22 in the record: the private journal is out by default, and in only when ticked ----

    @Test
    fun `the journal is left out by default and not even read`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel()
        assertFalse(vm.state.value.includeJournal)

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify(exactly = 0) { journal.entriesBetween(any(), any(), any()) }
        assertEquals(null, record.captured.journal)
    }

    @Test
    fun `a ticked journal carries this parent's own entries for the period, by name`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel()
        val from = vm.state.value.from
        val to = vm.state.value.to
        coEvery { journal.entriesBetween("u1", from, to) } returns listOf(
            JournalEntry(
                id = "j1",
                entryDate = to,
                text = "Pickup was an hour late",
                createdAtMillis = 1_000L,
                updatedAtMillis = 1_000L,
                familyId = null,
                createdByFirebaseUid = "u1"
            )
        )

        vm.setIncludeJournal(true)
        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify { journal.entriesBetween("u1", from, to) }
        val entry = record.captured.journal?.entries?.single()
        assertEquals("Pickup was an hour late", entry?.text)
        assertEquals("Alice", entry?.authorName)
    }

    // ---- MON-16: the record id is inside the bytes it vouches for -------------------------

    @Test
    fun `a registered export hashes exactly the bytes it saves, under the id it prints`() = runTest {
        val file = ExportedFile(mockk<Uri>(), "application/pdf")
        val saved = slot<ByteArray>()
        coEvery { receipts.reserve("u1__u2", any(), any(), ExportFormat.PDF) } returns RECORD_ID
        coEvery { receipts.register(any(), any(), any()) } returns true
        coEvery { writer.save(capture(saved), any(), any()) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)
            assertEquals(FinishedExport(file, RECORD_ID), awaitItem())
        }

        val expected = "registered $RECORD_ID $VERIFY_URL".toByteArray()
        assertContentEquals(expected, saved.captured)
        coVerify { receipts.register(RECORD_ID, ExportFingerprint.sha256Hex(expected), expected.size) }
    }

    @Test
    fun `offline, the file says it is not registered and nothing is registered`() = runTest {
        val file = ExportedFile(mockk<Uri>(), "text/csv")
        val saved = slot<ByteArray>()
        coEvery { writer.save(capture(saved), any(), any()) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            assertEquals(FinishedExport(file, null), awaitItem())
        }

        assertContentEquals("not registered".toByteArray(), saved.captured)
        coVerify(exactly = 0) { receipts.register(any(), any(), any()) }
    }

    @Test
    fun `an id reserved but not registered is taken off the file before it is saved`() = runTest {
        val file = ExportedFile(mockk<Uri>(), "application/pdf")
        val saved = slot<ByteArray>()
        coEvery { receipts.reserve(any(), any(), any(), any()) } returns RECORD_ID
        coEvery { receipts.register(any(), any(), any()) } returns false
        coEvery { writer.save(capture(saved), any(), any()) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)
            assertEquals(FinishedExport(file, null), awaitItem())
        }

        // Never a file naming an id the server holds no hash for.
        assertContentEquals("not registered".toByteArray(), saved.captured)
    }

    @Test
    fun `an account with no co-parent reserves under a blank family`() = runTest {
        coEvery { writer.save(any(), any(), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel(partnerOnScreen = null)

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify { receipts.reserve("", any(), any(), ExportFormat.CSV) }
    }

    // ---- a thread kept after the co-parent deleted their account (GDPR review, September 2026) ----

    @Test
    fun `with nobody paired, the thread the departed co-parent left is the one exported`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { departedThreads.current("u1") } returns listOf(kept("u1__u9", "u9", "Vera"))
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel(partnerOnScreen = null)

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify { source.gather(myUid = "u1", conversationId = "u1__u9", from = any(), to = any(), zone = any()) }
        // Reserved under the family the thread belonged to: the receipt vouches for that record.
        coVerify { receipts.reserve("u1__u9", any(), any(), ExportFormat.CSV) }
        // The name the thread recorded when the account went, since no profile is left to read.
        assertEquals(listOf("Alice", "Vera"), record.captured.parents)
    }

    @Test
    fun `the banner's thread is exported even while another family is on screen`() = runTest {
        val record = slot<CommunicationRecord>()
        coEvery { departedThreads.current("u1") } returns listOf(kept("u1__u9", "u9", ""))
        coEvery { writer.save(any(), capture(record), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel(thread = "u1__u9")

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify { source.gather(myUid = "u1", conversationId = "u1__u9", from = any(), to = any(), zone = any()) }
        // Bob, the co-parent on screen, belongs to another family and is not named in this one.
        assertEquals(listOf("Alice", "Co-parent"), record.captured.parents)
    }

    @Test
    fun `an ordinary export does not read the kept threads at all`() = runTest {
        coEvery { writer.save(any(), any(), any()) } returns ExportedFile(mockk<Uri>(), "text/csv")
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            awaitItem()
        }

        coVerify(exactly = 0) { departedThreads.current(any()) }
        coVerify { source.gather(myUid = "u1", conversationId = "u1__u2", from = any(), to = any(), zone = any()) }
    }

    @Test
    fun `the counterpart rule, pinned`() {
        val a = kept("u1__u8", "u8", "A")
        val b = kept("u1__u9", "u9", "B")

        assertEquals(ExportCounterpart("u9", b), ExportCounterpart.choose("u1__u9", "u2", listOf(a, b)))
        assertEquals(ExportCounterpart("u2"), ExportCounterpart.choose("u1__u7", "u2", listOf(a, b)))
        assertEquals(ExportCounterpart("u2"), ExportCounterpart.choose(null, "u2", listOf(a)))
        assertEquals(ExportCounterpart("u8", a), ExportCounterpart.choose(null, null, listOf(a, b)))
        assertEquals(ExportCounterpart(null), ExportCounterpart.choose(null, null, emptyList()))
    }

    private fun kept(conversationId: String, departedUid: String, name: String) =
        DepartedThread(conversationId, departedUid, name, retainedUntilMillis = Long.MAX_VALUE)

    private companion object {
        const val RECORD_ID = "7K3Q0ABCDEFGHJKM"
        const val VERIFY_URL = "https://coplanly.example/verify/"
        val PLAN = PlanSource(listOf("u1", "u2"), emptyMap(), serverReached = true, unsentHere = false)
    }
}
