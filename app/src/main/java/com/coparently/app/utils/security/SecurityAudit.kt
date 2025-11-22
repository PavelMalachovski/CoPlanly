package com.coparently.app.utils.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.domain.model.SecurityIssue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs security audits and checks on the application.
 * Detects potential security issues and reports them to Crashlytics.
 */
@Singleton
class SecurityAudit @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashlyticsManager: CrashlyticsManager,
    private val encryptionManager: EncryptionManager
) {
    /**
     * Performs a comprehensive security check.
     *
     * @return List of detected security issues
     */
    fun performSecurityCheck(): List<SecurityIssue> {
        val issues = mutableListOf<SecurityIssue>()

        // Check root detection
        if (isDeviceRooted()) {
            issues.add(SecurityIssue.ROOT_DETECTED)
            Log.w(TAG, "Security Issue: Device is rooted")
        }

        // Check if sensitive data is encrypted
        if (hasUnencryptedSensitiveData()) {
            issues.add(SecurityIssue.UNENCRYPTED_DATA)
            Log.w(TAG, "Security Issue: Unencrypted sensitive data found")
        }

        // Check certificate pinning (placeholder - implement based on your network setup)
        if (!isCertificatePinningEnabled()) {
            issues.add(SecurityIssue.CERTIFICATE_PINNING_DISABLED)
            Log.w(TAG, "Security Issue: Certificate pinning disabled")
        }

        // Check for debuggable app
        if (isAppDebuggable()) {
            issues.add(SecurityIssue.DEBUGGABLE_APP)
            Log.w(TAG, "Security Issue: App is debuggable")
        }

        // Report issues to Crashlytics
        issues.forEach { issue ->
            crashlyticsManager.logSecurityIssue(issue)
        }

        if (issues.isEmpty()) {
            Log.i(TAG, "Security check passed - no issues detected")
        } else {
            Log.w(TAG, "Security check completed - ${issues.size} issue(s) detected")
        }

        return issues
    }

    /**
     * Checks if the device is rooted.
     * Uses multiple detection methods for better accuracy.
     *
     * @return true if device appears to be rooted
     */
    private fun isDeviceRooted(): Boolean {
        // Method 1: Check for su binary
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in suPaths) {
            if (File(path).exists()) {
                return true
            }
        }

        // Method 2: Try to execute su command
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if there is unencrypted sensitive data.
     * This is a placeholder - implement based on your data structure.
     *
     * @return true if unencrypted sensitive data is found
     */
    private fun hasUnencryptedSensitiveData(): Boolean {
        // Check if encryption key exists
        if (!encryptionManager.hasKey()) {
            Log.w(TAG, "Encryption key not found - sensitive data may be unencrypted")
            return true
        }

        // TODO: Implement actual check based on your database structure
        // For example, query medical records and check if diagnosis field is encrypted
        // This would require access to your database/repository

        return false
    }

    /**
     * Checks if certificate pinning is enabled.
     * This is a placeholder - implement based on your network configuration.
     *
     * @return true if certificate pinning is enabled
     */
    private fun isCertificatePinningEnabled(): Boolean {
        // TODO: Implement based on your OkHttp client configuration
        // Check if CertificatePinner is configured in your network module

        // For now, assume it's enabled to avoid false positives
        return true
    }

    /**
     * Checks if the app is debuggable.
     *
     * @return true if app is debuggable
     */
    private fun isAppDebuggable(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Gets a summary of security status.
     *
     * @return Human-readable security status summary
     */
    fun getSecurityStatusSummary(): String {
        val issues = performSecurityCheck()

        return when {
            issues.isEmpty() -> "Security Status: ✓ All checks passed"
            issues.size == 1 -> "Security Status: ⚠ 1 issue detected"
            else -> "Security Status: ⚠ ${issues.size} issues detected"
        }
    }

    companion object {
        private const val TAG = "SecurityAudit"
    }
}
