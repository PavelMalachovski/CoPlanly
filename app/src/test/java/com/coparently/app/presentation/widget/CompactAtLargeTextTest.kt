package com.coparently.app.presentation.widget

import com.coparently.app.presentation.theme.ParentColorChoice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * At a large font scale the compact widget keeps the date, whose day it is and one more line —
 * the handover first, then a contact window, then the footer — so nothing is clipped.
 */
class CompactAtLargeTextTest {

    private val custody = WidgetLine("Today with Alex", ParentColorChoice.PINK)
    private val window = WidgetLine("15:00–19:00 · contact with Sam", ParentColorChoice.BLUE)
    private val handover = WidgetLine("Handover to Sam in 5 days", ParentColorChoice.BLUE)

    @Test
    fun `the handover is the third line when there is one`() {
        val lines = TodayWidgetLines(
            title = "Today · Wed, Sep 2",
            custody = custody,
            windows = listOf(window),
            handover = handover,
            footer = "2 events today"
        ).compactAtLargeText()

        assertEquals(handover, lines.handover)
        assertEquals(emptyList(), lines.windows)
        assertNull(lines.footer)
    }

    @Test
    fun `without a handover the first contact window takes its place`() {
        val lines = TodayWidgetLines(
            title = "Today · Wed, Sep 2",
            custody = custody,
            windows = listOf(window, window.copy(text = "Later window")),
            footer = "2 events today"
        ).compactAtLargeText()

        assertEquals(listOf(window), lines.windows)
        assertNull(lines.footer)
    }

    @Test
    fun `the footer stays when nothing else would be said`() {
        val lines = TodayWidgetLines(
            title = "Today · Wed, Sep 2",
            footer = "Sign in to CoPlanly to see your day."
        ).compactAtLargeText()

        assertEquals("Sign in to CoPlanly to see your day.", lines.footer)
    }
}
