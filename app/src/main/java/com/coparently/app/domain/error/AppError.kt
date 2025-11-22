package com.coparently.app.domain.error

/**
 * Sealed class hierarchy for application errors.
 * Provides user-friendly error messages and retry logic.
 */
sealed class AppError : Exception() {
    abstract val userMessage: String
    abstract val shouldRetry: Boolean

    /**
     * Network connectivity error.
     */
    data class NetworkError(
        override val userMessage: String = "Check your internet connection",
        override val shouldRetry: Boolean = true,
        val originalException: Throwable? = null
    ) : AppError()

    /**
     * Validation error for user input.
     */
    data class ValidationError(
        val field: String?,
        val validationMessage: String,
        override val userMessage: String = validationMessage,
        override val shouldRetry: Boolean = false
    ) : AppError()

    /**
     * Permission denied error.
     */
    data class PermissionError(
        val permission: String,
        override val userMessage: String = "Permission required: $permission",
        override val shouldRetry: Boolean = false
    ) : AppError()

    /**
     * Data synchronization error.
     */
    data class SyncError(
        override val userMessage: String = "Failed to sync data. Please try again.",
        override val shouldRetry: Boolean = true,
        val originalException: Throwable? = null
    ) : AppError()

    /**
     * Unknown or unexpected error.
     */
    data class UnknownError(
        override val userMessage: String = "Something went wrong. Please try again.",
        override val shouldRetry: Boolean = true,
        val originalException: Throwable
    ) : AppError()
}
