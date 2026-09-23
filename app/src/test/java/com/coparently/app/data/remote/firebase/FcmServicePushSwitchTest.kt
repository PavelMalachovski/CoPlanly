package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Settings push switch used to change the switch and nothing else: the token stayed on the
 * profile, app start re-registered it anyway, and every push kept arriving. These pin the part
 * that makes "off" stick — while it is off, no registration path writes a token.
 */
class FcmServicePushSwitchTest {

    private val stored = mutableMapOf<String, String>()
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val authService = mockk<FirebaseAuthService>()
    private lateinit var service: FcmService

    @Before
    fun setUp() {
        val prefs = mockk<EncryptedPreferences> {
            every { getString(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
            val key = slot<String>()
            val value = slot<String>()
            every { putString(capture(key), capture(value)) } answers { stored[key.captured] = value.captured }
        }
        // No signed-in user: unregisterToken() returns before touching the network, which keeps
        // this about the stored choice rather than Firebase's task plumbing.
        every { authService.getCurrentUser() } returns null
        service = FcmService(mockk<FirebaseMessaging>(relaxed = true), firestore, authService, prefs)
    }

    @Test
    fun `push is on until somebody switches it off`() {
        assertTrue(service.isPushEnabled())
    }

    @Test
    fun `while off, registering a token writes nothing`() = runTest {
        service.setPushEnabled(false)

        assertFalse(service.isPushEnabled())
        assertTrue(service.updateUserToken("token-from-app-start").isSuccess)
        verify(exactly = 0) { firestore.collection(any()) }
        assertTrue(stored[PreferenceKeys.PUSH_ENABLED] == "false")
    }
}
