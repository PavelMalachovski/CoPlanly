package com.coparently.app.presentation.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The iPhone's one remaining stand-in among the capabilities the shell provides beside
 * [PlatformServices] ([ProvideIosShell]): Google Calendar's sign-in says it is not available, so
 * Settings draws no Google Calendar row (the 11f review's I1), and a call that comes anyway opens
 * nothing. The credential sheets stopped being a stand-in in Task 9: they are the app's own
 * `IosCredentialSignIn`, unavailable without Google's configuration (`IosCredentialSignInTest`).
 */
class IosSignInStandInsTest {

    @Test
    fun `Google sign-in is not available and opens nothing`() {
        assertFalse(NoGoogleCalendarSignIn.available)
        assertEquals(GoogleSignInStart.UNAVAILABLE, NoGoogleCalendarSignIn.open())
    }
}
