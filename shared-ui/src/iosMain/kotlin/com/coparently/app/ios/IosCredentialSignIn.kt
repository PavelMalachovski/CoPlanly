package com.coparently.app.ios

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.GoogleAccessTokens
import com.coparently.app.data.remote.firebase.IosAccountLinking
import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.presentation.auth.GoogleSignInCancelled
import com.coparently.app.presentation.auth.NoSavedPassword
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.AppleSignInResult
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.SavedCredential
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.code
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Google's sign-in sheet, as the iPhone app's Swift side runs it (MON-24 phase 3, Task 9): the app's
 * `GoogleSignInBridge.swift` implements it over Google Sign-In for iOS
 * (`GIDSignIn.sharedInstance.signIn(withPresenting:)`).
 */
interface GoogleIdTokenSource {

    /**
     * Whether the sheet can run: the app carries a `GoogleService-Info.plist` with a `CLIENT_ID` and
     * its reversed client id is one of the app's URL schemes. False in CI and on the emulators,
     * where no such file is bundled.
     */
    val available: Boolean

    /**
     * Opens the sheet and calls [completion] once, on the main queue, with what it ended in: Google's
     * ID token and access token on success; `cancelled` when the parent dismissed it; otherwise an
     * `error` for the log, which no screen shows.
     */
    fun requestIdToken(
        completion: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit
    )

    /**
     * Ends Google Sign-In's own session on this phone (`GIDSignIn.sharedInstance.signOut()`), so the
     * sheet does not offer the previous parent's account first: called when the app signs out
     * (`IosAccountTraces.clearAfterSignOut`) and on the first launch after an install
     * (`clearPreviousInstall`). Does nothing when there is no session or no client id.
     */
    fun signOut()
}

/**
 * The iPhone's [CredentialSignIn] (MON-24 phase 3, Task 9), as `AndroidCredentialSignIn` is
 * Android's: Google's sheet through [google], whose access token it holds in [GoogleAccessTokens] for
 * the Firebase sign-in `AuthViewModel` makes next with the ID token (`IosFirebaseAuth`), since the
 * shared seams carry the ID token alone.
 *
 * Saved passwords are iOS's autofill, so there is no button for them ([offersSavedPasswords] is
 * false), [savedPassword] fails as [NoSavedPassword] and [offerToSave] leaves the offer to the
 * system. What makes iOS offer the Keychain's passwords is the fields' keyboard types
 * (`KeyboardType.Email` and `KeyboardType.Password`), which Compose Multiplatform 1.9.3 turns into
 * the e-mail and password text content types. It ignores the fields' `ContentType` semantics on iOS
 * (CMP-7154), so `NewPassword` on sign-up is not honoured there yet; Android's autofill reads them.
 *
 * Sign in with Apple (MON-24 phase 4, part A) is the system's sheet through [apple] with a fresh
 * [AppleNonce] per request, then Firebase through [appleAccount]. Once Firebase holds the Apple
 * credential, Apple's one-time authorization code goes to the server ([AppleAccount.storeCode]), which
 * keeps the refresh token it revokes on unlink and deletion; a failed store is logged and passed
 * over, because the account works and only that revoke is lost. Apple's name, which it shares at the
 * first authorisation only, is set as the account's display name when it was shared and the account
 * has none; the iOS SDK does not set it, where Android's web flow does.
 *
 * @property appleSignIn The build's flag (`CoplanlyAppleSignIn`): Apple is offered only when it is on.
 */
class IosCredentialSignIn(
    private val google: GoogleIdTokenSource,
    private val apple: AppleIdSource = NoAppleId,
    private val appleSignIn: Boolean = false,
    private val appleAccount: AppleAccount? = null,
) : CredentialSignIn {

    override val available: Boolean get() = google.available

    override val offersSavedPasswords: Boolean get() = false

    override suspend fun googleIdToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        google.requestIdToken { idToken, accessToken, cancelled, error ->
            val result = when {
                cancelled -> Result.failure(GoogleSignInCancelled())
                idToken != null -> {
                    if (accessToken != null) GoogleAccessTokens.hold(idToken, accessToken)
                    Result.success(idToken)
                }
                else -> Result.failure(IllegalStateException(error ?: NO_ID_TOKEN))
            }
            if (continuation.isActive) continuation.resume(result)
        }
    }

    override suspend fun savedPassword(): Result<SavedCredential> = Result.failure(NoSavedPassword())

    override suspend fun offerToSave(email: String, password: String) = Unit

    override val appleAvailable: Boolean
        get() = appleSignIn && apple.available && appleAccount != null

    /**
     * Signs in with Apple. A closed sheet fails with [AppleSignInCancelledException]; an address that
     * already signs in another way (Firebase's `accountExistsWithDifferentCredential`) with
     * [SignsInAnotherWayException]; any other failure is the sheet's or the SDK's own.
     */
    override suspend fun signInWithApple(): Result<AppleSignInResult> = withAppleId { account, nonce, answer ->
        attempt { account.signIn(answer.idToken, nonce.raw) }
            .recoverCatching { e ->
                logger.e(TAG, "Apple sign-in failed: ${codeOf(e)}")
                throw if (codeOf(e) == EXISTS_WITH_DIFFERENT_CREDENTIAL) SignsInAnotherWayException(e) else e
            }
            .map { outcome ->
                val result = AppleSignInResult(outcome, answer.givenName, answer.familyName)
                result.displayNameUpdate?.let { name ->
                    attempt { account.nameIfBlank(name) }
                        .onFailure { logger.e(TAG, "Apple's name was not set on the account: ${codeOf(it)}") }
                }
                storeCode(account, answer.authorizationCode)
                result
            }
    }

    /**
     * Links an Apple ID to the signed-in account. One another account holds fails with a
     * [LinkException] of `LinkFailure.InUseByAnotherAccount` (Firebase's `credentialAlreadyInUse`),
     * mapped as `IosAccountLinking` maps Google's: nothing is linked and the signed-in account stays
     * as it was, with no sign-out and no switch. A closed sheet fails with
     * [AppleSignInCancelledException].
     */
    override suspend fun linkApple(): Result<Unit> = withAppleId { account, nonce, answer ->
        attempt { account.link(answer.idToken, nonce.raw) }
            .recoverCatching { e ->
                logger.e(TAG, "Linking Apple failed: ${codeOf(e)}")
                throw e as? LinkException ?: LinkException(IosAccountLinking.linkFailureOf(e), e)
            }
            .map { storeCode(account, answer.authorizationCode) }
    }

    /**
     * Opens Apple's sheet with a fresh nonce and, when it answered, runs [then] with the Firebase
     * half, the nonce and the answer; otherwise answers the sheet's failure. Without a Firebase half
     * Apple is not offered here, and nothing opens.
     */
    private suspend fun <T> withAppleId(
        then: suspend (account: AppleAccount, nonce: AppleNonce, answer: AppleAnswer) -> Result<T>,
    ): Result<T> {
        val account = appleAccount ?: return Result.failure(UnsupportedOperationException(NOT_OFFERED))
        val nonce = AppleNonce.random()
        return requestAppleId(nonce).fold(onSuccess = { then(account, nonce, it) }, onFailure = { Result.failure(it) })
    }

    /** Opens Apple's sheet for [nonce] and answers what it gave, or the failure it ended in. */
    private suspend fun requestAppleId(nonce: AppleNonce): Result<AppleAnswer> =
        suspendCancellableCoroutine { continuation ->
            apple.requestAppleId(nonce.hashed) { idToken, code, givenName, familyName, cancelled, error ->
                val result = when {
                    cancelled -> Result.failure(AppleSignInCancelledException())
                    idToken != null -> Result.success(AppleAnswer(idToken, code, givenName, familyName))
                    else -> {
                        logger.e(TAG, "Apple's sheet failed: ${error ?: NO_APPLE_TOKEN}")
                        Result.failure(IllegalStateException(error ?: NO_APPLE_TOKEN))
                    }
                }
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /**
     * Hands Apple's authorization [code] to the server, once Firebase holds the credential. Without a
     * code, or when the server refuses it, the account still works and only the revoke is lost: the
     * failure is logged, never shown.
     */
    private suspend fun storeCode(account: AppleAccount, code: String?) {
        if (code == null) {
            logger.w(TAG, "Apple sent no authorization code: no token is kept to revoke")
            return
        }
        attempt { account.storeCode(code).getOrThrow() }
            .onFailure { logger.e(TAG, "Apple's token was not stored: ${codeOf(it)}") }
    }

    /** Runs [call]: its value, or the failure, a coroutine's cancellation rethrown. */
    private inline fun <T> attempt(call: () -> T): Result<T> =
        runCatching(call).onFailure { if (it is CancellationException) throw it }

    /** What Apple's sheet gave: the token, the code (when sent) and the name (when shared). */
    private data class AppleAnswer(
        val idToken: String,
        val authorizationCode: String?,
        val givenName: String?,
        val familyName: String?,
    ) {
        /** Leaves the token, the code and the name out, so a log line never carries them. */
        override fun toString(): String = "AppleAnswer"
    }

    private companion object {
        const val TAG = "IosCredentialSignIn"
        const val NO_ID_TOKEN = "Google's sign-in answered no ID token"
        const val NO_APPLE_TOKEN = "Apple's sheet answered no identity token"
        const val NOT_OFFERED = "Sign in with Apple is not offered here"

        /** Firebase Auth's `accountExistsWithDifferentCredential` in the iOS SDK 11.8.0, as GitLive's text code. */
        const val EXISTS_WITH_DIFFERENT_CREDENTIAL = "17012"

        /**
         * Firebase's error code for [e], what a [LinkException] stood for, or the class name: what is
         * logged, never the message, which can quote an address.
         */
        fun codeOf(e: Throwable): String = when (e) {
            is FirebaseAuthException -> e.code ?: e::class.simpleName.orEmpty()
            is LinkException -> e.failure.toString()
            else -> e::class.simpleName.orEmpty()
        }
    }
}
