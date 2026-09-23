package com.coparently.app.presentation.export

import android.net.Uri
import app.cash.turbine.test
import com.coparently.app.R
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.ExportFileWriter
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.RecordFixtures
import com.coparently.app.domain.export.RecordSources
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val writer = mockk<ExportFileWriter>()
    private val userRepository = mockk<UserRepository>()
    private val fallbacks = NameFallbacks(you = "You", coParent = "Co-parent", unknown = "Parent")

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ExportViewModel(source, writer, testParentsSource(me, partner), userRepository)

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
        coEvery { writer.writeCsv(capture(record), any()) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
            assertEquals(file, awaitItem())
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
        coEvery { writer.writePdf(any(), any()) } returns file
        val vm = viewModel()

        vm.files.test {
            vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)
            assertEquals(file, awaitItem())
        }
    }

    @Test
    fun `a failure is a sentence, and the screen is usable again`() = runTest {
        coEvery { writer.writeCsv(any(), any()) } throws IllegalStateException("disk full")
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
        coEvery { writer.writeCsv(any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val vm = viewModel()

        vm.export(ExportFormat.CSV, RecordFixtures.labels(), fallbacks)
        vm.state.first { it.working == ExportFormat.CSV }
        vm.export(ExportFormat.PDF, RecordFixtures.labels(), fallbacks)

        assertEquals(ExportFormat.CSV, vm.state.value.working)
        assertFalse(vm.state.value.error != null)
    }
}
