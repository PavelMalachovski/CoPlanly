package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inbox's history (phase 2, spec §6.2): every request answered or withdrawn, derived from the
 * records both phones already hold — never a stored history list that could disagree with them.
 */
class RequestHistoryTest {

    private val scope = RequestScope(myUid = "u1", coParentUid = "u2", familyId = "u1__u2", soleFamily = false)
    private val created = LocalDateTime.of(2026, 10, 1, 9, 0)

    @Suppress("LongParameterList") // one request's fields, all defaulted
    private fun request(
        id: String,
        from: String = "u1",
        to: String = "u2",
        status: ChangeRequestStatus,
        respondedAt: LocalDateTime? = null,
        synced: Boolean = true
    ) = ChangeRequest(
        id = id,
        eventId = "e-$id",
        eventTitle = "Swimming",
        requestedBy = from,
        requestedTo = to,
        currentStartDateTime = LocalDateTime.of(2026, 10, 15, 17, 0),
        proposedStartDateTime = LocalDateTime.of(2026, 10, 16, 18, 30),
        status = status,
        createdAt = created,
        respondedAt = respondedAt,
        syncedToFirestore = synced
    )

    private fun swap(
        status: DayOverrideStatus,
        requestedBy: String = "u1",
        decidedBy: String? = "u2".takeIf { status != DayOverrideStatus.PENDING },
        decidedAt: String? = "2026-09-02T20:00:00".takeIf { status != DayOverrideStatus.PENDING },
        groupId: String? = "g1"
    ) = DayOverride(
        toParent = "dad",
        requestedBy = requestedBy,
        requestedAt = "2026-08-30T10:00:00",
        status = status,
        decidedBy = decidedBy,
        decidedAt = decidedAt,
        groupId = groupId
    )

    private fun event(
        id: String,
        acceptance: EventAcceptance,
        acceptedAt: LocalDateTime? = null,
        familyId: String = "u1__u2"
    ) = Event(
        id = id,
        title = "Dentist",
        startDateTime = LocalDateTime.of(2026, 10, 21, 16, 0),
        eventType = "medical",
        parentOwner = "dad",
        createdAt = created,
        updatedAt = created,
        createdByFirebaseUid = "u1",
        familyId = familyId,
        acceptance = acceptance,
        acceptedBy = "u2".takeIf { acceptedAt != null },
        acceptedAt = acceptedAt
    )

    private fun history(
        changeRequests: List<ChangeRequest> = emptyList(),
        events: List<Event> = emptyList(),
        overrides: Map<String, DayOverride> = emptyMap(),
        decision: CustodyDecision? = null
    ) = RequestHistory.build(scope, changeRequests, events, overrides, decision)

    @Test
    fun `my request the co-parent accepted is answered by them at respondedAt`() {
        val answered = LocalDateTime.of(2026, 10, 2, 8, 15)

        val entries = history(listOf(request("r1", status = ChangeRequestStatus.ACCEPTED, respondedAt = answered)))

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "cr_r1",
                    subject = RequestSubject.NewTime("e-r1", "Swimming", LocalDateTime.of(2026, 10, 16, 18, 30)),
                    outcome = RequestOutcome.ACCEPTED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = answered
                )
            ),
            entries
        )
    }

    @Test
    fun `a withdrawn request without respondedAt is dated by its creation`() {
        val entry = history(listOf(request("r1", status = ChangeRequestStatus.CANCELLED))).single()

        assertEquals(RequestOutcome.WITHDRAWN, entry.outcome)
        assertEquals("u1", entry.decidedBy)
        assertEquals(created, entry.decidedAt)
    }

    @Test
    fun `a pending request is not history`() {
        assertEquals(emptyList(), history(listOf(request("r1", status = ChangeRequestStatus.PENDING))))
    }

    @Test
    fun `a swap offered as one group is one entry with its first date and day count, past days included`() {
        val decided = swap(DayOverrideStatus.ACCEPTED)

        val entries = history(
            overrides = mapOf("2026-09-01" to decided, "2026-09-02" to decided, "2026-09-03" to decided)
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "swap_g1",
                    subject = RequestSubject.DaySwap(LocalDate.of(2026, 9, 1), 3),
                    outcome = RequestOutcome.ACCEPTED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = LocalDateTime.of(2026, 9, 2, 20, 0)
                )
            ),
            entries
        )
    }

    @Test
    fun `a swap group with a day still pending is not history`() {
        val entries = history(
            overrides = mapOf(
                "2026-09-01" to swap(DayOverrideStatus.ACCEPTED),
                "2026-09-02" to swap(DayOverrideStatus.PENDING)
            )
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `a swap decided by an older build without decidedAt is dated by requestedAt`() {
        val missing = swap(DayOverrideStatus.DECLINED, decidedAt = null, groupId = null)
        val garbage = swap(DayOverrideStatus.DECLINED, decidedAt = "garbage", groupId = null)

        val entries = history(overrides = mapOf("2026-09-05" to missing, "2026-09-06" to garbage))

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.decidedAt == LocalDateTime.of(2026, 8, 30, 10, 0) })
        assertTrue(entries.all { it.outcome == RequestOutcome.DECLINED })
    }

    @Test
    fun `an event the co-parent declined is history and a pending one is not`() {
        val answered = LocalDateTime.of(2026, 10, 3, 19, 0)

        val entries = history(
            events = listOf(
                event("declined", EventAcceptance.DECLINED, acceptedAt = answered),
                event("pending", EventAcceptance.PENDING)
            )
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "ev_declined",
                    subject = RequestSubject.NewEvent("declined", "Dentist", LocalDateTime.of(2026, 10, 21, 16, 0)),
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = answered
                )
            ),
            entries
        )
    }

    @Test
    fun `the custody decision is one entry, asked by the other parent`() {
        val decision = CustodyDecision(
            outcome = CustodyDecisionOutcome.DECLINED,
            by = "u2",
            at = "2026-10-04T12:30:00",
            proposalAt = "2026-10-04T09:00:00",
            note = null
        )

        assertEquals(
            listOf(
                HistoryEntry(
                    key = "custody_2026-10-04T09:00:00",
                    subject = RequestSubject.CustodySchedule,
                    outcome = RequestOutcome.DECLINED,
                    askedBy = "u1",
                    decidedBy = "u2",
                    decidedAt = LocalDateTime.of(2026, 10, 4, 12, 30)
                )
            ),
            history(decision = decision)
        )
    }

    @Test
    fun `another family's request and event are left out`() {
        val entries = history(
            changeRequests = listOf(
                request("r1", to = "u3", status = ChangeRequestStatus.ACCEPTED),
                request("r2", from = "u3", to = "u1", status = ChangeRequestStatus.DECLINED)
            ),
            events = listOf(event("other", EventAcceptance.ACCEPTED, acceptedAt = created, familyId = "u1__u3"))
        )

        assertEquals(emptyList(), entries)
    }

    @Test
    fun `entries are newest first`() {
        val entries = history(
            changeRequests = listOf(
                request(
                    "older",
                    status = ChangeRequestStatus.ACCEPTED,
                    respondedAt = LocalDateTime.of(2026, 10, 2, 8, 0)
                ),
                request(
                    "newer",
                    status = ChangeRequestStatus.DECLINED,
                    respondedAt = LocalDateTime.of(2026, 10, 5, 8, 0)
                )
            ),
            events = listOf(event("middle", EventAcceptance.ACCEPTED, acceptedAt = LocalDateTime.of(2026, 10, 3, 8, 0)))
        )

        assertEquals(listOf("cr_newer", "ev_middle", "cr_older"), entries.map { it.key })
    }

    @Test
    fun `an answer this phone has not uploaded is marked queued`() {
        val entries = history(
            listOf(
                request("mine", from = "u2", to = "u1", status = ChangeRequestStatus.DECLINED, synced = false),
                request("theirs", status = ChangeRequestStatus.ACCEPTED, synced = true)
            )
        )

        assertEquals(mapOf("cr_mine" to true, "cr_theirs" to false), entries.associate { it.key to it.queued })
    }
}
