package com.coparently.app.presentation.common

import com.coparently.app.R
import com.coparently.app.domain.error.AppError

/**
 * The localised sentence a user reads for [AppError], chosen from its type.
 *
 * `AppError.userMessage` stays English and stays in the logs: the domain layer cannot localise
 * it, and the raw exception text some of its variants carry is not something to show a parent
 * (CQ-14). A validation failure gets the generic wording here — a caller that knows which form
 * it came from can map `ValidationError.field` to something more specific first.
 */
fun AppError.toUiText(): UiText = when (this) {
    is AppError.NetworkError ->
        UiText.Res(if (offline) R.string.common_error_offline else R.string.common_error_server)
    is AppError.PermissionError -> UiText.Res(R.string.common_error_permission)
    is AppError.ValidationError -> UiText.Res(R.string.common_error_invalid_input)
    is AppError.SyncError -> UiText.Res(R.string.common_error_sync)
    is AppError.UnknownError -> UiText.Res(R.string.common_error_generic)
}
