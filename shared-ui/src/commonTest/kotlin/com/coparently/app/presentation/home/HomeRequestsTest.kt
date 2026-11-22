package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.changerequests.InboxSection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home's Requests block (November 2026, board HomeG1): what it draws, and how it counts. */
class HomeRequestsTest {

    private val me = "u1"
    private val them = "u2"
    private val at = LocalDateTime(2026, 11, 12, 9, 0)

    private fun swap(by: String, status: DayOverrideStatus = DayOverrideStatus.PENDING) = InboxItem.Swap(
        DaySwapGroup(
            listOf(
                DaySwap(
                    LocalDate(2026, 11, 21),
                    DayOverride(toParent = "mom", requestedBy = by, requestedAt = "2026-11-12T09:00", status = status)
                )
            )
        )
    )

    private fun request(by: String, status: ChangeRequestStatus = ChangeRequestStatus.PENDING) = InboxItem.Request(
        ChangeRequest(
            id = "cr",
            eventId = "e1",
            eventTitle = "Dentist",
            requestedBy = by,
            requestedTo = if (by == me) them else me,
            currentStartDateTime = LocalDateTime(2026, 11, 13, 10, 0),
            proposedStartDateTime = LocalDateTime(2026, 11, 14, 14, 30),
            status = status,
            createdAt = at
        )
    )

    private val proposal = CustodyProposal(
        model = CustodyModel(
            id = "p",
            modelType = CustodyModelType.CUSTOM,
            patternDays = 7,
            momDayIndices = setOf(0, 1),
            startDate = LocalDate(2026, 11, 16)
        ),
        repeatYearly = true,
        proposedBy = them,
        proposedAt = "2026-11-12T09:00"
    )

    private fun split(mine: Boolean) =
        InboxItem.Split(SplitRatioProposal(SplitRatio(6_000), if (mine) me else them, 1L), mine = mine)

    private fun update(at: LocalDateTime) =
        ActivityItem("i$at", ActivityKind.EVENT_CREATED, "Dentist", at, "e1", isChangeRequest = false)

    @Test
    fun `the block takes the two waiting sections and leaves headers and history out`() {
        val forMe = swap(by = them)
        val forThem = split(mine = true)
        val items = listOf(
            InboxItem.Header(InboxSection.WAITING_ON_ME),
            forMe,
            InboxItem.Plan(2),
            InboxItem.Header(InboxSection.WAITING_ON_THEM),
            forThem,
            InboxItem.Header(InboxSection.HISTORY),
            InboxItem.History(emptyList())
        )

        val block = HomeRequestItems.of(items)

        assertEquals(listOf(forMe, InboxItem.Plan(2)), block.forMe)
        assertEquals(listOf<InboxItem>(forThem), block.forThem)
        assertFalse(block.isEmpty)
    }

    @Test
    fun `an inbox with only history leaves the block empty`() {
        val block = HomeRequestItems.of(
            listOf(InboxItem.Header(InboxSection.HISTORY), InboxItem.History(emptyList()))
        )

        assertTrue(block.isEmpty)
    }

    @Test
    fun `a parent's own requests are withdrawn from their row`() {
        assertEquals(WaitingRowAction.WITHDRAW, waitingRowAction(swap(by = me), me))
        assertEquals(WaitingRowAction.WITHDRAW, waitingRowAction(request(by = me), me))
        assertEquals(WaitingRowAction.WITHDRAW, waitingRowAction(split(mine = true), me))
        assertEquals(WaitingRowAction.WITHDRAW, waitingRowAction(InboxItem.Proposal(proposal, mine = true), me))
    }

    @Test
    fun `the co-parent's proposal past its deadline keeps Decline and nothing else does`() {
        assertEquals(WaitingRowAction.DECLINE, waitingRowAction(InboxItem.Proposal(proposal, mine = false), me))
        assertEquals(WaitingRowAction.DECLINE, waitingRowAction(split(mine = false), me))
        assertEquals(WaitingRowAction.NONE, waitingRowAction(swap(by = them), me))
        assertEquals(WaitingRowAction.NONE, waitingRowAction(request(by = them), me))
    }

    @Test
    fun `an answered request is no longer withdrawn`() {
        val answered = request(by = me, status = ChangeRequestStatus.ACCEPTED)
        assertEquals(WaitingRowAction.NONE, waitingRowAction(answered, me))
        assertEquals(WaitingRowAction.NONE, waitingRowAction(swap(by = me, status = DayOverrideStatus.ACCEPTED), me))
    }

    @Test
    fun `every request answered on Home has Details and the agreement rows do not`() {
        assertTrue(hasDetails(swap(by = them)))
        assertTrue(hasDetails(request(by = them)))
        assertTrue(hasDetails(InboxItem.Proposal(proposal, mine = false)))
        assertTrue(hasDetails(split(mine = false)))
        assertFalse(hasDetails(InboxItem.Plan(1)))
    }

    @Test
    fun `with no mark every update is new`() {
        val items = listOf(update(at), update(LocalDateTime(2026, 11, 11, 9, 0)))

        assertEquals(2, unseenCount(items, seenAtMillis = null, zone = TimeZone.UTC))
    }

    @Test
    fun `only updates after the mark are new`() {
        val seen = LocalDateTime(2026, 11, 12, 8, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        val items = listOf(
            update(at),
            update(LocalDateTime(2026, 11, 11, 9, 0)),
            update(LocalDateTime(2026, 11, 12, 8, 0))
        )

        assertEquals(1, unseenCount(items, seenAtMillis = seen, zone = TimeZone.UTC))
    }

    @Test
    fun `an update is read on the reader's own clock`() {
        // 09:00 in Prague is 08:00 UTC: not after a mark set at 08:30 UTC.
        val seen = LocalDateTime(2026, 11, 12, 8, 30).toInstant(TimeZone.UTC).toEpochMilliseconds()

        assertEquals(0, unseenCount(listOf(update(at)), seen, TimeZone.of("Europe/Prague")))
        assertEquals(1, unseenCount(listOf(update(at)), seen, TimeZone.UTC))
    }

    @Test
    fun `a change request's month marks the day it would move to`() {
        val model = changeRequestMonth(request(by = them).request) { "dad" }

        assertEquals(YearMonth(2026, 11), model.month)
        assertEquals(mapOf(LocalDate(2026, 11, 14) to MiniMonthMark.PROPOSED_DAY), model.marks)
        assertEquals("dad", model.custody[LocalDate(2026, 11, 14)])
    }
}
