package com.coparently.app.ios

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.darwin.NSObjectProtocol

/**
 * The app's return to the foreground on the iPhone (MON-24 phase 3, Task 6): the twin of the
 * `ForegroundSyncTrigger` Android's `CoPlanlyApplication` registers, where a started count going
 * from zero to one is the app becoming visible. UIKit says it in one notification,
 * `UIApplicationWillEnterForegroundNotification`, posted on each return from the background (not at
 * launch, which `IosApp.start` covers).
 */
object IosLifecycle {

    /**
     * The observer's token, the one handle that could remove it. Kept for the life of the process,
     * since the app never stops observing; its presence is also what makes [observe] run once.
     */
    private var foregroundObserver: NSObjectProtocol? = null

    /** The two observers [foreground] follows, kept for the life of the process like [foregroundObserver]. */
    private var stateObservers: List<NSObjectProtocol>? = null

    private val inForeground = MutableStateFlow(true)

    /**
     * Whether the app is in the foreground now: false from `UIApplicationDidEnterBackgroundNotification`
     * until the next `UIApplicationWillEnterForegroundNotification` (part B, Task 3: a hidden thread
     * must not mark messages read). Starts following on its first read, from the application's state
     * then; read it on the main thread, from the running app.
     */
    val foreground: StateFlow<Boolean>
        get() {
            followForeground()
            return inForeground.asStateFlow()
        }

    private fun followForeground() {
        if (stateObservers != null) return
        val center = NSNotificationCenter.defaultCenter
        stateObservers = listOf(
            center.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> inForeground.value = false },
            center.addObserverForName(
                name = UIApplicationWillEnterForegroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> inForeground.value = true }
        )
        inForeground.value =
            UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground
    }

    /**
     * Calls [onForeground] on the main queue each time the app returns to the foreground. Once per
     * process: a second call keeps the first observer and ignores the new block.
     */
    fun observe(onForeground: () -> Unit) {
        if (foregroundObserver != null) return
        foregroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onForeground() }
    }
}
