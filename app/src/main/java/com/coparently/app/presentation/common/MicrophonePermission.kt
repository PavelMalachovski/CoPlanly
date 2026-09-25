package com.coparently.app.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** True when RECORD_AUDIO is granted. */
fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Contextual RECORD_AUDIO request, in the shape of [rememberNotificationPermissionRequester].
 *
 * The microphone is asked for on the parent's first tap of the composer's microphone (voice
 * dictation) and never on start: the question appears at the moment its reason is on screen. The
 * wrapped action runs at once when the permission is already granted, otherwise after the system
 * dialog grants it; [onDenied] runs when it does not, and is where the screen explains what the
 * microphone is for and that it can be allowed in the phone's settings.
 *
 * @param onDenied Called when the user denies the system dialog (or denied it for good before)
 */
@Composable
fun rememberMicrophonePermissionRequester(
    onDenied: () -> Unit = {}
): MicrophonePermissionRequester {
    val context = LocalContext.current
    val currentOnDenied = rememberUpdatedState(onDenied)
    val requester = remember { MicrophonePermissionRequester() }
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

/** See [rememberMicrophonePermissionRequester]. */
class MicrophonePermissionRequester internal constructor() {
    internal var launcher: ManagedActivityResultLauncher<String, Boolean>? = null
    internal var context: Context? = null
    internal var pendingAction: (() -> Unit)? = null

    /**
     * Runs [action] immediately if the microphone is already allowed, otherwise launches the
     * system permission dialog and runs [action] on grant.
     */
    fun request(action: () -> Unit) {
        val ctx = context ?: return
        if (hasMicrophonePermission(ctx)) {
            action()
            return
        }
        pendingAction = action
        launcher?.launch(Manifest.permission.RECORD_AUDIO)
    }
}
