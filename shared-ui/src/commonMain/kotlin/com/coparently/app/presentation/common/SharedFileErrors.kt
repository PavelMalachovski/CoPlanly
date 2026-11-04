package com.coparently.app.presentation.common

import com.coparently.app.data.files.SharedFileIntegrityException
import com.coparently.app.data.files.SharedFileRejectedException
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.resources.Res
import com.coparently.app.resources.shared_file_error_integrity
import com.coparently.app.resources.shared_file_error_size
import com.coparently.app.resources.shared_file_error_type
import org.jetbrains.compose.resources.StringResource

/**
 * The sentence for a shared-file failure: the policy's reason when the file was refused before
 * upload, the integrity sentence when a download did not match its digest, [fallback] otherwise.
 * Never `e.message` (CQ-14).
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 10), out of the app's `SharedFileViewer.kt`, whose
 * `FileProvider` and intents stay with the screens: the ViewModels that word a file's failure call it.
 */
fun sharedFileError(error: Throwable, fallback: StringResource): UiText = when {
    error is SharedFileRejectedException && error.rejection == SharedFilePolicy.Rejection.TYPE ->
        UiText.Res(Res.string.shared_file_error_type)
    error is SharedFileRejectedException -> UiText.Res(Res.string.shared_file_error_size)
    error is SharedFileIntegrityException -> UiText.Res(Res.string.shared_file_error_integrity)
    else -> UiText.Res(fallback)
}
