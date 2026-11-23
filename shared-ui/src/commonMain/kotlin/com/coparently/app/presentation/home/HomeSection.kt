package com.coparently.app.presentation.home

/**
 * Home's sections, in the order the November 2026 redesign draws them (board HomeG1).
 */
internal enum class HomeSection {
    /** "Your family calendar" and "Together, for …", under the wordmark. */
    HEADING,

    /** The one Requests block: what waits on this parent and on the co-parent. */
    REQUESTS,

    /** The Money card: this month's spend, who owes whom, and settling up. */
    MONEY,

    /** Seven day tiles in each day's custody wash. */
    THIS_WEEK,

    /** The next handover, one compact card. */
    HANDOVER
}

/**
 * Which sections Home draws, and where.
 *
 * One column on a phone: the heading, Requests, the Money card, this week and the handover. From
 * two panes (design item 19) the first holds the heading and what the parent acts on — Requests —
 * and the second the Money card, this week and the handover.
 *
 * @param twoPane Whether the window is wide enough for two panes
 * @return The first (or only) pane's sections, then the second's; empty for one pane
 */
internal fun homeSections(twoPane: Boolean): Pair<List<HomeSection>, List<HomeSection>> {
    val actOn = listOf(HomeSection.HEADING, HomeSection.REQUESTS)
    val glance = listOf(HomeSection.MONEY, HomeSection.THIS_WEEK, HomeSection.HANDOVER)
    return if (twoPane) actOn to glance else (actOn + glance) to emptyList()
}
