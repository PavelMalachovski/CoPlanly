package com.coparently.app.domain.model

/**
 * Represents the result of a validation operation.
 */
sealed class ValidationResult {
    /**
     * Validation succeeded.
     */
    data object Success : ValidationResult()

    /**
     * Validation failed with an error message.
     */
    data class Error(val message: String, val field: String? = null) : ValidationResult()
}
