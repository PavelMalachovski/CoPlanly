package com.coparently.app.presentation.auth

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.code

/**
 * The iPhone's [AuthErrors] (MON-24 phase 3, Task 5): Firebase Auth's failures as GitLive 2.7.0
 * hands them over, mapped as the app's `AndroidAuthErrors` maps the Android SDK's.
 *
 * The iOS SDK answers with an `NSError` in `FIRAuthErrorDomain` whose code is an `AuthErrorCode`, and
 * GitLive turns it into one of its exception classes, copying the code as text into
 * [FirebaseAuthException.code] — except a network failure, which becomes [FirebaseNetworkException]
 * with no code. So the mapping reads the class for the network and the code for the rest, the same
 * codes Android's mapping names by their `ERROR_…` text. Anything else, or a failure that is not
 * Firebase's, is [AuthError.Unknown] on the e-mail path, as on Android.
 *
 * Android's Credential Manager outcomes have two iPhone counterparts since Task 9
 * (`IosCredentialSignIn`): a Google sheet the parent dismisses arrives as [GoogleSignInCancelled],
 * which is no error, and a saved password, which the iPhone's autofill offers in the fields rather
 * than in a sheet, fails as [NoSavedPassword] if it is ever asked for.
 */
object IosAuthErrors : AuthErrors {

    // Firebase Auth's `AuthErrorCode` values (FirebaseAuth/Sources/Swift/Utilities/AuthErrors.swift
    // in the iOS SDK 11.8.0). The Objective-C headers GitLive binds name the error domain but not
    // the codes, so GitLive's own mapping writes them as numbers too.
    private const val INVALID_CREDENTIAL = 17004L
    private const val USER_DISABLED = 17005L
    private const val EMAIL_ALREADY_IN_USE = 17007L
    private const val INVALID_EMAIL = 17008L
    private const val WRONG_PASSWORD = 17009L
    private const val TOO_MANY_REQUESTS = 17010L
    private const val USER_NOT_FOUND = 17011L
    private const val ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL = 17012L
    private const val NETWORK_ERROR = 17020L
    private const val WEAK_PASSWORD = 17026L

    /** A failure of the e-mail and password path: never null, as on Android. */
    override fun fromEmailPassword(cause: Throwable): AuthError = firebaseError(cause) ?: AuthError.Unknown

    /** A failure of the Google path: null when the parent dismissed Google's sheet. */
    override fun fromGoogle(cause: Throwable): AuthError? = when (cause) {
        is GoogleSignInCancelled -> null
        else -> firebaseError(cause) ?: AuthError.Unknown
    }

    /** A failure of the saved-password path, which the iPhone leaves to autofill. */
    override fun fromSavedPassword(cause: Throwable): AuthError? = when (cause) {
        is NoSavedPassword -> AuthError.NoSavedPassword
        else -> firebaseError(cause) ?: AuthError.Unknown
    }

    /** The Firebase reason, or null when [cause] is not Firebase Auth's. */
    private fun firebaseError(cause: Throwable): AuthError? = when (cause) {
        is FirebaseNetworkException -> AuthError.Network
        is FirebaseAuthException -> when (cause.code?.toLongOrNull()) {
            WRONG_PASSWORD, USER_NOT_FOUND, INVALID_CREDENTIAL -> AuthError.InvalidCredentials
            EMAIL_ALREADY_IN_USE, ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL -> AuthError.EmailAlreadyInUse
            WEAK_PASSWORD -> AuthError.WeakPassword
            INVALID_EMAIL -> AuthError.InvalidEmail
            USER_DISABLED -> AuthError.UserDisabled
            TOO_MANY_REQUESTS -> AuthError.TooManyRequests
            NETWORK_ERROR -> AuthError.Network
            else -> AuthError.Unknown
        }
        else -> null
    }
}

/**
 * The parent dismissed Google's sign-in sheet (Google Sign-In's `GIDSignInError.canceled`): what the
 * iPhone's Google sign-in (Task 9) fails with then, which [IosAuthErrors.fromGoogle] calls no error.
 */
class GoogleSignInCancelled : Exception("The Google sign-in was cancelled")

/**
 * No saved password was offered: on the iPhone the system's autofill fills the sign-in fields with
 * one instead of a sheet answering it (`IosCredentialSignIn.savedPassword`, Task 9), so
 * [IosAuthErrors.fromSavedPassword] calls it [AuthError.NoSavedPassword].
 */
class NoSavedPassword : Exception("Saved passwords are offered by autofill in the fields on the iPhone")
