package com.coparently.app.ios.controls

import androidx.compose.ui.graphics.Color
import com.coparently.app.presentation.common.BarMenuItem
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIMenuElementState
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISwitch
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

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

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    @Test
    fun `a menu button keeps its menu while the items look the same`() {
        val button = MenuButtonView {}
        button.show(MENU, OPTION_LOOK, "Repeat, Weekly", UIColor.whiteColor)
        // Compared by the Objective-C object's address: Kotlin/Native hands out a new wrapper for
        // a pure Objective-C object (a `UIMenu`) on each read, so `===` and `assertSame` said "not
        // the same" for one menu at one address (CI run 12), and `assertNotSame` passed whatever.
        val first = checkNotNull(button.menu).objcPtr()
        button.show(MENU.map { it.copy(onClick = {}) }, OPTION_LOOK, "Repeat, Weekly", UIColor.whiteColor)
        assertEquals(first, checkNotNull(button.menu).objcPtr(), "the menu was rebuilt for the same items")

        button.show(MENU.map { it.copy(checked = false) }, OPTION_LOOK, "Repeat", UIColor.whiteColor)
        assertNotEquals(first, checkNotNull(button.menu).objcPtr(), "the menu was kept for changed items")
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
     *
     * The registration is read from UIKit (each target must answer its action's selector), but the
     * action is then called in Kotlin, not through `performSelector:withObject:`: the actions return
     * nothing, and `performSelector` reads the return register as an object, which Kotlin/Native then
     * retains. Whatever that register held decided whether the test ran or the process ended with
     * signal 11 (CI run 11).
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun sendValueChanged(control: UIControl) {
        var sent = 0
        control.allTargets.forEach { target ->
            control.actionsForTarget(target, forControlEvent = UIControlEventValueChanged)?.forEach { action ->
                assertTrue((target as NSObject).respondsToSelector(NSSelectorFromString(action as String)), "$action")
                assertEquals(VALUE_CHANGED, action, "the action registered")
                when (target) {
                    is ValueChangedSwitch -> target.valueChanged(control as UISwitch)
                    is ValueChangedSegmented -> target.valueChanged(control as UISegmentedControl)
                    else -> fail("an unexpected target $target")
                }
                sent++
            }
        }
        assertTrue(sent > 0, "no action is registered for UIControlEventValueChanged")
    }

    private companion object {
        /** The selector both controls register for `UIControlEventValueChanged`. */
        const val VALUE_CHANGED = "valueChanged:"
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
