package com.coparently.app.ios.shell

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSLog
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerState
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIGestureRecognizerStatePossible
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.transitionCoordinator
import platform.darwin.NSObject

/**
 * The edge swipe back on every pushed screen of [navigation] (MON-24 phase 3): the delegate of its
 * interactive pop gesture.
 *
 * UIKit's own delegate refuses the swipe while the navigation bar is hidden, which it is over every
 * screen that draws its own Compose bar, and Compose's back gesture is off in the shell, so those
 * screens had no swipe back at all. This one lets the swipe begin wherever the system would with
 * its bar showing: over a pushed screen, outside a transition, and not where the screen hid its
 * back button (a back that is disabled). Nor while the screen on top has an inner step its back
 * undoes first ([com.coparently.app.presentation.common.BackSteps]): a swipe cannot be half-run
 * against a step, so it is refused, and the back button undoes the step.
 *
 * UIKit holds a gesture's delegate weakly, so the stack host keeps this object as long as
 * [navigation], and attaches it ([attach]) once the gesture exists.
 *
 * Every decision is logged under `COPLANLY_SWIPE` ([swipeLog]): each condition of
 * [gestureRecognizerShouldBegin] with its answer, a delegate this object replaces, and each state the
 * gesture moves through. The walk's back swipe stopped popping Expenses on CI with nothing on screen to
 * say why (MON-24 phase 4); these lines say whether the swipe was refused or UIKit cancelled it.
 *
 * @property stepPending Whether a screen has an inner step its back undoes first.
 */
// `NSSelectorFromString` and `useContents` are Kotlin/Native's C interop, `@ObjCAction` its export.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class EdgeSwipeBack(
    private val navigation: UINavigationController,
    private val stepPending: (UIViewController) -> Boolean
) : NSObject(), UIGestureRecognizerDelegateProtocol {

    /** Whether [stateChanged] is one of the gesture's targets. */
    private var observing = false

    /** The gesture's state [stateChanged] last logged, so a drag's every frame is not a line. */
    private var lastState: UIGestureRecognizerState? = null

    /**
     * Becomes the gesture's delegate. The navigation controller makes its gesture with its view, so
     * this is called again whenever it shows a screen ([StackHost]), until the gesture exists.
     */
    fun attach() {
        val gesture = navigation.interactivePopGestureRecognizer ?: return
        if (gesture.delegate !== this) {
            swipeLog("attach replaces delegate=${gesture.delegate?.let { it::class.simpleName }}")
            gesture.delegate = this
        }
        if (!observing) {
            // A second target beside UIKit's own: it only reads the gesture's state.
            gesture.addTarget(this, NSSelectorFromString("stateChanged:"))
            observing = true
        }
    }

    /** Lets go of the gesture once the stack has ended. */
    fun detach() {
        if (navigation.interactivePopGestureRecognizer?.delegate === this) {
            navigation.interactivePopGestureRecognizer?.delegate = null
        }
        if (observing) {
            navigation.interactivePopGestureRecognizer?.removeTarget(this, NSSelectorFromString("stateChanged:"))
            observing = false
        }
    }

    override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean {
        val screens = navigation.viewControllers.size
        val transitioning = navigation.transitionCoordinator != null
        val backHidden = navigation.topViewController?.navigationItem?.hidesBackButton == true
        val step = navigation.topViewController?.let(stepPending) == true
        val begins = screens > 1 && !transitioning && !backHidden && !step
        swipeLog(
            "shouldBegin screens=$screens transitioning=$transitioning backHidden=$backHidden " +
                "stepPending=$step answer=$begins"
        )
        return begins
    }

    /** The gesture's target beside UIKit's: logs each state it moves into, and where the touch is. */
    @ObjCAction
    fun stateChanged(sender: UIGestureRecognizer) {
        val state = sender.state
        if (state == lastState) return
        lastState = state
        val x = sender.locationInView(null).useContents { x }
        swipeLog("gesture state=${stateName(state)} x=$x")
    }

    private fun stateName(state: UIGestureRecognizerState): String = when (state) {
        UIGestureRecognizerStatePossible -> "possible"
        UIGestureRecognizerStateBegan -> "began"
        UIGestureRecognizerStateChanged -> "changed"
        UIGestureRecognizerStateEnded -> "ended"
        UIGestureRecognizerStateCancelled -> "cancelled"
        UIGestureRecognizerStateFailed -> "failed"
        else -> state.toString()
    }
}

/**
 * Logs [message] with the prefix `COPLANLY_SWIPE`, at NSLog's default level, which the walk's job
 * prints (`simulator.sh shell-test`). A log line only: nothing on screen or in the accessibility tree
 * changes.
 */
internal fun swipeLog(message: String) {
    // As `tabTapLog`: the whole line as the format, no variadic argument, its percent signs doubled.
    NSLog("COPLANLY_SWIPE " + message.replace("%", "%%"))
}
