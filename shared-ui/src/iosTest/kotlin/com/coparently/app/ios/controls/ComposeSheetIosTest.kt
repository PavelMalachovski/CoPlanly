package com.coparently.app.ios.controls

import com.coparently.app.presentation.common.SheetHeight
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UISheetPresentationControllerDetentIdentifierMedium
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.sheetPresentationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The iPhone's in-screen sheet (MON-24 phase 3, part C, Task 6): it ends once — a swipe down or a
 * presentation UIKit dropped calling `onDismiss` once, a close calling nothing — it is built once,
 * as a page sheet at its height's detents, and a form inside that reports edits keeps it from
 * closing on a swipe.
 */
class ComposeSheetIosTest {

    private var dismissed = 0
    private var built = 0

    private fun sheet(height: SheetHeight = SheetHeight.Large) = ComposeSheet(
        height = height,
        onDismissed = { dismissed++ }
    ) {
        built++
        UIViewController(nibName = null, bundle = null)
    }

    @Test
    fun `a swipe down calls onDismiss once`() {
        val sheet = sheet()

        sheet.dismissed()
        sheet.dismissed()
        sheet.close()

        assertEquals(1, dismissed)
        assertTrue(sheet.finished)
    }

    @Test
    fun `a close calls nothing and ends the sheet`() {
        val sheet = sheet()

        sheet.close()
        sheet.dismissed()

        assertEquals(0, dismissed)
        assertTrue(sheet.finished)
    }

    @Test
    fun `the sheet is built once and is a page sheet with the grabber`() {
        val sheet = sheet(SheetHeight.Medium)

        val first = sheet.prepare()
        assertSame(first, sheet.prepare())
        assertEquals(1, built)
        assertEquals(UIModalPresentationPageSheet, first.modalPresentationStyle)
        val presentation = assertNotNull(first.sheetPresentationController)
        assertTrue(presentation.prefersGrabberVisible)
        assertEquals(UISheetPresentationControllerDetentIdentifierMedium, presentation.largestUndimmedDetentIdentifier)
    }

    @Test
    fun `a large sheet dims what is under it`() {
        val presentation = assertNotNull(sheet(SheetHeight.Large).prepare().sheetPresentationController)

        assertEquals(null, presentation.largestUndimmedDetentIdentifier)
    }

    @Test
    fun `the medium sheet can grow to large and the large one cannot shrink`() {
        val medium = detentsFor(SheetHeight.Medium)
        val large = detentsFor(SheetHeight.Large)

        assertEquals(2, medium.size)
        assertEquals(UISheetPresentationControllerDetent.mediumDetent().identifier, medium.first().identifier)
        assertEquals(UISheetPresentationControllerDetent.largeDetent().identifier, medium.last().identifier)
        assertEquals(listOf(UISheetPresentationControllerDetent.largeDetent().identifier), large.map { it.identifier })
    }

    @Test
    fun `edits reported by a form keep the sheet from a swipe and a refused swipe asks`() {
        var asked = 0
        val sheet = sheet()
        val controller = sheet.prepare()

        sheet.guard.report(true) { asked++ }
        assertTrue(controller.modalInPresentation)
        sheet.attemptedToDismiss()
        assertEquals(1, asked)

        sheet.guard.report(false) {}
        assertFalse(controller.modalInPresentation)
        assertSame(controller, sheet.controller)
    }
}
