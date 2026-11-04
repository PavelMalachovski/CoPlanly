package com.coparently.app.presentation.auth

/**
 * Why an authentication attempt did not succeed, as a type rather than a sentence.
 *
 * The screen used to keep an English `String` in its UI state, assembled inside the ViewModel.
 * That is the one shape CLAUDE.md rules out for localizable text, and it forbids the obvious
 * shortcut too: a ViewModel must not be handed a `Context` to solve it. Carrying the *reason*
 * instead lets `AuthError.messageRes()` name a string resource the composable resolves, where
 * `stringResource` is legal.
 *
 * In `:shared-ui` since MON-24 phase 2 (slice 11f). Which reason an SDK's exception is — Firebase
 * Auth's codes, Credential Manager's cancellations — is the platform's, through [AuthErrors]: on
 * Android the app's `AndroidAuthErrors`, the mappings this type's companion held (`fromEmailPassword`,
 * `fromGoogle`, `fromSavedPassword`, extensions of [Companion] there now).
 */
sealed interface AuthError {

    /** Email or password left blank. Caught before any network call. */
    data object EmptyFields : AuthError

    /** Password reset requested with a blank email field. Caught before any network call. */
    data object EmptyEmail : AuthError

    /** Wrong password, unknown account, or a credential Firebase would not accept. */
    data object InvalidCredentials : AuthError

    /** Sign-up against an email that already has an account. */
    data object EmailAlreadyInUse : AuthError

    /** Firebase rejected the password as too weak. */
    data object WeakPassword : AuthError

    /** The email is not a well-formed address. */
    data object InvalidEmail : AuthError

    /** The account exists but has been disabled in the Firebase console. */
    data object UserDisabled : AuthError

    /** The device could not reach Firebase. Retrying later is the fix. */
    data object Network : AuthError

    /** Firebase is rate-limiting this account or device. */
    data object TooManyRequests : AuthError

    /** The credential sheet was torn down by something other than the user. Retrying works. */
    data object Interrupted : AuthError

    /** Google's flow produced no account. Distinct from [NoSavedPassword]. */
    data object NoGoogleAccount : AuthError

    /** No password has been saved for this app yet. Distinct from [NoGoogleAccount]. */
    data object NoSavedPassword : AuthError

    /** Anything the mapping does not recognise. */
    data object Unknown : AuthError

    /** The platform's mappings extend it (on Android, the app's `AndroidAuthErrors.kt`). */
    companion object
}
