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
 * under a key that changes when the ask does (release audit R-2).
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
        val queue = HomeAskQueue.of(HomeAsks(proposal = proposal, split = split()), listOf(swap), requestCount = 2)

        assertEquals(
            listOf("proposal_2026-10-12T08:00:00", "split_$SPLIT_AT", "swap_${swap.revision}", "requests_2"),
            queue.map { it.key }
        )
        assertIs<HomeAsk.Proposal>(HomeAskQueue.next(queue, putOff = emptySet()))
    }

    @Test
    fun `a split put off gives way to the next ask, and nothing is asked once all are put off`() {
        val queue = HomeAskQueue.of(HomeAsks(split = split()), listOf(swap), requestCount = 0)

        assertIs<HomeAsk.Split>(HomeAskQueue.next(queue, putOff = emptySet()))
        val afterLater = HomeAskQueue.next(queue, putOff = setOf("split_$SPLIT_AT"))
        assertEquals(swap, assertIs<HomeAsk.Swap>(afterLater).group)
        assertNull(HomeAskQueue.next(queue, putOff = queue.map { it.key }.toSet()))
    }

    @Test
    fun `a new split proposal asks again after the last one was put off`() {
        val later = SPLIT_AT + 60_000L
        val queue = HomeAskQueue.of(HomeAsks(split = split(proposedAtMillis = later)), emptyList(), requestCount = 0)

        val next = assertIs<HomeAsk.Split>(HomeAskQueue.next(queue, putOff = setOf("split_$SPLIT_AT")))
        assertEquals(later, next.ask.proposal.proposedAtMillis)
    }

    @Test
    fun `nothing waiting asks nothing`() {
        val queue = HomeAskQueue.of(HomeAsks(), emptyList(), requestCount = 0)

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
