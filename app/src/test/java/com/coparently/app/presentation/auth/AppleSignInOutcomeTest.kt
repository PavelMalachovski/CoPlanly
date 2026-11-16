package com.coparently.app.presentation.auth

import com.coparently.app.data.remote.firebase.LinkException
import com.coparently.app.data.remote.firebase.LinkFailure
import com.coparently.app.data.remote.firebase.SignInOutcome
import com.coparently.app.data.session.ProfileIdentity
import com.coparently.app.presentation.platform.AppleSignInCancelledException
import com.coparently.app.presentation.platform.AppleSignInResult
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a sign-in with Apple makes of the name Apple shared (MON-24 phase 4, part A; plan Review
 * Focus 5): the display name it writes, read back through the same [ProfileIdentity] rule a Google
 * name takes, and the failure an address that signs in another way is answered with.
 */
class AppleSignInOutcomeTest {

    private val relayAddress = "x7k2p9q4r1@privaterelay.appleid.com"
    private val appleProvider = ProfileIdentity.ProviderIdentity(
        providerId = "apple.com",
        displayName = null,
        email = relayAddress,
        photoUrl = null
    )

    /** The profile's name for a new Apple account whose display name is [displayName]. */
    private fun profileName(displayName: String?): String? = ProfileIdentity.resolveName(
        displayName = displayName,
        storedRemoteName = null,
        storedLocalName = null,
        email = relayAddress,
        providers = listOf(appleProvider)
    )

    @Test
    fun noNameLeavesProfileNameEmpty() {
        val result = AppleSignInResult(SignInOutcome(isNewUser = true), givenName = null, familyName = null)

        // No name shared: nothing is written to the account's display name.
        assertNull(result.displayNameUpdate)
        // And the relay address is not made a name either, so onboarding's Profile step asks.
        assertNull(profileName(result.displayNameUpdate))
    }

    @Test
    fun aSharedNameIsWrittenAsGivenThenFamily() {
        val result = AppleSignInResult(SignInOutcome(isNewUser = true), givenName = "Jana", familyName = "Nováková")

        assertEquals("Jana Nováková", result.displayNameUpdate)
        assertEquals("Jana Nováková", profileName(result.displayNameUpdate))
    }

    @Test
    fun blankPartsAreLeftOut() {
        val returning = SignInOutcome(isNewUser = false)

        assertEquals("Jana", AppleSignInResult(returning, givenName = " Jana ", familyName = " ").displayNameUpdate)
        assertNull(AppleSignInResult(returning, givenName = "", familyName = "").displayNameUpdate)
    }

    @Test
    fun anAddressThatSignsInAnotherWayIsItsOwnFailure() {
        val error = mockk<FirebaseAuthException> {
            every { errorCode } returns "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
        }

        val failure = AndroidCredentialSignIn.signInFailureOf(error)

        assertTrue(failure is SignsInAnotherWayException)
        assertSame(error, failure.cause)
    }

    @Test
    fun aClosedSignInSheetIsACancel() {
        for (code in listOf("ERROR_WEB_CONTEXT_CANCELED", "ERROR_WEB_CONTEXT_ALREADY_PRESENTED")) {
            val error = authError(code)

            val failure = AndroidCredentialSignIn.signInFailureOf(error)

            assertTrue(code, failure is AppleSignInCancelledException)
            assertSame(error, failure.cause)
        }
    }

    @Test
    fun anyOtherSignInFailureIsTheSdksOwn() {
        val error = authError("ERROR_INTERNAL_ERROR")

        assertSame(error, AndroidCredentialSignIn.signInFailureOf(error))
    }

    @Test
    fun aClosedLinkSheetIsACancel() {
        val failure = AndroidCredentialSignIn.linkFailureOf(authError("ERROR_WEB_CONTEXT_CANCELED"))

        assertTrue(failure is AppleSignInCancelledException)
    }

    @Test
    fun anAppleIdAnotherAccountHoldsLinksNothing() {
        val failure = AndroidCredentialSignIn.linkFailureOf(authError("ERROR_CREDENTIAL_ALREADY_IN_USE"))

        assertEquals(LinkFailure.InUseByAnotherAccount, (failure as LinkException).failure)
    }

    @Test
    fun aPendingResultCountsOnlyAsThisAccountsAppleLink() {
        assertTrue(AndroidCredentialSignIn.isOwnAppleLink(authResult("alice", "password", "apple.com"), "alice"))
        assertFalse(AndroidCredentialSignIn.isOwnAppleLink(authResult("bob", "apple.com"), "alice"))
        assertFalse(AndroidCredentialSignIn.isOwnAppleLink(authResult("alice", "password"), "alice"))
        assertFalse(AndroidCredentialSignIn.isOwnAppleLink(mockk { every { user } returns null }, "alice"))
    }

    @Test
    fun anotherAccountsPendingResultIsSetAsideForAFreshLink() = runTest {
        mockkStatic(OAuthProvider::class)
        try {
            every { OAuthProvider.newBuilder("apple.com") } returns mockk(relaxed = true)
            val alice = user("alice", "password")
            val auth = mockk<FirebaseAuth> {
                every { currentUser } returns alice
                every { pendingAuthResult } returns Tasks.forResult(authResult("bob", "apple.com"))
            }
            every { alice.startActivityForLinkWithProvider(any(), any()) } returns
                Tasks.forResult(authResult("alice", "password", "apple.com"))

            val result = AndroidCredentialSignIn(mockk(relaxed = true), mockk(relaxed = true), auth).linkApple()

            assertTrue(result.isSuccess)
            verify(exactly = 1) { alice.startActivityForLinkWithProvider(any(), any()) }
        } finally {
            unmockkStatic(OAuthProvider::class)
        }
    }

    @Test
    fun thisAccountsPendingLinkIsTakenWithoutAFreshOne() = runTest {
        val alice = user("alice", "password")
        val auth = mockk<FirebaseAuth> {
            every { currentUser } returns alice
            every { pendingAuthResult } returns Tasks.forResult(authResult("alice", "password", "apple.com"))
        }

        val result = AndroidCredentialSignIn(mockk(relaxed = true), mockk(relaxed = true), auth).linkApple()

        assertTrue(result.isSuccess)
        verify(exactly = 0) { alice.startActivityForLinkWithProvider(any(), any()) }
    }

    private fun authError(code: String): FirebaseAuthException = mockk {
        every { errorCode } returns code
    }

    private fun user(uid: String, vararg providers: String): FirebaseUser {
        val infos = providers.map { id -> mockk<UserInfo> { every { providerId } returns id } }
        return mockk(relaxed = true) {
            every { this@mockk.uid } returns uid
            every { providerData } returns infos
        }
    }

    private fun authResult(uid: String, vararg providers: String): AuthResult {
        val signedIn = user(uid, *providers)
        return mockk { every { user } returns signedIn }
    }
}
