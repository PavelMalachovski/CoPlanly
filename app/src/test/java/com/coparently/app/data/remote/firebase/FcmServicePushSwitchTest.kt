package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Settings push switch used to change the switch and nothing else: the token stayed on the
 * profile, app start re-registered it anyway, and every push kept arriving. These pin the part
 * that makes "off" stick — while it is off, no registration path writes a token — and what the
 * switch does to `users/{uid}.fcmToken` either way (`docs/DEVICE-CHECKLIST.md` §3.7's first two
 * boxes, as far as they are this device's writes rather than the Firebase console's view of them).
 */
class FcmServicePushSwitchTest {

    private val stored = mutableMapOf<String, String>()
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val authService = mockk<FirebaseAuthService>()
    private lateinit var prefs: EncryptedPreferences
    private lateinit var service: FcmService

    @Before
    fun setUp() {
        prefs = mockk<EncryptedPreferences> {
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

    @Test
    fun `turning push off removes the token from the profile and deletes it on the device`() = runTest {
        val profile = signedInProfile()
        val removed = slot<Any>()
        every { profile.update("fcmToken", capture(removed)) } returns Tasks.forResult(null)
        val messaging = mockk<FirebaseMessaging> { every { deleteToken() } returns Tasks.forResult(null) }

        assertTrue(signedInService(messaging).setPushEnabled(false).isSuccess)

        verify(exactly = 1) { profile.update("fcmToken", any()) }
        assertEquals(FieldValue.delete().javaClass, removed.captured.javaClass)
        verify(exactly = 1) { messaging.deleteToken() }
        assertEquals(false.toString(), stored[PreferenceKeys.PUSH_ENABLED])
    }

    @Test
    fun `turning push on again writes this device's token back to the profile`() = runTest {
        val profile = signedInProfile()
        val written = slot<Any>()
        every { profile.set(capture(written), any<SetOptions>()) } returns Tasks.forResult(null)
        val messaging = mockk<FirebaseMessaging> { every { token } returns Tasks.forResult(TOKEN) }
        stored[PreferenceKeys.PUSH_ENABLED] = false.toString()

        assertTrue(signedInService(messaging).setPushEnabled(true).isSuccess)

        assertEquals(mapOf("fcmToken" to TOKEN), written.captured)
        assertEquals(true.toString(), stored[PreferenceKeys.PUSH_ENABLED])
    }

    /** `users/{UID}`, with [UID] signed in. */
    private fun signedInProfile(): DocumentReference {
        every { authService.getCurrentUser() } returns mockk<FirebaseUser> { every { uid } returns UID }
        val users = mockk<CollectionReference>()
        val profile = mockk<DocumentReference>()
        every { firestore.collection("users") } returns users
        every { users.document(UID) } returns profile
        return profile
    }

    private fun signedInService(messaging: FirebaseMessaging) = FcmService(messaging, firestore, authService, prefs)

    private companion object {
        const val UID = "alice-uid"
        const val TOKEN = "fresh-device-token"
    }
}
