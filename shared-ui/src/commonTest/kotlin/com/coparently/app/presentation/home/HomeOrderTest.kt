package com.coparently.app.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

/** Home's order (redesign, November 2026, variant A): one column, or two panes from 840 dp. */
class HomeOrderTest {

    @Test
    fun `one pane with requests waiting draws every section in order`() {
        val (first, second) = homeSections(hasOpenRequests = true, twoPane = false)

        assertEquals(
            listOf(
                HomeSection.UPDATES,
                HomeSection.OPEN_REQUESTS,
                HomeSection.THIS_WEEK,
                HomeSection.HANDOVER,
                HomeSection.TILES
            ),
            first
        )
        assertEquals(emptyList(), second)
    }

    @Test
    fun `nothing waiting leaves the open requests out`() {
        val (first, _) = homeSections(hasOpenRequests = false, twoPane = false)

        assertEquals(
            listOf(HomeSection.UPDATES, HomeSection.THIS_WEEK, HomeSection.HANDOVER, HomeSection.TILES),
            first
        )
    }

    @Test
    fun `two panes put updates - requests and the handover left and the week and tiles right`() {
        val (first, second) = homeSections(hasOpenRequests = true, twoPane = true)

        assertEquals(listOf(HomeSection.UPDATES, HomeSection.OPEN_REQUESTS, HomeSection.HANDOVER), first)
        assertEquals(listOf(HomeSection.THIS_WEEK, HomeSection.TILES), second)
    }

    @Test
    fun `two panes with nothing waiting keep the handover on the left`() {
        val (first, second) = homeSections(hasOpenRequests = false, twoPane = true)

        assertEquals(listOf(HomeSection.UPDATES, HomeSection.HANDOVER), first)
        assertEquals(listOf(HomeSection.THIS_WEEK, HomeSection.TILES), second)
    }
}
