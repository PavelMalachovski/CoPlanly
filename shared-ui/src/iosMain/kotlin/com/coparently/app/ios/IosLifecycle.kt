package com.coparently.app.ios

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
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
