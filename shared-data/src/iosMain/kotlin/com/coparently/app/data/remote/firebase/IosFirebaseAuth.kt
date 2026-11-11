package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.logger
import com.coparently.app.data.session.ProfileIdentity
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
 * @property auth GitLive's `FirebaseAuth`, the default app's (`Firebase.auth`), which `IosFirebase`
 *   points at the emulator when the app runs on one.
 */
class IosFirebaseAuth(private val auth: FirebaseAuth) : SignedInUser, AuthSession, AccountSignIn {

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
     * **Fails on the iPhone until Task 9**: GitLive's iOS `GoogleAuthProvider.credential` requires
     * an access token beside the ID token (the iOS SDK's Google credential is made of both), where
     * Android's takes a null one. Asked with the ID token alone, as [AccountSignIn] asks, it throws,
     * and that becomes this call's failure. Nothing on the iPhone calls it yet: the shared screen
     * offers Google only where `CredentialSignIn.available`, which the iPhone's stand-in is not.
     */
    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Any> = attempt {
        checkNotNull(auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null)).user)
    }.onFailure { logger.e(TAG, "Google sign-in failed", it) }

    /** Sends a password reset e-mail. */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = attempt {
        auth.sendPasswordResetEmail(email)
    }

    /** Signs out of Firebase Auth, which removes the user the SDK keeps in the Keychain. */
    override suspend fun signOutCompletely(): Result<Unit> = attempt {
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
    }
}
