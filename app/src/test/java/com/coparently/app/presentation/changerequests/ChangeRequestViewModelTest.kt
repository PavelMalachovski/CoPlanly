package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.requests.RequestClockActions
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.UpdateEventUseCase
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox's answers: accepting a change request moves the event and only then marks the request
 * accepted, and every refusal reaches the parent as a localised sentence (CQ-14).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val requests = MutableSharedFlow<List<ChangeRequest>>()
    private val changeRequestRepository = mockk<ChangeRequestRepository>(relaxed = true) {
        every { getAllChangeRequests() } returns requests
    }
    private val eventRepository = mockk<EventRepository>(relaxed = true) {
        every { getAllEvents() } returns flowOf(emptyList())
    }
    private val updateEvent = mockk<UpdateEventUseCase>()
    private val custodyModelRepository = mockk<CustodyModelRepository>(relaxed = true) {
        every { observeDayOverrides() } returns flowOf(emptyMap())
        every { observeShared() } returns flowOf(null)
        every { observeSharedOf(any()) } returns flowOf(null)
    }
    private val planReferences = mockk<PlanReferenceSource>()
    private val clockActions = mockk<RequestClockActions>(relaxed = true)

    /** The family's split document; none unless a test says so. */
    private val splitSettings = MutableStateFlow<FamilySettings?>(null)
    private val familySettingsRepository = mockk<FamilySettingsRepository>(relaxed = true) {
        every { observeSettingsOf(any()) } returns splitSettings
        coEvery { acceptProposal(any()) } returns Result.success(Unit)
        coEvery { declineProposal(any()) } returns Result.success(Unit)
        coEvery { withdrawProposal(any()) } returns Result.success(Unit)
    }

    /** The family's request clocks; none unless a test says so, so every request is placed as it always was. */
    private val clocks = MutableStateFlow(ClocksAt())
    private val olya = User(
        id = "u1",
        email = "olya@example.com",
        name = "Olya",
        role = "mom",
        colorCode = "",
        partnerId = "u2"
    )
    private val userRepository = mockk<UserRepository>(relaxed = true) {
        coEvery { getCurrentUser() } returns olya
        every { observeCurrentUserId() } returns flowOf("u1")
        every { getAllUsers() } returns flowOf(listOf(olya))
    }
    private val pairingRepository = mockk<PairingRepository> {
        every { observePairingState() } returns flowOf(
            PairingState.Paired(
                PartnerSummary(id = "u2", name = "Pavel", email = "pavel@example.com", pairedSinceMillis = 1L)
            )
        )
    }

    private val event = Event(
        id = "e1",
        title = "Dentist",
        startDateTime = LocalDateTime.of(2026, 9, 10, 9, 0),
        endDateTime = LocalDateTime.of(2026, 9, 10, 10, 0),
        eventType = "medical",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        updatedAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        lastModifiedBy = "u2"
    )
    private val request = ChangeRequest(
        id = "r1",
        eventId = "e1",
        eventTitle = "Dentist",
        requestedBy = "u2",
        requestedTo = "u1",
        currentStartDateTime = event.startDateTime,
        currentEndDateTime = event.endDateTime,
        proposedStartDateTime = LocalDateTime.of(2026, 9, 11, 14, 0),
        proposedEndDateTime = LocalDateTime.of(2026, 9, 11, 15, 0),
        createdAt = LocalDateTime.of(2026, 9, 2, 9, 0)
    )

    private fun viewModel(parents: ParentsSource = testParentsSource()) = ChangeRequestViewModel(
        changeRequestRepository = changeRequestRepository,
        eventRepository = eventRepository,
        eventUseCases = EventUseCases(
            createEvent = mockk(relaxed = true),
            updateEvent = updateEvent,
            deleteEvent = mockk(relaxed = true),
            getEvents = mockk(relaxed = true)
        ),
        userRepository = userRepository,
        custodyModelRepository = custodyModelRepository,
        parentsSource = parents,
        planReferenceSource = planReferences,
        requestClockActions = clockActions,
        requestsSource = RequestsSource(
            changeRequestRepository = changeRequestRepository,
            eventRepository = eventRepository,
            custodyModelRepository = custodyModelRepository,
            userRepository = userRepository,
            pairingRepository = pairingRepository,
            requestClocks = mockk<RequestClocks> { every { observeFamily(any(), any()) } returns clocks },
            familySettingsRepository = familySettingsRepository
        ),
        familySettingsRepository = familySettingsRepository
    )

    /** The split document of the family Olya shares with Pavel, agreed half each, with [by]'s 35/65 proposal. */
    private fun splitProposedBy(by: String) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf("u1", "u2"),
        lastModifiedBy = "u2",
        lastModifiedAtMillis = 1L,
        proposal = SplitRatioProposal(SplitRatio(THIRTY_FIVE), proposedBy = by, proposedAtMillis = SPLIT_PROPOSED_AT)
    )

    /** A view model whose inbox has drawn the family Olya shares with Pavel. */
    private suspend fun TestScope.inboxDrawn(
        parents: ParentsSource = testParentsSource()
    ): ChangeRequestViewModel {
        val vm = viewModel(parents)
        backgroundScope.launch { vm.requests.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())
        advanceUntilIdle()
        assertEquals("u1__u2", (vm.requests.value as RequestsState.Ready).scope.familyId)
        return vm
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
    fun `accepting fetches an unsynced event, moves it, then marks the request`() = runTest(dispatcher) {
        // change_requests is mirrored in realtime and events are not, so the request routinely
        // arrives first; accepting used to fail with "the event no longer exists".
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns null
        coEvery { eventRepository.fetchRemoteEvent("e1") } returns event
        val moved = slot<Event>()
        coEvery { updateEvent.invoke(capture(moved)) } answers { Result.success(moved.captured) }
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(request.proposedStartDateTime, moved.captured.startDateTime)
        assertEquals(request.proposedEndDateTime, moved.captured.endDateTime)
        assertEquals("u1", moved.captured.lastModifiedBy)
        coVerify(exactly = 1) { changeRequestRepository.updateStatus("r1", ChangeRequestStatus.ACCEPTED) }
    }

    @Test
    fun `an event missing everywhere is reported and the request stays open`() = runTest(dispatcher) {
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns null
        coEvery { eventRepository.fetchRemoteEvent("e1") } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_event_missing), vm.errorMessage.value)
        coVerify(exactly = 0) { changeRequestRepository.updateStatus(any(), any()) }
    }

    @Test
    fun `a move that fails leaves the request pending and says so`() = runTest(dispatcher) {
        coEvery { changeRequestRepository.getChangeRequestById("r1") } returns request
        coEvery { eventRepository.getEventById("e1") } returns event
        coEvery { updateEvent.invoke(any()) } returns Result.failure(IllegalStateException("validation"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.accept("r1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_apply_failed), vm.errorMessage.value)
        coVerify(exactly = 0) { changeRequestRepository.updateStatus(any(), any()) }

        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `a refused custody proposal answer is surfaced, not swallowed`() = runTest(dispatcher) {
        coEvery { custodyModelRepository.declineProposal(any()) } returns
            Result.failure(IllegalStateException("not yours to decide"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.declineProposal()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_proposal_decline_failed), vm.errorMessage.value)
    }

    @Test
    fun `an answer drawn for one family is refused once another family is on screen`() = runTest(dispatcher) {
        // The inbox drew the family with u2; a push tap has since switched the phone to the family
        // with u3. The decision is written to the family on screen, so it would answer u3's offer.
        val vm = viewModel(parents = testParentsSource(me = olya.copy(partnerId = "u3")))
        backgroundScope.launch { vm.requests.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())
        advanceUntilIdle()
        assertEquals("u1__u2", (vm.requests.value as RequestsState.Ready).scope.familyId)

        vm.acceptProposal()
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
        vm.clearError()
        vm.proposeSwapChange(listOf(LocalDate.of(2026, 10, 23)), note = null, answerBy = null)
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
        coVerify(exactly = 0) { custodyModelRepository.acceptProposal() }
        coVerify(exactly = 0) { custodyModelRepository.applyDayOverrides(any(), any(), any()) }
    }

    @Test
    fun `the inbox is Loading until its sources answer, then Ready even when empty`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.requests.collect {} }
        advanceUntilIdle()
        assertEquals(RequestsState.Loading, vm.requests.value)

        requests.emit(emptyList())
        advanceUntilIdle()

        val ready = vm.requests.value as RequestsState.Ready
        assertTrue(ready.inbox.isEmpty)
    }

    @Test
    fun `a waiting event gets a mini month of its own month`() = runTest(dispatcher) {
        val waiting = event.copy(
            id = "e-waiting",
            startDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
            endDateTime = LocalDateTime.of(2026, 10, 21, 17, 30),
            createdByFirebaseUid = "u2",
            familyId = "u1__u2",
            acceptance = EventAcceptance.PENDING
        )
        every { eventRepository.getAllEvents() } returns flowOf(listOf(waiting))
        every { custodyModelRepository.getActiveModel() } returns flowOf(null)
        val vm = viewModel()
        backgroundScope.launch { vm.miniMonths.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())

        // Computed off the main thread, so waited for in real time rather than on the test clock.
        val months = vm.miniMonths.first { it.isNotEmpty() }

        assertEquals(setOf("e-waiting"), months.keys)
        assertEquals(YearMonth.of(2026, 10), months.getValue("e-waiting").month)
    }

    /** An event Pavel created on 21 October, waiting for Olya's answer. */
    private val waitingInOctober = event.copy(
        id = "e-waiting",
        startDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
        endDateTime = LocalDateTime.of(2026, 10, 21, 17, 30),
        createdByFirebaseUid = "u2",
        familyId = "u1__u2",
        acceptance = EventAcceptance.PENDING
    )

    @Test
    fun `a family with no custody document gets no other family's days on its mini months`() =
        runTest(dispatcher) {
            every { eventRepository.getAllEvents() } returns flowOf(listOf(waitingInOctober))
            // Room's one active model is another family's: this family has no document of its own.
            every { custodyModelRepository.getActiveModel() } returns
                flowOf(CustodyModel.weekOnWeekOff(id = "elsewhere", startDate = LocalDate.of(2026, 9, 7)))
            val vm = viewModel()
            backgroundScope.launch { vm.miniMonths.collect {} }
            advanceUntilIdle()
            requests.emit(emptyList())

            val months = vm.miniMonths.first { it.isNotEmpty() }

            assertEquals(emptyMap(), months.getValue("e-waiting").custody)
        }

    @Test
    fun `the family's own custody document colours its mini months`() = runTest(dispatcher) {
        every { eventRepository.getAllEvents() } returns flowOf(listOf(waitingInOctober))
        every { custodyModelRepository.observeSharedOf("u1__u2") } returns flowOf(sharedWithProposal("u2", null))
        val vm = viewModel()
        backgroundScope.launch { vm.miniMonths.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())

        val months = vm.miniMonths.first { it.isNotEmpty() }

        // Every day of October 2026's grid, Monday 28 September to Sunday 1 November.
        assertEquals(35, months.getValue("e-waiting").custody.size)
    }

    private fun sharedWithProposal(by: String, citation: String?): SharedCustody {
        val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))
        return SharedCustody(
            model = model,
            lastModifiedBy = by,
            lastModifiedAtMillis = 1L,
            createdAt = "",
            proposal = CustodyProposal(
                model = model,
                repeatYearly = true,
                proposedBy = by,
                proposedAt = "",
                planCitationWire = citation
            )
        )
    }

    @Test
    fun `a cited proposal from the co-parent reports what the plan says about its source`() =
        runTest(dispatcher) {
            val wire = "p1|care_weekday|0123456789abcdef"
            every { custodyModelRepository.observeShared() } returns flowOf(sharedWithProposal("u2", wire))
            every { planReferences.observeCitation(wire, "u1", "u2") } returns
                flowOf(CitationStatus.Changed("care_weekday"))
            val vm = viewModel()
            backgroundScope.launch { vm.pendingProposalCitation.collect {} }
            advanceUntilIdle()

            assertEquals(CitationStatus.Changed("care_weekday"), vm.pendingProposalCitation.value)
        }

    @Test
    fun `this parent's own proposal is not theirs to answer, so its citation is not shown here`() =
        runTest(dispatcher) {
            every { custodyModelRepository.observeShared() } returns
                flowOf(sharedWithProposal("u1", "p1|care_weekday|0123456789abcdef"))
            val vm = viewModel()
            backgroundScope.launch { vm.pendingProposalCitation.collect {} }
            advanceUntilIdle()

            assertEquals(CitationStatus.None, vm.pendingProposalCitation.value)
            verify(exactly = 0) { planReferences.observeCitation(any(), any(), any()) }
        }

    @Test
    fun `a plan that cannot be read leaves the card as it always was`() = runTest(dispatcher) {
        val wire = "p1|care_weekday|0123456789abcdef"
        every { custodyModelRepository.observeShared() } returns flowOf(sharedWithProposal("u2", wire))
        every { planReferences.observeCitation(wire, "u1", "u2") } returns
            flow { throw IllegalStateException("denied") }
        val vm = viewModel()
        backgroundScope.launch { vm.pendingProposalCitation.collect {} }
        advanceUntilIdle()

        assertEquals(CitationStatus.None, vm.pendingProposalCitation.value)
    }

    @Test
    fun `the proposal pop-up follows the inbox, so a proposal past its deadline does not pop up`() =
        runTest(dispatcher) {
            val shared = sharedWithProposal("u2", null).let {
                it.copy(proposal = it.proposal?.copy(proposedAt = "2026-09-01T10:00:00"))
            }
            val proposal = shared.proposal!!
            every { custodyModelRepository.observeSharedOf("u1__u2") } returns flowOf(shared)
            val vm = viewModel()
            backgroundScope.launch { vm.pendingProposal.collect {} }
            advanceUntilIdle()
            requests.emit(emptyList())
            advanceUntilIdle()

            assertEquals(proposal, vm.pendingProposal.value, "no clock: it pops up as it always did")

            val now = 1_788_000_000_000L
            val clock = RequestDeadline(
                id = RequestDeadlineId.forProposal(proposal, "u1__u2"),
                kind = RequestKind.PROPOSAL,
                ref = proposal.proposedAt,
                familyId = "u1__u2",
                proposerUid = "u2",
                responderUid = "u1",
                answerByMillis = now + 3_600_000L
            )
            clocks.value = ClocksAt.of(listOf(clock), now)
            advanceUntilIdle()
            assertEquals(proposal, vm.pendingProposal.value, "open: it still pops up")

            clocks.value = ClocksAt.of(listOf(clock.copy(answerByMillis = now - 3_600_000L)), now)
            advanceUntilIdle()
            assertNull(vm.pendingProposal.value, "past its deadline: the next move is the proposer's")
        }

    @Test
    fun `a failed last chance says so`() = runTest(dispatcher) {
        coEvery { clockActions.giveLastChance("clock-1") } returns false
        val vm = viewModel()
        advanceUntilIdle()

        vm.giveLastChance("clock-1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.request_last_chance_failed), vm.errorMessage.value)
    }

    @Test
    fun `a second tap on the last chance while the first is under way gives nothing twice and says nothing`() =
        runTest(dispatcher) {
            // The server read and the write take a moment; the parent taps again meanwhile.
            coEvery { clockActions.giveLastChance("clock-1") } coAnswers {
                delay(2_000L)
                true
            }
            val vm = viewModel()
            advanceUntilIdle()

            vm.giveLastChance("clock-1")
            runCurrent()
            assertEquals(setOf("clock-1"), vm.lastChanceInFlight.value, "the card's button is disabled meanwhile")
            vm.giveLastChance("clock-1")
            advanceUntilIdle()

            coVerify(exactly = 1) { clockActions.giveLastChance("clock-1") }
            assertNull(vm.errorMessage.value)
            assertEquals(emptySet<String>(), vm.lastChanceInFlight.value, "and enabled again once it is done")
        }

    @Test
    fun `a request is marked seen by its clock's id, and a request with no clock is left alone`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.markSeen("clock-1")
            vm.markSeen(null)
            advanceUntilIdle()

            coVerify(exactly = 1) { clockActions.markSeen("clock-1") }
            coVerify(exactly = 1) { clockActions.markSeen(any()) }
        }

    @Test
    fun `a split answer goes to the family the inbox drew`() = runTest(dispatcher) {
        val vm = inboxDrawn()

        vm.acceptSplit()
        advanceUntilIdle()
        vm.declineSplit()
        advanceUntilIdle()
        vm.withdrawSplit()
        advanceUntilIdle()

        coVerify(exactly = 1) { familySettingsRepository.acceptProposal("u1__u2") }
        coVerify(exactly = 1) { familySettingsRepository.declineProposal("u1__u2") }
        coVerify(exactly = 1) { familySettingsRepository.withdrawProposal("u1__u2") }
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `a split answer drawn for one family is refused once another family is on screen`() = runTest(dispatcher) {
        // The inbox drew the family with u2; a push tap has since switched the phone to the family
        // with u3. Answering would decide a split of the family the parent is no longer looking at.
        val vm = inboxDrawn(parents = testParentsSource(me = olya.copy(partnerId = "u3")))

        vm.acceptSplit()
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
        vm.clearError()
        vm.withdrawSplit()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
        coVerify(exactly = 0) { familySettingsRepository.acceptProposal(any()) }
        coVerify(exactly = 0) { familySettingsRepository.withdrawProposal(any()) }
    }

    @Test
    fun `a refused split answer is said, not swallowed`() = runTest(dispatcher) {
        coEvery { familySettingsRepository.declineProposal(any()) } returns
            Result.failure(IllegalStateException("already answered"))
        val vm = inboxDrawn()

        vm.declineSplit()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.expenses_split_answer_failed), vm.errorMessage.value)
    }

    @Test
    fun `the split pop-up follows the inbox: the co-parent's split, never this parent's own`() =
        runTest(dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.pendingSplit.collect {} }
            backgroundScope.launch { vm.agreedSplit.collect {} }
            advanceUntilIdle()
            requests.emit(emptyList())
            advanceUntilIdle()
            assertNull(vm.pendingSplit.value, "no split document: nothing pops up")

            val pavels = splitProposedBy("u2")
            splitSettings.value = pavels
            advanceUntilIdle()
            assertEquals(pavels.proposal, vm.pendingSplit.value)
            assertEquals(SplitRatio.EVEN, vm.agreedSplit.value, "what the proposal would replace")

            splitSettings.value = splitProposedBy("u1")
            advanceUntilIdle()
            assertNull(vm.pendingSplit.value, "this parent's own split waits on the co-parent")
        }

    @Test
    fun `a split past its deadline does not pop up`() = runTest(dispatcher) {
        val pavels = splitProposedBy("u2")
        val proposal = pavels.proposal!!
        splitSettings.value = pavels
        val vm = viewModel()
        backgroundScope.launch { vm.pendingSplit.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())
        advanceUntilIdle()

        val now = SPLIT_PROPOSED_AT + 60_000L
        val clock = RequestDeadline(
            id = RequestDeadlineId.forSplit(proposal, "u1__u2"),
            kind = RequestKind.SPLIT,
            ref = proposal.proposedAtMillis.toString(),
            familyId = "u1__u2",
            proposerUid = "u2",
            responderUid = "u1",
            answerByMillis = now + 3_600_000L
        )
        clocks.value = ClocksAt.of(listOf(clock), now)
        advanceUntilIdle()
        assertEquals(proposal, vm.pendingSplit.value, "open: it pops up")

        clocks.value = ClocksAt.of(listOf(clock.copy(answerByMillis = now - 3_600_000L)), now)
        advanceUntilIdle()
        assertNull(vm.pendingSplit.value, "past its deadline: the next move is the proposer's")
    }

    @Test
    fun `opening the split marks its clock seen, under the family's id`() = runTest(dispatcher) {
        val pavels = splitProposedBy("u2")
        splitSettings.value = pavels
        val vm = viewModel()
        runCurrent()

        // Waited for in virtual time without advancing it: the look at the inbox is bounded by a
        // timeout that advancing the clock would fire.
        vm.markSplitSeen()
        runCurrent()
        requests.emit(emptyList())
        runCurrent()

        coVerify(exactly = 1) { clockActions.markSeen(RequestDeadlineId.forSplit(pavels.proposal!!, "u1__u2")) }
    }

    @Test
    fun `asking to change an agreed swap offers each day back from the agreement it reads`() = runTest(dispatcher) {
        coEvery { userRepository.getCurrentUserId() } returns "u1"
        val transform = slot<(Map<String, DayOverride>, String) -> Result<Map<String, DayOverride>>>()
        coEvery { custodyModelRepository.applyDayOverridesForDates(any(), capture(transform), any()) } returns
            Result.success(emptyMap())
        val agreed = DayOverride(
            toParent = "dad",
            requestedBy = "u2",
            requestedAt = "2026-10-01T09:00:00",
            status = DayOverrideStatus.ACCEPTED,
            decidedBy = "u1",
            decidedAt = "2026-10-01T10:00:00",
            groupId = "g1"
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.proposeSwapChange(listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 23)), "Ill", answerBy = null)
        advanceUntilIdle()

        coVerify { custodyModelRepository.applyDayOverridesForDates(listOf("2026-10-23", "2026-10-24"), any(), null) }
        val current = mapOf("2026-10-23" to agreed)
        val change = transform.captured(current, "2026-10-23").getOrThrow().getValue("2026-10-23")
        assertEquals("mom", change.toParent)
        assertEquals("u1", change.requestedBy)
        assertEquals(DayOverrideStatus.PENDING, change.status)
        assertEquals("Ill", change.note)
        assertEquals("dad", change.agreedToParent, "the agreement stands until the change is accepted")
        // A day whose agreement is gone by the time of the write is left as it is.
        assertEquals(current, transform.captured(current, "2026-10-24").getOrThrow())
    }

    private companion object {
        /** 35 % for slot 1, in basis points. */
        const val THIRTY_FIVE = 3_500

        /** When Pavel proposed the split: 2026-08-29T10:40:00Z. */
        const val SPLIT_PROPOSED_AT = 1_788_000_000_000L
    }
}
