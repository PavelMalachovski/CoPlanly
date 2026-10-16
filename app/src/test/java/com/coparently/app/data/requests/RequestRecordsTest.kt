package com.coparently.app.data.requests

import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RequestRecords]: what a request's own record says when a step of its clock runs (phase 3a,
 * spec §4.3–§4.4) — still waiting, answered, or gone — and what the request's chat cards name.
 *
 * Every step reads the record again rather than trusting the moment it was scheduled, so a request
 * answered, withdrawn or replaced meanwhile finds nothing to do. A family the parent is no longer in
 * reads as gone (Review Focus 1), and a custody document that cannot be read reads as unknown, so
 * the step tries again rather than closing a request it could not see.
 */
class RequestRecordsTest {

    private val changeRequests = mockk<ChangeRequestRepository>()
    private val events = mockk<EventRepository>()
    private val custodyDataSource = mockk<FirestoreCustodyDataSource>()
    private val custodyRepository = mockk<CustodyModelRepository>()
    private val users = mockk<UserRepository>()

    private val records = RequestRecords(changeRequests, events, custodyDataSource, custodyRepository, users)

    private fun signedInAs(uid: String, partnerIds: List<String>, partnerId: String? = partnerIds.firstOrNull()) {
        coEvery { users.getCurrentUser() } returns User(
            id = uid,
            email = "$uid@example.test",
            name = uid,
            role = "mom",
            colorCode = "",
            partnerId = partnerId,
            partnerIds = partnerIds
        )
    }

    @Test
    fun `each kind reads pending, answered or gone from its own record`() = runTest {
        signedInAs(ALICE, listOf(BOB))

        // A change request, by id.
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns changeRequest(ChangeRequestStatus.PENDING)
        assertEquals(RecordStatus.PENDING, records.look(clock(RequestKind.CHANGE, "cr-1")).status)
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns changeRequest(ChangeRequestStatus.DECLINED)
        assertEquals(RecordStatus.ANSWERED, records.look(clock(RequestKind.CHANGE, "cr-1")).status)
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns changeRequest(ChangeRequestStatus.CANCELLED)
        assertEquals("a withdrawn request is decided", RecordStatus.ANSWERED, statusOf(RequestKind.CHANGE, "cr-1"))
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns null
        assertEquals(RecordStatus.GONE, records.look(clock(RequestKind.CHANGE, "cr-1")).status)

        // An event's acceptance.
        coEvery { events.getEventById("ev-1") } returns event(EventAcceptance.PENDING)
        assertEquals(RecordStatus.PENDING, records.look(clock(RequestKind.EVENT, "ev-1")).status)
        coEvery { events.getEventById("ev-1") } returns event(EventAcceptance.ACCEPTED)
        assertEquals(RecordStatus.ANSWERED, records.look(clock(RequestKind.EVENT, "ev-1")).status)
        coEvery { events.getEventById("ev-1") } returns null
        assertEquals(RecordStatus.GONE, records.look(clock(RequestKind.EVENT, "ev-1")).status)

        // A swap group, by its group id, in the family's custody document.
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(
            swaps = mapOf(
                "2026-05-08" to swap("g-1", DayOverrideStatus.PENDING),
                "2026-05-09" to swap("g-1", DayOverrideStatus.PENDING)
            )
        )
        assertEquals(RecordStatus.PENDING, records.look(clock(RequestKind.SWAP, "g-1")).status)
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(
            swaps = mapOf("2026-05-08" to swap("g-1", DayOverrideStatus.ACCEPTED))
        )
        assertEquals(RecordStatus.ANSWERED, records.look(clock(RequestKind.SWAP, "g-1")).status)
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(
            swaps = mapOf("2026-05-08" to swap("g-2", DayOverrideStatus.PENDING))
        )
        assertEquals("offered again under another group", RecordStatus.GONE, statusOf(RequestKind.SWAP, "g-1"))

        // A proposal, by its proposedAt.
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(proposal = proposal(PROPOSED_AT))
        assertEquals(RecordStatus.PENDING, records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).status)
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(decision = decision(PROPOSED_AT))
        assertEquals(RecordStatus.ANSWERED, records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).status)
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(proposal = proposal("2026-05-06T09:00:00"))
        assertEquals("replaced by a newer proposal", RecordStatus.GONE, statusOf(RequestKind.PROPOSAL, PROPOSED_AT))
        coEvery { custodyDataSource.getCustody(FAMILY) } returns null
        assertEquals(RecordStatus.GONE, records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).status)
    }

    @Test
    fun `a family the parent has left reads as gone`() = runTest {
        // Alice now co-parents with Carol only: Bob's family has ended, so nothing of it is acted on,
        // whatever its records still say.
        signedInAs(ALICE, listOf(CAROL))
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns changeRequest(ChangeRequestStatus.PENDING)
        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(proposal = proposal(PROPOSED_AT))

        assertEquals(RecordStatus.GONE, records.look(clock(RequestKind.CHANGE, "cr-1")).status)
        assertEquals(RecordStatus.GONE, records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).status)
        assertFalse(records.withdrawUnansweredProposal(clock(RequestKind.PROPOSAL, PROPOSED_AT)))
        coVerify(exactly = 0) { custodyDataSource.getCustody(any()) }
        coVerify(exactly = 0) { custodyRepository.withdrawUnanswered(any(), any()) }
    }

    @Test
    fun `an unreadable custody document reads as unknown`() = runTest {
        signedInAs(ALICE, listOf(BOB))
        coEvery { custodyDataSource.getCustody(FAMILY) } throws IOException("offline, nothing cached")

        assertEquals(RecordStatus.UNKNOWN, records.look(clock(RequestKind.SWAP, "g-1")).status)
        assertEquals(RecordStatus.UNKNOWN, records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).status)
    }

    @Test
    fun `the card names what the request's own cards name`() = runTest {
        signedInAs(ALICE, listOf(BOB))
        coEvery { changeRequests.getChangeRequestById("cr-1") } returns changeRequest(ChangeRequestStatus.PENDING)
        coEvery { events.getEventById("ev-1") } returns event(EventAcceptance.PENDING)

        assertEquals(
            "a change request: the event's id and title",
            CardSubject(ActivityEntityType.CHANGE_REQUEST, "ev-9", "Dentist"),
            records.look(clock(RequestKind.CHANGE, "cr-1")).card
        )
        assertEquals(
            CardSubject(ActivityEntityType.EVENT, "ev-1", "Swimming"),
            records.look(clock(RequestKind.EVENT, "ev-1")).card
        )

        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(
            swaps = mapOf(
                "2026-05-09" to swap("g-1", DayOverrideStatus.PENDING),
                "2026-05-08" to swap("g-1", DayOverrideStatus.PENDING)
            )
        )
        assertEquals(
            "a swap: the first ISO date as both id and title",
            CardSubject(ActivityEntityType.DAY_SWAP, "2026-05-08", "2026-05-08"),
            records.look(clock(RequestKind.SWAP, "g-1")).card
        )

        coEvery { custodyDataSource.getCustody(FAMILY) } returns custody(proposal = proposal(PROPOSED_AT))
        assertEquals(
            "a proposal: the family's id and no title of its own",
            CardSubject(ActivityEntityType.CUSTODY_PROPOSAL, FAMILY, ""),
            records.look(clock(RequestKind.PROPOSAL, PROPOSED_AT)).card
        )
    }

    @Test
    fun `withdrawing a proposal acts in the clock's family`() = runTest {
        signedInAs(ALICE, listOf(BOB, CAROL), partnerId = CAROL)
        coEvery { custodyRepository.withdrawUnanswered(FAMILY, PROPOSED_AT) } returns true

        assertTrue(records.withdrawUnansweredProposal(clock(RequestKind.PROPOSAL, PROPOSED_AT)))
        assertFalse(
            "only a proposal is withdrawn",
            records.withdrawUnansweredProposal(clock(RequestKind.CHANGE, "cr-1"))
        )
        coVerify(exactly = 1) { custodyRepository.withdrawUnanswered(FAMILY, PROPOSED_AT) }
    }

    private suspend fun statusOf(kind: RequestKind, ref: String): RecordStatus = records.look(clock(kind, ref)).status

    private fun clock(kind: RequestKind, ref: String) = RequestDeadline(
        id = RequestDeadlineId.of(kind, ALICE, ref, FAMILY),
        kind = kind,
        ref = ref,
        familyId = FAMILY,
        proposerUid = ALICE,
        responderUid = BOB,
        answerByMillis = 1_778_000_000_000L
    )

    private fun changeRequest(status: ChangeRequestStatus) = ChangeRequest(
        id = "cr-1",
        eventId = "ev-9",
        eventTitle = "Dentist",
        requestedBy = ALICE,
        requestedTo = BOB,
        currentStartDateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        proposedStartDateTime = LocalDateTime.parse("2026-05-11T10:00:00"),
        status = status,
        createdAt = LocalDateTime.parse("2026-05-05T09:00:00")
    )

    private fun event(acceptance: EventAcceptance) = Event(
        id = "ev-1",
        title = "Swimming",
        startDateTime = LocalDateTime.parse("2026-05-10T10:00:00"),
        eventType = "activity",
        parentOwner = "dad",
        createdAt = LocalDateTime.parse("2026-05-05T09:00:00"),
        updatedAt = LocalDateTime.parse("2026-05-05T09:00:00"),
        createdByFirebaseUid = ALICE,
        acceptance = acceptance
    )

    private fun swap(groupId: String, status: DayOverrideStatus) = DayOverride(
        toParent = "mom",
        requestedBy = ALICE,
        requestedAt = "2026-05-05T09:00:00",
        status = status,
        groupId = groupId
    )

    private fun model() = CustodyModel(
        id = "model-1",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.parse("2026-01-05")
    )

    private fun proposal(proposedAt: String) = CustodyProposal(
        model = model(),
        repeatYearly = true,
        proposedBy = ALICE,
        proposedAt = proposedAt
    )

    private fun decision(proposalAt: String) = CustodyDecision(
        outcome = CustodyDecisionOutcome.DECLINED,
        by = BOB,
        at = "2026-05-06T10:00:00",
        proposalAt = proposalAt,
        note = null
    )

    private fun custody(
        swaps: Map<String, DayOverride> = emptyMap(),
        proposal: CustodyProposal? = null,
        decision: CustodyDecision? = null
    ) = SharedCustody(
        model = model(),
        lastModifiedBy = ALICE,
        lastModifiedAtMillis = 1_777_000_000_000L,
        createdAt = "2026-01-01T00:00:00Z",
        proposal = proposal,
        lastDecision = decision,
        dayOverrides = swaps
    )

    private companion object {
        const val ALICE = "uidA"
        const val BOB = "uidB"
        const val CAROL = "uidC"
        const val FAMILY = "uidA__uidB"
        const val PROPOSED_AT = "2026-05-05T09:00:00"
    }
}
