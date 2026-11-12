package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.GoogleAccessTokens
import com.coparently.app.presentation.auth.AuthError
import com.coparently.app.presentation.auth.IosAuthErrors
import com.coparently.app.presentation.auth.NoSavedPassword
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's [IosCredentialSignIn] (MON-24 phase 3, Task 9) over a fake [GoogleIdTokenSource]: what
 * Google's sheet answers becomes the result `AuthViewModel` folds, and the access token the sheet
 * hands over beside the ID token waits in [GoogleAccessTokens] for the Firebase sign-in that follows.
 * The real sheet is the owner's to check on a Mac; the emulator half of the pairing is
 * `IosFirebaseAuthEmulatorTest` in `shared-data`.
 */
class IosCredentialSignInTest {

    @AfterTest
    fun forgetTheTokens() {
        GoogleAccessTokens.clear()
    }

    @Test
    fun `a token from the sheet is the result`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(idToken = ID_TOKEN, accessToken = ACCESS_TOKEN)))

        assertEquals(ID_TOKEN, signIn.googleIdToken().getOrNull())
    }

    @Test
    fun `the access token waits for the matching ID token and is used once`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(idToken = ID_TOKEN, accessToken = ACCESS_TOKEN)))
        signIn.googleIdToken()

        assertEquals(ACCESS_TOKEN, GoogleAccessTokens.take(ID_TOKEN))
        assertNull(GoogleAccessTokens.take(ID_TOKEN))
    }

    @Test
    fun `a stale access token is not used for another ID token`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(idToken = ID_TOKEN, accessToken = ACCESS_TOKEN)))
        signIn.googleIdToken()

        assertNull(GoogleAccessTokens.take("another-id-token"))
        assertNull(GoogleAccessTokens.take(ID_TOKEN))
    }

    @Test
    fun `a dismissed sheet is a failure that is no error`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(cancelled = true)))

        val failure = signIn.googleIdToken().exceptionOrNull()

        assertNull(IosAuthErrors.fromGoogle(checkNotNull(failure)))
        assertNull(GoogleAccessTokens.take(ID_TOKEN))
    }

    @Test
    fun `an error from the sheet is an unknown failure`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(error = "The network went away")))

        val failure = signIn.googleIdToken().exceptionOrNull()

        assertEquals(AuthError.Unknown, IosAuthErrors.fromGoogle(checkNotNull(failure)))
    }

    @Test
    fun `an answer with no ID token is an unknown failure`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(accessToken = ACCESS_TOKEN)))

        val failure = signIn.googleIdToken().exceptionOrNull()

        assertEquals(AuthError.Unknown, IosAuthErrors.fromGoogle(checkNotNull(failure)))
        assertNull(GoogleAccessTokens.take(ID_TOKEN))
    }

    @Test
    fun `available mirrors the source`() {
        assertTrue(IosCredentialSignIn(FakeSource(Answer(), available = true)).available)
        assertFalse(IosCredentialSignIn(FakeSource(Answer(), available = false)).available)
    }

    @Test
    fun `saved passwords are never offered and fail as none saved`() = runTest {
        val signIn = IosCredentialSignIn(FakeSource(Answer(), available = true))

        assertFalse(signIn.offersSavedPasswords)
        val failure = signIn.savedPassword().exceptionOrNull()
        assertIs<NoSavedPassword>(failure)
        assertEquals(AuthError.NoSavedPassword, IosAuthErrors.fromSavedPassword(failure))
    }

    /** What the fake sheet answers. */
    private data class Answer(
        val idToken: String? = null,
        val accessToken: String? = null,
        val cancelled: Boolean = false,
        val error: String? = null,
    )

    /** A Google sheet that answers [answer] at once, as the Swift bridge answers on the main queue. */
    private class FakeSource(private val answer: Answer, override val available: Boolean = true) : GoogleIdTokenSource {
        override fun requestIdToken(
            completion: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit
        ) = completion(answer.idToken, answer.accessToken, answer.cancelled, answer.error)
    }

    private companion object {
        const val ID_TOKEN = "an-id-token"
        const val ACCESS_TOKEN = "an-access-token"
    }
}
