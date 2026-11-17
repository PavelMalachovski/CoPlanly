package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * Whether this iPhone may show the app's notifications (iPhone phase 4 part C, spec §7.6), the
 * permission Android asks for as `POST_NOTIFICATIONS`. Asked only at the moment its reason is on
 * screen — the push switch, an event's reminder, onboarding's notifications step — and never on
 * start (UX overhaul item 7): [request] is what those ask, and the app's start only [refresh]es.
 */
interface NotificationPermission {

    /**
     * The answer last read or given: true once the system says notifications are allowed. False
     * until the first [refresh] or [request] has answered, so a screen read before then shows the
     * permission as not given rather than claiming one it does not know of.
     */
    val granted: Boolean

    /** Reads the system's answer now, without asking, and remembers it in [granted]. */
    suspend fun refresh(): Boolean

    /**
     * Asks for alerts, sounds and badges. The system shows its question only while the parent has
     * not answered it; afterwards it answers at once with what they chose (a "no" is changed only in
     * the phone's Settings). Remembers the answer in [granted].
     */
    suspend fun request(): Boolean
}

/**
 * [NotificationPermission] over `UNUserNotificationCenter`. One object for the process, so the push
 * registration (`platformDataModule`) and the screens' permission requester (`:shared-ui`'s
 * `IosPermissions`) share one remembered answer. The notification center is reached only when
 * asked, never on construction: a test process with no app bundle cannot open one.
 */
object UserNotificationsPermission : NotificationPermission {

    @Volatile
    private var lastAnswer: Boolean = false

    override val granted: Boolean get() = lastAnswer

    override suspend fun refresh(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val allowed = settings?.authorizationStatus?.let { it in ALLOWED } == true
            lastAnswer = allowed
            if (continuation.isActive) continuation.resume(allowed)
        }
    }

    override suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { allowed, _ ->
            lastAnswer = allowed
            if (continuation.isActive) continuation.resume(allowed)
        }
    }

    /** What the system calls allowed: granted, provisionally, or for an App Clip's while. */
    private val ALLOWED = setOf(
        UNAuthorizationStatusAuthorized,
        UNAuthorizationStatusProvisional,
        UNAuthorizationStatusEphemeral,
    )
}
