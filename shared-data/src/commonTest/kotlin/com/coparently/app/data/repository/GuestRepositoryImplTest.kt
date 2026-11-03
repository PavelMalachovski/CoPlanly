package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.ReadSource
import com.coparently.app.data.remote.firebase.RecordingCallableFunctions
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.model.PairingError
import com.coparently.app.domain.pairing.InviteCodeGenerator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * A child's guest invitation (item 16's guest, MON-24 phase 2, Task 8d): the document the
 * repository writes through [com.coparently.app.data.remote.firebase.FirestoreClient] is the one
 * it wrote through the Android SDK, key for key, and a refusal never reaches Firestore.
 */
class GuestRepositoryImplTest {

    private val firestore = RecordingFirestoreClient()
    private val signedIn = FakeSignedInUser(uid = ME, email = "alice@example.com")
    private val repository = GuestRepositoryImpl(firestore, signedIn, PairingFunctions(RecordingCallableFunctions()))

    @Test
    fun `the invitation names its sender by their profile's name and carries every key`() = runTest {
        firestore.put(DocumentPath("users", ME), mapOf("name" to "Alice"))
        val before = now()

        val invite = repository.inviteGuest(childInfoId = "c1", grantExpiresAtMillis = before + DAY).getOrThrow()

        assertEquals(Operation.Get(DocumentPath("users", ME), ReadSource.DEFAULT), firestore.operations.first())
        val set = firestore.operations.last()
        assertIs<Operation.Set>(set)
        assertEquals(DocumentPath("invitations", invite.id), set.path)
        assertEquals(SetMerge.Overwrite, set.merge)
        val written = set.data
        assertEquals(
            listOf(
                "id", "code", "fromUserId", "fromUserName", "fromUserEmail", "toEmail", "status", "createdAt",
                "expiresAt", "acceptedBy", "kind", "childInfoId", "guestExpiresAt"
            ),
            written.keys.toList()
        )
        assertEquals(invite.id, written["id"])
        assertEquals(invite.code, written["code"])
        assertTrue(InviteCodeGenerator.isValid(invite.code))
        assertEquals(ME, written["fromUserId"])
        assertEquals("Alice", written["fromUserName"])
        assertEquals("alice@example.com", written["fromUserEmail"])
        assertEquals("", written["toEmail"])
        assertEquals("pending", written["status"])
        assertEquals(null, written["acceptedBy"])
        assertTrue(written.containsKey("acceptedBy"))
        assertEquals("guest", written["kind"])
        assertEquals("c1", written["childInfoId"])
        assertEquals(before + DAY, written["guestExpiresAt"])
        val createdAt = written["createdAt"] as Long
        assertTrue(createdAt >= before)
        assertEquals(invite.inviteExpiresAtMillis, written["expiresAt"])
        assertTrue(invite.inviteExpiresAtMillis >= before + WEEK)
        // The id is a random UUID, in its usual text.
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(invite.id))
    }

    @Test
    fun `a sender whose profile has no name is named by their address`() = runTest {
        repository.inviteGuest("c1", now() + DAY).getOrThrow()

        val written = firestore.operations.filterIsInstance<Operation.Set>().single().data
        assertEquals("alice@example.com", written["fromUserName"])
    }

    @Test
    fun `a name that is not text fails the invitation as the SDK's getString did`() = runTest {
        firestore.put(DocumentPath("users", ME), mapOf("name" to 42L))

        val failure = repository.inviteGuest("c1", now() + DAY).exceptionOrNull()

        assertIs<PairingException>(failure)
        assertEquals(PairingError.Unknown("Field 'name' is not a java.lang.String"), failure.error)
        assertTrue(firestore.writes.isEmpty())
    }

    @Test
    fun `an access that has already ended is refused before anything is read`() = runTest {
        val failure = repository.inviteGuest("c1", now() - 1).exceptionOrNull()

        assertEquals(PairingError.GrantEnded, (failure as PairingException).error)
        assertTrue(firestore.operations.isEmpty())
    }

    @Test
    fun `no child record and nobody signed in are refused`() = runTest {
        assertIs<PairingException>(repository.inviteGuest("", now() + DAY).exceptionOrNull())
        signedIn.uid = null
        val failure = repository.inviteGuest("c1", now() + DAY).exceptionOrNull()
        assertEquals(PairingError.Unknown("Not signed in"), (failure as PairingException).error)
        assertTrue(firestore.operations.isEmpty())
    }

    @Test
    fun `a code that cannot be one is refused without a call`() = runTest {
        val failure = repository.acceptGuestInvite("nope").exceptionOrNull()

        assertEquals(PairingError.NotFound, (failure as PairingException).error)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /** The signed-in account, as the platform's sign-in answers for it. */
    class FakeSignedInUser(var uid: String?, var email: String? = null) : SignedInUser {
        override fun getCurrentUid(): String? = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = email
        override fun getCurrentPhotoUrl(): String? = null
    }

    private companion object {
        const val ME = "alice"
        const val DAY = 24L * 60 * 60 * 1000
        const val WEEK = 7 * DAY
    }
}
