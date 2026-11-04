package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable

/**
 * The runtime permissions the app asks for, each at the moment its reason is on screen and never
 * on start (UX overhaul item 7).
 */
interface PermissionRequester : PlatformCapability {

    /** Whether [permission] is granted now (a permission the platform does not ask for always is). */
    fun isGranted(permission: PlatformPermission): Boolean

    /**
     * A request for [permission]: [PermissionRequest.request] runs its action at once when the
     * permission is already granted, and otherwise after the system dialog grants it.
     *
     * @param onDenied Called when the dialog is refused (or was refused for good before)
     */
    @Composable
    fun rememberRequest(permission: PlatformPermission, onDenied: () -> Unit): PermissionRequest
}

/** A permission a screen may ask for. */
enum class PlatformPermission {

    /** Showing notifications (on Android, POST_NOTIFICATIONS from Android 13). */
    NOTIFICATIONS,

    /** The microphone, for dictation. */
    MICROPHONE,

    /** The camera. */
    CAMERA,
}

/** See [PermissionRequester.rememberRequest]. */
fun interface PermissionRequest {

    /** Runs [action] once the permission is granted, asking first when it is not. */
    fun request(action: () -> Unit)
}
