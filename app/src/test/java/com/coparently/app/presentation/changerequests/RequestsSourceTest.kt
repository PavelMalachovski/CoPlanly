package com.coparently.app.presentation.changerequests

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * One source for the requests of the family on screen (phase 2, spec §6.3): the inbox, Home's
 * Requests row and the calendar's banner all read it, so a parent in two families sees one
 * family's requests at a time, and the three surfaces count the same ones.
 */
class RequestsSourceTest {

    private val at = LocalDateTime.of(2026, 10, 10, 9, 0)
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

    /**
     * @param shown The co-parent the family switcher projects onto the signed-in row.
     * @param coParents Every co-parent the row lists.
     */
    private fun source(
        pairing: PairingState,
        shown: String? = null,
        coParents: List<String> = emptyList(),
        requests: List<ChangeRequest> = emptyList(),
        events: List<Event> = emptyList()
    ): RequestsSource {
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
                every { observeDayOverrides() } returns flowOf(emptyMap())
                every { observeShared() } returns flowOf(null)
            },
            userRepository = mockk<UserRepository> {
                every { observeCurrentUserId() } returns flowOf("u1")
                every { getAllUsers() } returns flowOf(listOf(row))
            },
            pairingRepository = mockk<PairingRepository> { every { observePairingState() } returns flowOf(pairing) }
        )
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
}
