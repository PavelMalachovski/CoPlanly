package com.coparently.app.ios.controls

import com.coparently.app.presentation.common.BarMenuItem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIAction
import platform.UIKit.UIControl
import platform.UIKit.UIMenuElementState
import platform.UIKit.UISwitch
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
    fun `a menu anchor shows one entry per item and ticks the chosen one`() {
        val button = MenuAnchorButton {}
        button.show(MENU)

        val entries = button.menu?.children.orEmpty().map { it as UIAction }
        assertEquals(listOf("None", "Weekly", "Monthly"), entries.map { it.title })
        assertEquals(
            listOf(false, true, false),
            entries.map { it.state == UIMenuElementState.UIMenuElementStateOn }
        )
        assertTrue(button.showsMenuAsPrimaryAction)
        assertTrue(button.isAccessibilityElement)
        assertEquals("Weekly", button.accessibilityLabel)
    }

    @Test
    fun `a menu anchor keeps its menu while the items look the same`() {
        val button = MenuAnchorButton {}
        button.show(MENU)
        val first = button.menu
        button.show(MENU.map { it.copy(onClick = {}) })
        assertSame(first, button.menu)

        button.show(MENU.map { it.copy(checked = false) })
        assertNotSame(first, button.menu)
        assertFalse(button.isAccessibilityElement)
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
        val MENU = listOf(
            BarMenuItem(label = "None", sfSymbol = null, onClick = {}),
            BarMenuItem(label = "Weekly", sfSymbol = null, onClick = {}, checked = true),
            BarMenuItem(label = "Monthly", sfSymbol = null, onClick = {})
        )
    }
}
