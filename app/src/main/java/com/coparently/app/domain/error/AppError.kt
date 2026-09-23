package com.coparently.app.domain.error

/**
 * What kind of failure an event operation hit, as [ErrorHandler] classifies it (CQ-11).
 *
 * A classification, not a message: the domain layer has no `Context`, so the sentence a user
 * reads is chosen by the presentation layer from the variant (`presentation/common/ErrorText.kt`,
 * CQ-14). The original exception goes to Crashlytics in [ErrorHandler.handleError] and to the log
 * at the call site. Only variants something produces live here — a `SyncError`, a `userMessage`
 * and a `shouldRetry` flag were declared for years and read by nothing, which read as coverage.
 */
sealed class AppError : Exception() {

    /**
     * An I/O failure.
     *
     * @property offline True when the device had no connection; false when it did and the server
     *   failed. The two need different advice.
     */
    data class NetworkError(
        val originalException: Throwable? = null,
        val offline: Boolean = true
    ) : AppError()

    /**
     * Input a use case refused.
     *
     * @property field The field the validator named, so a form can say which one; null when the
     *   refusal is not about one field.
     * @property validationMessage The validator's own English text, for the log.
     */
    data class ValidationError(
        val field: String?,
        val validationMessage: String
    ) : AppError()

    /** The platform refused the operation (a [SecurityException]). */
    data class PermissionError(
        val originalException: Throwable? = null
    ) : AppError()

    /** Anything else. */
    data class UnknownError(
        val originalException: Throwable
    ) : AppError()
}
