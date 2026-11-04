package com.coparently.app.presentation.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/*
 * The Android checks behind `PermissionRequester`'s Android half (`presentation/platform/
 * AndroidPlatform.kt`), which the app's own screens read too. The requesters themselves are common
 * since MON-24 phase 2, Task 11 (`:shared-ui`'s `presentation/common/PermissionRequesters.kt`).
 */

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

/** True when RECORD_AUDIO is granted. */
fun hasMicrophonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
