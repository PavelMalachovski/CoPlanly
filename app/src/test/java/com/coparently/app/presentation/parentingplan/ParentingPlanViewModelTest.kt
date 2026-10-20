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
import com.coparently.app.domain.model.User
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
        every { observeSharedOf(any()) } returns shared
    }

    /**
     * @param shownPartnerUid The co-parent of the family on screen, as the family switcher projects
     *   it onto the signed-in row; null leaves the row unprojected, as right after a pairing.
     * @param pairingStates What every collection of the pairing state emits; [pairing] alone unless
     *   a test needs the order a real phone sees.
     * @param rows The Room rows, when a test switches the family while the screen lives.
     */
    private fun viewModel(
        pairing: PairingState = PairingState.Paired(partner),
        shownPartnerUid: String? = null,
        pairingStates: Flow<PairingState> = flowOf(pairing),
        rows: Flow<List<User>> = flowOf(listOf(signedInRow(shownPartnerUid)))
    ): ParentingPlanViewModel {
        val userRepository = mockk<UserRepository> {
            every { observeCurrentUserId() } returns flowOf("u1")
            every { getAllUsers() } returns rows
        }
        val pairingRepository = mockk<PairingRepository> {
            every { observePairingState() } returns pairingStates
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

    /** The signed-in parent's Room row, projected onto [shownPartnerUid]'s family. */
    private fun signedInRow(shownPartnerUid: String?) = User(
        id = "u1",
        email = "olya@example.com",
        name = "Olya",
        role = "mom",
        colorCode = "#FF4081",
        partnerId = shownPartnerUid
    )

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
    fun `the plan is the family on screen's, not the first co-parent's`() = runTest(dispatcher) {
        // With two families the server's link names the first co-parent (u2); the switcher shows
        // the second (u3). The plan, its names and the custody proposal beside it must all be
        // that family's — the names and the proposal already were.
        every { repository.observe("u1__u3", "u1") } returns flowOf(
            ParentingPlanPair(yours = ParentingPlanEntry(answers = mapOf("q1" to "Weekends")), theirs = null)
        )
        val vm = viewModel(shownPartnerUid = "u3")
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("u3", (vm.uiState.value as ParentingPlanUiState.Ready).coParentUid)
        verify(exactly = 0) { repository.observe(familyId, any()) }
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
    fun `a save after a switch never writes the previous family's half into the next family's plan`() =
        runTest(dispatcher) {
            every { repository.observe(familyId, "u1") } returns flowOf(
                ParentingPlanPair(yours = ParentingPlanEntry(answers = mapOf("q1" to "Bob's days")), theirs = null)
            )
            every { repository.observe("u1__u3", "u1") } returns flowOf(
                ParentingPlanPair(yours = ParentingPlanEntry(answers = mapOf("q1" to "Carol's days")), theirs = null)
            )
            val saved = slot<ParentingPlanEntry>()
            coEvery { repository.save("u1__u3", "u1", capture(saved)) } returns Unit
            val rows = MutableStateFlow(listOf(signedInRow("u2")))
            val vm = viewModel(rows = rows)
            val screen = backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(familyId, (vm.uiState.value as ParentingPlanUiState.Ready).familyId)

            // The screen stops collecting and still holds the first family's half when a push tap
            // switches the family; the answer the parent then saves goes to the family on screen.
            screen.cancel()
            advanceTimeBy(SHARING_STOPS_AFTER_MS)
            rows.value = listOf(signedInRow("u3"))
            vm.answer("q2", "Alternate holidays")
            advanceUntilIdle()

            assertEquals(mapOf("q1" to "Carol's days", "q2" to "Alternate holidays"), saved.captured.answers)
        }

    @Test
    fun `another family's pending proposal never blocks this family's plan`() = runTest(dispatcher) {
        every { repository.observe(familyId, "u1") } returns flowOf(agreedOn("care_weekday", "Week on, week off"))
        every { custody.observeSharedOf(familyId) } returns flowOf(null)
        every { custody.observeSharedOf("u1__u3") } returns flowOf(pendingProposalFrom("u3"))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val ready = vm.uiState.value as ParentingPlanUiState.Ready
        assertFalse(ready.coParentProposalPending)
        assertTrue(ready.offersProposal("care_weekday"))
    }

    @Test
    fun `an answer saved while the pairing listener is still loading is not dropped`() = runTest(dispatcher) {
        // What a real phone sees: every collection of the pairing state starts at Loading and
        // reaches Paired once Firestore answers. The save used to take the first family the scope
        // built — the one from Loading, which is none — and returned without writing anything.
        every { repository.observe(familyId, "u1") } returns flowOf(
            ParentingPlanPair(yours = ParentingPlanEntry(answers = mapOf("q1" to "Weekends")), theirs = null)
        )
        val saved = slot<ParentingPlanEntry>()
        coEvery { repository.save(familyId, "u1", capture(saved)) } returns Unit
        val vm = viewModel(
            pairingStates = flow {
                emit(PairingState.Loading)
                delay(PAIRING_ANSWERS_AFTER_MS)
                emit(PairingState.Paired(partner))
            }
        )

        vm.answer("q2", "Alternate holidays")
        advanceUntilIdle()

        assertTrue(saved.isCaptured, "the answer was never written")
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

    private companion object {
        /** How long the pairing listener takes to answer in the test of a save made before it has. */
        const val PAIRING_ANSWERS_AFTER_MS = 100L

        /** Past the view model's `WhileSubscribed` timeout: the plan's listener has stopped. */
        const val SHARING_STOPS_AFTER_MS = 6_000L
    }
}
