package com.coparently.app.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns true when POST_NOTIFICATIONS is granted (always true below Android 13).
 */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Contextual POST_NOTIFICATIONS request helper.
 *
 * The app deliberately does NOT ask for notification permission on app start;
 * instead call [NotificationPermissionRequester.request] from the exact interaction
 * that needs notifications (enabling the push toggle, picking an event reminder).
 * The wrapped action runs immediately when permission is already granted or not
 * required, otherwise it runs after the user grants the system dialog.
 *
 * @param onDenied Optional callback when the user denies the system dialog
 */
@Composable
fun rememberNotificationPermissionRequester(
    onDenied: () -> Unit = {}
): NotificationPermissionRequester {
    val context = LocalContext.current
    val currentOnDenied = rememberUpdatedState(onDenied)
    val requester = remember { NotificationPermissionRequester() }
    requester.launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = requester.pendingAction
        requester.pendingAction = null
        if (granted) pending?.invoke() else currentOnDenied.value.invoke()
    }
    requester.context = context
    return requester
}

/** See [rememberNotificationPermissionRequester]. */
class NotificationPermissionRequester internal constructor() {
    internal var launcher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>? =
        null
    internal var context: Context? = null
    internal var pendingAction: (() -> Unit)? = null

    /**
     * Runs [action] immediately if notifications are already allowed, otherwise
     * launches the system permission dialog and runs [action] on grant.
     * (Named `request`, not `run`, to avoid clashing with Kotlin's stdlib `run`.)
     */
    fun request(action: () -> Unit) {
        val ctx = context
        if (ctx == null || hasNotificationPermission(ctx)) {
            action()
            return
        }
        pendingAction = action
        launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
