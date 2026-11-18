package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's push registration (phase 4 part C, spec §7.2), which replaced `NoPush`: the token is
 * kept on the profile through the queue the sync calls (`PushDevice.updateUserToken`), the switch
 * registers and detaches it, and sign-out's detach deletes the profile's field and the token, in
 * `FcmService`'s order (CLAUDE.md item 23). The token and the permission are fakes: a test process
 * has neither Firebase Messaging nor a notification center.
 */
class IosPushRegistrationTest {

    private val firestore = RecordingFirestoreClient()
    private val signedIn = SignedInAs("alice")
    private val preferences = SealedSecurePreferences(MemoryTextStore())
    private val token = FakeToken("fcm-1") { firestore.writes.size }
    private val permission = FakePermission(granted = true)
    private val registration = IosPushRegistration(firestore, signedIn, preferences, token, permission)
    private val queue: PushDevice = FirestorePushQueue(firestore, signedIn, tokens = registration)

    @Test
    fun `the sync's registration writes the token through the push device`() = runTest {
        val current = queue.getCurrentToken()
        val result = queue.updateUserToken(checkNotNull(current))

        assertTrue(result.isSuccess)
        val write = assertIs<Operation.Set>(firestore.writes.single())
        assertEquals(DocumentPath("users", "alice"), write.path)
        assertEquals(mapOf("fcmToken" to "fcm-1"), write.data)
        assertEquals(SetMerge.Merge, write.merge)
    }

    @Test
    fun `no token is kept while the system does not let the app notify`() = runTest {
        permission.answer = false

        assertTrue(queue.updateUserToken("fcm-1").isSuccess)

        assertEquals(emptyList(), firestore.writes)
        assertFalse(registration.isPushEnabled())
    }

    @Test
    fun `no token is kept once the parent switched pushes off`() = runTest {
        preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")

        assertTrue(queue.updateUserToken("fcm-1").isSuccess)

        assertEquals(emptyList(), firestore.writes)
    }

    @Test
    fun `unregistering deletes the profile's field and then the token`() = runTest {
        registration.unregisterToken()

        val write = assertIs<Operation.UpdateField>(firestore.writes.single())
        assertEquals(DocumentPath("users", "alice"), write.path)
        assertEquals("fcmToken", write.field)
        assertEquals(ServerValue.Delete, write.value)
        assertEquals(1, token.deletions)
        assertEquals(1, token.writesWhenDeleted, "the field is deleted before the token")
    }

    @Test
    fun `a failed field delete still deletes the token`() = runTest {
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE))

        registration.unregisterToken()

        assertEquals(1, token.deletions)
    }

    @Test
    fun `with nobody signed in unregistering touches nothing`() = runTest {
        signedIn.uid = null

        registration.unregisterToken()

        assertEquals(emptyList(), firestore.writes)
        assertEquals(0, token.deletions)
    }

    @Test
    fun `turning the switch off removes the field and the token and keeps it off`() = runTest {
        val result = registration.setPushEnabled(false)

        assertTrue(result.isSuccess)
        assertIs<Operation.UpdateField>(firestore.writes.single())
        assertEquals(1, token.deletions)
        assertEquals("false", preferences.getString(PreferenceKeys.PUSH_ENABLED, null))
        assertFalse(registration.isPushEnabled())
    }

    @Test
    fun `turning the switch on asks for the permission and registers the token`() = runTest {
        preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")
        permission.answer = false
        permission.answerOnRequest = true

        val result = registration.setPushEnabled(true)

        assertTrue(result.isSuccess)
        assertEquals(1, permission.requests)
        assertEquals(mapOf("fcmToken" to "fcm-1"), firestore.lastSet(DocumentPath("users", "alice")))
        assertTrue(registration.isPushEnabled())
    }

    @Test
    fun `turning the switch on fails when the permission is refused`() = runTest {
        permission.answer = false
        permission.answerOnRequest = false

        val result = registration.setPushEnabled(true)

        assertTrue(result.isFailure)
        assertEquals(emptyList(), firestore.writes)
    }

    @Test
    fun `turning the switch on fails when there is no token`() = runTest {
        token.value = null

        val result = registration.setPushEnabled(true)

        assertTrue(result.isFailure)
        assertEquals(emptyList(), firestore.writes)
    }

    @Test
    fun `a token refreshed after unregisterToken is not kept for the same uid`() = runTest {
        registration.unregisterToken()
        // Sign-out is still running: the session ends last, and a fresh token arrives meanwhile.
        preferences.clear()

        assertTrue(queue.updateUserToken("fcm-2").isSuccess)

        assertIs<Operation.UpdateField>(firestore.writes.single())
        assertNull(queue.getCurrentToken())
        assertEquals(0, token.reads)
    }

    @Test
    fun `a new session after a detach keeps its token again`() = runTest {
        registration.unregisterToken()
        registration.sessionChanged("alice")
        assertTrue(queue.updateUserToken("fcm-2").isSuccess)
        assertEquals(1, firestore.writes.size, "the same uid still signed in: still detached")

        registration.sessionChanged(null)
        registration.sessionChanged("alice")
        assertTrue(queue.updateUserToken("fcm-2").isSuccess)

        assertEquals(mapOf("fcmToken" to "fcm-2"), firestore.lastSet(DocumentPath("users", "alice")))
    }

    @Test
    fun `turning pushes on again ends a detach`() = runTest {
        registration.setPushEnabled(false)

        assertTrue(registration.setPushEnabled(true).isSuccess)

        assertEquals(mapOf("fcmToken" to "fcm-1"), firestore.lastSet(DocumentPath("users", "alice")))
    }

    @Test
    fun `nobody signed in keeps nothing and asks Messaging for nothing`() = runTest {
        signedIn.uid = null

        assertTrue(queue.updateUserToken("fcm-1").isSuccess)
        assertNull(queue.getCurrentToken())

        assertEquals(emptyList(), firestore.writes)
        assertEquals(0, token.reads)
    }

    @Test
    fun `with pushes off the token is not even read`() = runTest {
        preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")

        assertNull(queue.getCurrentToken())

        assertEquals(0, token.reads)
    }

    @Test
    fun `the switch is offered on the iPhone`() {
        assertTrue(registration.available)
    }

    /** A [SignedInUser] whose uid the test sets. */
    private class SignedInAs(var uid: String?) : SignedInUser {
        override fun getCurrentUid(): String? = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }

    /** The Swift side's token, answering [value] and counting deletions, with [writesSoFar] at each. */
    private class FakeToken(var value: String?, private val writesSoFar: () -> Int) : IosPushToken {
        var deletions = 0
        var reads = 0
        var writesWhenDeleted: Int? = null

        override fun current(completion: (token: String?, error: String?) -> Unit) {
            reads++
            completion(value, if (value == null) "no token" else null)
        }

        override fun delete(completion: (error: String?) -> Unit) {
            deletions++
            writesWhenDeleted = writesSoFar()
            completion(null)
        }
    }

    /** The system's permission: [answer] when read, [answerOnRequest] when asked. */
    private class FakePermission(granted: Boolean) : NotificationPermission {
        var answer: Boolean = granted
        var answerOnRequest: Boolean = granted
        var requests = 0

        override val granted: Boolean get() = answer

        override suspend fun refresh(): Boolean = answer

        override suspend fun request(): Boolean {
            requests++
            answer = answerOnRequest
            return answer
        }
    }
}
