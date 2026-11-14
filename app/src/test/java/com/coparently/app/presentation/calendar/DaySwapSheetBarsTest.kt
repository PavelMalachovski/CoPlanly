package com.coparently.app.presentation.calendar

import androidx.compose.runtime.Composable
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
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The day swap's review sheet under a bar host (MON-24 phase 3, part B, final review). The sheet is
 * opened from screens whose bar is the iPhone's navigation bar, and its content inherits their
 * locals; a `StickyActionBar` under a host draws nothing, so the sheet takes the host away and keeps
 * its own "Send to {name}" and "Change days" on both platforms. Nothing it draws reaches the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class DaySwapSheetBarsTest : PushedBarsHarness() {

    @Test
    fun `under a host the sheet still draws Send to and Change days, and sends`() {
        compose(withHost = true) { sheet() }
        assertTrue(rule.onAllNodesWithText("Send to Bob").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Change days").fetchSemanticsNodes().isNotEmpty())
        assertTrue("The sheet handed a bar to the host", shown.isEmpty())
        rule.onNodeWithText("Send to Bob").performClick()
        rule.onNodeWithText("Change days").performClick()
        rule.waitForIdle()
        assertEquals(listOf("offer:$GIVE,$TAKE", "change"), clicks)
    }

    @Test
    fun `on Android the sheet draws the same button`() {
        compose(withHost = false) { sheet() }
        assertTrue(rule.onAllNodesWithText("Send to Bob").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Change days").fetchSemanticsNodes().isNotEmpty())
        assertTrue(shown.isEmpty())
    }

    @Composable
    private fun sheet() {
        DaySwapSheetContent(
            offer = SwapSheetOffer(
                sides = SwapSides(toOther = listOf(GIVE), toOfferer = listOf(TAKE)),
                coParentName = "Bob"
            ),
            parentNames = ScreenshotFixtures.parentNames,
            actions = SwapSheetActions(
                onOffer = { dates, _, _ -> clicks += "offer:" + dates.joinToString(",") },
                onCancel = { clicks += "cancel" },
                onChangeDays = { clicks += "change" }
            )
        )
    }

    private companion object {
        val GIVE = LocalDate(2026, 5, 15)
        val TAKE = LocalDate(2026, 5, 20)
    }
}
