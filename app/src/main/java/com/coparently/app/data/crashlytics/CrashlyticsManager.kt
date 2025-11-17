package com.coparently.app.data.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for logging exceptions and custom data to Firebase Crashlytics.
 * Provides centralized crash reporting and monitoring.
 *
 * @property crashlytics Firebase Crashlytics instance
 */
@Singleton
class CrashlyticsManager @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) {

    /**
     * Records a non-fatal exception to Crashlytics.
     *
     * @param throwable The exception to record
     */
    fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    /**
     * Logs a message to Crashlytics for debugging crashes.
     *
     * @param message The message to log
     */
    fun log(message: String) {
        crashlytics.log(message)
    }

    /**
     * Sets the user ID for crash reports.
     *
     * @param userId User identifier
     */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (String)
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (Boolean)
     */
    fun setCustomKey(key: String, value: Boolean) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (Int)
     */
    fun setCustomKey(key: String, value: Int) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (Long)
     */
    fun setCustomKey(key: String, value: Long) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (Float)
     */
    fun setCustomKey(key: String, value: Float) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Sets a custom key-value pair for crash reports.
     *
     * @param key The key name
     * @param value The value (Double)
     */
    fun setCustomKey(key: String, value: Double) {
        crashlytics.setCustomKey(key, value)
    }

    /**
     * Checks if automatic data collection is enabled.
     *
     * @return True if enabled, false otherwise
     */
    fun isCrashlyticsCollectionEnabled(): Boolean {
        return crashlytics.isCrashlyticsCollectionEnabled
    }

    /**
     * Enables or disables automatic data collection.
     *
     * @param enabled True to enable, false to disable
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Manually sends any unsent crash reports.
     */
    fun sendUnsentReports() {
        crashlytics.sendUnsentReports()
    }

    /**
     * Deletes any unsent crash reports.
     */
    fun deleteUnsentReports() {
        crashlytics.deleteUnsentReports()
    }

    /**
     * Records an exception with additional context information.
     *
     * @param throwable The exception to record
     * @param context Additional context information as key-value pairs
     */
    fun recordExceptionWithContext(throwable: Throwable, context: Map<String, String>) {
        context.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }
        crashlytics.recordException(throwable)
    }
}

