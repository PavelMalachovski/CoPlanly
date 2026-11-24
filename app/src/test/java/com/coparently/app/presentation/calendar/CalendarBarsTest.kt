package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.presentation.calendar.components.CalendarHeaderActions
import com.coparently.app.presentation.calendar.components.CalendarHeaderState
import com.coparently.app.presentation.calendar.components.CalendarMonthBar
import com.coparently.app.presentation.calendar.components.CalendarTopBar
import com.coparently.app.presentation.calendar.components.CalendarTopBarState
import com.coparently.app.presentation.calendar.components.SwapSelectionBarActions
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.FamilyMember
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
 * The calendar's bars (MON-24 phase 3, part B, Task 8; part C, Task 8; calendar K1): where a shell
 * draws the bars (the iPhone, a recording host here) the calendar hands it a hidden bar named
 * "Calendar" and leads with its header in content, as Android does: the month with its year, "?",
 * the member avatars and the gear; the grid card's month bar keeps the M | W | D control, Today, "⇄"
 * and "+". While days are chosen for a swap the selection header is drawn in content too, its close
 * button and month arrows Compose's. With no host (Android) every row is drawn as it always was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class CalendarBarsTest : PushedBarsHarness() {

    @Test
    fun `the iPhone hands the shell a hidden bar named Calendar`() {
        compose(withHost = true) { calendarBar() }
        rule.waitForIdle()
        val bar = shown.last()
        assertTrue("The bar is shown", bar.hidden)
        assertEquals("Calendar", bar.title)
        assertEquals(BarLeading.None, bar.leading)
        assertTrue(bar.actions.isEmpty())
    }

    @Test
    fun `while a Day view a month cell opened waits the bar is shown with a back button`() {
        compose(withHost = true) { calendarBar(backStep = { clicks += "back" }) }
        rule.waitForIdle()
        val bar = shown.last()
        assertTrue("The bar is shown while the step waits", !bar.hidden)
        assertEquals("Calendar", bar.title)
        val leading = bar.leading
        assertTrue("A back button leads", leading is BarLeading.Back)
        (leading as BarLeading.Back).onClick()
        rule.waitForIdle()
        assertEquals(listOf("back"), clicks)
    }

    @Test
    fun `with no step waiting the bar stays hidden`() {
        compose(withHost = true) { calendarBar(backStep = null) }
        rule.waitForIdle()
        assertTrue(shown.last().hidden)
        assertEquals(BarLeading.None, shown.last().leading)
    }

    @Test
    fun `the iPhone draws the month title, the legend, the avatars and the gear in content`() {
        compose(withHost = true) { calendarBar() }
        assertTrue(rule.onAllNodesWithText("May 2026").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("CoPlanly", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Your family calendar").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("May 2026").performClick()
        rule.onNodeWithContentDescription("What the colours mean").performClick()
        rule.onNodeWithContentDescription("Show for Emma").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        assertEquals(listOf("jump", "legend", "member Emma", "settings"), clicks)
    }

    @Test
    fun `the month title carries this year's year too`() {
        compose(withHost = false) { calendarBar() }
        assertTrue(rule.onAllNodesWithText("May 2026").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("May").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a chosen member's avatar reads as checked`() {
        val chosen = HEADER.copy(selectedMembers = listOf(FamilyMemberRef.Child("emma")))
        compose(withHost = false) { calendarBar(header = chosen) }
        rule.onNodeWithContentDescription("Show for Emma").assertIsOn()
        rule.onNodeWithContentDescription("Show for Leo").assertIsOff()
    }

    @Test
    fun `one member draws no avatar to filter by`() {
        compose(withHost = false) { calendarBar(header = HEADER.copy(members = MEMBERS.take(1))) }
        assertTrue(rule.onAllNodesWithContentDescription("Show for Emma").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `with nowhere to open Settings the header offers no gear`() {
        compose(withHost = true) { calendarBar(onSettings = null) }
        assertTrue(rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("What the colours mean").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `while days are chosen the iPhone draws the selection header in content`() {
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
        assertTrue(rule.onAllNodesWithText("Choose days to swap").fetchSemanticsNodes().isNotEmpty())
        assertTrue("The bar is shown", shown.isNotEmpty() && shown.all { it.hidden })
        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.onNodeWithContentDescription("Stop choosing days").performClick()
        rule.waitForIdle()
        assertEquals(listOf("previous", "next", "close"), clicks)
        assertTrue(rule.onAllNodesWithText("Choose days to swap").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("May 2026").fetchSemanticsNodes().isNotEmpty())
        assertTrue(shown.last().hidden)
    }

    @Test
    fun `under a host the grid card's month bar keeps Today, swap and plus, with no arrows`() {
        compose(withHost = true) { monthBar() }
        assertTrue(rule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Add event").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Swap days").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Previous period").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Next period").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("May", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `on Android the header draws its row and nothing reaches a host`() {
        compose(withHost = false) { calendarBar() }
        assertTrue(shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("May 2026").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("What the colours mean").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Show for Emma").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `on Android the month bar names the view in full and opens a menu of the three`() {
        compose(withHost = false) { monthBar() }
        assertTrue(shown.isEmpty())
        assertTrue(rule.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Add event").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Month").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("Week").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("View: Month").performClick()
        rule.onNodeWithContentDescription("Month").assertIsSelected()
        rule.onNodeWithContentDescription("Week").performClick()
        rule.waitForIdle()
        assertTrue("the menu closes", rule.onAllNodesWithContentDescription("Day").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("View: Month").performClick()
        rule.onNodeWithContentDescription("Day").performClick()
        rule.waitForIdle()
        assertEquals(listOf("view WEEK", "view DAY"), clicks)
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
        header: CalendarHeaderState = HEADER,
        onSettings: (() -> Unit)? = { clicks += "settings" },
        swapMonth: YearMonth? = null,
        swapActions: SwapSelectionBarActions = SwapSelectionBarActions({}, {}, {}),
        backStep: (() -> Unit)? = null
    ) {
        CompositionLocalProvider(LocalFixedToday provides DAY) {
            CalendarTopBar(
                state = CalendarTopBarState(
                    header = header,
                    swapMonth = swapMonth,
                    backStep = backStep
                ),
                actions = CalendarHeaderActions(
                    onJumpToDate = { clicks += "jump" },
                    onLegend = { clicks += "legend" },
                    onToggleMember = { ref -> clicks += "member " + header.members.first { it.ref == ref }.name },
                    onSettings = onSettings
                ),
                swapActions = swapActions
            )
        }
    }

    @Composable
    private fun monthBar() {
        CompositionLocalProvider(LocalFixedToday provides DAY) {
            CalendarMonthBar(
                viewMode = CalendarViewMode.MONTH,
                onViewModeChange = { clicks += "view $it" },
                onToday = {},
                onSwapDays = {},
                onAddEvent = {}
            )
        }
    }

    private companion object {
        val MONTH = YearMonth(2026, 5)
        val MONTH_START = LocalDate(2026, 5, 1)
        val MEMBERS = listOf(
            FamilyMember(FamilyMemberRef.Child("emma"), "Emma"),
            FamilyMember(FamilyMemberRef.Child("leo"), "Leo")
        )
        val HEADER = CalendarHeaderState(
            month = MONTH_START,
            members = MEMBERS,
            selectedMembers = emptyList(),
            filtersActive = false
        )
    }
}
