package com.coparently.app.ios.shell

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectContainsPoint
import platform.Foundation.NSLog
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UITabBarController
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UITouch
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityValue
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Every tap on the tab already selected in [controller]'s tab bar, told to [onReselect] with that
 * tab's view controller (MON-24 phase 3, part C).
 *
 * UIKit's delegate is not a reliable witness of such a tap: on the iOS 26 simulator in CI the walk
 * and the tour found Day view still open, with its bar, after a tap on the selected Calendar tab,
 * with the tap counted from `didSelect` (before 3136dda) and from `shouldSelect` (3136dda). So the
 * taps are also watched where they land: a tap recognizer on the controller's view that receives
 * only touches inside the tab bar, never cancels or delays them, and recognizes beside every other
 * gesture. It notes the tab selected when the touch begins, and, once UIKit has handled the tap,
 * reports the tab if it is still the one selected. (It sits on the controller's view rather than
 * on the bar so that it still sees the touch if the bar's glass draws its items in views of its
 * own.) A tap that changes the tab is UIKit's `didSelect` to report, not this.
 *
 * A tap on the bar outside every item (its margins) also leaves the tab selected, and is reported
 * as a reselection too: the only answer to one is to show the tab's start, which is what a tap on
 * the bar asks for anyway.
 *
 * UIKit holds a recognizer's target and delegate weakly, so the tab shell keeps this object as long
 * as its controller, and [attach]es it once.
 *
 * @property trace Where each touch and tap is recorded ([TabTapTrace]).
 */
// `NSSelectorFromString` and `CGRectContainsPoint` are Kotlin/Native's C interop, and
// `@ObjCAction` its Objective-C export.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class TabReselectTaps(
    private val controller: UITabBarController,
    private val trace: TabTapTrace,
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
        controller.view.addGestureRecognizer(recognizer)
    }

    /** Stops watching, once the tabs have ended. */
    fun detach() {
        recognizer.delegate = null
        controller.view.removeGestureRecognizer(recognizer)
    }

    /** The recognizer's action: a tap ended on the bar. The sender is the recognizer itself. */
    @ObjCAction
    fun tapped(sender: UITapGestureRecognizer) {
        if (sender.state != UIGestureRecognizerStateEnded) return
        trace.note(TabTapTrace.Event.TAPPED)
        val before = selectedAtTouch ?: return
        selectedAtTouch = null
        // After UIKit's own handling of the same tap, which may have moved to another tab.
        dispatch_async(dispatch_get_main_queue()) {
            if (controller.selectedViewController === before) onReselect(before)
        }
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldReceiveTouch: UITouch): Boolean {
        val bar = controller.tabBar
        if (bar.hidden || !CGRectContainsPoint(bar.bounds, shouldReceiveTouch.locationInView(bar))) return false
        trace.note(TabTapTrace.Event.TOUCH)
        selectedAtTouch = controller.selectedViewController
        return true
    }

    @ObjCSignatureOverride
    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
    ): Boolean = true
}

/**
 * What the shell saw of the tab bar's taps, for the iPhone's UI tests (MON-24 phase 3, part C): a
 * tap on the selected Calendar tab did not return Day view to Month on the CI simulator, and which
 * of UIKit's callbacks a tap reaches there is not documented. Each event is logged with the prefix
 * `COPLANLY_TABTAP` and counted, and the counts are the tab bar's accessibility value, which the
 * walk and the tour print when the Calendar keeps Day view after a tap. The tab bar is a container,
 * not an element VoiceOver focuses, so the value is not spoken.
 *
 * @property controller The tab bar controller whose bar carries the counts.
 */
internal class TabTapTrace(private val controller: UITabBarController) {

    /** One thing the shell saw. */
    enum class Event(val key: String) {
        /** A touch began inside the tab bar ([TabReselectTaps]). */
        TOUCH("touch"),

        /** A tap ended inside the tab bar ([TabReselectTaps]). */
        TAPPED("tapped"),

        /** UIKit asked the delegate `shouldSelectViewController`. */
        SHOULD_SELECT("shouldVc"),

        /** UIKit asked the delegate `shouldSelectTab`. */
        SHOULD_SELECT_TAB("shouldTab"),

        /** UIKit told the delegate `didSelectViewController`. */
        DID_SELECT("didVc"),

        /** UIKit told the delegate `didSelectTab`. */
        DID_SELECT_TAB("didTab"),

        /** A tap on the selected tab was reported, from any of the above. */
        RESELECT("reselect"),

        /** `CalendarTabTaps` was counted. */
        CALENDAR_COUNTED("calendarCount"),

        /** The Calendar's first screen had an inner step, which the tap undid. */
        ROOT_STEP_UNDONE("rootStepUndone"),

        /** A moment after a Calendar tap, its first screen still had an inner step. */
        ROOT_STEP_LEFT("rootStepLeft")
    }

    private val counts = mutableMapOf<Event, Int>()

    /** Records [event], with [detail] in the log line. */
    fun note(event: Event, detail: String = "") {
        counts[event] = (counts[event] ?: 0) + 1
        NSLog("COPLANLY_TABTAP %@ %@", event.key, detail)
        controller.tabBar.accessibilityValue = Event.entries.joinToString(" ") { "${it.key}=${counts[it] ?: 0}" }
    }
}
