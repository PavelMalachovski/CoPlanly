package com.coparently.app.presentation.changerequests

import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.requests.AgreementWithdrawals
import com.coparently.app.data.requests.FamilyAgreementsSource
import com.coparently.app.data.requests.RequestClockActions
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.FamilyAgreements
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.domain.usecase.DeleteEventUseCase
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testParentsSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The asker's withdrawals in the requests inbox (October 2026, owner request: "we can withdraw an
 * expense split; let the rest be withdrawn too"): a day swap, a custody proposal and an event
 * waiting for an answer. Each is taken back only while it is still the asker's and still waits,
 * and a refusal is said, never swallowed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeRequestWithdrawTest {

    private val dispatcher = StandardTestDispatcher()
    private val requests = MutableSharedFlow<List<ChangeRequest>>()
    private val changeRequestRepository = mockk<ChangeRequestRepository>(relaxed = true) {
        every { getAllChangeRequests() } returns requests
    }
    private val eventRepository = mockk<EventRepository>(relaxed = true) {
        every { getAllEvents() } returns flowOf(emptyList())
    }
    private val deleteEvent = mockk<DeleteEventUseCase>().also { useCase ->
        coEvery { useCase.invoke(any(), any()) } returns Result.success(Unit)
    }
    private val custodyModelRepository = mockk<CustodyModelRepository>(relaxed = true) {
        every { observeDayOverrides() } returns flowOf(emptyMap())
        every { observeShared() } returns flowOf(null)
        every { observeSharedOf(any()) } returns flowOf(null)
    }

    private val agreementWithdrawals = mockk<AgreementWithdrawals> {
        coEvery { withdrawSupport(any()) } returns true
        coEvery { withdrawProfessional(any()) } returns true
    }

    /** Each test's child support, professional grants and plan; none unless a test sets them. */
    private var agreements = FamilyAgreements()
    private val agreementsSource = mockk<FamilyAgreementsSource> {
        every { observe(any()) } answers { flowOf(agreements) }
    }
    private val familySettingsRepository = mockk<FamilySettingsRepository>(relaxed = true) {
        every { observeSettingsOf(any()) } returns flowOf(null)
    }
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
        coEvery { getCurrentUserId() } returns "u1"
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
        title = "Swimming gala",
        startDateTime = LocalDateTime.of(2026, 11, 7, 17, 0),
        endDateTime = LocalDateTime.of(2026, 11, 7, 18, 0),
        eventType = "activity",
        parentOwner = "dad",
        createdAt = LocalDateTime.of(2026, 10, 20, 9, 0),
        updatedAt = LocalDateTime.of(2026, 10, 20, 9, 0),
        lastModifiedBy = "u1"
    )

    private fun viewModel(parents: ParentsSource = testParentsSource()) = ChangeRequestViewModel(
        changeRequestRepository = changeRequestRepository,
        eventRepository = eventRepository,
        eventUseCases = EventUseCases(
            createEvent = mockk(relaxed = true),
            updateEvent = mockk(relaxed = true),
            deleteEvent = deleteEvent,
            getEvents = mockk(relaxed = true)
        ),
        userRepository = userRepository,
        custodyModelRepository = custodyModelRepository,
        parentsSource = parents,
        planReferenceSource = mockk(),
        requestClockActions = mockk<RequestClockActions>(relaxed = true),
        requestsSource = RequestsSource(
            changeRequestRepository = changeRequestRepository,
            eventRepository = eventRepository,
            custodyModelRepository = custodyModelRepository,
            userRepository = userRepository,
            pairingRepository = pairingRepository,
            requestClocks = mockk<RequestClocks> {
                every { observeFamily(any(), any()) } returns MutableStateFlow(ClocksAt())
            },
            familySettingsRepository = familySettingsRepository,
            familyAgreementsSource = agreementsSource
        ),
        familySettingsRepository = familySettingsRepository,
        agreementWithdrawals = agreementWithdrawals
    )

    /** A view model whose inbox has drawn the family Olya shares with Pavel. */
    private suspend fun TestScope.inboxDrawn(parents: ParentsSource = testParentsSource()): ChangeRequestViewModel {
        val vm = viewModel(parents)
        backgroundScope.launch { vm.requests.collect {} }
        advanceUntilIdle()
        requests.emit(emptyList())
        advanceUntilIdle()
        assertEquals("u1__u2", (vm.requests.value as RequestsState.Ready).scope.familyId)
        return vm
    }

    private fun offer(status: DayOverrideStatus = DayOverrideStatus.PENDING, by: String = "u1") = DayOverride(
        toParent = "mom",
        requestedBy = by,
        requestedAt = "2026-10-20T09:00:00",
        status = status,
        groupId = "g9"
    )

    private fun groupOf(vararg days: Pair<Int, DayOverride>) =
        DaySwapGroup(days.map { (day, override) -> DaySwap(LocalDate.of(2026, 10, day), override) })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `withdrawing a swap withdraws each of this parent's days still waiting, from the map it reads`() =
        runTest(dispatcher) {
            val transform = slot<(Map<String, DayOverride>, String) -> Result<Map<String, DayOverride>>>()
            coEvery {
                custodyModelRepository.applyDayOverridesForDates(any(), capture(transform), any(), any())
            } returns
                Result.success(emptyMap())
            val vm = inboxDrawn()

            vm.withdrawSwapGroup(groupOf(30 to offer(), 31 to offer()))
            advanceUntilIdle()

            coVerify {
                custodyModelRepository.applyDayOverridesForDates(listOf("2026-10-30", "2026-10-31"), any(), null, any())
            }
            val pending = mapOf("2026-10-30" to offer())
            val withdrawn = transform.captured(pending, "2026-10-30").getOrThrow().getValue("2026-10-30")
            assertEquals(DayOverrideStatus.CANCELLED, withdrawn.status)
            assertEquals("u1", withdrawn.decidedBy)
            // A day the co-parent answered meanwhile is left as they answered it.
            val answered = mapOf("2026-10-31" to offer(DayOverrideStatus.ACCEPTED))
            assertEquals(answered, transform.captured(answered, "2026-10-31").getOrThrow())
            assertNull(vm.errorMessage.value)
        }

    @Test
    fun `an offer answered day by day takes the accepted days, then turns down the rest`() = runTest(dispatcher) {
        // 21 October 2026, Zuzana's test: one run per answer, a day left out stays waiting.
        val transforms = mutableListOf<Pair<List<String>, SwapTransform>>()
        coEvery { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), any()) } answers {
            transforms += firstArg<List<String>>() to secondArg()
            Result.success(emptyMap())
        }
        val vm = inboxDrawn()
        val fromBob = offer(by = "u2")

        vm.decideSwapDays(
            groupOf(29 to fromBob, 30 to fromBob, 31 to fromBob),
            mapOf("2026-10-29" to true, "2026-10-31" to false)
        )
        advanceUntilIdle()

        assertEquals(listOf(listOf("2026-10-29"), listOf("2026-10-31")), transforms.map { it.first })
        val (_, accept) = transforms[0]
        val (_, decline) = transforms[1]
        assertEquals(
            DayOverrideStatus.ACCEPTED,
            accept(mapOf("2026-10-29" to fromBob), "2026-10-29").getOrThrow().getValue("2026-10-29").status
        )
        assertEquals(
            DayOverrideStatus.DECLINED,
            decline(mapOf("2026-10-31" to fromBob), "2026-10-31").getOrThrow().getValue("2026-10-31").status
        )
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `a swap that is not this parent's own, or no longer waits, is not withdrawn`() = runTest(dispatcher) {
        val vm = inboxDrawn()

        vm.withdrawSwapGroup(groupOf(30 to offer(by = "u2")))
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.change_request_error_offer_answered), vm.errorMessage.value)
        vm.clearError()
        vm.withdrawSwapGroup(groupOf(30 to offer(DayOverrideStatus.DECLINED)))
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_offer_answered), vm.errorMessage.value)
        coVerify(exactly = 0) { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed swap withdrawal says so`() = runTest(dispatcher) {
        coEvery { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("offline"))
        val vm = inboxDrawn()

        vm.withdrawSwapGroup(groupOf(30 to offer()))
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_withdraw_failed), vm.errorMessage.value)
    }

    @Test
    fun `a swap drawn for one family is not withdrawn once another family is on screen`() = runTest(dispatcher) {
        val vm = inboxDrawn(parents = testParentsSource(me = olya.copy(partnerId = "u3")))

        vm.withdrawSwapGroup(groupOf(30 to offer()))
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
        coVerify(exactly = 0) { custodyModelRepository.applyDayOverridesForDates(any(), any(), any(), any()) }
    }

    @Test
    fun `withdrawing this parent's custody proposal goes to the repository, and a refusal is said`() =
        runTest(dispatcher) {
            coEvery { custodyModelRepository.withdrawProposal() } returns Result.success(Unit)
            val vm = inboxDrawn()

            vm.withdrawProposal()
            advanceUntilIdle()
            coVerify(exactly = 1) { custodyModelRepository.withdrawProposal() }
            assertNull(vm.errorMessage.value)

            coEvery { custodyModelRepository.withdrawProposal() } returns Result.failure(IllegalStateException("gone"))
            vm.withdrawProposal()
            advanceUntilIdle()
            assertEquals(UiText.Res(R.string.change_request_error_withdraw_failed), vm.errorMessage.value)
        }

    @Test
    fun `a custody proposal drawn for one family is not withdrawn once another family is on screen`() =
        runTest(dispatcher) {
            val vm = inboxDrawn(parents = testParentsSource(me = olya.copy(partnerId = "u3")))

            vm.withdrawProposal()
            advanceUntilIdle()

            assertEquals(UiText.Res(R.string.change_request_error_family_changed), vm.errorMessage.value)
            coVerify(exactly = 0) { custodyModelRepository.withdrawProposal() }
        }

    @Test
    fun `withdrawing an event this parent created and still waiting deletes it, telling the co-parent`() =
        runTest(dispatcher) {
            val mine = event.copy(createdByFirebaseUid = "u1", acceptance = EventAcceptance.PENDING)
            coEvery { eventRepository.getEventById("e1") } returns mine
            val vm = viewModel()
            advanceUntilIdle()

            vm.withdrawEvent("e1")
            advanceUntilIdle()

            coVerify(exactly = 1) { deleteEvent.invoke(mine, true) }
            assertNull(vm.errorMessage.value)
        }

    @Test
    fun `an event that is the co-parent's, or already answered, is not withdrawn`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coEvery { eventRepository.getEventById("e1") } returns
            event.copy(createdByFirebaseUid = "u2", acceptance = EventAcceptance.PENDING)
        vm.withdrawEvent("e1")
        advanceUntilIdle()
        assertEquals(UiText.Res(R.string.change_request_error_offer_answered), vm.errorMessage.value)
        vm.clearError()
        coEvery { eventRepository.getEventById("e1") } returns
            event.copy(createdByFirebaseUid = "u1", acceptance = EventAcceptance.ACCEPTED)
        vm.withdrawEvent("e1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_offer_answered), vm.errorMessage.value)
        coVerify(exactly = 0) { deleteEvent.invoke(any(), any()) }
    }

    @Test
    fun `a failed event withdrawal says so`() = runTest(dispatcher) {
        coEvery { eventRepository.getEventById("e1") } returns
            event.copy(createdByFirebaseUid = "u1", acceptance = EventAcceptance.PENDING)
        coEvery { deleteEvent.invoke(any(), any()) } returns Result.failure(IllegalStateException("disk"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.withdrawEvent("e1")
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.change_request_error_withdraw_failed), vm.errorMessage.value)
    }

    private fun supportBy(uid: String) = SupportAgreement(
        familyId = "u1__u2",
        participants = listOf("u1", "u2"),
        proposal = SupportProposal(
            SupportTerms(BigDecimal("3000"), "CZK", 15, "u2", YearMonth.of(2026, 11)),
            proposedBy = uid,
            proposedAtMillis = 1L
        )
    )

    @Test
    fun withdrawSupportUsesShownAgreement() = runTest(dispatcher) {
        val shown = supportBy("u1")
        agreements = FamilyAgreements(support = shown)
        val vm = inboxDrawn()

        // The family switches afterwards: what is withdrawn is still what the row showed.
        agreements = FamilyAgreements(support = supportBy("u1").copy(familyId = "u1__u3"))
        vm.withdrawSupport()
        advanceUntilIdle()

        coVerify(exactly = 1) { agreementWithdrawals.withdrawSupport(shown) }
    }

    @Test
    fun withdrawSupportIgnoresTheirs() = runTest(dispatcher) {
        agreements = FamilyAgreements(support = supportBy("u2"))
        val vm = inboxDrawn()

        vm.withdrawSupport()
        advanceUntilIdle()

        coVerify(exactly = 0) { agreementWithdrawals.withdrawSupport(any()) }
    }

    @Test
    fun withdrawProfessionalRevokes() = runTest(dispatcher) {
        val vm = inboxDrawn()

        vm.withdrawProfessional("u1__u2__pro")
        advanceUntilIdle()

        coVerify(exactly = 1) { agreementWithdrawals.withdrawProfessional("u1__u2__pro") }
    }

    @Test
    fun failedWithdrawShowsError() = runTest(dispatcher) {
        coEvery { agreementWithdrawals.withdrawSupport(any()) } returns false
        agreements = FamilyAgreements(support = supportBy("u1"))
        val vm = inboxDrawn()

        vm.withdrawSupport()
        advanceUntilIdle()

        assertEquals(UiText.Res(R.string.request_agreement_withdraw_failed), vm.errorMessage.value)
    }
}

/** One day's transition, as `applyDayOverridesForDates` takes it. */
private typealias SwapTransform = (Map<String, DayOverride>, String) -> Result<Map<String, DayOverride>>
