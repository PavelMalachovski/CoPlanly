package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen describes its bar (`ScreenBar`) and `AppTopBar` either draws it — Android, where no
 * `LocalBarHost` is provided — or hands it to the host and draws nothing (the iPhone, whose bar is
 * UIKit's).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class AppTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun bar(
        leading: BarLeading = BarLeading.None,
        subtitle: String? = null,
        trailing: Boolean = false
    ) = ScreenBar(
        title = TITLE,
        subtitle = subtitle,
        leading = leading,
        actions = listOf(
            BarAction(label = ADD, icon = Icons.Default.Add, sfSymbol = "plus", onClick = { clicks += ADD }),
            BarAction(
                label = SETTINGS,
                icon = Icons.Default.Settings,
                sfSymbol = "gearshape",
                onClick = { clicks += SETTINGS }
            ),
            BarAction(
                label = DISABLED,
                icon = Icons.Default.Add,
                sfSymbol = "plus",
                onClick = { clicks += DISABLED },
                enabled = false
            )
        ),
        trailingContent = if (trailing) {
            { Box(Modifier.size(24.dp).testTag(TRAILING)) }
        } else {
            null
        }
    )

    /** A bar like a screen's: built afresh on each composition, every lambda new. */
    private fun liveBar(title: String, enabled: Boolean, now: Int) = ScreenBar(
        title = title,
        leading = BarLeading.Back(BACK, onClick = { clicks += "$BACK $now" }),
        actions = listOf(
            BarAction(
                label = ADD,
                icon = Icons.Default.Add,
                sfSymbol = "plus",
                onClick = { clicks += "$ADD $now" },
                enabled = enabled
            )
        ),
        trailingContent = { Box(Modifier.size(24.dp)) }
    )

    @Test
    fun `with a host the bar goes to the host and nothing is drawn`() {
        val shown = mutableListOf<ScreenBar>()
        val host = BarHost { shown += it }
        val bar = bar(leading = BarLeading.Back(BACK, onClick = {}), subtitle = SUBTITLE, trailing = true)

        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalBarHost provides host) { AppTopBar(bar) }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText(TITLE).assertDoesNotExist()
        rule.onNodeWithText(SUBTITLE).assertDoesNotExist()
        rule.onNodeWithContentDescription(SETTINGS).assertDoesNotExist()
        rule.onNodeWithContentDescription(BACK).assertDoesNotExist()
        rule.onNodeWithTag(TRAILING).assertDoesNotExist()
        assertEquals("one bar, one show", 1, shown.size)
        val held = shown.single()
        assertEquals(TITLE, held.title)
        assertEquals(SUBTITLE, held.subtitle)
        assertEquals(BACK, (held.leading as BarLeading.Back).label)
        assertEquals(listOf(ADD, SETTINGS, DISABLED), held.actions.map { it.label })
        assertEquals(listOf("plus", "gearshape", "plus"), held.actions.map { it.sfSymbol })
        assertEquals(listOf(true, true, false), held.actions.map { it.enabled })
        assertNotNull(held.trailingContent)
        held.actions[1].onClick()
        assertEquals(listOf(SETTINGS), clicks)
    }

    @Test
    fun `a recomposition that changes nothing visible shows nothing new`() {
        val shown = mutableListOf<ScreenBar>()
        val host = BarHost { shown += it }
        val tick = mutableIntStateOf(0)
        rule.setContent {
            val now = tick.intValue
            CompositionLocalProvider(LocalBarHost provides host) {
                // Fresh lambdas on every composition, as a screen's are.
                AppTopBar(liveBar(title = TITLE, enabled = true, now = now))
            }
        }
        rule.waitForIdle()

        tick.intValue = 1
        rule.waitForIdle()
        tick.intValue = 2
        rule.waitForIdle()

        assertEquals("a recomposition with nothing visible changed reached the host", 1, shown.size)
    }

    @Test
    fun `a changed title or enabled shows the bar again`() {
        val shown = mutableListOf<ScreenBar>()
        val host = BarHost { shown += it }
        val title = mutableStateOf(TITLE)
        val enabled = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalBarHost provides host) {
                AppTopBar(liveBar(title = title.value, enabled = enabled.value, now = 0))
            }
        }
        rule.waitForIdle()

        title.value = "Settings"
        rule.waitForIdle()
        assertEquals(2, shown.size)
        assertEquals("Settings", shown.last().title)

        enabled.value = false
        rule.waitForIdle()
        assertEquals(3, shown.size)
        assertFalse(shown.last().actions.single().enabled)
    }

    @Test
    fun `the bar the host holds runs the screen's latest lambda`() {
        val shown = mutableListOf<ScreenBar>()
        val host = BarHost { shown += it }
        val tick = mutableIntStateOf(0)
        rule.setContent {
            val now = tick.intValue
            CompositionLocalProvider(LocalBarHost provides host) {
                AppTopBar(liveBar(title = TITLE, enabled = true, now = now))
            }
        }
        rule.waitForIdle()
        tick.intValue = 1
        rule.waitForIdle()

        shown.first().actions.single().onClick()
        (shown.first().leading as BarLeading.Back).onClick()

        assertEquals(listOf("$ADD 1", "$BACK 1"), clicks)
    }

    @Test
    fun `without a host the title and every action are drawn here`() {
        rule.setContent { MaterialTheme { AppTopBar(bar(subtitle = SUBTITLE)) } }

        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onNodeWithText(SUBTITLE).assertIsDisplayed()
        rule.onNodeWithContentDescription(ADD).assertHasClickAction().assertIsEnabled().performClick()
        rule.onNodeWithContentDescription(SETTINGS).assertHasClickAction().assertIsEnabled().performClick()
        rule.onNodeWithContentDescription(DISABLED).assertIsNotEnabled()
        assertEquals(listOf(ADD, SETTINGS), clicks)
    }

    @Test
    fun `the trailing content comes before the actions`() {
        rule.setContent { MaterialTheme { AppTopBar(bar(trailing = true)) } }

        val trailing = rule.onNodeWithTag(TRAILING).assertIsDisplayed().getBoundsInRoot()
        val add = rule.onNodeWithContentDescription(ADD).getBoundsInRoot()
        val settings = rule.onNodeWithContentDescription(SETTINGS).getBoundsInRoot()
        assertTrue("trailing content after the first action", trailing.right <= add.left)
        assertTrue("actions out of order", add.right <= settings.left)
    }

    @Test
    fun `a back leading draws a navigation icon with its label`() {
        rule.setContent {
            MaterialTheme { AppTopBar(bar(leading = BarLeading.Back(BACK, onClick = { clicks += BACK }))) }
        }

        rule.onNodeWithContentDescription(BACK).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(BACK), clicks)
    }

    @Test
    fun `a close leading draws a navigation icon with its label`() {
        rule.setContent {
            MaterialTheme { AppTopBar(bar(leading = BarLeading.Close(CLOSE, onClick = { clicks += CLOSE }))) }
        }

        rule.onNodeWithContentDescription(CLOSE).assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(listOf(CLOSE), clicks)
    }

    @Test
    fun `a disabled leading is drawn but cannot be tapped`() {
        rule.setContent {
            MaterialTheme {
                AppTopBar(bar(leading = BarLeading.Back(BACK, onClick = { clicks += BACK }, enabled = false)))
            }
        }

        rule.onNodeWithContentDescription(BACK).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun `no leading draws no navigation icon`() {
        rule.setContent { MaterialTheme { AppTopBar(bar()) } }

        rule.onNodeWithContentDescription(BACK).assertDoesNotExist()
        rule.onNodeWithContentDescription(CLOSE).assertDoesNotExist()
    }

    private companion object {
        const val TITLE = "Expenses"
        const val SUBTITLE = "Tuesday, 10 November"
        const val ADD = "Add expense"
        const val SETTINGS = "Settings"
        const val DISABLED = "Unavailable"
        const val BACK = "Back"
        const val CLOSE = "Close"
        const val TRAILING = "trailing"
    }
}
