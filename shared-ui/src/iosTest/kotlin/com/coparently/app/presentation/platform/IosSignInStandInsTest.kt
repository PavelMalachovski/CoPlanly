package com.coparently.app.presentation.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone's stand-ins for the two capabilities the shell provides beside [PlatformServices]
 * ([ProvideIosShell]) say they are not available, so the sign-in offers neither Google nor a saved
 * password and Settings draws no Google Calendar row (the 11f review's I1), and a call that comes
 * anyway fails rather than pretending.
 */
class IosSignInStandInsTest {

    @Test
    fun `the credential sheets are not available and fail every call`() = runTest {
        assertFalse(NoCredentialSignIn.available)
        assertTrue(NoCredentialSignIn.googleIdToken().isFailure)
        assertTrue(NoCredentialSignIn.savedPassword().isFailure)
    }

    @Test
    fun `Google sign-in is not available and opens nothing`() {
        assertFalse(NoGoogleCalendarSignIn.available)
        assertEquals(GoogleSignInStart.UNAVAILABLE, NoGoogleCalendarSignIn.open())
    }
}
