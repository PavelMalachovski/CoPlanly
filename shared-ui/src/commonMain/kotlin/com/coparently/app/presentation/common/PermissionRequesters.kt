package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coparently.app.presentation.platform.LocalPermissionRequester
import com.coparently.app.presentation.platform.PermissionRequest
import com.coparently.app.presentation.platform.PlatformPermission

/**
 * Contextual notification permission request helper.
 *
 * The app does not ask for notification permission on app start. It asks once in onboarding, on
 * a step of its own that says what the notifications are for (tester feedback, 21 October 2026:
 * a parent who never touched the push switch got no reminders and no pushes at all), and again
 * from the exact interaction that needs notifications (enabling the push toggle, picking an
 * event reminder) — call [NotificationPermissionRequester.request] there.
 * The wrapped action runs immediately when permission is already granted or not
 * required, otherwise it runs after the user grants the system dialog.
 *
 * Through the platform's [com.coparently.app.presentation.platform.PermissionRequester] (MON-24
 * phase 2, Task 11): on Android POST_NOTIFICATIONS from Android 13, always granted below it;
 * `hasNotificationPermission(context)` stays in the app beside the Android half.
 *
 * @param onDenied Optional callback when the user denies the system dialog
 */
@Composable
fun rememberNotificationPermissionRequester(
    onDenied: () -> Unit = {}
): NotificationPermissionRequester {
    val request = LocalPermissionRequester.current.rememberRequest(PlatformPermission.NOTIFICATIONS, onDenied)
    return remember(request) { NotificationPermissionRequester(request) }
}

/** See [rememberNotificationPermissionRequester]. */
class NotificationPermissionRequester internal constructor(private val permission: PermissionRequest) {

    /**
     * Runs [action] immediately if notifications are already allowed, otherwise
     * launches the system permission dialog and runs [action] on grant.
     * (Named `request`, not `run`, to avoid clashing with Kotlin's stdlib `run`.)
     */
    fun request(action: () -> Unit) = permission.request(action)
}

/**
 * Contextual microphone request, in the shape of [rememberNotificationPermissionRequester].
 *
 * The microphone is asked for on the parent's first tap of the composer's microphone (voice
 * dictation) and never on start: the question appears at the moment its reason is on screen. The
 * wrapped action runs at once when the permission is already granted, otherwise after the system
 * dialog grants it; [onDenied] runs when it does not, and is where the screen explains what the
 * microphone is for and that it can be allowed in the phone's settings.
 *
 * Through the platform's [com.coparently.app.presentation.platform.PermissionRequester] (MON-24
 * phase 2, Task 11): on Android RECORD_AUDIO; `hasMicrophonePermission(context)` stays in the app.
 *
 * @param onDenied Called when the user denies the system dialog (or denied it for good before)
 */
@Composable
fun rememberMicrophonePermissionRequester(
    onDenied: () -> Unit = {}
): MicrophonePermissionRequester {
    val request = LocalPermissionRequester.current.rememberRequest(PlatformPermission.MICROPHONE, onDenied)
    return remember(request) { MicrophonePermissionRequester(request) }
}

/** See [rememberMicrophonePermissionRequester]. */
class MicrophonePermissionRequester internal constructor(private val permission: PermissionRequest) {

    /**
     * Runs [action] immediately if the microphone is already allowed, otherwise launches the
     * system permission dialog and runs [action] on grant.
     */
    fun request(action: () -> Unit) = permission.request(action)
}
