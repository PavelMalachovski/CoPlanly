package com.coparently.app.presentation.changerequests

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.requests.RequestClockKeeper
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.data.requests.RequestTime
import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * One source for the requests of the family on screen (phase 2, spec §6.3): the inbox, Home's
 * Requests row and the calendar's banner all read it, so a parent in two families sees one
 * family's requests at a time, and the three surfaces count the same ones. Since phase 3a it also
 * reads that family's request clocks, and their minute tick re-places each request.
 */
class RequestsSourceTest {

    private val at = LocalDateTime.of(2026, 10, 10, 9, 0)

    /** The time the clocks are read at, which a test moves before it ticks. */
    private var now = NOW

    /** The minute ticks: one at once, then whenever a test emits. */
    private val ticks = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    /** The clocks listener, each family's clocks by family id; a family not named has none. */
    private val clockRepository = mockk<RequestDeadlineRepository>()

    /** The split listener, each family's `family_settings` document by family id; a family not named has none. */
    private val settingsRepository = mockk<FamilySettingsRepository>()
    private val bob = PartnerSummary(id = "u2", name = "Bob", email = "bob@example.com", pairedSinceMillis = 1L)

    private fun request(id: String, status: ChangeRequestStatus = ChangeRequestStatus.PENDING) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = "u2",
        requestedTo = "u1",
        currentStartDateTime = at,
        proposedStartDateTime = at.plusDays(1),
        status = status,
        createdAt = at,
        respondedAt = at.plusHours(1).takeIf { status != ChangeRequestStatus.PENDING }
    )

    private val bobsEvent = Event(
        id = "e1",
        title = "Dentist",
        startDateTime = at.plusDays(3),
        eventType = "medical",
        parentOwner = "mom",
        createdAt = at,
        updatedAt = at,
        createdByFirebaseUid = "u2",
        familyId = "u1__u2",
        acceptance = EventAcceptance.PENDING
    )

    /** Bob's offer to swap a day five days from now, still waiting for Alice. */
    private val bobsSwap = mapOf(
        LocalDate.now().plusDays(5).toString() to DayOverride(
            toParent = "dad",
            requestedBy = "u2",
            requestedAt = "2026-10-10T09:00:00"
        )
    )

    /** The custody document of the family Alice shares with Bob, holding [bobsSwap]. */
    private val bobsFamilyCustody = SharedCustody(
        model = CustodyModel(
            id = "m1",
            modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
            patternDays = 14,
            momDayIndices = (0..6).toSet(),
            startDate = LocalDate.of(2026, 8, 3)
        ),
        lastModifiedBy = "u2",
        lastModifiedAtMillis = 1L,
        createdAt = "2026-08-01T09:00:00",
        dayOverrides = bobsSwap
    )

    /**
     * @param shown The co-parent the family switcher projects onto the signed-in row.
     * @param coParents Every co-parent the row lists.
     * @param roomSwaps What Room's one active custody model holds, whichever family it came from.
     * @param documents Each family's custody document, by family id; a family not named has none.
     * @param clocks Each family's request clocks, by family id; a family not named has none.
     * @param settings Each family's split document, by family id; a family not named has none.
     */
    @Suppress("LongParameterList") // one fixture for every source the requests are derived from
    private fun source(
        pairing: PairingState,
        shown: String? = null,
        coParents: List<String> = emptyList(),
        requests: List<ChangeRequest> = emptyList(),
        events: List<Event> = emptyList(),
        roomSwaps: Map<String, DayOverride> = emptyMap(),
        documents: Map<String, SharedCustody> = emptyMap(),
        clocks: Map<String, List<RequestDeadline>> = emptyMap(),
        settings: Map<String, FamilySettings> = emptyMap()
    ): RequestsSource {
        every { clockRepository.observeFamily("u1", any()) } answers { flowOf(clocks[secondArg()].orEmpty()) }
        every { settingsRepository.observeSettingsOf(any()) } answers { flowOf(settings[firstArg()]) }
        val row = User(
            id = "u1",
            email = "alice@example.com",
            name = "Alice",
            role = "mom",
            colorCode = "#FF4081",
            partnerId = shown,
            partnerIds = coParents
        )
        return RequestsSource(
            changeRequestRepository = mockk<ChangeRequestRepository> {
                every { getAllChangeRequests() } returns flowOf(requests)
            },
            eventRepository = mockk<EventRepository> { every { getAllEvents() } returns flowOf(events) },
            custodyModelRepository = mockk<CustodyModelRepository> {
                every { observeDayOverrides() } returns flowOf(roomSwaps)
                every { observeShared() } returns flowOf(documents.values.firstOrNull())
                every { observeSharedOf(any()) } answers { flowOf(documents[firstArg()]) }
            },
            userRepository = mockk<UserRepository> {
                every { observeCurrentUserId() } returns flowOf("u1")
                every { getAllUsers() } returns flowOf(listOf(row))
            },
            pairingRepository = mockk<PairingRepository> { every { observePairingState() } returns flowOf(pairing) },
            requestClocks = RequestClocks(
                clockRepository,
                RequestTime(now = { now }, zoneOf = { ZoneOffset.UTC }, ticks = ticks),
                // Delivery is the keeper's, and `RequestClocksTest` covers the hand-over.
                mockk<RequestClockKeeper>(relaxed = true)
            ),
            familySettingsRepository = settingsRepository
        )
    }

    /** The split document of the family Alice shares with Bob, holding [by]'s proposal of 35/65. */
    private fun splitProposedBy(by: String) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf("u1", "u2"),
        lastModifiedBy = "u2",
        lastModifiedAtMillis = 1L,
        proposal = SplitRatioProposal(SplitRatio(THIRTY_FIVE), proposedBy = by, proposedAtMillis = NOW)
    )

    /** The clock of [request], written by its author for the parent it asks, due an hour after [NOW]. */
    private fun clockOf(request: ChangeRequest, familyId: String = "u1__u2") = RequestDeadline(
        id = RequestDeadlineId.forChange(request),
        kind = RequestKind.CHANGE,
        ref = request.id,
        familyId = familyId,
        proposerUid = request.requestedBy,
        responderUid = request.requestedTo,
        answerByMillis = NOW + HOUR
    )

    /** The next state that is [RequestsState.Ready] and satisfies [until], skipping the rest. */
    private suspend fun ReceiveTurbine<RequestsState>.awaitReady(
        until: (RequestsState.Ready) -> Boolean
    ): RequestsState.Ready {
        while (true) {
            val state = awaitItem()
            if (state is RequestsState.Ready && until(state)) return state
        }
    }

    @Test
    fun `loading until the pairing state answers`() = runTest {
        assertEquals(RequestsState.Loading, source(PairingState.Loading).observe().first())
    }

    @Test
    fun `no co-parent when not paired`() = runTest {
        assertEquals(RequestsState.NoCoParent, source(PairingState.NotPaired()).observe().first())
    }

    @Test
    fun `the family on screen decides`() = runTest {
        val state = source(PairingState.Paired(bob), shown = "u3", coParents = listOf("u2", "u3")).observe().first()

        val ready = assertIs<RequestsState.Ready>(state)
        assertEquals("u3", ready.scope.coParentUid)
        assertEquals("u1__u3", ready.scope.familyId)
        assertEquals(false, ready.scope.soleFamily)
    }

    @Test
    fun `another family's day swap is in neither section`() = runTest {
        // Alice switched to Carol's family, which has no custody document, so Room's one active
        // model still holds the schedule — and Bob's swap — of the family she shares with Bob.
        val carolsFamily = source(
            PairingState.Paired(bob),
            shown = "u3",
            coParents = listOf("u2", "u3"),
            roomSwaps = bobsSwap,
            documents = mapOf("u1__u2" to bobsFamilyCustody)
        ).observe().first()
        val bobsFamily = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2", "u3"),
            roomSwaps = bobsSwap,
            documents = mapOf("u1__u2" to bobsFamilyCustody)
        ).observe().first()

        val inCarols = assertIs<RequestsState.Ready>(carolsFamily).inbox
        assertEquals(emptyList(), inCarols.waitingOnMe.swaps)
        assertEquals(emptyList(), inCarols.waitingOnThem.swaps)
        assertEquals(1, assertIs<RequestsState.Ready>(bobsFamily).inbox.waitingOnMe.swaps.size)
    }

    @Test
    fun `another family's request, event and count are in neither section`() = runTest {
        val elsewhere = source(
            PairingState.Paired(bob),
            shown = "u3",
            coParents = listOf("u2", "u3"),
            requests = listOf(request("r1")),
            events = listOf(bobsEvent)
        ).observe().first()
        val here = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2", "u3"),
            requests = listOf(request("r1")),
            events = listOf(bobsEvent)
        ).observe().first()

        assertEquals(0, assertIs<RequestsState.Ready>(elsewhere).inbox.waitingOnMe.count)
        assertEquals(emptyList(), (elsewhere as RequestsState.Ready).changeRequests)
        assertEquals(2, assertIs<RequestsState.Ready>(here).inbox.waitingOnMe.count)
    }

    @Test
    fun `history and waiting come from the same lists`() = runTest {
        val state = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2"),
            requests = listOf(request("waiting"), request("answered", ChangeRequestStatus.DECLINED))
        ).observe().first()

        val ready = assertIs<RequestsState.Ready>(state)
        assertEquals(listOf("waiting"), ready.inbox.waitingOnMe.changeRequests.map { it.id })
        assertEquals(listOf("cr_answered"), ready.inbox.history.map { it.key })
        assertEquals(setOf("waiting", "answered"), ready.changeRequests.map { it.id }.toSet())
    }

    @Test
    fun `the family's clocks and the minute tick reach the inbox`() = runTest {
        val waiting = request("r1")
        val bobsClock = clockOf(waiting)
        // Alice's other family has a clock of its own, which the inbox of Bob's family never asks for.
        val carolsClock = clockOf(waiting.copy(id = "r9", requestedBy = "u3"), familyId = "u1__u3")
        val requests = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2", "u3"),
            requests = listOf(waiting),
            clocks = mapOf("u1__u2" to listOf(bobsClock), "u1__u3" to listOf(carolsClock))
        )

        requests.observe().test {
            val first = awaitReady { it.clocks.nowMillis == NOW }
            assertEquals(ClocksAt.of(listOf(bobsClock), NOW), first.clocks)
            assertEquals(ClockState.OPEN, first.inbox.timedFor(bobsClock.id)?.state)

            now = NOW + MINUTE
            ticks.emit(Unit)

            val ticked = awaitReady { it.clocks.nowMillis == NOW + MINUTE }
            assertEquals(ClocksAt.of(listOf(bobsClock), NOW + MINUTE), ticked.clocks)
            assertEquals(NOW + MINUTE, ticked.inbox.timedFor(bobsClock.id)?.nowMillis)
            cancelAndIgnoreRemainingEvents()
        }
        verify { clockRepository.observeFamily("u1", "u1__u2") }
        verify(exactly = 0) { clockRepository.observeFamily("u1", "u1__u3") }
    }

    @Test
    fun `a tick moves a request past its deadline without any document changing`() = runTest {
        val waiting = request("r1")
        val requests = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2"),
            requests = listOf(waiting),
            clocks = mapOf("u1__u2" to listOf(clockOf(waiting)))
        )

        requests.observe().test {
            val open = awaitReady { it.clocks.nowMillis == NOW }
            assertEquals(listOf("r1"), open.inbox.waitingOnMe.changeRequests.map { it.id })
            assertEquals(1, open.inbox.waitingOnMe.count)

            // The deadline passes: nothing is written and nothing arrives; only the minute turns over.
            now = NOW + 2 * HOUR
            ticks.emit(Unit)

            val past = awaitReady { it.clocks.nowMillis == NOW + 2 * HOUR }
            assertEquals(0, past.inbox.waitingOnMe.count)
            assertEquals(listOf("r1"), past.inbox.waitingOnThem.changeRequests.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the requests are not held back until the clocks listener answers`() = runTest {
        val waiting = request("r1")
        val requests = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2"),
            requests = listOf(waiting)
        )
        // A listener that has not answered yet — offline with a cold cache, or retrying a denial.
        every { clockRepository.observeFamily(any(), any()) } returns MutableSharedFlow()

        val ready = requests.observe().filterIsInstance<RequestsState.Ready>().first()

        assertEquals(ClocksAt(), ready.clocks)
        assertEquals(listOf("r1"), ready.inbox.waitingOnMe.changeRequests.map { it.id })
    }

    @Test
    fun `the family's own split proposal waits on the parent it asks`() = runTest {
        val bobs = splitProposedBy("u2")

        val ready = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2", "u3"),
            settings = mapOf("u1__u2" to bobs)
        ).observe().filterIsInstance<RequestsState.Ready>().first { it.settings != null }

        assertEquals(bobs, ready.settings)
        assertEquals(bobs.proposal, ready.inbox.waitingOnMe.split)
        assertNull(ready.inbox.waitingOnThem.split)
        assertEquals(1, ready.inbox.waitingOnMe.count)
        verify { settingsRepository.observeSettingsOf("u1__u2") }
    }

    @Test
    fun `another family's split is in neither section`() = runTest {
        // Alice switched to Carol's family; Bob's proposal is in the family she shares with him.
        val ready = source(
            PairingState.Paired(bob),
            shown = "u3",
            coParents = listOf("u2", "u3"),
            settings = mapOf("u1__u2" to splitProposedBy("u2"))
        ).observe().filterIsInstance<RequestsState.Ready>().first()

        assertNull(ready.settings)
        assertNull(ready.inbox.waitingOnMe.split)
        assertNull(ready.inbox.waitingOnThem.split)
        verify(exactly = 0) { settingsRepository.observeSettingsOf("u1__u2") }
    }

    @Test
    fun `the requests are not held back until the split listener answers`() = runTest {
        val requests = source(
            PairingState.Paired(bob),
            shown = "u2",
            coParents = listOf("u2"),
            requests = listOf(request("r1"))
        )
        // A listener that has not answered yet — offline with a cold cache, or retrying a denial.
        every { settingsRepository.observeSettingsOf(any()) } returns MutableSharedFlow()

        val ready = requests.observe().filterIsInstance<RequestsState.Ready>().first()

        assertNull(ready.settings)
        assertEquals(listOf("r1"), ready.inbox.waitingOnMe.changeRequests.map { it.id })
    }

    private companion object {
        /** 35 % for slot 1, in basis points. */
        const val THIRTY_FIVE = 3_500

        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** 2026-10-14T09:00:00Z. */
        const val NOW = 1_791_968_400_000L
    }
}
