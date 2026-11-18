package com.coparently.app.data.notification

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * The few calls the iPhone's local reminders make of the system's notification center (iPhone phase
 * 4 part C, design §7.4–7.5), narrow so the tests hand [IosLocalReminders] a fake.
 *
 * A request is known by its id alone: adding an id that is pending replaces it, as the system does.
 */
interface LocalNotificationCenter {

    /** The ids of every request still waiting to be shown; one that has been shown is not among them. */
    suspend fun pending(): List<String>

    /**
     * Adds (or replaces) the request [id], shown at the instant [atMillis] (epoch millis) with [title]
     * and [body]; a tap hands [data] to the app, as a push's data is handed (`IosApp.openPush`).
     */
    fun add(id: String, atMillis: Long, title: String, body: String, data: Map<String, String>)

    /** Removes the pending requests [ids]; an id that is not pending is ignored. */
    fun remove(ids: List<String>)
}

/**
 * [LocalNotificationCenter] over `UNUserNotificationCenter`. The trigger is a time interval from
 * now to the instant, so a change of the phone's zone or of daylight-saving time does not move it;
 * every return to the foreground arms again from the instants (a sync's re-arm), which corrects a
 * clock the parent set by hand. The center is reached only when asked, never on construction: a
 * test process with no app bundle cannot open one.
 */
object UserNotificationCenter : LocalNotificationCenter {

    private const val TAG = "UserNotificationCenter"

    /** The shortest wait the system accepts for a time-interval trigger, in seconds. */
    private const val SHORTEST_WAIT_SECONDS = 1.0

    private const val MILLIS_PER_SECOND = 1000.0

    override suspend fun pending(): List<String> = suspendCancellableCoroutine { continuation ->
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getPendingNotificationRequestsWithCompletionHandler { list ->
            val ids = list.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
            if (continuation.isActive) continuation.resume(ids)
        }
    }

    override fun add(id: String, atMillis: Long, title: String, body: String, data: Map<String, String>) {
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        content.setSound(UNNotificationSound.defaultSound())
        content.setUserInfo(data.mapKeys { it.key as Any? }.mapValues { it.value as Any? })
        val wait = ((atMillis - Clock.System.now().toEpochMilliseconds()) / MILLIS_PER_SECOND)
            .coerceAtLeast(SHORTEST_WAIT_SECONDS)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(wait, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error: NSError? ->
            if (error != null) logger.w(TAG, "A reminder could not be armed: ${error.domain} ${error.code}")
        }
    }

    override fun remove(ids: List<String>) {
        if (ids.isEmpty()) return
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(ids)
    }
}
