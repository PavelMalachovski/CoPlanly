package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.DaySwapGroup

/**
 * One of Home's pop-up asks, under the key "Later" puts it off by ([PutOffAsksViewModel]).
 *
 * Each key is the ask's revision, so a new proposal, an amended offer or a different number of
 * requests opens again even after the last one was put off (release audit R-2).
 */
internal sealed interface HomeAsk {

    /** What [PutOffAsksViewModel] stores a put-off under. */
    val key: String

    /** The co-parent's custody proposal, keyed on when it was proposed. */
    data class Proposal(val ask: ProposalAsk) : HomeAsk {
        override val key: String get() = "proposal_${ask.proposal.proposedAt}"
    }

    /** The co-parent's proposed expense split, keyed on when it was proposed. */
    data class Split(val ask: SplitAsk) : HomeAsk {
        override val key: String get() = "split_${ask.proposal.proposedAtMillis}"
    }

    /** One day-swap offer, however many days it covers, keyed on what it says. */
    data class Swap(val group: DaySwapGroup) : HomeAsk {
        override val key: String get() = "swap_${group.revision}"
    }

    /** The event change requests and pending events, as one summary keyed on how many there are. */
    data class Requests(val count: Int) : HomeAsk {
        override val key: String get() = "requests_$count"
    }
}

/**
 * The order Home asks in: a custody proposal first, then a proposed expense split, then each day-swap
 * offer, then one summary of the change requests. One dialog at a time — the first that was not put
 * off ([next]) — so a parent with several things to answer is asked about them one after another.
 */
internal object HomeAskQueue {

    /**
     * Every ask waiting on this parent, in the order Home asks them.
     *
     * @param asks The two proposals, either of which may be absent.
     * @param swaps The day-swap offers waiting on this parent, soonest first.
     * @param requestCount The change requests and pending events waiting on this parent.
     */
    fun of(asks: HomeAsks, swaps: List<DaySwapGroup>, requestCount: Int): List<HomeAsk> = buildList {
        asks.proposal?.let { add(HomeAsk.Proposal(it)) }
        asks.split?.let { add(HomeAsk.Split(it)) }
        swaps.forEach { add(HomeAsk.Swap(it)) }
        if (requestCount > 0) add(HomeAsk.Requests(requestCount))
    }

    /** The ask to show now: the first of [queue] whose key is not in [putOff], or null for none. */
    fun next(queue: List<HomeAsk>, putOff: Set<String>): HomeAsk? = queue.firstOrNull { it.key !in putOff }
}
