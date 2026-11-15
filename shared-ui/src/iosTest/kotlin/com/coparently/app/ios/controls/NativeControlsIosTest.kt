package com.coparently.app.ios.controls

import androidx.compose.ui.graphics.Color
import com.coparently.app.presentation.common.BarMenuItem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The iPhone's switch (MON-24 phase 3, part C, Task 1): a value set from outside while it is shown
 * (a sync, a form's reset) is shown without calling back, and only the switch's own
 * `UIControlEventValueChanged` reaches the screen.
 */
class NativeControlsIosTest {

    private val received = mutableListOf<Boolean>()
    private val view = makeSwitch { received += it }

    @Test
    fun `a value set from outside does not call back`() {
        updateSwitch(view, checked = true, enabled = true, accessibilityLabel = LABEL)
        assertTrue(view.on)
        updateSwitch(view, checked = false, enabled = true, accessibilityLabel = LABEL)
        assertFalse(view.on)
        assertEquals(emptyList(), received)

        // The parent's flip: UIKit sets the value, then sends the event.
        view.setOn(true, animated = false)
        sendValueChanged(view)

        assertEquals(listOf(true), received)
    }

    @Test
    fun `the label and the enabled state are set on the switch`() {
        updateSwitch(view, checked = false, enabled = false, accessibilityLabel = LABEL)

        assertFalse(view.enabled)
        assertEquals(LABEL, view.accessibilityLabel)
        assertEquals(emptyList(), received)
    }

    @Test
    fun `a segment set from outside does not call back`() {
        val chosen = mutableListOf<Int>()
        val control = makeSegmented(SEGMENTS) { chosen += it }
        assertEquals(3uL, control.numberOfSegments)

        updateSegmented(control, SEGMENTS, selectedIndex = 1, enabled = true)
        assertEquals(1L, control.selectedSegmentIndex)
        updateSegmented(control, SEGMENTS, selectedIndex = 2, enabled = true)
        assertEquals(2L, control.selectedSegmentIndex)
        assertEquals(emptyList(), chosen)

        // The parent's tap: UIKit sets the index, then sends the event.
        control.selectedSegmentIndex = 0
        sendValueChanged(control)

        assertEquals(listOf(0), chosen)
    }

    @Test
    fun `segments are rebuilt only when the labels change`() {
        val control = makeSegmented(SEGMENTS) {}
        updateSegmented(control, SEGMENTS, selectedIndex = 0, enabled = true)
        updateSegmented(control, listOf("A", "B"), selectedIndex = 1, enabled = false)

        assertEquals(2uL, control.numberOfSegments)
        assertEquals(1L, control.selectedSegmentIndex)
        assertFalse(control.enabled)
    }

    @Test
    fun `a menu button shows one entry per item and ticks the chosen one`() {
        val button = MenuButtonView {}
        button.show(MENU, OPTION_LOOK, "Repeat, Weekly", UIColor.whiteColor)

        val entries = button.menu?.children.orEmpty().map { it as UIAction }
        assertEquals(listOf("None", "Weekly", "Monthly"), entries.map { it.title })
        assertEquals(
            listOf(false, true, false),
            entries.map { it.state == UIMenuElementState.UIMenuElementStateOn }
        )
        assertTrue(button.showsMenuAsPrimaryAction)
        assertTrue(button.isAccessibilityElement)
        assertEquals("Repeat, Weekly", button.accessibilityLabel)
        assertEquals("Weekly", button.configuration?.title)
    }

    @Test
    fun `a segmented control is named for VoiceOver when its site names the choice`() {
        val control = makeSegmented(SEGMENTS) {}
        updateSegmented(control, SEGMENTS, selectedIndex = 0, enabled = true, accessibilityLabel = "Show")

        assertEquals("Show", control.accessibilityLabel)
    }

    @Test
    fun `a menu button that is a symbol alone is at least the touch target square`() {
        val (width, height) = menuButtonSize(MenuButtonLook(title = null, sfSymbol = "plus", tint = Color.Unspecified))

        assertTrue(width >= MIN_TOUCH_TARGET_PT && height >= MIN_TOUCH_TARGET_PT, "$width x $height")
    }

    @Test
    fun `a menu button with nothing checked is still named and opens`() {
        val button = MenuButtonView {}
        val plus = MenuButtonLook(title = null, sfSymbol = "plus", tint = Color.Unspecified)
        button.show(MENU.map { it.copy(checked = false) }, plus, "Add", UIColor.whiteColor)

        assertTrue(button.isAccessibilityElement)
        assertEquals("Add", button.accessibilityLabel)
        assertTrue(button.showsMenuAsPrimaryAction)
        assertEquals(3, button.menu?.children?.size)
    }

    @Test
    fun `a menu button keeps its menu while the items look the same`() {
        val button = MenuButtonView {}
        button.show(MENU, OPTION_LOOK, "Repeat, Weekly", UIColor.whiteColor)
        val first = button.menu
        button.show(MENU.map { it.copy(onClick = {}) }, OPTION_LOOK, "Repeat, Weekly", UIColor.whiteColor)
        assertSame(first, button.menu)

        button.show(MENU.map { it.copy(checked = false) }, OPTION_LOOK, "Repeat", UIColor.whiteColor)
        assertNotSame(first, button.menu)
    }

    @Test
    fun `a segmented host paints behind the control and not its track`() {
        val control = makeSegmented(SEGMENTS) {}
        val host = SegmentedHost(control)
        host.backgroundColor = uiColorOf(Color(red = 1f, green = 0f, blue = 0f))

        assertSame(control, host.control)
        assertEquals(null, control.backgroundColor)
    }

    /**
     * Sends `UIControlEventValueChanged` to each action registered for it, as
     * `sendActionsForControlEvents` would: that call routes through `UIApplication`, which a test
     * process spawned on the simulator does not have, so it would reach no target.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun sendValueChanged(control: UIControl) {
        control.allTargets.forEach { target ->
            control.actionsForTarget(target, forControlEvent = UIControlEventValueChanged)?.forEach { action ->
                (target as NSObject).performSelector(NSSelectorFromString(action as String), withObject = control)
            }
        }
    }

    private companion object {
        const val LABEL = "Pause before sending"
        val SEGMENTS = listOf("Month", "Week", "Day")
        val OPTION_LOOK = MenuButtonLook("Weekly", null, Color.Unspecified)
        val MENU = listOf(
            BarMenuItem(label = "None", sfSymbol = null, onClick = {}),
            BarMenuItem(label = "Weekly", sfSymbol = null, onClick = {}, checked = true),
            BarMenuItem(label = "Monthly", sfSymbol = null, onClick = {})
        )
    }
}
