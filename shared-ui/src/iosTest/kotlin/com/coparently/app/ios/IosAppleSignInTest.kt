package com.coparently.app.ios

import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInOutcome
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import dev.gitlive.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sign in with Apple through [IosCredentialSignIn] (MON-24 phase 4, part A), over a fake sheet and a
 * fake Firebase half: the nonce, the order of Firebase and the code exchange, the name, and how each
 * failure reaches the screen. The real sheet is the owner's to check on a phone.
 */
class IosAppleSignInTest {

    @Test
    fun `Apple is offered only with the flag on`() {
        assertTrue(signIn(FakeApple(Answer()), FakeAccount(), flag = true).appleAvailable)
        assertFalse(signIn(FakeApple(Answer()), FakeAccount(), flag = false).appleAvailable)
        val noSheet = IosCredentialSignIn(NoGoogleSheet, NoAppleId, appleSignIn = true, appleAccount = FakeAccount())
        assertFalse(noSheet.appleAvailable)
    }

    @Test
    fun `Firebase gets the raw nonce whose hash Apple got`() = runTest {
        val apple = FakeApple(Answer(idToken = ID_TOKEN, code = CODE))
        val account = FakeAccount()

        signIn(apple, account).signInWithApple().getOrThrow()

        assertEquals(sha256Hex(checkNotNull(account.rawNonce)), apple.hashedNonce)
        assertEquals(ID_TOKEN, account.idToken)
    }

    @Test
    fun `a sign-in stores the code after Firebase holds the credential`() = runTest {
        val account = FakeAccount(outcome = SignInOutcome(isNewUser = true))

        val result = signIn(FakeApple(Answer(idToken = ID_TOKEN, code = CODE)), account).signInWithApple().getOrThrow()

        assertTrue(result.outcome.isNewUser)
        assertEquals(listOf("signIn", "store:$CODE"), account.calls)
    }

    @Test
    fun `a shared name is set on the account`() = runTest {
        val account = FakeAccount()
        val apple = FakeApple(Answer(idToken = ID_TOKEN, code = CODE, given = "Jana", family = "Nováková"))

        val result = signIn(apple, account).signInWithApple().getOrThrow()

        assertEquals("Jana", result.givenName)
        assertEquals("Nováková", result.familyName)
        assertEquals(listOf("signIn", "name:Jana Nováková", "store:$CODE"), account.calls)
    }

    @Test
    fun `no name leaves the account's name alone`() = runTest {
        val account = FakeAccount()

        val result = signIn(FakeApple(Answer(idToken = ID_TOKEN, code = CODE)), account).signInWithApple().getOrThrow()

        assertNull(result.displayNameUpdate)
        assertEquals(listOf("signIn", "store:$CODE"), account.calls)
    }

    @Test
    fun `a failed store keeps the sign-in`() = runTest {
        val account = FakeAccount(storeFails = true)

        val result = signIn(FakeApple(Answer(idToken = ID_TOKEN, code = CODE)), account).signInWithApple()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `a closed sheet is a cancel and reaches Firebase not at all`() = runTest {
        val account = FakeAccount()

        val failure = signIn(FakeApple(Answer(cancelled = true)), account).signInWithApple().exceptionOrNull()

        assertIs<AppleSignInCancelledException>(failure)
        assertTrue(account.calls.isEmpty())
    }

    @Test
    fun `an address that signs in another way is the generic failure`() = runTest {
        val account = FakeAccount(signInError = FirebaseAuthException("exists", "17012"))
        val apple = FakeApple(Answer(idToken = ID_TOKEN, code = CODE))

        val failure = signIn(apple, account).signInWithApple().exceptionOrNull()

        assertIs<SignsInAnotherWayException>(failure)
        assertEquals(listOf("signIn"), account.calls)
    }

    @Test
    fun `another Firebase failure is passed on`() = runTest {
        val error = FirebaseAuthException("network", "17020")
        val account = FakeAccount(signInError = error)
        val apple = FakeApple(Answer(idToken = ID_TOKEN, code = CODE))

        val failure = signIn(apple, account).signInWithApple().exceptionOrNull()

        assertEquals(error, failure)
    }

    @Test
    fun `a link stores the code after Firebase holds the credential`() = runTest {
        val account = FakeAccount()

        signIn(FakeApple(Answer(idToken = ID_TOKEN, code = CODE)), account).linkApple().getOrThrow()

        assertEquals(listOf("link", "store:$CODE"), account.calls)
    }

    @Test
    fun `an Apple ID another account holds links nothing`() = runTest {
        val account = FakeAccount(linkError = FirebaseAuthException("in use", "17025"))

        val failure = signIn(FakeApple(Answer(idToken = ID_TOKEN, code = CODE)), account).linkApple().exceptionOrNull()

        assertEquals(LinkFailure.InUseByAnotherAccount, assertIs<LinkException>(failure).failure)
        assertEquals(listOf("link"), account.calls)
    }

    @Test
    fun `a closed sheet while linking is a cancel`() = runTest {
        val failure = signIn(FakeApple(Answer(cancelled = true)), FakeAccount()).linkApple().exceptionOrNull()

        assertIs<AppleSignInCancelledException>(failure)
    }

    private fun signIn(apple: AppleIdSource, account: AppleAccount, flag: Boolean = true) =
        IosCredentialSignIn(NoGoogleSheet, apple, appleSignIn = flag, appleAccount = account)

    /** What the fake sheet answers. */
    private data class Answer(
        val idToken: String? = null,
        val code: String? = null,
        val given: String? = null,
        val family: String? = null,
        val cancelled: Boolean = false,
    )

    /** An Apple sheet that answers [answer] at once and remembers the nonce it was asked with. */
    private class FakeApple(private val answer: Answer) : AppleIdSource {
        var hashedNonce: String? = null

        override val available: Boolean = true

        override fun requestAppleId(
            hashedNonce: String,
            completion: (
                idToken: String?,
                authorizationCode: String?,
                givenName: String?,
                familyName: String?,
                cancelled: Boolean,
                error: String?,
            ) -> Unit
        ) {
            this.hashedNonce = hashedNonce
            completion(answer.idToken, answer.code, answer.given, answer.family, answer.cancelled, null)
        }
    }

    /** The Firebase half, recording each call in order. */
    private class FakeAccount(
        private val outcome: SignInOutcome = SignInOutcome(isNewUser = false),
        private val signInError: Throwable? = null,
        private val linkError: Throwable? = null,
        private val storeFails: Boolean = false,
    ) : AppleAccount {
        val calls = mutableListOf<String>()
        var idToken: String? = null
        var rawNonce: String? = null

        override suspend fun signIn(idToken: String, rawNonce: String): SignInOutcome {
            calls += "signIn"
            this.idToken = idToken
            this.rawNonce = rawNonce
            signInError?.let { throw it }
            return outcome
        }

        override suspend fun link(idToken: String, rawNonce: String) {
            calls += "link"
            linkError?.let { throw it }
        }

        override suspend fun nameIfBlank(displayName: String) {
            calls += "name:$displayName"
        }

        override suspend fun storeCode(code: String): Result<Unit> {
            calls += "store:$code"
            return if (storeFails) Result.failure(LinkException(LinkFailure.Other)) else Result.success(Unit)
        }
    }

    /** No Google here: these tests are Apple's. */
    private object NoGoogleSheet : GoogleIdTokenSource {
        override val available: Boolean = false

        override fun requestIdToken(
            completion: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit
        ) = completion(null, null, false, "no Google here")

        override fun signOut() = Unit

        override fun disconnect() = Unit

        override val calendarAvailable: Boolean = false

        override fun requestCalendarAccess(
            scope: String,
            completion: (String?, String?, String?, Boolean, String?) -> Unit
        ) = completion(null, null, null, false, "no Google here")
    }

    private companion object {
        const val ID_TOKEN = "an-apple-id-token"
        const val CODE = "an-authorization-code"
    }
}
