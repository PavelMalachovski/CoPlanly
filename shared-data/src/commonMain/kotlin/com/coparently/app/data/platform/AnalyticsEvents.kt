package com.coparently.app.data.platform

/*
 * The product events the shared ViewModels log (MON-24 phase 2, Task 10), each the event and
 * parameters `AnalyticsManager`'s own function of the same name logged, through [Analytics.log].
 * Never a name, an address or anything a parent typed.
 */

/** Push notifications switched on or off in Settings. */
fun Analytics.logNotificationsToggled(enabled: Boolean) =
    log("notifications_toggled", mapOf("enabled" to enabled.toString()))

/** The app's theme set to dark or light in Settings. */
fun Analytics.logThemeChanged(isDark: Boolean) = log("theme_changed", mapOf("dark_mode" to isDark.toString()))

/** An invitation addressed to this parent accepted. */
fun Analytics.logInvitationAccepted() = log("invitation_accepted")
