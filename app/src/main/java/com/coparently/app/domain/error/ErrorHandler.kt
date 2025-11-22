package com.coparently.app.domain.error

import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.usecase.ValidationException
import com.coparently.app.utils.NetworkMonitor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for converting exceptions to user-friendly AppError instances.
 */
@Singleton
class ErrorHandler @Inject constructor(
    private val crashlyticsManager: CrashlyticsManager,
    private val networkMonitor: NetworkMonitor
) {
    /**
     * Converts an exception to an AppError.
     */
    fun handleError(error: Throwable): AppError {
        // Log to crashlytics
        crashlyticsManager.recordException(error)

        return when (error) {
            is ValidationException -> AppError.ValidationError(
                field = error.field,
                validationMessage = error.message ?: "Validation failed"
            )

            is SecurityException -> AppError.PermissionError(
                permission = "Unknown permission"
            )

            is IOException -> {
                if (networkMonitor.isOnline()) {
                    AppError.NetworkError(
                        userMessage = "Server error occurred",
                        originalException = error
                    )
                } else {
                    AppError.NetworkError(
                        userMessage = "No internet connection",
                        originalException = error
                    )
                }
            }

            is AppError -> error

            else -> AppError.UnknownError(originalException = error)
        }
    }

    /**
     * Gets a retry action for an error if applicable.
     */
    fun getRetryAction(error: AppError): (() -> Unit)? {
        return if (error.shouldRetry) {
            // Return appropriate retry action based on error type
            when (error) {
                is AppError.NetworkError -> { /* retry network call */ null }
                is AppError.SyncError -> { /* retry sync */ null }
                else -> null
            }
        } else null
    }
}
