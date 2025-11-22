package com.coparently.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.coparently.app.R
import com.coparently.app.presentation.MainActivity

/**
 * Utility object for managing app shortcuts.
 * Provides quick access to frequently used features.
 */
object AppShortcuts {

    /**
     * Sets up dynamic app shortcuts.
     * Call this from MainActivity onCreate.
     */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun setupShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = listOf(
            createNewEventShortcut(context),
            createTodayEventsShortcut(context),
            createEmergencyInfoShortcut(context)
        )

        shortcutManager.dynamicShortcuts = shortcuts
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createNewEventShortcut(context: Context): ShortcutInfo {
        return ShortcutInfo.Builder(context, "new_event")
            .setShortLabel("New Event")
            .setLongLabel("Create new calendar event")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_input_add))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("coparently://new-event")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createTodayEventsShortcut(context: Context): ShortcutInfo {
        return ShortcutInfo.Builder(context, "today_events")
            .setShortLabel("Today's Events")
            .setLongLabel("View today's schedule")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_menu_today))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("coparently://today")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createEmergencyInfoShortcut(context: Context): ShortcutInfo {
        return ShortcutInfo.Builder(context, "emergency")
            .setShortLabel("Emergency Info")
            .setLongLabel("Access critical medical information")
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_dialog_alert))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("coparently://emergency")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            .build()
    }

    /**
     * Handles deep link navigation from shortcuts.
     * Call this from MainActivity onCreate.
     *
     * @param intent The intent containing the deep link
     * @param onNavigate Callback to handle navigation
     */
    fun handleShortcutIntent(
        intent: Intent?,
        onNavigate: (destination: ShortcutDestination) -> Unit
    ) {
        when (intent?.data?.host) {
            "new-event" -> onNavigate(ShortcutDestination.NewEvent)
            "today" -> onNavigate(ShortcutDestination.TodayView)
            "emergency" -> onNavigate(ShortcutDestination.Emergency)
        }
    }

    /**
     * Destinations that can be accessed via shortcuts.
     */
    sealed class ShortcutDestination {
        object NewEvent : ShortcutDestination()
        object TodayView : ShortcutDestination()
        object Emergency : ShortcutDestination()
    }
}
