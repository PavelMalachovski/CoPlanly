package com.coparently.app.presentation.common

/**
 * Generic UI state wrapper with comprehensive error handling.
 * Supports idle, loading, success, and error states with retry capability.
 *
 * @param T Type of successful data
 */
sealed class UiState<out T> {
    /**
     * Idle state - no operation in progress.
     */
    data object Idle : UiState<Nothing>()

    /**
     * Loading state - operation in progress.
     *
     * @property message Optional loading message
     * @property progress Optional progress (0.0 to 1.0)
     */
    data class Loading(
        val message: String? = null,
        val progress: Float? = null
    ) : UiState<Nothing>()

    /**
     * Success state - operation completed successfully.
     *
     * @property data Successful result data
     * @property message Optional success message
     */
    data class Success<T>(
        val data: T,
        val message: String? = null
    ) : UiState<T>()

    /**
     * Error state - operation failed.
     *
     * @property error Error details
     * @property previousData Optional previous data to show during error
     */
    data class Error(
        val error: UiError,
        val previousData: Any? = null
    ) : UiState<Nothing>()

    /**
     * Checks if the state is loading.
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * Checks if the state is successful.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Checks if the state is error.
     */
    fun isError(): Boolean = this is Error

    /**
     * Gets the data if state is Success, null otherwise.
     */
    fun getDataOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

/**
 * Detailed error information with retry capability.
 */
data class UiError(
    val message: String,
    val type: ErrorType = ErrorType.UNKNOWN,
    val throwable: Throwable? = null,
    val retry: (() -> Unit)? = null
) {
    companion object {
        /**
         * Creates error from exception.
         */
        fun fromException(
            throwable: Throwable,
            retry: (() -> Unit)? = null
        ): UiError {
            val type = when {
                throwable is java.io.IOException -> ErrorType.NETWORK
                throwable.message?.contains("auth", ignoreCase = true) == true -> ErrorType.AUTHENTICATION
                throwable.message?.contains("permission", ignoreCase = true) == true -> ErrorType.PERMISSION
                throwable.message?.contains("not found", ignoreCase = true) == true -> ErrorType.NOT_FOUND
                else -> ErrorType.UNKNOWN
            }

            val message = when (type) {
                ErrorType.NETWORK -> "Network error. Please check your connection."
                ErrorType.AUTHENTICATION -> "Authentication failed. Please sign in again."
                ErrorType.PERMISSION -> "You don't have permission to perform this action."
                ErrorType.NOT_FOUND -> "The requested resource was not found."
                ErrorType.VALIDATION -> "Invalid input. Please check your data."
                ErrorType.SERVER -> "Server error. Please try again later."
                ErrorType.UNKNOWN -> throwable.message ?: "Something went wrong. Please try again."
            }

            return UiError(
                message = message,
                type = type,
                throwable = throwable,
                retry = retry
            )
        }

        /**
         * Creates network error.
         */
        fun network(
            message: String = "Network error. Please check your connection.",
            retry: (() -> Unit)? = null
        ) = UiError(
            message = message,
            type = ErrorType.NETWORK,
            retry = retry
        )

        /**
         * Creates validation error.
         */
        fun validation(
            message: String,
            retry: (() -> Unit)? = null
        ) = UiError(
            message = message,
            type = ErrorType.VALIDATION,
            retry = retry
        )

        /**
         * Creates authentication error.
         */
        fun authentication(
            message: String = "Authentication failed. Please sign in again.",
            retry: (() -> Unit)? = null
        ) = UiError(
            message = message,
            type = ErrorType.AUTHENTICATION,
            retry = retry
        )
    }
}

/**
 * Types of errors that can occur.
 */
enum class ErrorType {
    NETWORK,
    AUTHENTICATION,
    PERMISSION,
    VALIDATION,
    NOT_FOUND,
    SERVER,
    UNKNOWN
}

/**
 * Extension function to convert Result to UiState.
 */
fun <T> Result<T>.toUiState(
    retry: (() -> Unit)? = null
): UiState<T> {
    return fold(
        onSuccess = { UiState.Success(it) },
        onFailure = { UiState.Error(UiError.fromException(it, retry)) }
    )
}

/**
 * Extension function to map UiState data.
 */
fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> {
    return when (this) {
        is UiState.Idle -> UiState.Idle
        is UiState.Loading -> UiState.Loading(message, progress)
        is UiState.Success -> UiState.Success(transform(data), message)
        is UiState.Error -> UiState.Error(error, previousData)
    }
}

