package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inbox's three sections (phase 2, spec §6.2): what waits on this parent, what waits on the
 * co-parent, and how every past request ended.
 */
class RequestInboxTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)
    private val today = LocalDate.of(2026, 10, 14)
    private val created = LocalDateTime.of(2026, 10, 10, 9, 0)

    private fun request(
        id: String,
        from: String,
        to: String,
        status: ChangeRequestStatus = ChangeRequestStatus.PENDING,
        createdAt: LocalDateTime = created
    ) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = LocalDateTime.of(2026, 10, 15, 17, 0),
        proposedStartDateTime = LocalDateTime.of(2026, 10, 16, 18, 30),
        status = status,
        createdAt = createdAt,
        respondedAt = created.plusDays(1).takeIf { status != ChangeRequestStatus.PENDING }
    )

    private fun event(id: String, creator: String, start: LocalDateTime = LocalDateTime.of(2026, 10, 21, 16, 0)) =
        Event(
            id = id,
            title = id,
            startDateTime = start,
            eventType = "other",
            parentOwner = if (creator == "u1") "dad" else "mom",
            createdAt = created,
            updatedAt = created,
            createdByFirebaseUid = creator,
            familyId = "u1__u2",
            acceptance = EventAcceptance.PENDING
        )

    private fun swap(requestedBy: String, groupId: String) = DayOverride(
        toParent = "mom",
        requestedBy = requestedBy,
        requestedAt = "2026-10-13T10:00:00",
        groupId = groupId
    )

    private fun proposalBy(uid: String): SharedCustody {
        val model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7))
        return SharedCustody(
            model = model,
            lastModifiedBy = uid,
            lastModifiedAtMillis = 1L,
            createdAt = "",
            proposal = CustodyProposal(
                model = model,
                repeatYearly = true,
                proposedBy = uid,
                proposedAt = "2026-10-12T08:00:00"
            )
        )
    }

    private val requests = listOf(request("theirs", from = "u2", to = "u1"), request("mine", from = "u1", to = "u2"))
    private val events = listOf(event("for-me", creator = "u2"), event("for-them", creator = "u1"))
    private val overrides = mapOf(
        "2026-10-20" to swap(requestedBy = "u2", groupId = "theirs"),
        "2026-10-22" to swap(requestedBy = "u1", groupId = "mine")
    )

    @Test
    fun `what waits on me is the co-parent's proposal, their events, swaps offered to me and their requests`() {
        val inbox = RequestInbox.build(scope, requests, events, overrides, proposalBy("u2"), today)

        assertEquals("u2", inbox.waitingOnMe.proposal?.proposedBy)
        assertEquals(listOf("for-me"), inbox.waitingOnMe.events.map { it.id })
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), inbox.waitingOnMe.swaps.map { it.firstDate })
        assertEquals(listOf("theirs"), inbox.waitingOnMe.changeRequests.map { it.id })
        assertNull(inbox.waitingOnThem.proposal)
    }

    @Test
    fun `what waits on them is my proposal, my pending events, my pending swap offers and my pending requests`() {
        val inbox = RequestInbox.build(scope, requests, events, overrides, proposalBy("u1"), today)

        assertEquals("u1", inbox.waitingOnThem.proposal?.proposedBy)
        assertEquals(listOf("for-them"), inbox.waitingOnThem.events.map { it.id })
        assertEquals(listOf(LocalDate.of(2026, 10, 22)), inbox.waitingOnThem.swaps.map { it.firstDate })
        assertEquals(listOf("mine"), inbox.waitingOnThem.changeRequests.map { it.id })
        assertNull(inbox.waitingOnMe.proposal)
    }

    @Test
    fun `an answered item leaves both waiting sections and appears in history`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = listOf(request("answered", from = "u1", to = "u2", status = ChangeRequestStatus.ACCEPTED)),
            events = emptyList(),
            overrides = emptyMap(),
            shared = null,
            today = today
        )

        assertTrue(inbox.waitingOnMe.isEmpty)
        assertTrue(inbox.waitingOnThem.isEmpty)
        assertEquals(listOf("cr_answered"), inbox.history.map { it.key })
    }

    @Test
    fun `a swap I offered never waits on me`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = emptyList(),
            overrides = mapOf("2026-10-22" to swap(requestedBy = "u1", groupId = "mine")),
            shared = null,
            today = today
        )

        assertEquals(emptyList(), inbox.waitingOnMe.swaps)
        assertEquals(1, inbox.waitingOnThem.swaps.size)
    }

    @Test
    fun `count includes the proposal and countWithoutProposal does not`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = emptyList(),
            events = listOf(event("for-me", creator = "u2")),
            overrides = mapOf("2026-10-20" to swap(requestedBy = "u2", groupId = "theirs")),
            shared = proposalBy("u2"),
            today = today
        )

        assertEquals(3, inbox.waitingOnMe.count)
        assertEquals(2, inbox.waitingOnMe.countWithoutProposal)
        assertEquals(0, inbox.waitingOnThem.count)
    }

    @Test
    fun `waiting items keep the inbox's order, events by start and requests by creation`() {
        val inbox = RequestInbox.build(
            scope,
            changeRequests = listOf(
                request("later", from = "u2", to = "u1", createdAt = created.plusHours(2)),
                request("earlier", from = "u2", to = "u1", createdAt = created)
            ),
            events = listOf(
                event("second", creator = "u2", start = LocalDateTime.of(2026, 10, 25, 9, 0)),
                event("first", creator = "u2", start = LocalDateTime.of(2026, 10, 18, 9, 0))
            ),
            overrides = emptyMap(),
            shared = null,
            today = today
        )

        assertEquals(listOf("first", "second"), inbox.waitingOnMe.events.map { it.id })
        assertEquals(listOf("earlier", "later"), inbox.waitingOnMe.changeRequests.map { it.id })
    }

    @Test
    fun `an inbox is empty only when all three sections are`() {
        assertTrue(RequestInbox.build(scope, emptyList(), emptyList(), emptyMap(), null, today).isEmpty)
        assertFalse(
            RequestInbox.build(
                scope,
                changeRequests = listOf(
                    request("answered", from = "u2", to = "u1", status = ChangeRequestStatus.DECLINED)
                ),
                events = emptyList(),
                overrides = emptyMap(),
                shared = null,
                today = today
            ).isEmpty
        )
    }
}
