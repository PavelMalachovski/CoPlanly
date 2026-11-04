package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.DaySwapGroup

/**
 * One of Home's pop-up asks, under the keys "Later" puts it off by ([PutOffAsksViewModel]).
 *
 * Each key is a revision, so a new proposal or an amended offer opens again even after the last
 * one was put off (release audit R-2). The requests summary has one key per request it covers:
 * answering one of them leaves the rest put off, and only a request the parent has not dealt with
 * asks again (new-user review, October 2026). It used to be keyed on how many there were, so every
 * answer made a "new" count and the dialog came straight back.
 */
internal sealed interface HomeAsk {

    /** What [PutOffAsksViewModel] stores a put-off under; the ask shows while any is not put off. */
    val keys: Set<String>

    /** The co-parent's custody proposal, keyed on when it was proposed. */
    data class Proposal(val ask: ProposalAsk) : HomeAsk {
        override val keys: Set<String> get() = setOf("proposal_${ask.proposal.proposedAt}")
    }

    /** The co-parent's proposed expense split, keyed on when it was proposed. */
    data class Split(val ask: SplitAsk) : HomeAsk {
        override val keys: Set<String> get() = setOf("split_${ask.proposal.proposedAtMillis}")
    }

    /** One day-swap offer, however many days it covers, keyed on what it says. */
    data class Swap(val group: DaySwapGroup) : HomeAsk {
        override val keys: Set<String> get() = setOf("swap_${group.revision}")
    }

    /**
     * The event change requests and pending events, as one summary keyed on each of them.
     *
     * @property ids `change:<id>` and `event:<id>` of every request waiting on this parent
     */
    data class Requests(val ids: Set<String>) : HomeAsk {
        override val keys: Set<String> get() = ids.mapTo(mutableSetOf()) { "request_$it" }
    }
}

/**
 * The order Home asks in: a custody proposal first, then a proposed expense split, then each day-swap
 * offer, then one summary of the change requests. One dialog per visit to Home ([nextThisVisit]) —
 * the first that was not put off — and the rest on later visits, so opening the app never stacks
 * them one after another.
 */
internal object HomeAskQueue {

    /**
     * Every ask waiting on this parent, in the order Home asks them.
     *
     * @param asks The two proposals, either of which may be absent.
     * @param swaps The day-swap offers waiting on this parent, soonest first.
     * @param requestIds The change requests and pending events waiting on this parent, as
     *   `change:<id>` and `event:<id>`.
     */
    fun of(asks: HomeAsks, swaps: List<DaySwapGroup>, requestIds: Collection<String>): List<HomeAsk> = buildList {
        asks.proposal?.let { add(HomeAsk.Proposal(it)) }
        asks.split?.let { add(HomeAsk.Split(it)) }
        swaps.forEach { add(HomeAsk.Swap(it)) }
        if (requestIds.isNotEmpty()) add(HomeAsk.Requests(requestIds.toSet()))
    }

    /** The ask to show now: the first of [queue] with a key not in [putOff], or null for none. */
    fun next(queue: List<HomeAsk>, putOff: Set<String>): HomeAsk? = queue.firstOrNull { !putOff.containsAll(it.keys) }

    /**
     * The ask to show now, one per visit to Home (second audit: a swap and then the requests
     * summary, one dialog after the other, read as pressure on opening the app).
     *
     * @param held The keys of the asks that were already waiting, beside the first one shown, when
     *   this visit showed it; null until it shows one. Those wait for the next visit, on the
     *   Requests row and the calendar's banner meanwhile. An ask that arrives during the visit is
     *   new, and is asked.
     */
    fun nextThisVisit(queue: List<HomeAsk>, putOff: Set<String>, held: Set<String>?): HomeAsk? =
        next(queue, putOff + held.orEmpty())

    /** The keys [nextThisVisit] holds once [shown] is on screen: every other ask now in [queue]. */
    fun heldBeside(queue: List<HomeAsk>, shown: HomeAsk): Set<String> =
        queue.filter { it.keys != shown.keys }.flatMapTo(mutableSetOf()) { it.keys } - shown.keys
}
