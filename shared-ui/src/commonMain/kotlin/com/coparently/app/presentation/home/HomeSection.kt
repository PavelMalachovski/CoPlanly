package com.coparently.app.presentation.home

/**
 * Home's cards, in the order variant A of the November 2026 redesign draws them.
 */
internal enum class HomeSection {
    /** The co-parent's feed: answers, new and changed events, change requests. */
    UPDATES,

    /** What waits on this parent and on the co-parent; left out when nothing waits. */
    OPEN_REQUESTS,

    /** Seven day tiles in each day's custody wash. */
    THIS_WEEK,

    /** The next handover, one compact card. */
    HANDOVER,

    /** The Money card: this month's spend, who owes whom, and settling up. */
    MONEY
}

/**
 * Which sections Home draws, and where.
 *
 * One column on a phone: Updates, open requests, this week, the handover, the Money card. From two
 * panes (design item 19) the left holds what the parent reads and acts on — Updates, open
 * requests, the handover — and the right this week and the Money card.
 *
 * @param hasOpenRequests Whether anything waits on either parent
 * @param twoPane Whether the window is wide enough for two panes
 * @return The first (or only) pane's sections, then the second's; empty for one pane
 */
internal fun homeSections(
    hasOpenRequests: Boolean,
    twoPane: Boolean
): Pair<List<HomeSection>, List<HomeSection>> {
    val readNow = listOfNotNull(
        HomeSection.UPDATES,
        HomeSection.OPEN_REQUESTS.takeIf { hasOpenRequests }
    )
    val week = listOf(HomeSection.THIS_WEEK)
    val handover = listOf(HomeSection.HANDOVER)
    val money = listOf(HomeSection.MONEY)
    return if (twoPane) {
        (readNow + handover) to (week + money)
    } else {
        (readNow + week + handover + money) to emptyList()
    }
}
