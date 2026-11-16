package com.coparently.app.presentation.auth

import com.coparently.app.resources.Res
import com.coparently.app.resources.auth_error_email_already_in_use
import com.coparently.app.resources.auth_error_empty_email
import com.coparently.app.resources.auth_error_empty_fields
import com.coparently.app.resources.auth_error_exists_with_different_method
import com.coparently.app.resources.auth_error_interrupted
import com.coparently.app.resources.auth_error_invalid_credentials
import com.coparently.app.resources.auth_error_invalid_email
import com.coparently.app.resources.auth_error_network
import com.coparently.app.resources.auth_error_no_google_account
import com.coparently.app.resources.auth_error_no_saved_password
import com.coparently.app.resources.auth_error_too_many_requests
import com.coparently.app.resources.auth_error_unknown
import com.coparently.app.resources.auth_error_user_disabled
import com.coparently.app.resources.auth_error_weak_password
import org.jetbrains.compose.resources.StringResource

/**
 * The string resource that explains an [AuthError] to the parent.
 *
 * This is the half of the split that knows about resources. Keeping it out of the ViewModel is
 * what lets that class stay free of a `Context` — the arrangement CLAUDE.md asks for and which
 * the login screen, alone among the redesigned screens, did not have.
 *
 * Deliberately **not** `@Composable`. Returning the resource id rather than the resolved string
 * lets the call site decide when to resolve it, which matters inside `AnimatedVisibility`: its
 * content lambda still runs while the card animates out, at which point the error is already
 * null and there is nothing to resolve.
 *
 * @return the id of the localized message for this error
 */
fun AuthError.messageRes(): StringResource = when (this) {
    AuthError.EmptyFields,
    AuthError.EmptyEmail,
    AuthError.InvalidEmail,
    AuthError.WeakPassword -> formMessageRes()
    AuthError.Interrupted,
    AuthError.NoGoogleAccount,
    AuthError.NoSavedPassword -> sheetMessageRes()
    AuthError.InvalidCredentials -> Res.string.auth_error_invalid_credentials
    AuthError.EmailAlreadyInUse -> Res.string.auth_error_email_already_in_use
    AuthError.UserDisabled -> Res.string.auth_error_user_disabled
    AuthError.Network -> Res.string.auth_error_network
    AuthError.TooManyRequests -> Res.string.auth_error_too_many_requests
    AuthError.ExistsWithDifferentMethod -> Res.string.auth_error_exists_with_different_method
    AuthError.Unknown -> Res.string.auth_error_unknown
}

/**
 * The message for what the typed form itself got wrong. [messageRes] routes only those four here,
 * and its own `when` stays exhaustive, so a new [AuthError] cannot fall into the `else`.
 */
private fun AuthError.formMessageRes(): StringResource = when (this) {
    AuthError.EmptyFields -> Res.string.auth_error_empty_fields
    AuthError.EmptyEmail -> Res.string.auth_error_empty_email
    AuthError.InvalidEmail -> Res.string.auth_error_invalid_email
    AuthError.WeakPassword -> Res.string.auth_error_weak_password
    else -> Res.string.auth_error_unknown
}

/** The message for how a credential sheet ended; [messageRes] routes only those three here. */
private fun AuthError.sheetMessageRes(): StringResource = when (this) {
    AuthError.Interrupted -> Res.string.auth_error_interrupted
    AuthError.NoGoogleAccount -> Res.string.auth_error_no_google_account
    AuthError.NoSavedPassword -> Res.string.auth_error_no_saved_password
    else -> Res.string.auth_error_unknown
}
