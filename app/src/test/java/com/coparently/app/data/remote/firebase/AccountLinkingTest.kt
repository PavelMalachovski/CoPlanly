package com.coparently.app.data.remote.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AdditionalUserInfo
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Linking and unlinking sign-in methods through Android's [FirebaseAccountLinking] (MON-24 phase 4,
 * part A; the plan's review focus 1): a credential another account holds links nothing and leaves
 * the signed-in account as it was, the methods are read from the user's providers, and a Google
 * sign-in ([FirebaseAuthService]) says whether it created the account.
 */
class AccountLinkingTest {

    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val user = mockk<FirebaseUser>(relaxed = true)
    private val googleCredential = mockk<AuthCredential>()
    private val appleTokens = mockk<AppleTokens>()
    private lateinit var service: FirebaseAccountLinking

    @Before
    fun setUp() {
        mockkStatic(GoogleAuthProvider::class)
        every { GoogleAuthProvider.getCredential(any(), any()) } returns googleCredential
        every { auth.currentUser } returns user
        every { user.uid } returns "alice"
        every { user.providerData } returns providers("firebase", "password")
        service = FirebaseAccountLinking(auth, appleTokens)
    }

    @After
    fun tearDown() = unmockkStatic(GoogleAuthProvider::class)

    @Test
    fun credentialInUseLinksNothingAndKeepsSession() = runTest {
        every { user.linkWithCredential(googleCredential) } returns Tasks.forException(
            FirebaseAuthUserCollisionException("ERROR_CREDENTIAL_ALREADY_IN_USE", "in use")
        )

        val result = service.linkGoogle("google-id-token")

        val failure = result.exceptionOrNull()
        assertTrue("expected a LinkException, got $failure", failure is LinkException)
        assertEquals(LinkFailure.InUseByAnotherAccount, (failure as LinkException).failure)
        verify(exactly = 0) { auth.signOut() }
        verify(exactly = 0) { auth.signInWithCredential(any()) }
        assertEquals("alice", auth.currentUser?.uid)
        assertEquals(setOf(SignInMethod.EMAIL), service.linkedMethods().first())
    }

    @Test
    fun linkedMethodsReadsProviders() = runTest {
        every { user.providerData } returns providers("firebase", "password", "google.com", "apple.com", "phone")

        assertEquals(
            setOf(SignInMethod.EMAIL, SignInMethod.GOOGLE, SignInMethod.APPLE),
            service.linkedMethods().first()
        )
    }

    @Test
    fun linkedMethodsIsEmptyWhileSignedOut() = runTest {
        every { auth.currentUser } returns null

        assertEquals(emptySet<SignInMethod>(), service.linkedMethods().first())
    }

    @Test
    fun googleSignInReportsNewUser() = runTest {
        val info = mockk<AdditionalUserInfo> { every { isNewUser } returns true }
        val result = mockk<AuthResult>()
        every { result.user } returns user
        every { result.additionalUserInfo } returns info
        every { auth.signInWithCredential(googleCredential) } returns Tasks.forResult(result)

        val signIn = FirebaseAuthService(auth)
        assertEquals(SignInOutcome(isNewUser = true), signIn.signInWithGoogleIdToken("google-id-token").getOrThrow())
    }

    @Test
    fun theLastMethodIsNeverUnlinked() = runTest {
        val failure = service.unlink(SignInMethod.EMAIL).exceptionOrNull()

        assertEquals(LinkFailure.LastMethod, (failure as LinkException).failure)
        verify(exactly = 0) { user.unlink(any()) }
    }

    @Test
    fun aRecentSignInIsAskedForByName() {
        assertEquals(
            LinkFailure.NeedsRecentSignIn,
            FirebaseAccountLinking.linkFailureOf(
                FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "x")
            )
        )
        assertEquals(
            LinkFailure.InUseByAnotherAccount,
            FirebaseAccountLinking.linkFailureOf(
                FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x")
            )
        )
    }

    private fun providers(vararg ids: String): List<UserInfo> =
        ids.map { id -> mockk<UserInfo> { every { providerId } returns id } }
}
