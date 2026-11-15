package com.coparently.app.ios.controls

import com.coparently.app.presentation.common.BarMenuItem
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone's alerts and action sheets (MON-24 phase 3, part C, Task 5): which UIKit style each
 * question takes, the answers in order and styled, and that it ends once — each answer calling its
 * own lambda, a presentation UIKit dropped ending as a dismissal, and a close calling nothing.
 */
class AlertsIosTest {

    private val calls = mutableListOf<String>()

    private fun confirm(destructive: Boolean, asActionSheet: Boolean) = confirmAlert(
        title = TITLE,
        message = MESSAGE,
        confirmText = CONFIRM,
        dismissText = DISMISS,
        destructive = destructive,
        asActionSheet = asActionSheet,
        onConfirm = { calls += "confirm" },
        onDismiss = { calls += "dismiss" }
    )

    @Test
    fun `a destructive confirmation is an action sheet with a red confirm and a Cancel`() {
        val alert = confirm(destructive = true, asActionSheet = true)

        assertEquals(UIAlertControllerStyleActionSheet, alert.controller.preferredStyle)
        assertEquals(TITLE, alert.controller.title)
        assertEquals(MESSAGE, alert.controller.message)
        val actions = alert.controller.actions.map { it as UIAlertAction }
        assertEquals(listOf(CONFIRM, DISMISS), actions.map { it.title })
        assertEquals(listOf(UIAlertActionStyleDestructive, UIAlertActionStyleCancel), actions.map { it.style })
    }

    @Test
    fun `discard changes is an alert even with a red answer`() {
        val alert = confirm(destructive = true, asActionSheet = false)

        assertEquals(UIAlertControllerStyleAlert, alert.controller.preferredStyle)
        val actions = alert.controller.actions.map { it as UIAlertAction }
        assertEquals(UIAlertActionStyleDestructive, actions.first().style)
    }

    @Test
    fun `a plain question confirms in the default style`() {
        val alert = confirm(destructive = false, asActionSheet = false)

        val actions = alert.controller.actions.map { it as UIAlertAction }
        assertEquals(listOf(UIAlertActionStyleDefault, UIAlertActionStyleCancel), actions.map { it.style })
    }

    @Test
    fun `each answer calls its own lambda once`() {
        val confirmed = confirm(destructive = true, asActionSheet = true)
        confirmed.choose(0)
        confirmed.choose(0)
        confirmed.choose(1)
        confirmed.dropped()
        assertEquals(listOf("confirm"), calls)
        assertTrue(confirmed.finished)

        calls.clear()
        val cancelled = confirm(destructive = true, asActionSheet = true)
        cancelled.choose(1)
        cancelled.close()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `a presentation UIKit dropped ends as a dismissal only once`() {
        val alert = confirm(destructive = false, asActionSheet = false)
        alert.dropped()
        alert.dropped()
        alert.choose(0)
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `closing calls nothing and ends it`() {
        val alert = confirm(destructive = true, asActionSheet = true)
        alert.close()
        alert.choose(0)
        alert.dropped()
        assertEquals(emptyList(), calls)
        assertTrue(alert.finished)
    }

    @Test
    fun `an action sheet of items adds the system's Cancel and runs an item by its place`() {
        val ran = mutableListOf<Int>()
        val items = listOf(
            BarMenuItem(label = "Report message", sfSymbol = "flag", onClick = {}),
            BarMenuItem(label = "Block", sfSymbol = "nosign", onClick = {}, destructive = true, enabled = false)
        )
        val sheet = menuActionSheet(
            title = null,
            items = items,
            cancelText = DISMISS,
            onItem = { ran += it },
            onDismiss = { calls += "dismiss" }
        )

        assertEquals(UIAlertControllerStyleActionSheet, sheet.controller.preferredStyle)
        val actions = sheet.controller.actions.map { it as UIAlertAction }
        assertEquals(listOf("Report message", "Block", DISMISS), actions.map { it.title })
        assertEquals(
            listOf(UIAlertActionStyleDefault, UIAlertActionStyleDestructive, UIAlertActionStyleCancel),
            actions.map { it.style }
        )
        assertTrue(actions[0].enabled)
        assertFalse(actions[1].enabled)

        sheet.choose(0)
        sheet.choose(2)
        assertEquals(listOf(0), ran)
        assertEquals(emptyList(), calls)
    }

    private companion object {
        const val TITLE = "Delete event"
        const val MESSAGE = "This cannot be undone."
        const val CONFIRM = "Delete"
        const val DISMISS = "Cancel"
    }
}
