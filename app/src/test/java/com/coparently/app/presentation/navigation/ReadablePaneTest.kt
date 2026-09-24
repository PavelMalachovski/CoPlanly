package com.coparently.app.presentation.navigation

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cap every detail screen is laid out in (`pane` in the navigation graph).
 *
 * Measured rather than screenshotted: what matters is the column's width and where it sits, at a
 * tablet's width and at a phone's, and both are numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadablePaneTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun `on a wide window the screen is one centred column`() {
        rule.setContent { ReadablePane { Box(Modifier.fillMaxSize().testTag(SCREEN)) } }

        rule.onNodeWithTag(SCREEN)
            .assertWidthIsEqualTo(READABLE_PANE_WIDTH)
            .assertLeftPositionInRootIsEqualTo((1000.dp - READABLE_PANE_WIDTH) / 2)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on a phone the cap changes nothing`() {
        rule.setContent { ReadablePane { Box(Modifier.fillMaxSize().testTag(SCREEN)) } }

        rule.onNodeWithTag(SCREEN)
            .assertWidthIsEqualTo(411.dp)
            .assertLeftPositionInRootIsEqualTo(0.dp)
    }

    private companion object {
        const val SCREEN = "screen"
    }
}
