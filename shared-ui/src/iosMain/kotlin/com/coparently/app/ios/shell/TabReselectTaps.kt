package com.coparently.app.ios.shell

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UITabBarController
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UITouch
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Every tap on the tab already selected in [controller]'s tab bar, told to [onReselect] with that
 * tab's view controller (MON-24 phase 3, part C).
 *
 * UIKit's delegate is not a reliable witness of such a tap: on the iOS 26 simulator in CI a tap on
 * the selected Calendar tab reached neither `tabBarController(_:didSelect:)` nor
 * `tabBarController(_:shouldSelect:)` (the walk and the tour each found Day view still open, with
 * its bar, after the tap that `CalendarTabTaps` answers by opening Month). So the tab bar's own
 * touches are watched: a tap recognizer on the bar, which never cancels or delays the bar's touches
 * and recognizes beside the bar's own gestures, notes the tab selected when the touch begins, and
 * once UIKit has handled the tap, reports the tab if it is still the one selected. A tap that
 * changes the tab is UIKit's `didSelect` to report, not this.
 *
 * A tap on the bar outside every item (its margins) also leaves the tab selected, and is reported
 * as a reselection too: the only answer to one is to show the tab's start, which is what a tap on
 * the bar asks for anyway.
 *
 * UIKit holds a recognizer's target and delegate weakly, so the tab shell keeps this object as long
 * as its controller, and [attach]es it once.
 */
// `NSSelectorFromString` is Kotlin/Native's C interop, and `@ObjCAction` its Objective-C export.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class TabReselectTaps(
    private val controller: UITabBarController,
    private val onReselect: (UIViewController) -> Unit
) : NSObject(), UIGestureRecognizerDelegateProtocol {

    /** The tab selected when the tap's touch began, until the tap ends or fails. */
    private var selectedAtTouch: UIViewController? = null

    private val recognizer = UITapGestureRecognizer(target = this, action = NSSelectorFromString("tapped:")).also {
        it.cancelsTouchesInView = false
        it.delaysTouchesBegan = false
        it.delaysTouchesEnded = false
        it.delegate = this
    }

    /** Starts watching the tab bar's taps. */
    fun attach() {
        controller.tabBar.addGestureRecognizer(recognizer)
    }

    /** Stops watching, once the tabs have ended. */
    fun detach() {
        recognizer.delegate = null
        controller.tabBar.removeGestureRecognizer(recognizer)
    }

    /** The recognizer's action: a tap ended on the bar. The sender is the recognizer itself. */
    @ObjCAction
    fun tapped(sender: UITapGestureRecognizer) {
        if (sender.state != UIGestureRecognizerStateEnded) return
        val before = selectedAtTouch ?: return
        selectedAtTouch = null
        // After UIKit's own handling of the same tap, which may have moved to another tab.
        dispatch_async(dispatch_get_main_queue()) {
            if (controller.selectedViewController === before) onReselect(before)
        }
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldReceiveTouch: UITouch): Boolean {
        selectedAtTouch = controller.selectedViewController
        return true
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
    ): Boolean = true
}
