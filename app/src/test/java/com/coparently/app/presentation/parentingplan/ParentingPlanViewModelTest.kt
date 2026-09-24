package com.coparently.app.presentation.parentingplan

import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.ParentingPlanPair
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The parenting plan (MON-5, CLAUDE.md item 21): this parent writes only their own half, an edit
 * keeps the answers already there, and an agreement records the co-parent's wording.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParentingPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val partner = PartnerSummary(id = "u2", name = "Pavel", email = "pavel@example.com", pairedSinceMillis = 1L)
    private val familyId = "u1__u2"
    private val repository = mockk<ParentingPlanRepository>()
    private val crashlytics = mockk<CrashlyticsManager>(relaxed = true)

    private val shared = MutableStateFlow<SharedCustody?>(null)
    private val custody = mockk<CustodyModelRepository> {
        every { observeShared() } returns shared
    }

    private fun viewModel(pairing: PairingState = PairingState.Paired(partner)): ParentingPlanViewModel {
        val userRepository = mockk<UserRepository> {
            every { observeCurrentUserId() } returns flowOf("u1")
        }
        val pairingRepository = mockk<PairingRepository> {
            every { observePairingState() } returns flowOf(pairing)
        }
        return ParentingPlanViewModel(
            repository,
            userRepository,
            pairingRepository,
            testParentsSource(),
            crashlytics,
            custody
        )
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
    fun `without a co-parent there is no plan to fill in`() = runTest(dispatcher) {
        val vm = viewModel(pairing = PairingState.NotPaired())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ParentingPlanUiState.NoCoParent, vm.uiState.value)
    }

    @Test
    fun `an answer is added to this parent's half, keeping the answers already there`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(
            ParentingPlanPair(yours = ParentingPlanEntry(answers = mapOf("q1" to "Weekends")), theirs = null)
        )
        val saved = slot<ParentingPlanEntry>()
        coEvery { repository.save(familyId, "u1", capture(saved)) } returns Unit
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.answer("q2", "  Alternate holidays ")
        advanceUntilIdle()

        assertEquals(mapOf("q1" to "Weekends", "q2" to "Alternate holidays"), saved.captured.answers)
    }

    @Test
    fun `agreeing records the co-parent's wording, not a flag`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(
            ParentingPlanPair(
                yours = ParentingPlanEntry(answers = mapOf("q1" to "Weekends")),
                theirs = ParentingPlanEntry(answers = mapOf("q1" to "Every other weekend"))
            )
        )
        val saved = slot<ParentingPlanEntry>()
        coEvery { repository.save(familyId, "u1", capture(saved)) } returns Unit
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.agree("q1", "Every other weekend")
        advanceUntilIdle()

        assertEquals(mapOf("q1" to "Every other weekend"), saved.captured.agreedTo)
        assertEquals(mapOf("q1" to "Weekends"), saved.captured.answers)
    }

    @Test
    fun `a failed save is reported, not thrown`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(
            ParentingPlanPair(yours = ParentingPlanEntry(), theirs = null)
        )
        val failure = IllegalStateException("disk full")
        coEvery { repository.save(any(), any(), any()) } throws failure
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.answer("q1", "Weekends")
        advanceUntilIdle()

        verify(exactly = 1) { crashlytics.recordException(failure) }
    }

    /** Both parents wrote and ticked [text] for [questionId] — an agreed answer. */
    private fun agreedOn(questionId: String, text: String) = ParentingPlanPair(
        yours = ParentingPlanEntry(answers = mapOf(questionId to text), agreedTo = mapOf(questionId to text)),
        theirs = ParentingPlanEntry(answers = mapOf(questionId to text), agreedTo = mapOf(questionId to text))
    )

    private fun pendingProposalFrom(uid: String): SharedCustody {
        val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))
        return SharedCustody(
            model = model,
            lastModifiedBy = uid,
            lastModifiedAtMillis = 1L,
            createdAt = "",
            proposal = CustodyProposal(model = model, repeatYearly = true, proposedBy = uid, proposedAt = "")
        )
    }

    @Test
    fun `an agreed custody answer offers to become a proposal`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(agreedOn("care_weekday", "Week on, week off"))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val ready = vm.uiState.value as ParentingPlanUiState.Ready
        assertTrue(ready.offersProposal("care_weekday"))
        assertFalse(ready.proposalBlocked("care_weekday"))
        // Agreed, but not about the schedule: nothing to propose.
        assertFalse(ready.offersProposal("health_doctor"))
    }

    @Test
    fun `a custody answer only one parent has ticked offers nothing`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(
            ParentingPlanPair(
                yours = ParentingPlanEntry(answers = mapOf("care_weekday" to "Week on, week off")),
                theirs = ParentingPlanEntry(
                    answers = mapOf("care_weekday" to "Week on, week off"),
                    agreedTo = mapOf("care_weekday" to "Week on, week off")
                )
            )
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val ready = vm.uiState.value as ParentingPlanUiState.Ready
        assertFalse(ready.offersProposal("care_weekday"))
        assertFalse(ready.proposalBlocked("care_weekday"))
    }

    @Test
    fun `the co-parent's pending proposal blocks the action and says so`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(agreedOn("holidays_school", "Halves"))
        shared.value = pendingProposalFrom("u2")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val ready = vm.uiState.value as ParentingPlanUiState.Ready
        assertTrue(ready.coParentProposalPending)
        assertFalse(ready.offersProposal("holidays_school"))
        assertTrue(ready.proposalBlocked("holidays_school"))
    }

    @Test
    fun `this parent's own pending proposal does not block a corrected one`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(agreedOn("care_weekday", "Week on, week off"))
        shared.value = pendingProposalFrom("u1")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val ready = vm.uiState.value as ParentingPlanUiState.Ready
        assertTrue(ready.offersProposal("care_weekday"))
    }
}
