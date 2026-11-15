package com.coparently.app.ios.controls

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISwitch
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * Sends `UIControlEventValueChanged` to each action registered for it, as
     * `sendActionsForControlEvents` would: that call routes through `UIApplication`, which a test
     * process spawned on the simulator does not have, so it would reach no target.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun sendValueChanged(control: UISwitch) {
        control.allTargets.forEach { target ->
            control.actionsForTarget(target, forControlEvent = UIControlEventValueChanged)?.forEach { action ->
                (target as NSObject).performSelector(NSSelectorFromString(action as String), withObject = control)
            }
        }
    }

    private companion object {
        const val LABEL = "Pause before sending"
    }
}
