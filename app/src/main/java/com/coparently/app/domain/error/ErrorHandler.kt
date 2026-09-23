package com.coparently.app.domain.error

import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.usecase.ValidationException
import com.coparently.app.utils.NetworkMonitor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies a failure that reaches a ViewModel as an arbitrary [Throwable] (CQ-11).
 *
 * Its caller is `EventViewModel`, whose use cases return `Result` failures of every shape — a
 * validator's refusal, an I/O error, a rules denial — and whose screen shows one sentence per
 * *kind*. That is the only place in the app where the kind is all a ViewModel knows; everywhere
 * else the ViewModel knows which operation failed and says so with its own resource
 * (`UiText.Res(R.string.change_request_error_apply_failed)` and the like), which is more useful to
 * a parent than a sentence chosen by exception type. Do not route those through here.
 */
@Singleton
class ErrorHandler @Inject constructor(
    private val crashlyticsManager: CrashlyticsManager,
    private val networkMonitor: NetworkMonitor
) {
    /**
     * Records [error] to Crashlytics and classifies it.
     *
     * @return The [AppError] the presentation layer words by type.
     */
    fun handleError(error: Throwable): AppError {
        crashlyticsManager.recordException(error)

        return when (error) {
            is ValidationException -> AppError.ValidationError(
                field = error.field,
                validationMessage = error.message ?: "Validation failed"
            )

            is SecurityException -> AppError.PermissionError(originalException = error)

            is IOException -> AppError.NetworkError(
                originalException = error,
                offline = !networkMonitor.isOnline()
            )

            is AppError -> error

            else -> AppError.UnknownError(originalException = error)
        }
    }
}
