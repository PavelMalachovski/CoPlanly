package com.coparently.app.presentation.auth

import com.coparently.app.data.remote.firebase.SignInOutcome
import com.coparently.app.data.session.ProfileIdentity
import com.coparently.app.presentation.platform.AppleSignInResult
import com.coparently.app.presentation.platform.SignsInAnotherWayException
import com.google.firebase.auth.FirebaseAuthException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
    fun anyOtherFailureIsTheSdksOwn() {
        val error = mockk<FirebaseAuthException> {
            every { errorCode } returns "ERROR_WEB_CONTEXT_CANCELED"
        }

        assertSame(error, AndroidCredentialSignIn.signInFailureOf(error))
    }
}
