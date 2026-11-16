package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.Flow

/**
 * The signed-in account's sign-in methods (MON-24 phase 4, part A): what Settings' "Sign-in
 * methods" lists, links and unlinks. Android's is the app's `FirebaseAccountLinking`, the
 * iPhone's `IosAccountLinking`; Apple's unlink goes through the server ([AppleTokens]).
 *
 * Every failure is a [LinkException] naming a [LinkFailure]. Linking a credential another account
 * holds links nothing and never signs out or switches the account, and the account's last method
 * is never removed ([unlinkRefusal]).
 */
interface AccountLinking {

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

    /**
     * Reads the signed-in account's methods again, so [linkedMethods] reports a link or unlink made
     * around this seam: an Apple ID linked through the shell's sheet
     * (`CredentialSignIn.linkApple`), on the iPhone or through Android's web flow. Reloads the user
     * from the server first, on a best-effort basis: a reload that fails still re-reads what the
     * phone holds.
     */
    suspend fun refresh()
}
