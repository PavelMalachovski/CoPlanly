package com.coparently.app.presentation.auth

import android.app.Activity
import com.coparently.app.BuildConfig
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirebaseAccountLinking
import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInOutcome
import com.coparently.app.data.remote.google.CredentialAuthenticator
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.AppleSignInResult
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.SavedCredential
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The Android [CredentialSignIn] (MON-24 phase 2, slice 11f): [CredentialAuthenticator]'s three
 * Credential Manager calls on [activity], which hosts their sheets, as `AuthViewModel` called them
 * with the Activity it was handed before it moved to `:shared-ui`.
 *
 * Sign in with Apple (MON-24 phase 4, part A) is Firebase Auth's web flow on [activity]
 * (`OAuthProvider` for `apple.com`, with the `email` and `name` scopes), whose return URL is
 * Firebase's own handler. **No Apple token is stored on Android**: the web flow hands the app no
 * authorization code, so there is nothing for `AppleTokens.store` to exchange, and an Apple ID
 * added here has no token for the server to revoke; it is still unlinked and deleted with the
 * Firebase account (spec §3.8 call 1). Apple's name, which it shares at the first authorisation
 * only, is written to the account's display name by Firebase itself, so [signInWithApple] answers
 * no name parts and nothing more is written; `ProfileIdentity` reads that display name as it reads
 * a Google one, and a parent who shared none is asked by onboarding's Profile step.
 *
 * `MainActivity` builds it in its own composition, so the Activity is held no longer than the screen
 * it hosts; the ViewModel is handed this on every call and keeps nothing.
 *
 * @property firebaseAuth The Firebase Auth instance the app signs in with, the web flow's host.
 * @property appleAvailable Whether Apple is offered: the build's `APPLE_SIGN_IN` flag.
 */
class AndroidCredentialSignIn(
    private val activity: Activity,
    private val credentialAuthenticator: CredentialAuthenticator,
    private val firebaseAuth: FirebaseAuth,
    override val appleAvailable: Boolean = BuildConfig.APPLE_SIGN_IN,
) : CredentialSignIn {

    override val available: Boolean = true

    override suspend fun googleIdToken(): Result<String> = credentialAuthenticator.signInWithGoogle(activity)

    override suspend fun savedPassword(): Result<SavedCredential> =
        credentialAuthenticator.getSavedPassword(activity).map { saved ->
            SavedCredential(email = saved.email, password = saved.password)
        }

    override suspend fun offerToSave(email: String, password: String) =
        credentialAuthenticator.savePassword(activity, email, password)

    /**
     * Signs in with Apple through Firebase's web flow. A result the flow left pending (the activity
     * was recreated while the browser was open) is taken first, as Firebase asks. A closed sheet
     * fails with [AppleSignInCancelledException], an address that signs in another way with
     * [SignsInAnotherWayException]; any other failure is the SDK's own.
     */
    override suspend fun signInWithApple(): Result<AppleSignInResult> = try {
        val flow = firebaseAuth.pendingAuthResult
            ?: firebaseAuth.startActivityForSignInWithProvider(activity, appleProvider())
        val result = flow.await()
        checkNotNull(result.user)
        Result.success(
            AppleSignInResult(
                outcome = SignInOutcome(isNewUser = result.additionalUserInfo?.isNewUser == true),
                givenName = null,
                familyName = null
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(TAG, "Apple sign-in failed: ${codeOf(e)}")
        Result.failure(signInFailureOf(e))
    }

    /**
     * Links an Apple ID to the signed-in account through Firebase's web flow. One another account
     * holds fails with [LinkFailure.InUseByAnotherAccount], mapped as `FirebaseAccountLinking` maps
     * Google's: nothing is linked and the signed-in account stays as it was. A closed sheet fails
     * with [AppleSignInCancelledException].
     *
     * A result the flow left pending is accepted only when it is this account's Apple link
     * ([isOwnAppleLink]): after a recreation it may be a sign-in, or another account's, and is then
     * set aside and a fresh link started.
     */
    override suspend fun linkApple(): Result<Unit> {
        val user = firebaseAuth.currentUser ?: return Result.failure(LinkException(LinkFailure.Other))
        val uid = user.uid
        return try {
            if (!pendingLinkLanded(uid)) {
                user.startActivityForLinkWithProvider(activity, appleProvider()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(TAG, "Linking Apple failed: ${codeOf(e)}")
            Result.failure(linkFailureOf(e))
        }
    }

    /**
     * Whether a result the web flow left pending is this account's own Apple link ([isOwnAppleLink]).
     * A pending result that is something else, or that failed, is set aside: the caller starts a
     * fresh link.
     */
    private suspend fun pendingLinkLanded(uid: String): Boolean {
        val pending = firebaseAuth.pendingAuthResult ?: return false
        return try {
            isOwnAppleLink(pending.await(), uid)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.w(TAG, "A pending Apple result was set aside: ${codeOf(e)}")
            false
        }
    }

    companion object {
        private const val TAG = "AndroidCredentialSignIn"

        /** Firebase Auth's provider id for Apple. */
        private const val APPLE_PROVIDER_ID = "apple.com"

        /**
         * Firebase's codes for a web flow the parent closed, or one that could not open because
         * another was already showing: neither is an error to show.
         */
        private val CANCELLED_CODES = setOf("ERROR_WEB_CONTEXT_CANCELED", "ERROR_WEB_CONTEXT_ALREADY_PRESENTED")

        /** Firebase's code for a sign-in whose address already signs in another way. */
        private const val EXISTS_WITH_DIFFERENT_CREDENTIAL = "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"

        /** Apple's provider for the web flow, asking for the address and the name. */
        private fun appleProvider(): OAuthProvider =
            OAuthProvider.newBuilder(APPLE_PROVIDER_ID).setScopes(listOf("email", "name")).build()

        /**
         * The failure an Apple sign-in [e] is answered with: [AppleSignInCancelledException] for a
         * closed sheet, [SignsInAnotherWayException] when the address already signs in another way,
         * the SDK's own exception otherwise.
         */
        fun signInFailureOf(e: Throwable): Throwable = when (codeOf(e)) {
            in CANCELLED_CODES -> AppleSignInCancelledException(e)
            EXISTS_WITH_DIFFERENT_CREDENTIAL -> SignsInAnotherWayException(e)
            else -> e
        }

        /**
         * The failure an Apple link [e] is answered with: [AppleSignInCancelledException] for a closed
         * sheet, otherwise a [LinkException] mapped as `FirebaseAccountLinking` maps Google's.
         */
        fun linkFailureOf(e: Throwable): Throwable =
            if (codeOf(e) in CANCELLED_CODES) {
                AppleSignInCancelledException(e)
            } else {
                LinkException(FirebaseAccountLinking.linkFailureOf(e), e)
            }

        /** Whether [result] is an Apple link of the account [uid]: the same user, now holding `apple.com`. */
        fun isOwnAppleLink(result: AuthResult, uid: String): Boolean {
            val user = result.user ?: return false
            return user.uid == uid && user.providerData.any { it.providerId == APPLE_PROVIDER_ID }
        }

        /** Firebase's error code for [e], or its class name: what is logged, never the message. */
        private fun codeOf(e: Throwable): String =
            (e as? FirebaseAuthException)?.errorCode ?: e::class.simpleName.orEmpty()
    }
}
