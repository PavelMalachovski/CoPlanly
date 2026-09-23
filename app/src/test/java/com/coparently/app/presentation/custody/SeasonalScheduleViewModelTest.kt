package com.coparently.app.presentation.custody

import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.custody.SharedCustodyRead
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Year
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The seasonal-schedule section (MON-14) and the fairness card (MON-20): a layer change always
 * goes through the repository's proposal road, is refused while the co-parent's proposal waits,
 * and the card counts every night of the year once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonalScheduleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<CustodyModelRepository>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val children = mockk<ChildInfoRepository>()
    private val base = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))
    private val summer =
        SeasonalLayer.allWith("summer", "Summer", LocalDate.of(2027, 7, 1)..LocalDate.of(2027, 8, 31), "mom")

    private fun draft(name: String, from: String, to: String) =
        SeasonalLayerDraft(name = name, from = LocalDate.parse(from), to = LocalDate.parse(to))

    private suspend fun SeasonalScheduleViewModel.firstMessage() = layersState.first { it.message != null }.message

    private fun viewModel(model: CustodyModel? = base, read: SharedCustodyRead = SharedCustodyRead.Absent):
        SeasonalScheduleViewModel {
        every { repository.getActiveModel() } returns flowOf(model)
        every { repository.observeShared() } returns flowOf(null)
        every { repository.observeDayOverrides() } returns flowOf(emptyMap())
        coEvery { repository.getActiveModelSync() } returns model
        coEvery { repository.readShared() } returns read
        coEvery { repository.submitSeasonalLayers(any()) } returns PatternSubmission.PROPOSED
        every { users.observeCurrentUserId() } returns flowOf("alice")
        every { users.observeUserById(any()) } returns flowOf(null)
        coEvery { users.getCurrentUserId() } returns "alice"
        every { children.getAllChildInfo() } returns flowOf(emptyList())
        return SeasonalScheduleViewModel(repository, users, children, testParentsSource())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a new layer is submitted on top of the agreed ones, and the screen hears it was proposed`() =
        runTest(dispatcher) {
            val vm = viewModel(model = base.copy(seasonalLayers = listOf(summer)))
            val sent = slot<List<SeasonalLayer>>()
            coEvery { repository.submitSeasonalLayers(capture(sent)) } returns PatternSubmission.PROPOSED

            vm.saveLayer(draft("Christmas", "2026-12-24", "2026-12-26"), editingId = null)
            advanceUntilIdle()

            assertEquals(2, sent.captured.size)
            assertTrue(sent.captured.any { it.id == "summer" })
            assertTrue(sent.captured.any { it.name == "Christmas" && it.id != "summer" })
            assertEquals(UiText.Res(R.string.seasonal_sent_for_approval), vm.firstMessage())
        }

    @Test
    fun `nothing is sent while the co-parent's proposal waits for an answer`() = runTest(dispatcher) {
        val proposal = CustodyProposal(model = base, repeatYearly = true, proposedBy = "bob", proposedAt = "")
        val shared = SharedCustody(base, "bob", 1L, "", proposal = proposal)
        val vm = viewModel(read = SharedCustodyRead.Found(shared))

        vm.saveLayer(draft("X", "2026-07-01", "2026-07-02"), null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.submitSeasonalLayers(any()) }
        assertEquals(UiText.Res(R.string.seasonal_answer_pending_first), vm.firstMessage())
    }

    @Test
    fun `deleting a layer submits the rest`() = runTest(dispatcher) {
        val vm = viewModel(model = base.copy(seasonalLayers = listOf(summer)))

        vm.deleteLayer("summer")
        advanceUntilIdle()

        coVerify { repository.submitSeasonalLayers(emptyList()) }
    }

    @Test
    fun `without a base pattern a layer cannot be saved`() = runTest(dispatcher) {
        val vm = viewModel(model = null)

        vm.saveLayer(draft("X", "2026-07-01", "2026-07-02"), null)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.submitSeasonalLayers(any()) }
        assertEquals(UiText.Res(R.string.seasonal_needs_base_pattern), vm.firstMessage())
    }

    @Test
    fun `the fairness card counts every night of the year once, and switches only between two years`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val state = vm.fairnessState.first { it.fairness != null }

            assertEquals(Year.of(state.year).length(), state.fairness!!.nightsBySlot.values.sum())
            vm.selectYear(state.year + 5)
            assertEquals(state.year, vm.fairnessState.value.year)
            assertTrue(state.years.size == 2)
        }
}
