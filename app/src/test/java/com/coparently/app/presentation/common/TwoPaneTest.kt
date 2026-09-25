package com.coparently.app.presentation.common

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tabs' wide layouts (release audit R-8): when two panes appear, and how wide they are.
 *
 * Measured, as `ReadablePaneTest` is: the panes' widths and where they sit are numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TwoPaneTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun `two equal panes fill a window narrower than the cap`() {
        rule.setContent {
            TwoPanes(
                start = { Box(Modifier.fillMaxSize().testTag(START)) },
                end = { Box(Modifier.fillMaxSize().testTag(END)) }
            )
        }
        val pane = (1000.dp - Spacing.L) / 2

        rule.onNodeWithTag(START).assertWidthIsEqualTo(pane).assertLeftPositionInRootIsEqualTo(0.dp)
        rule.onNodeWithTag(END).assertWidthIsEqualTo(pane).assertLeftPositionInRootIsEqualTo(pane + Spacing.L)
    }

    @Test
    @Config(qualifiers = "w1400dp-h800dp")
    fun `past the cap the pair is centred`() {
        rule.setContent {
            TwoPanes(
                start = { Box(Modifier.fillMaxSize().testTag(START)) },
                end = { Box(Modifier.fillMaxSize().testTag(END)) }
            )
        }

        rule.onNodeWithTag(START)
            .assertWidthIsEqualTo((TWO_PANE_MAX_WIDTH - Spacing.L) / 2)
            .assertLeftPositionInRootIsEqualTo((1400.dp - TWO_PANE_MAX_WIDTH) / 2)
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun `a wide column is capped and centred`() {
        rule.setContent { WideColumn { Box(Modifier.fillMaxSize().testTag(START)) } }

        rule.onNodeWithTag(START)
            .assertWidthIsEqualTo(WIDE_COLUMN_MAX_WIDTH)
            .assertLeftPositionInRootIsEqualTo((1000.dp - WIDE_COLUMN_MAX_WIDTH) / 2)
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp")
    fun `from 840 dp a tab is two panes`() {
        var twoPane = false
        rule.setContent { twoPane = rememberTwoPane() }

        rule.waitForIdle()
        assertEquals(true, twoPane)
    }

    @Test
    @Config(qualifiers = "w700dp-h900dp")
    fun `below 840 dp it stays one column, rail or not`() {
        var twoPane = true
        rule.setContent { twoPane = rememberTwoPane() }

        rule.waitForIdle()
        assertEquals(false, twoPane)
    }

    private companion object {
        const val START = "start"
        const val END = "end"
    }
}
