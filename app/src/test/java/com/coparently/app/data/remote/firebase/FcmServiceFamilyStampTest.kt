package com.coparently.app.data.remote.firebase

import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Every client push names the family it belongs to (M-8), so the tap on the other phone can
 * switch to that family before it opens anything. The stamp is made in one place, from the two
 * uids the push travels between, because a pair *is* a family.
 */
class FcmServiceFamilyStampTest {

    private val queue = mockk<CollectionReference>()
    private val firestore = mockk<FirebaseFirestore>()
    private val authService = mockk<FirebaseAuthService>()
    private val written = slot<Map<String, Any>>()
    private lateinit var service: FcmService

    @Before
    fun setUp() {
        every { firestore.collection("notification_queue") } returns queue
        every { queue.add(capture(written)) } returns Tasks.forResult(mockk<DocumentReference>())
        every { authService.getCurrentUser() } returns
            mockk<FirebaseUser> { every { uid } returns ALICE }
        service = FcmService(
            mockk<FirebaseMessaging>(relaxed = true),
            firestore,
            authService,
            mockk<EncryptedPreferences>(relaxed = true)
        )
    }

    @Test
    fun `a push to the co-parent carries the family the two of them share`() = runTest {
        service.queueNotificationForUser(BOB, mapOf(PushPayload.TYPE to PushPayload.EVENT_CREATED))

        assertEquals("alice-uid__bob-uid", payload()[PushPayload.FAMILY_ID])
        assertEquals(PushPayload.EVENT_CREATED, payload()[PushPayload.TYPE])
    }

    @Test
    fun `a push to oneself names no family`() = runTest {
        // The rule accepts a self-addressed push, and a family of one is not a family.
        service.queueNotificationForUser(ALICE, mapOf(PushPayload.TYPE to PushPayload.EVENT_CREATED))

        assertFalse(PushPayload.FAMILY_ID in payload())
    }

    @Test
    fun `a family the caller already named is kept`() = runTest {
        service.queueNotificationForUser(
            BOB,
            mapOf(
                PushPayload.TYPE to PushPayload.EVENT_CREATED,
                PushPayload.FAMILY_ID to "alice-uid__bob-uid"
            )
        )

        assertEquals("alice-uid__bob-uid", payload()[PushPayload.FAMILY_ID])
    }

    @Suppress("UNCHECKED_CAST")
    private fun payload(): Map<String, String> = written.captured["data"] as Map<String, String>

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
    }
}
