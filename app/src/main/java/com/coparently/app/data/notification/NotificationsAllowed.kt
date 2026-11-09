package com.coparently.app.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Whether a notification posted on [channelId] now would be shown: the permission is held (API 33
 * and later), the app's notifications are on, and the channel is not muted. `notify` accepts a
 * notification in every one of those cases and shows nothing, so a caller that records that it
 * reminded somebody asks here first (notification audit, 9 November 2026: a request's card counted
 * "Reminders: 2 of 3" while the other parent's phone had shown none, and a month's child support
 * reminder was spent on a notification nobody saw).
 *
 * Call it after the channel is created: a channel that does not exist yet is not muted.
 */
internal fun notificationsAllowed(context: Context, channelId: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    val channel = manager.getNotificationChannelCompat(channelId) ?: return true
    return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
}
