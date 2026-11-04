package com.coparently.app.data.analytics

import com.coparently.app.data.platform.Analytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

/**
 * Manager for logging analytics events to Firebase Analytics.
 * Provides centralized analytics tracking for app events.
 *
 * It is the Android [Analytics] (MON-24 phase 2). It never turns collection on or off:
 * `TelemetryConsentApplier` is the one switch (REL-5). The events the shared ViewModels log are
 * `:shared-data`'s `AnalyticsEvents.kt`, through [log], with the names and parameters the functions
 * they replaced here wrote (Task 10).
 *
 * @property analytics Firebase Analytics instance
 */
class AnalyticsManager(
    private val analytics: FirebaseAnalytics
) : Analytics {

    /**
     * Logs [event] with [params] as Firebase parameters: a text as a string, a whole number as a
     * long, a decimal as a double — what the `log…` functions below write — and anything else as
     * its text.
     */
    override fun log(event: String, params: Map<String, Any>) {
        analytics.logEvent(event) {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Double -> param(key, value)
                    is Float -> param(key, value.toDouble())
                    else -> param(key, value.toString())
                }
            }
        }
    }

    /**
     * Log screen view event.
     *
     * @param screenName Name of the screen viewed
     * @param screenClass Class name of the screen
     */
    fun logScreenView(screenName: String, screenClass: String = screenName) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
        }
    }

    /**
     * Log user login event.
     *
     * @param method Authentication method used (e.g., "google", "email")
     */
    fun logLogin(method: String) {
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN) {
            param(FirebaseAnalytics.Param.METHOD, method)
        }
    }

    /**
     * Log user signup event.
     *
     * @param method Registration method used (e.g., "google", "email")
     */
    fun logSignUp(method: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP) {
            param(FirebaseAnalytics.Param.METHOD, method)
        }
    }

    /**
     * Log pairing invitation sent event.
     *
     * @param method Method used to send invitation (e.g., "email")
     */
    fun logInvitationSent(method: String = "email") {
        analytics.logEvent("invitation_sent") {
            param("method", method)
        }
    }

    /**
     * Log Google Calendar sync event.
     *
     * @param success Whether sync was successful
     */
    fun logCalendarSync(success: Boolean) {
        analytics.logEvent("calendar_sync") {
            param("success", success.toString())
        }
    }

    /**
     * Log calendar view mode changed.
     *
     * @param viewMode View mode selected (e.g., "day", "week", "month")
     */
    fun logCalendarViewChanged(viewMode: String) {
        analytics.logEvent("calendar_view_changed") {
            param("view_mode", viewMode)
        }
    }

    /**
     * Log search performed.
     *
     * @param searchTerm Search term used
     */
    fun logSearch(searchTerm: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SEARCH) {
            param(FirebaseAnalytics.Param.SEARCH_TERM, searchTerm)
        }
    }

    /**
     * Set user ID for analytics.
     *
     * @param userId User ID to set
     */
    fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    /**
     * Set user property.
     *
     * @param name Property name
     * @param value Property value
     */
    fun setUserProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    /**
     * Log custom event with parameters.
     *
     * @param eventName Name of the event
     * @param params Map of event parameters
     */
    fun logCustomEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        analytics.logEvent(eventName) {
            params.forEach { (key, value) ->
                param(key, value)
            }
        }
    }
}
