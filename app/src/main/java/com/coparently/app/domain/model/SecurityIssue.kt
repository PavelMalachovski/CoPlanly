package com.coparently.app.domain.model

/**
 * Enum representing different types of security issues that can be detected.
 * Used by SecurityAudit to categorize and report security problems.
 */
enum class SecurityIssue(val severity: Severity, val description: String) {
    ROOT_DETECTED(
        Severity.CRITICAL,
        "Device is rooted - security may be compromised"
    ),
    UNENCRYPTED_DATA(
        Severity.HIGH,
        "Sensitive data found without encryption"
    ),
    CERTIFICATE_PINNING_DISABLED(
        Severity.MEDIUM,
        "Certificate pinning is not enabled"
    ),
    DEBUGGABLE_APP(
        Severity.HIGH,
        "App is debuggable - should not be used in production"
    ),
    WEAK_ENCRYPTION(
        Severity.HIGH,
        "Weak encryption algorithm detected"
    ),
    OUTDATED_DEPENDENCIES(
        Severity.MEDIUM,
        "Outdated dependencies with known vulnerabilities"
    );

    /**
     * Severity levels for security issues.
     */
    enum class Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
