package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.CustodyModel
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Home asks one thing at a time, in one order — a custody proposal, then a proposed expense split,
 * then each day-swap offer, then the change requests as one summary — and "Later" puts an ask off
 * under keys that change when the ask does (release audit R-2). The requests summary is keyed on
 * each request it covers, so answering one never asks again and a new one does (new-user review,
 * October 2026).
 */
class HomeAskQueueTest {

    private val proposal = ProposalAsk(
        proposal = CustodyProposal(
            model = CustodyModel.weekOnWeekOff(id = "m1", startDate = LocalDate.of(2026, 9, 7)),
            repeatYearly = true,
            proposedBy = "u2",
            proposedAt = "2026-10-12T08:00:00"
        ),
        diff = null
    )

    private fun split(proposedAtMillis: Long = SPLIT_AT) = SplitAsk(
        proposal = SplitRatioProposal(SplitRatio(THIRTY_FIVE), proposedBy = "u2", proposedAtMillis = proposedAtMillis),
        agreed = SplitRatio.EVEN
    )

    private val swap = DaySwapGroup(
        listOf(
            DaySwap(
                LocalDate.of(2026, 10, 20),
                DayOverride(toParent = "dad", requestedBy = "u2", requestedAt = "2026-10-13T10:00:00", groupId = "g1")
            )
        )
    )

    @Test
    fun `a proposal, then a split, then each swap, then the requests`() {
        val queue = HomeAskQueue.of(
            HomeAsks(proposal = proposal, split = split()),
            listOf(swap),
            requestIds = listOf("change:a", "event:b")
        )

        assertEquals(
            listOf(
                setOf("proposal_2026-10-12T08:00:00"),
                setOf("split_$SPLIT_AT"),
                setOf("swap_${swap.revision}"),
                setOf("request_change:a", "request_event:b")
            ),
            queue.map { it.keys }
        )
        assertIs<HomeAsk.Proposal>(HomeAskQueue.next(queue, putOff = emptySet()))
    }

    @Test
    fun `a split put off gives way to the next ask, and nothing is asked once all are put off`() {
        val queue = HomeAskQueue.of(HomeAsks(split = split()), listOf(swap), requestIds = emptyList())

        assertIs<HomeAsk.Split>(HomeAskQueue.next(queue, putOff = emptySet()))
        val afterLater = HomeAskQueue.next(queue, putOff = setOf("split_$SPLIT_AT"))
        assertEquals(swap, assertIs<HomeAsk.Swap>(afterLater).group)
        assertNull(HomeAskQueue.next(queue, putOff = queue.flatMap { it.keys }.toSet()))
    }

    @Test
    fun `a new split proposal asks again after the last one was put off`() {
        val later = SPLIT_AT + 60_000L
        val queue = HomeAskQueue.of(
            HomeAsks(split = split(proposedAtMillis = later)),
            emptyList(),
            requestIds = emptyList()
        )

        val next = assertIs<HomeAsk.Split>(HomeAskQueue.next(queue, putOff = setOf("split_$SPLIT_AT")))
        assertEquals(later, next.ask.proposal.proposedAtMillis)
    }

    @Test
    fun `answering one of the requests put off does not ask again`() {
        val before = HomeAskQueue.of(
            HomeAsks(),
            emptyList(),
            requestIds = listOf("change:a", "change:b", "event:c")
        )
        val putOff = before.flatMap { it.keys }.toSet()

        val after = HomeAskQueue.of(HomeAsks(), emptyList(), requestIds = listOf("change:a", "event:c"))

        assertNull(HomeAskQueue.next(after, putOff))
    }

    @Test
    fun `a new request asks again after the others were put off`() {
        val putOff = HomeAskQueue.of(HomeAsks(), emptyList(), requestIds = listOf("change:a", "change:b"))
            .flatMap { it.keys }.toSet()

        val after = HomeAskQueue.of(
            HomeAsks(),
            emptyList(),
            requestIds = listOf("change:a", "change:b", "change:d")
        )

        val next = assertIs<HomeAsk.Requests>(HomeAskQueue.next(after, putOff))
        assertEquals(setOf("change:a", "change:b", "change:d"), next.ids)
    }

    @Test
    fun `one ask per visit to Home - what already waited beside it waits for the next visit`() {
        // Two dialogs in a row on opening the app read as pressure (second audit).
        val queue = HomeAskQueue.of(HomeAsks(), listOf(swap), requestIds = listOf("change:a"))
        val first = assertIs<HomeAsk.Swap>(HomeAskQueue.nextThisVisit(queue, putOff = emptySet(), held = null))
        val held = HomeAskQueue.heldBeside(queue, first)
        assertEquals(setOf("request_change:a"), held)

        // The swap stays until it is answered or put off ...
        assertIs<HomeAsk.Swap>(HomeAskQueue.nextThisVisit(queue, putOff = emptySet(), held = held))
        // ... and then nothing more is asked this visit.
        assertNull(HomeAskQueue.nextThisVisit(queue, putOff = first.keys, held = held))
        // The next visit asks about the requests.
        assertIs<HomeAsk.Requests>(HomeAskQueue.nextThisVisit(queue, putOff = first.keys, held = null))
    }

    @Test
    fun `an ask that arrives during the visit is asked`() {
        val queue = HomeAskQueue.of(HomeAsks(), listOf(swap), requestIds = emptyList())
        val first = assertIs<HomeAsk.Swap>(HomeAskQueue.nextThisVisit(queue, putOff = emptySet(), held = null))
        val held = HomeAskQueue.heldBeside(queue, first)

        val later = HomeAskQueue.of(HomeAsks(split = split()), emptyList(), requestIds = emptyList())

        assertIs<HomeAsk.Split>(HomeAskQueue.nextThisVisit(later, putOff = first.keys, held = held))
    }

    @Test
    fun `nothing waiting asks nothing`() {
        val queue = HomeAskQueue.of(HomeAsks(), emptyList(), requestIds = emptyList())

        assertEquals(emptyList(), queue)
        assertNull(HomeAskQueue.next(queue, putOff = emptySet()))
    }

    private companion object {
        /** 35 % for slot 1, in basis points. */
        const val THIRTY_FIVE = 3_500

        /** When the split was proposed: 2026-10-13T09:00:00Z. */
        const val SPLIT_AT = 1_791_882_000_000L
    }
}
