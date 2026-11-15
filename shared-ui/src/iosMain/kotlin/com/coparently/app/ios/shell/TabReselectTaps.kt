package com.coparently.app.ios.shell

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSLog
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UITabBarController
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UITouch
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Every tap on [controller]'s tab bar, by tab index (MON-24 phase 3, part C): [onReselect] for a tap
 * that left the tab selected, [onChange] for one that selected another.
 *
 * UIKit's delegate is not a reliable witness on the iOS 26 simulator in CI: there no tap reached the
 * shell through `tabBarController(_:didSelect:)`, not even one that changed the tab (CI run 9's
 * `COPLANLY_TABTAP` trace logged every touch here and no Calendar count), and a tap on the selected
 * Calendar tab left Day view open. So the tab bar's own touches are watched: a tap recognizer on
 * the bar, which never cancels or delays the bar's touches and recognizes beside the bar's own
 * gestures, notes the selected index when the touch begins and, once UIKit has handled the tap,
 * compares it with the selected index then. Indices, not the view controllers: in the same trace
 * the selected controller read before and after a tap on the selected tab never compared as the
 * same object. A tap reported by the delegate as well is counted twice, which nothing minds.
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
    private val onReselect: (Int) -> Unit,
    private val onChange: (Int) -> Unit
) : NSObject(), UIGestureRecognizerDelegateProtocol {

    /** The tab index selected when the tap's touch began, until the tap ends. */
    private var selectedAtTouch: Int? = null

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
        tabTapLog("recognizer tapped")
        val before = selectedAtTouch ?: return
        selectedAtTouch = null
        // After UIKit's own handling of the same tap, which may have moved to another tab.
        dispatch_async(dispatch_get_main_queue()) {
            val now = controller.selectedIndex.toInt()
            if (now == before) {
                tabTapLog("reselect index=$now")
                onReselect(now)
            } else {
                tabTapLog("changed from=$before to=$now")
                onChange(now)
            }
        }
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldReceiveTouch: UITouch): Boolean {
        selectedAtTouch = controller.selectedIndex.toInt()
        tabTapLog("recognizer touch index=$selectedAtTouch")
        return true
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
    ): Boolean = true
}

/**
 * Logs [message] with the prefix `COPLANLY_TABTAP` (MON-24 phase 3, part C): which of UIKit's
 * callbacks a tap on the tab bar reaches on the CI simulator is not documented, and a tap on the
 * selected Calendar tab did not return Day view to Month there. A log line only: nothing on screen
 * or in the accessibility tree changes.
 */
internal fun tabTapLog(message: String) {
    // The whole line as NSLog's format, with no variadic argument (Kotlin/Native's bridging of a
    // Kotlin string through C varargs is not something to rely on), its percent signs doubled.
    NSLog("COPLANLY_TABTAP " + message.replace("%", "%%"))
}
