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

/** A child's record saved for the first time. */
fun Analytics.logChildInfoAdded() = log("child_info_added")

/** A child's record saved again. */
fun Analytics.logChildInfoUpdated() = log("child_info_updated")

/** A child's record deleted. */
fun Analytics.logChildInfoDeleted() = log("child_info_deleted")

/** A pet's record saved for the first time. */
fun Analytics.logPetAdded() = log("pet_added")

/** A pet's record saved again. */
fun Analytics.logPetUpdated() = log("pet_updated")

/** A pet's record deleted. */
fun Analytics.logPetDeleted() = log("pet_deleted")
