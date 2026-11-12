package com.coparently.app.ios.shell

import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UINavigationController
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
 * back button (a back that is disabled).
 *
 * UIKit holds a gesture's delegate weakly, so the stack host keeps this object as long as
 * [navigation].
 */
internal class EdgeSwipeBack(private val navigation: UINavigationController) :
    NSObject(), UIGestureRecognizerDelegateProtocol {

    init {
        navigation.interactivePopGestureRecognizer?.delegate = this
    }

    /** Lets go of the gesture once the stack has ended. */
    fun detach() {
        if (navigation.interactivePopGestureRecognizer?.delegate === this) {
            navigation.interactivePopGestureRecognizer?.delegate = null
        }
    }

    override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean =
        navigation.viewControllers.size > 1 &&
            navigation.transitionCoordinator == null &&
            navigation.topViewController?.navigationItem?.hidesBackButton != true
}
