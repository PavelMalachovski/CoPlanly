@file:OptIn(ExperimentalAtomicApi::class)

package com.coparently.app.data.remote.firebase

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The access token Google's sign-in sheet answered beside its ID token, held on the iPhone for the
 * Firebase sign-in that follows (MON-24 phase 3, Task 9).
 *
 * GitLive's iOS `GoogleAuthProvider.credential` needs both tokens, because the iOS SDK's Google
 * credential is made of both, while the shared seams (`CredentialSignIn.googleIdToken`,
 * [AccountSignIn.signInWithGoogleIdToken]) carry the ID token alone, which is all Android's
 * credential needs. So the iPhone's credential sheet ([hold]) leaves the pair here, and
 * [IosFirebaseAuth] [take]s the access token back when it is asked to sign in with the same ID
 * token. Android is untouched: nothing there reads this.
 *
 * **Lifetime.** One pair at a time, in memory only, never written anywhere. [take] empties the
 * holder whatever it answers, so a pair is used at most once, and a pair whose ID token does not
 * match the one being signed in with is dropped rather than used. A sign-out ([clear]) drops it too.
 */
object GoogleAccessTokens {

    private val held = AtomicReference<HeldPair?>(null)

    /** Holds [accessToken] for the sign-in with [idToken], replacing any pair held before. */
    fun hold(idToken: String, accessToken: String) {
        held.store(HeldPair(idToken, accessToken))
    }

    /**
     * The access token held for [idToken], or null when none is held or the one held belongs to
     * another ID token. Either way the holder is empty afterwards.
     */
    fun take(idToken: String): String? = held.exchange(null)?.takeIf { it.idToken == idToken }?.accessToken

    /** Drops whatever is held. */
    fun clear() {
        held.store(null)
    }

    /** A pair of tokens; [toString] leaves both out, so a log line never carries them. */
    private class HeldPair(val idToken: String, val accessToken: String) {
        override fun toString(): String = "HeldPair"
    }
}
