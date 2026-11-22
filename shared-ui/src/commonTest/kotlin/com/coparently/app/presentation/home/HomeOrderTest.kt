package com.coparently.app.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's order (November 2026, board HomeG1): one column, or two panes from 840 dp. */
class HomeOrderTest {

    @Test
    fun `one pane draws the heading - requests - money - the week and the handover in order`() {
        val (first, second) = homeSections(twoPane = false)

        assertEquals(
            listOf(
                HomeSection.HEADING,
                HomeSection.REQUESTS,
                HomeSection.MONEY,
                HomeSection.THIS_WEEK,
                HomeSection.HANDOVER
            ),
            first
        )
        assertEquals(emptyList(), second)
    }

    @Test
    fun `two panes put the heading and requests first and money - the week and the handover second`() {
        val (first, second) = homeSections(twoPane = true)

        assertEquals(listOf(HomeSection.HEADING, HomeSection.REQUESTS), first)
        assertEquals(listOf(HomeSection.MONEY, HomeSection.THIS_WEEK, HomeSection.HANDOVER), second)
    }
}
