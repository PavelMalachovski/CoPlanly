package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

/**
 * The Android [AccountLinking] (MON-24 phase 4, part A): the signed-in account's sign-in methods,
 * linked and unlinked through the Firebase Auth SDK, Apple's unlink through the server.
 *
 * A failure is logged with Firebase's error code only, never the exception's message, which can
 * quote an address.
 *
 * @property firebaseAuth Firebase Authentication instance, the one [FirebaseAuthService] signs in with.
 * @property appleTokens The server's Apple token callables, which revoke Apple's token and unlink it.
 */
class FirebaseAccountLinking(
    private val firebaseAuth: FirebaseAuth,
    private val appleTokens: AppleTokens
) : AccountLinking {

    /** Bumped after each link or unlink, so [linkedMethods] reads the providers again. */
    private val providerChanges = MutableStateFlow(0L)

    /** Links the Google account [idToken] names to the signed-in account. */
    override suspend fun linkGoogle(idToken: String): Result<Unit> =
        link(GoogleAuthProvider.getCredential(idToken, null))

    /** Links an e-mail address and a password to the signed-in account. */
    override suspend fun linkEmail(email: String, password: String): Result<Unit> =
        link(EmailAuthProvider.getCredential(email, password))

    /**
     * Links [credential] to the signed-in user. A credential another account holds fails with
     * [LinkFailure.InUseByAnotherAccount]: nothing is linked, and the signed-in account stays as it
     * was — no sign-out, no switch to the other account.
     */
    private suspend fun link(credential: AuthCredential): Result<Unit> {
        val user = firebaseAuth.currentUser ?: return Result.failure(LinkException(LinkFailure.Other))
        return try {
            user.linkWithCredential(credential).await()
            providerChanges.update { it + 1 }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(TAG, "Linking a sign-in method failed: ${codeOf(e)}")
            Result.failure(LinkException(linkFailureOf(e), e))
        }
    }

    /**
     * Removes [method] from the signed-in account; never its last one ([unlinkRefusal]). E-mail and
     * Google go to the SDK's `unlink`; Apple to the server, which revokes Apple's token and unlinks
     * it, after which the user is reloaded so its providers say so.
     */
    override suspend fun unlink(method: SignInMethod): Result<Unit> {
        val user = firebaseAuth.currentUser
        val refusal = unlinkRefusal(user?.let(::methodsOf), method)
        return if (user == null || refusal != null) {
            Result.failure(LinkException(refusal ?: LinkFailure.Other))
        } else {
            unlinkFrom(user, method)
        }
    }

    private suspend fun unlinkFrom(user: FirebaseUser, method: SignInMethod): Result<Unit> = try {
        if (method == SignInMethod.APPLE) {
            appleTokens.unlink().getOrThrow()
            user.reload().await()
        } else {
            user.unlink(method.providerId).await()
        }
        providerChanges.update { it + 1 }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: LinkException) {
        logger.e(TAG, "The server refused to unlink Apple: ${e.failure}")
        Result.failure(e)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(TAG, "Unlinking a sign-in method failed: ${codeOf(e)}")
        // Apple's server unlink may have landed before a failed reload: read the providers again.
        providerChanges.update { it + 1 }
        Result.failure(LinkException(linkFailureOf(e), e))
    }

    /** The signed-in account's methods, read again on every sign-in change and after each link or unlink. */
    override fun linkedMethods(): Flow<Set<SignInMethod>> =
        merge(providerChanges, authChanges())
            .map { methodsOf(firebaseAuth.currentUser) }
            .distinctUntilChanged()

    /** One value each time the sign-in state changes, as `FirebaseAuthService.getAuthStateFlow` reports it. */
    private fun authChanges(): Flow<Unit> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(Unit) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun methodsOf(user: FirebaseUser?): Set<SignInMethod> =
        user?.providerData.orEmpty().mapNotNull { SignInMethod.fromProviderId(it.providerId) }.toSet()

    companion object {
        private const val TAG = "FirebaseAccountLinking"
        private val IN_USE_CODES = setOf("ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_EMAIL_ALREADY_IN_USE")
        private const val RECENT_LOGIN_CODE = "ERROR_REQUIRES_RECENT_LOGIN"

        /** The [LinkFailure] a failed link or unlink [e] stands for, by Firebase's error code. */
        fun linkFailureOf(e: Throwable): LinkFailure = when (codeOf(e)) {
            in IN_USE_CODES -> LinkFailure.InUseByAnotherAccount
            RECENT_LOGIN_CODE -> LinkFailure.NeedsRecentSignIn
            else -> LinkFailure.Other
        }

        /** Firebase's error code for [e], or its class name when it is not Firebase Auth's: what is logged. */
        private fun codeOf(e: Throwable): String =
            (e as? FirebaseAuthException)?.errorCode ?: e::class.simpleName.orEmpty()
    }
}
