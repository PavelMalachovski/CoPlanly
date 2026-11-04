package com.coparently.app.presentation.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException

/**
 * The Android [AuthErrors] (MON-24 phase 2, slice 11f): Firebase Auth's codes and Credential
 * Manager's exceptions, mapped as [AuthError]'s companion mapped them before the type moved to
 * `:shared-ui` (the three functions below are those, unchanged, as extensions of the companion).
 */
object AndroidAuthErrors : AuthErrors {
    override fun fromEmailPassword(cause: Throwable): AuthError = AuthError.fromEmailPassword(cause)

    override fun fromGoogle(cause: Throwable): AuthError? = AuthError.fromGoogle(cause)

    override fun fromSavedPassword(cause: Throwable): AuthError? = AuthError.fromSavedPassword(cause)
}

/**
 * Maps a failure of the email/password path.
 *
 * Never null: typing an email and a password has no "user changed their mind" outcome,
 * so every failure here is worth reporting.
 */
fun AuthError.Companion.fromEmailPassword(cause: Throwable): AuthError = firebaseError(cause) ?: AuthError.Unknown

/**
 * Maps a failure of the Google path.
 *
 * @return null when the user dismissed Google's sheet. A deliberate cancel is not an
 *   error, and callers must clear the displayed error rather than substitute [AuthError.Unknown].
 */
fun AuthError.Companion.fromGoogle(cause: Throwable): AuthError? = when (cause) {
    is GetCredentialCancellationException -> null
    is GetCredentialInterruptedException -> AuthError.Interrupted
    is NoCredentialException -> AuthError.NoGoogleAccount
    else -> firebaseError(cause) ?: AuthError.Unknown
}

/**
 * Maps a failure of the saved-password path.
 *
 * `NoCredentialException` reaches this function and [fromGoogle] alike and means
 * something different in each — "nothing saved yet" here, "no Google account" there.
 * That is why the two are separate entry points rather than one shared mapping.
 *
 * @return null when the user dismissed the sheet, on the same contract as [fromGoogle].
 */
fun AuthError.Companion.fromSavedPassword(cause: Throwable): AuthError? = when (cause) {
    is GetCredentialCancellationException -> null
    is GetCredentialInterruptedException -> AuthError.Interrupted
    is NoCredentialException -> AuthError.NoSavedPassword
    else -> firebaseError(cause) ?: AuthError.Unknown
}

/** @return the Firebase-shaped reason, or null when [cause] is not from Firebase. */
private fun firebaseError(cause: Throwable): AuthError? = when {
    cause is FirebaseNetworkException -> AuthError.Network
    cause is FirebaseAuthException -> when (cause.errorCode) {
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND",
        "ERROR_INVALID_CREDENTIAL" -> AuthError.InvalidCredentials
        "ERROR_EMAIL_ALREADY_IN_USE",
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthError.EmailAlreadyInUse
        "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
        "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
        "ERROR_USER_DISABLED" -> AuthError.UserDisabled
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TooManyRequests
        else -> AuthError.Unknown
    }
    else -> null
}
