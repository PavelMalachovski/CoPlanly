package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.presentation.calendar.components.DaySwapSheetContent
import com.coparently.app.presentation.calendar.components.SwapSheetActions
import com.coparently.app.presentation.calendar.components.SwapSheetOffer
import com.coparently.app.presentation.common.PushedBarsHarness
import com.coparently.app.screenshots.ScreenshotFixtures
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Clock

/**
 * The day swap sheet always says when the co-parent should answer and how many reminders they get
 * (meeting of 25 November 2026, item 5): the summary is drawn without tapping anything, and a tap
 * opens the choices under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class DaySwapSheetDeadlineTest : PushedBarsHarness() {

    @Test
    fun `the deadline and the reminders are on the sheet before anything is tapped`() {
        compose(withHost = false) { sheet(daysAhead = 20) }
        assertTrue(texts("Answer by", "up to", "reminder").isNotEmpty())
        rule.onNodeWithText("Bob is reminded automatically until they answer. Tap to change the date.").assertExists()
    }

    @Test
    fun `a tap on the summary opens the choices`() {
        compose(withHost = false) { sheet(daysAhead = 20) }
        assertTrue(rule.onAllNodesWithText("Tomorrow").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Bob is reminded automatically until they answer. Tap to change the date.").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Tomorrow").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a swap too soon for a deadline says so and has no reminders line`() {
        compose(withHost = false) { sheet(daysAhead = 0) }
        val tooSoon = rule.onAllNodesWithText("Too soon for a deadline", substring = true)
        val reminded = rule.onAllNodesWithText("reminded automatically", substring = true)
        assertTrue(tooSoon.fetchSemanticsNodes().isNotEmpty())
        assertTrue(reminded.fetchSemanticsNodes().isEmpty())
    }

    private fun texts(vararg parts: String) = rule.onAllNodesWithText("", substring = true)
        .fetchSemanticsNodes()
        .filter { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text }.orEmpty()
            parts.all { it in text }
        }

    @Composable
    private fun sheet(daysAhead: Int) {
        val day: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(daysAhead, DateTimeUnit.DAY)
        DaySwapSheetContent(
            offer = SwapSheetOffer(
                sides = SwapSides(toOther = listOf(day), toOfferer = emptyList()),
                coParentName = "Bob"
            ),
            parentNames = ScreenshotFixtures.parentNames,
            actions = SwapSheetActions(onOffer = { _, _, _ -> }, onCancel = {}, onChangeDays = null)
        )
    }
}
