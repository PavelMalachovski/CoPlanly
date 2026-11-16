package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.AppleTokens
import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInOutcome
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.OAuthProvider

/**
 * What a Sign in with Apple does with Apple's answer once the sheet has closed (MON-24 phase 4, part
 * A): the Firebase half, which [IosCredentialSignIn] calls after [AppleIdSource]. Each call throws
 * the SDK's own failure, which [IosCredentialSignIn] maps.
 */
interface AppleAccount {

    /** Signs in to Firebase with Apple's [idToken] and the [rawNonce] its request was hashed from. */
    suspend fun signIn(idToken: String, rawNonce: String): SignInOutcome

    /** Links Apple's [idToken] to the signed-in account; with nobody signed in, a [LinkException]. */
    suspend fun link(idToken: String, rawNonce: String)

    /**
     * Sets [displayName] as the signed-in account's display name, only while the account has none:
     * Apple shares a name at the first authorisation only, and an account that already carries one
     * keeps it.
     */
    suspend fun nameIfBlank(displayName: String)

    /**
     * Has the server exchange Apple's one-time authorization [code] and keep the refresh token, which
     * it revokes when Apple is unlinked or the account deleted ([AppleTokens.store]). Called only
     * once Firebase holds the Apple credential: the server checks the code's Apple ID against it.
     */
    suspend fun storeCode(code: String): Result<Unit>
}

/**
 * The iPhone's [AppleAccount], over GitLive's `FirebaseAuth` (the default app's, which
 * `IosFirebase` points at the emulators when the app runs on them) and the server's [AppleTokens].
 *
 * The credential is GitLive's `OAuthProvider.credential` for `apple.com` with the ID token and the
 * raw nonce and no access token, which on iOS is the SDK's
 * `credentialWithProviderID:IDToken:rawNonce:`.
 */
internal class GitLiveAppleAccount(
    private val auth: FirebaseAuth,
    private val appleTokens: AppleTokens,
) : AppleAccount {

    override suspend fun signIn(idToken: String, rawNonce: String): SignInOutcome {
        val result = auth.signInWithCredential(credential(idToken, rawNonce))
        checkNotNull(result.user)
        return SignInOutcome(isNewUser = result.additionalUserInfo?.isNewUser == true)
    }

    override suspend fun link(idToken: String, rawNonce: String) {
        val user = auth.currentUser ?: throw LinkException(LinkFailure.Other)
        user.linkWithCredential(credential(idToken, rawNonce))
    }

    override suspend fun nameIfBlank(displayName: String) {
        val user = auth.currentUser ?: return
        if (user.displayName.isNullOrBlank()) {
            user.updateProfile(displayName = displayName, photoUrl = user.photoURL)
        }
    }

    override suspend fun storeCode(code: String): Result<Unit> = appleTokens.store(code, CLIENT_ID)

    private fun credential(idToken: String, rawNonce: String): AuthCredential =
        OAuthProvider.credential(providerId = PROVIDER_ID, idToken = idToken, rawNonce = rawNonce)

    private companion object {
        /** Firebase Auth's provider id for Apple. */
        const val PROVIDER_ID = "apple.com"

        /** The client Apple issued the code to: the app's bundle id (`APPLE_BUNDLE_ID` on the server). */
        const val CLIENT_ID = "app.coplanly"
    }
}
