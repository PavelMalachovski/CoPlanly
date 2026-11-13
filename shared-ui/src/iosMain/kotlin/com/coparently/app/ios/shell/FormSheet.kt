package com.coparently.app.ios.shell

import com.coparently.app.presentation.common.SheetGuard
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject

/**
 * A form presented as a sheet over the screen it came from (MON-24 phase 3, spec §5.4): the system
 * page sheet, at its large height with a grabber, holding a navigation controller whose first
 * screen is the form.
 *
 * A form with edits is not dismissed by a swipe. [guard] is what the form's
 * `rememberDiscardGuard` reports into ([SheetGuard]): while the form is dirty the sheet is modal
 * (`isModalInPresentation`), and a swipe down then asks the form's own "Discard changes?" question
 * instead of closing it. "Keep editing" leaves the sheet open; "Discard" leaves the form, which pops
 * the shared back stack and so dismisses the sheet. A swipe down over a clean form closes the sheet
 * at once and [onDismissedByUser] tells the stack host, which pops the shared back stack to match.
 *
 * @property entryId The back-stack entry of the form at the sheet's root.
 */
internal class FormSheet(
    val entryId: String,
    private val onDismissedByUser: () -> Unit
) {
    private var dirty = false
    private var ask: (() -> Unit)? = null
    private var presented: UINavigationController? = null
    private val presentationDelegate = PresentationDelegate()

    /** What the form's discard guard reports into. */
    val guard: SheetGuard = SheetGuard { isDirty, askQuestion ->
        dirty = isDirty
        ask = askQuestion
        presented?.modalInPresentation = isDirty
    }

    /**
     * The navigation controller the sheet presents, with [root] as its first screen and
     * [navigationDelegate] told of its pushes and pops. Built once.
     */
    fun navigation(
        root: UIViewController,
        navigationDelegate: UINavigationControllerDelegateProtocol
    ): UINavigationController =
        presented ?: UINavigationController(rootViewController = root).also { sheet ->
            sheet.modalPresentationStyle = UIModalPresentationPageSheet
            sheet.sheetPresentationController?.let { controller ->
                controller.detents = listOf(UISheetPresentationControllerDetent.largeDetent())
                controller.prefersGrabberVisible = true
            }
            sheet.presentationController?.delegate = presentationDelegate
            sheet.delegate = navigationDelegate
            sheet.modalInPresentation = dirty
            presented = sheet
        }

    /** The presented navigation controller, once [navigation] built it. */
    val navigationOrNull: UINavigationController? get() = presented

    /** The sheet's own answers: a swipe refused over edits asks; a swipe allowed has closed it. */
    private inner class PresentationDelegate : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidAttemptToDismiss(presentationController: UIPresentationController) {
            ask?.invoke()
        }

        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            onDismissedByUser()
        }
    }
}
