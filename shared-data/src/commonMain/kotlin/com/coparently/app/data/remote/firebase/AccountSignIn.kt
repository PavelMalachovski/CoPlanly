package com.coparently.app.data.remote.firebase

/**
 * Signing in to the app's own account (MON-24 phase 2, Task 11): what the sign-in screen's
 * `AuthViewModel` asks of Firebase Auth, which stays in the app.
 *
 * The app's `FirebaseAuthService` answers it on Android, each call the one it made before the
 * ViewModel moved; the iPhone's stand-in signs nobody in yet (phase 3). Every failure is a
 * `Result.failure` carrying the SDK's own exception, which the screen's `AuthErrors` words; a
 * coroutine cancellation may arrive the same way, and the caller rethrows it.
 */
interface AccountSignIn {

    /** Signs in with an email address and a password; the value is the platform's signed-in user. */
    suspend fun signInWithEmail(email: String, password: String): Result<Any>

    /** Creates an account with an email address and a password, and signs it in. */
    suspend fun createAccountWithEmail(email: String, password: String): Result<Any>

    /** Signs in with the id token Google's own sign-in handed back. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Any>

    /** Sends a password-reset link to [email]. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
