package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import com.coparently.app.data.session.ProfileIdentity
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
 * Firebase Authentication on the iPhone (MON-24 phase 3, Task 5), over GitLive's
 * `dev.gitlive.firebase.auth`: the iPhone's [SignedInUser], [AuthSession] and [AccountSignIn], as
 * the app's `FirebaseAuthService` is Android's. It mirrors that class call for call; where GitLive
 * cannot say what the Android SDK call did, the member says so.
 *
 * The data layer and the shared sign-in screen reach it only through those three interfaces; the
 * signed-in user GitLive keeps in the Keychain, as the iOS SDK always does. That session outlives the
 * app's deletion, where Android's goes with the app, so the first launch after a reinstall signs it
 * out before the first screen (`clearPreviousInstall`, `data/session/PreviousInstall.kt`).
 *
 * Since MON-24 phase 4 (part A) it links and unlinks the account's sign-in methods as Android does,
 * Apple's unlink through the server ([AppleTokens]).
 *
 * @property auth GitLive's `FirebaseAuth`, the default app's (`Firebase.auth`), which `IosFirebase`
 *   points at the emulator when the app runs on one.
 * @property appleTokens The server's Apple token callables; null in the tests that build this class
 *   by hand, which then fails Apple's unlink.
 */
class IosFirebaseAuth(
    private val auth: FirebaseAuth,
    private val appleTokens: AppleTokens? = null,
) : SignedInUser, AuthSession, AccountSignIn {

    /** Bumped after each link or unlink, so [linkedMethods] reads the providers again. */
    private val providerChanges = MutableStateFlow(0L)

    /** The current user's Firebase UID. */
    override fun getCurrentUid(): String? = auth.currentUser?.uid

    /** The current user's display name, as the account carries it. */
    override fun getCurrentDisplayName(): String? = auth.currentUser?.displayName

    /** The current user's e-mail address, as the account carries it. */
    override fun getCurrentEmail(): String? = auth.currentUser?.email

    /** The address of the current user's own picture, as the account carries it. */
    override fun getCurrentPhotoUrl(): String? = auth.currentUser?.photoURL

    /**
     * The signed-in uid each time the sign-in state changes: GitLive's `authStateChanged`, the iOS
     * SDK's state listener, which (as Android's `AuthStateListener`) reports the current state as
     * soon as it is registered.
     */
    override fun observeCurrentUid(): Flow<String?> = auth.authStateChanged.map { it?.uid }

    /**
     * The current user's identity as plain values, read from one [FirebaseUser], each
     * `providerData` entry projected as [ProfileIdentity] reads it.
     */
    override fun getCurrentAccount(): SignedInAccount? = auth.currentUser?.let { user ->
        SignedInAccount(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoURL,
            isAnonymous = user.isAnonymous,
            providers = user.providerData.map { info ->
                ProfileIdentity.ProviderIdentity(
                    providerId = info.providerId,
                    displayName = info.displayName,
                    email = info.email,
                    photoUrl = info.photoURL
                )
            }
        )
    }

    /** Signs in with an e-mail address and a password; the user on success, the failure otherwise. */
    override suspend fun signInWithEmail(email: String, password: String): Result<Any> = attempt {
        checkNotNull(auth.signInWithEmailAndPassword(email, password).user)
    }

    /**
     * Creates an account with an e-mail address and a password, which signs it in; the user on
     * success. A failure is logged, as Android logs it; Android also logs the project id and the API
     * key's first characters first, which on the iPhone `IosFirebase` fixes per configuration.
     */
    override suspend fun createAccountWithEmail(email: String, password: String): Result<Any> = attempt {
        checkNotNull(auth.createUserWithEmailAndPassword(email, password).user)
    }.onFailure { logger.e(TAG, "Account creation failed", it) }

    /**
     * Signs in with a Google ID token.
     *
     * GitLive's iOS `GoogleAuthProvider.credential` requires Google's access token beside the ID
     * token (the iOS SDK's Google credential is made of both), where Android's takes a null one. The
     * iPhone's credential sheet holds the access token it was answered with in [GoogleAccessTokens],
     * and it is taken from there only for the ID token it came with. Without a matching one the
     * credential throws, and that becomes this call's failure. The outcome says whether Firebase
     * created the account at this sign-in (GitLive's `additionalUserInfo`, the iOS SDK's).
     */
    override suspend fun signInWithGoogleIdToken(idToken: String): Result<SignInOutcome> {
        val accessToken = GoogleAccessTokens.take(idToken)
        return attempt {
            val result = auth.signInWithCredential(GoogleAuthProvider.credential(idToken, accessToken))
            checkNotNull(result.user)
            SignInOutcome(isNewUser = result.additionalUserInfo?.isNewUser == true)
        }.onFailure { logger.e(TAG, "Google sign-in failed", it) }
    }

    /**
     * Links the Google account [idToken] names, with the access token the credential sheet held for
     * it ([GoogleAccessTokens]), as [signInWithGoogleIdToken] signs in with it.
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
            logger.e(TAG, "Linking a sign-in method failed", e)
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
            checkNotNull(appleTokens) { "Apple's token callables are not bound here" }.unlink().getOrThrow()
            user.reload()
        } else {
            user.unlink(method.providerId)
        }
        providerChanges.update { it + 1 }
    }.recoverCatching { e ->
        logger.e(TAG, "Unlinking a sign-in method failed", e)
        // Apple's server unlink may have landed before a failed reload: read the providers again.
        providerChanges.update { it + 1 }
        throw if (e is LinkException) e else LinkException(linkFailureOf(e), e)
    }

    /** The signed-in account's methods, read again on every sign-in change and after each link or unlink. */
    override fun linkedMethods(): Flow<Set<SignInMethod>> =
        merge(providerChanges, auth.authStateChanged)
            .map { methodsOf(auth.currentUser) }
            .distinctUntilChanged()

    private fun methodsOf(user: FirebaseUser?): Set<SignInMethod> =
        user?.providerData.orEmpty().mapNotNull { SignInMethod.fromProviderId(it.providerId) }.toSet()

    /** Sends a password reset e-mail. */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = attempt {
        auth.sendPasswordResetEmail(email)
    }

    /**
     * Signs out of Firebase Auth, which removes the user the SDK keeps in the Keychain, and drops a
     * Google access token still held for a sign-in that never came ([GoogleAccessTokens]).
     */
    override suspend fun signOutCompletely(): Result<Unit> = attempt {
        GoogleAccessTokens.clear()
        auth.signOut()
    }.onFailure { logger.e(TAG, "Error signing out: ${it.message}", it) }

    /**
     * The signed-in uid once Firebase is ready, or null.
     *
     * Android's `waitForAuthReady` polls up to five seconds for the app's project id to be set and
     * then reads the current user. On the iPhone there is nothing to wait for: `IosFirebase`
     * configures the app before the Koin graph exists, and the iOS SDK restores the saved user from
     * the Keychain when Auth is created, so the current user is read at once. (Before the phone's
     * first unlock the Keychain cannot be read; the SDK then reports nobody and signs the user in
     * again when protected data becomes available, which [observeCurrentUid] reports.)
     */
    override suspend fun awaitSignedInUid(): String? = getCurrentUid()

    /**
     * Runs [call] as `FirebaseAuthService` runs each SDK call: its value, or the exception as a
     * failure. Unlike Android's `catch (e: Exception)`, a coroutine's cancellation is rethrown
     * rather than reported as a failed sign-in.
     */
    private inline fun <T> attempt(call: () -> T): Result<T> =
        runCatching(call).onFailure { if (it is CancellationException) throw it }

    private companion object {
        const val TAG = "IosFirebaseAuth"

        // Firebase Auth's `AuthErrorCode` values in the iOS SDK 11.8.0, which GitLive copies as text
        // into `FirebaseAuthException.code` (see `IosAuthErrors`).
        const val EMAIL_ALREADY_IN_USE = 17007L
        const val REQUIRES_RECENT_LOGIN = 17014L
        const val CREDENTIAL_ALREADY_IN_USE = 17025L

        /** The [LinkFailure] a failed link or unlink [e] stands for, by Firebase's error code. */
        fun linkFailureOf(e: Throwable): LinkFailure = when ((e as? FirebaseAuthException)?.code?.toLongOrNull()) {
            CREDENTIAL_ALREADY_IN_USE, EMAIL_ALREADY_IN_USE -> LinkFailure.InUseByAnotherAccount
            REQUIRES_RECENT_LOGIN -> LinkFailure.NeedsRecentSignIn
            else -> LinkFailure.Other
        }
    }
}
