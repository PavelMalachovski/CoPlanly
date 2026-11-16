package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.Flow

/**
 * Signing in to the app's own account (MON-24 phase 2, Task 11): what the sign-in screen's
 * `AuthViewModel` asks of Firebase Auth, which stays in the app.
 *
 * The app's `FirebaseAuthService` answers it on Android, each call the one it made before the
 * ViewModel moved; the iPhone's stand-in signs nobody in yet (phase 3). Every failure is a
 * `Result.failure` carrying the SDK's own exception, which the screen's `AuthErrors` words; a
 * coroutine cancellation may arrive the same way, and the caller rethrows it.
 *
 * Since MON-24 phase 4 (part A) it also links and unlinks the account's sign-in methods. Those
 * calls fail with a [LinkException] naming a [LinkFailure]; linking a credential another account
 * holds links nothing and never signs out or switches the account, and the account's last method
 * is never removed.
 */
interface AccountSignIn {

    /** Signs in with an email address and a password; the value is the platform's signed-in user. */
    suspend fun signInWithEmail(email: String, password: String): Result<Any>

    /** Creates an account with an email address and a password, and signs it in. */
    suspend fun createAccountWithEmail(email: String, password: String): Result<Any>

    /**
     * Signs in with the id token Google's own sign-in handed back; the outcome says whether
     * Firebase created the account at this sign-in.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<SignInOutcome>

    /** Sends a password-reset link to [email]. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    /** Links the Google account [idToken] names to the signed-in account. */
    suspend fun linkGoogle(idToken: String): Result<Unit>

    /** Links an e-mail address and a password to the signed-in account. */
    suspend fun linkEmail(email: String, password: String): Result<Unit>

    /**
     * Removes [method] from the signed-in account, refusing its last method with
     * [LinkFailure.LastMethod]. Apple goes through the server, which revokes Apple's token first.
     */
    suspend fun unlink(method: SignInMethod): Result<Unit>

    /**
     * The signed-in account's methods, read from its providers whenever the sign-in state changes
     * and again after each link or unlink; empty while nobody is signed in.
     */
    fun linkedMethods(): Flow<Set<SignInMethod>>
}
