package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.coparently.app.presentation.calendar.components.CalendarMonthBar
import com.coparently.app.presentation.calendar.components.CalendarTopBar
import com.coparently.app.presentation.calendar.components.CalendarTopBarActions
import com.coparently.app.presentation.calendar.components.CalendarTopBarState
import com.coparently.app.presentation.calendar.components.SwapSelectionBarActions
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.LocalFixedToday
import com.coparently.app.presentation.common.PushedBarsHarness
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The calendar's bars (MON-24 phase 3, part B, Task 8): where a shell draws the bars (the iPhone, a
 * recording host here) the calendar hands it the month the grid card shows as its title, then
 * Today, Filters, the gear and "+", and draws neither the brand row nor the heading lines; the grid
 * card's month bar keeps its arrows and drops Today and "+". While days are chosen for a swap the
 * bar is the selection header's content: a close button and the month arrows. With no host
 * (Android) every row is drawn as it always was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class CalendarBarsTest : PushedBarsHarness() {

    @Test
    fun `the bar is titled with the month and carries Today, Filters, the gear and plus`() {
        compose(withHost = true) { calendarBar() }
        assertBar(
            title = "May",
            back = null,
            actions = listOf(
                "calendar" to "Today",
                "line.3.horizontal.decrease.circle" to "Filters",
                "gearshape" to "Settings",
                "plus" to "Add event"
            )
        )
        assertTrue(shown.last().actions.none { it.primary })
        clickAction("Today")
        clickAction("Filters")
        clickAction("Settings")
        clickAction("Add event")
        assertEquals(listOf("today", "filters", "settings", "add"), clicks)
    }

    @Test
    fun `the iPhone draws no brand row, heading or Filters button, and keeps the view control`() {
        compose(withHost = true) { calendarBar() }
        assertTrue(rule.onAllNodesWithText("coplanly", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Your family calendar").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Filters").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Month").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Week").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `another year's month carries its year, and an active filter fills its symbol`() {
        compose(withHost = true) {
            calendarBar(titleDate = LocalDate(2027, 1, 1), filtersActive = true)
        }
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals("January 2027", bar.title)
        assertEquals("line.3.horizontal.decrease.circle.fill", bar.actions[1].sfSymbol)
    }

    @Test
    fun `with nowhere to open Settings the bar offers no gear`() {
        compose(withHost = true) { calendarBar(onSettings = null) }
        assertBar(
            title = "May",
            back = null,
            actions = listOf(
                "calendar" to "Today",
                "line.3.horizontal.decrease.circle" to "Filters",
                "plus" to "Add event"
            )
        )
    }

    @Test
    fun `while days are chosen the bar closes the selection and pages the month`() {
        compose(withHost = true) {
            var month by remember { mutableStateOf<YearMonth?>(MONTH) }
            calendarBar(
                swapMonth = month,
                swapActions = SwapSelectionBarActions(
                    onPreviousMonth = {
                        month = month?.minusMonth()
                        clicks += "previous"
                    },
                    onNextMonth = {
                        month = month?.plusMonth()
                        clicks += "next"
                    },
                    onClose = {
                        month = null
                        clicks += "close"
                    }
                )
            )
        }
        rule.waitForIdle()
        val choosing = shown.last()
        assertEquals("Choose days to swap", choosing.title)
        val leading = choosing.leading
        assertTrue("The leading is $leading", leading is BarLeading.Close)
        assertEquals("Stop choosing days", (leading as BarLeading.Close).label)
        assertEquals(
            listOf("chevron.left" to "Previous month", "chevron.right" to "Next month"),
            choosing.actions.map { it.sfSymbol to it.label }
        )
        clickAction("Previous month")
        clickAction("Next month")
        rule.runOnIdle { (shown.last().leading as BarLeading.Close).onClick() }
        rule.waitForIdle()
        assertEquals(listOf("previous", "next", "close"), clicks)
        assertEquals("May", shown.last().title)
        assertEquals(BarLeading.None, shown.last().leading)
    }

    @Test
    fun `under a host the grid card's month bar keeps its arrows and drops Today and plus`() {
        compose(withHost = true) { monthBar() }
        assertTrue(rule.onAllNodesWithText("Today").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Add event").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Previous period").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Next period").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Swap days").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("May").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `on Android the header draws every row and nothing reaches a host`() {
        compose(withHost = false) { calendarBar() }
        assertTrue(shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("coplanly", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Your family calendar").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Filters").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Month").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `on Android the month bar keeps Today and plus`() {
        compose(withHost = false) { monthBar() }
        assertTrue(shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Add event").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `on Android the swap selection draws its own bar`() {
        compose(withHost = false) { calendarBar(swapMonth = MONTH) }
        assertTrue(shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("Choose days to swap").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Stop choosing days").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Next month").fetchSemanticsNodes().isNotEmpty())
    }

    @Composable
    private fun calendarBar(
        titleDate: LocalDate = MONTH_START,
        filtersActive: Boolean = false,
        onSettings: (() -> Unit)? = { clicks += "settings" },
        swapMonth: YearMonth? = null,
        swapActions: SwapSelectionBarActions = SwapSelectionBarActions({}, {}, {})
    ) {
        CompositionLocalProvider(LocalFixedToday provides DAY) {
            CalendarTopBar(
                state = CalendarTopBarState(
                    children = emptyList(),
                    viewMode = CalendarViewMode.MONTH,
                    titleDate = titleDate,
                    filtersActive = filtersActive,
                    swapMonth = swapMonth
                ),
                actions = CalendarTopBarActions(
                    onViewModeChange = {},
                    onFilters = { clicks += "filters" },
                    onSettings = onSettings,
                    onToday = { clicks += "today" },
                    onAddEvent = { clicks += "add" }
                ),
                swapActions = swapActions
            )
        }
    }

    @Composable
    private fun monthBar() {
        CompositionLocalProvider(LocalFixedToday provides DAY) {
            CalendarMonthBar(
                selectedDate = MONTH_START,
                onToday = {},
                onPrevious = {},
                onNext = {},
                onJumpToDate = {},
                onSwapDays = {},
                onAddEvent = {}
            )
        }
    }

    private companion object {
        val MONTH = YearMonth(2026, 5)
        val MONTH_START = LocalDate(2026, 5, 1)
    }
}
