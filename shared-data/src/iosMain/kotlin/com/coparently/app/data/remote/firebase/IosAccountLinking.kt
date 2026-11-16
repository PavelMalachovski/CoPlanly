package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.code
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * The iPhone's [AccountLinking] (MON-24 phase 4, part A), over GitLive's `dev.gitlive.firebase.auth`,
 * as the app's `FirebaseAccountLinking` is Android's: the signed-in account's sign-in methods, linked
 * and unlinked through Firebase Auth, Apple's unlink through the server.
 *
 * A failure is logged with Firebase's error code only, never the exception's message, which can
 * quote an address.
 *
 * @property auth GitLive's `FirebaseAuth`, the one [IosFirebaseAuth] signs in with.
 * @property appleTokens The server's Apple token callables, which revoke Apple's token and unlink it.
 */
class IosAccountLinking(
    private val auth: FirebaseAuth,
    private val appleTokens: AppleTokens,
) : AccountLinking {

    /** Bumped after each link or unlink, so [linkedMethods] reads the providers again. */
    private val providerChanges = MutableStateFlow(0L)

    /**
     * Links the Google account [idToken] names, with the access token the credential sheet held for
     * it ([GoogleAccessTokens]), as [IosFirebaseAuth.signInWithGoogleIdToken] signs in with it.
     */
    override suspend fun linkGoogle(idToken: String): Result<Unit> {
        val accessToken = GoogleAccessTokens.take(idToken)
        return link { GoogleAuthProvider.credential(idToken, accessToken) }
    }

    /** Links an e-mail address and a password to the signed-in account. */
    override suspend fun linkEmail(email: String, password: String): Result<Unit> =
        link { EmailAuthProvider.credential(email, password) }

    /**
     * Links the credential [make] builds to the signed-in user. A credential another account holds
     * fails with [LinkFailure.InUseByAnotherAccount]: nothing is linked, and the signed-in account
     * stays as it was — no sign-out, no switch to the other account.
     */
    private suspend fun link(make: () -> AuthCredential): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(LinkException(LinkFailure.Other))
        return attempt {
            user.linkWithCredential(make())
            providerChanges.update { it + 1 }
        }.recoverCatching { e ->
            logger.e(TAG, "Linking a sign-in method failed: ${codeOf(e)}")
            throw LinkException(linkFailureOf(e), e)
        }
    }

    /**
     * Removes [method] from the signed-in account; never its last one ([unlinkRefusal]). E-mail and
     * Google go to the SDK's `unlink`; Apple to the server, which revokes Apple's token and unlinks
     * it, after which the user is reloaded so its providers say so.
     */
    override suspend fun unlink(method: SignInMethod): Result<Unit> {
        val user = auth.currentUser
        val refusal = unlinkRefusal(user?.let(::methodsOf), method)
        return if (user == null || refusal != null) {
            Result.failure(LinkException(refusal ?: LinkFailure.Other))
        } else {
            unlinkFrom(user, method)
        }
    }

    private suspend fun unlinkFrom(user: FirebaseUser, method: SignInMethod): Result<Unit> = attempt {
        if (method == SignInMethod.APPLE) {
            appleTokens.unlink().getOrThrow()
            user.reload()
        } else {
            user.unlink(method.providerId)
        }
        providerChanges.update { it + 1 }
    }.recoverCatching { e ->
        val failure = if (e is LinkException) e else LinkException(linkFailureOf(e), e)
        logger.e(TAG, "Unlinking a sign-in method failed: ${if (e is LinkException) e.failure else codeOf(e)}")
        // Apple's server unlink may have landed before a failed reload: read the providers again.
        providerChanges.update { it + 1 }
        throw failure
    }

    /** The signed-in account's methods, read again on every sign-in change and after each link or unlink. */
    override fun linkedMethods(): Flow<Set<SignInMethod>> =
        merge(providerChanges, auth.authStateChanged)
            .map { methodsOf(auth.currentUser) }
            .distinctUntilChanged()

    /**
     * Reloads the signed-in user and reads its methods again, for a link made around this class
     * (Apple through the system's sheet, `IosCredentialSignIn.linkApple`, which links on the iOS
     * SDK's own user). A reload that fails is logged by its code and the providers the phone holds
     * are read anyway.
     */
    override suspend fun refresh() {
        auth.currentUser?.let { user ->
            attempt { user.reload() }.onFailure { e ->
                logger.w(TAG, "Reloading the user before reading its methods failed: ${codeOf(e)}")
            }
        }
        providerChanges.update { it + 1 }
    }

    private fun methodsOf(user: FirebaseUser?): Set<SignInMethod> =
        user?.providerData.orEmpty().mapNotNull { SignInMethod.fromProviderId(it.providerId) }.toSet()

    /** Runs [call] as `IosFirebaseAuth` does: its value, or the failure, a cancellation rethrown. */
    private inline fun <T> attempt(call: () -> T): Result<T> =
        runCatching(call).onFailure { if (it is CancellationException) throw it }

    companion object {
        private const val TAG = "IosAccountLinking"

        // Firebase Auth's `AuthErrorCode` values in the iOS SDK 11.8.0, which GitLive copies as text
        // into `FirebaseAuthException.code` (see `IosAuthErrors`).
        private const val EMAIL_ALREADY_IN_USE = 17007L
        private const val REQUIRES_RECENT_LOGIN = 17014L
        private const val CREDENTIAL_ALREADY_IN_USE = 17025L

        /**
         * The [LinkFailure] a failed link or unlink [e] stands for, by Firebase's error code; also
         * how the iPhone's Apple link (`IosCredentialSignIn.linkApple`, in `:shared-ui`) words one.
         */
        fun linkFailureOf(e: Throwable): LinkFailure = when (codeOf(e).toLongOrNull()) {
            CREDENTIAL_ALREADY_IN_USE, EMAIL_ALREADY_IN_USE -> LinkFailure.InUseByAnotherAccount
            REQUIRES_RECENT_LOGIN -> LinkFailure.NeedsRecentSignIn
            else -> LinkFailure.Other
        }

        /** Firebase's error code for [e], or its class name when it is not Firebase Auth's: what is logged. */
        private fun codeOf(e: Throwable): String =
            (e as? FirebaseAuthException)?.code ?: e::class.simpleName.orEmpty()
    }
}
