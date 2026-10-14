package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.AcceptInvitationResult
import com.coparently.app.data.remote.firebase.PairingException
import com.coparently.app.domain.model.InvitePreview
import com.coparently.app.domain.model.PairingError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository's calls: previewing, redeeming and accepting an invitation, unpairing, and the
 * shareable code's reuse and revocation — each delegating to the callables or Firestore as the
 * tests below pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingRepositoryImplCallablesTest : PairingRepositoryImplFixture() {

    // ---- previewInvite --------------------------------------------------

    @Test
    fun `previewInvite rejects a malformed code without calling the backend`() = runTest {
        val result = repository.previewInvite("nope")

        assertEquals(PairingError.NotFound, (result.exceptionOrNull() as PairingException).error)
        coVerify(exactly = 0) { pairingFunctions.previewInvitation(any()) }
    }

    @Test
    fun `previewInvite normalizes the code and passes the name through`() = runTest {
        coEvery { pairingFunctions.previewInvitation("4F7K2M") } returns
            Result.success(InvitePreview(inviterName = "Alice"))

        val result = repository.previewInvite("  4f7k2m ")

        assertEquals(InvitePreview(inviterName = "Alice"), result.getOrNull())
        coVerify(exactly = 0) { pairingFunctions.acceptInvitation(any(), any()) }
    }

    // ---- redeem ---------------------------------------------------------

    @Test
    fun `redeem rejects a malformed code without calling the backend`() = runTest {
        val result = repository.redeem("nope")

        assertTrue(result.isFailure)
        assertEquals(
            PairingError.NotFound,
            (result.exceptionOrNull() as PairingException).error
        )
        coVerify(exactly = 0) { pairingFunctions.acceptInvitation(any(), any()) }
    }

    @Test
    fun `redeem normalizes the code before calling the backend`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.redeem("  4f7k2m ")

        assertTrue(result.isSuccess)
        coVerify { pairingFunctions.acceptInvitation(code = "4F7K2M", invitationId = null) }
    }

    @Test
    fun `redeem surfaces the newly assigned slot from the callable, the same as acceptIncoming`() = runTest {
        // QR scan, manual code entry and deep link all funnel through redeem(), and reach the
        // same acceptPairingInvitation callable as an addressed-invitation accept — this path
        // must carry the role through too, or PairingViewModel has nothing to re-stamp with.
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.redeem("4F7K2M")

        assertEquals("dad", result.getOrNull())
    }

    @Test
    fun `redeem surfaces a null role without failing when the callable does not report one`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = null))

        val result = repository.redeem("4F7K2M")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    // ---- acceptIncoming ----------------------------------------------------

    @Test
    fun `acceptIncoming surfaces the newly assigned slot from the callable`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = "dad"))

        val result = repository.acceptIncoming("invite-1")

        assertEquals("dad", result.getOrNull())
    }

    @Test
    fun `acceptIncoming surfaces a null role without failing when the callable does not report one`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.success(AcceptInvitationResult(partnerId = "user-b", role = null))

        val result = repository.acceptIncoming("invite-1")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `acceptIncoming surfaces the backend error unchanged`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(invitationId = "invite-1") } returns
            Result.failure(PairingException(PairingError.NotFound))

        val result = repository.acceptIncoming("invite-1")

        assertEquals(
            PairingError.NotFound,
            (result.exceptionOrNull() as PairingException).error
        )
    }

    @Test
    fun `redeem surfaces the backend error unchanged`() = runTest {
        coEvery { pairingFunctions.acceptInvitation(code = "4F7K2M") } returns
            Result.failure(PairingException(PairingError.AlreadyPaired))

        val result = repository.redeem("4F7K2M")

        assertEquals(
            PairingError.AlreadyPaired,
            (result.exceptionOrNull() as PairingException).error
        )
    }

    // ---- unpair -----------------------------------------------------------

    @Test
    fun `unpair delegates to the callable`() = runTest {
        coEvery { pairingFunctions.unpair() } returns Result.success("user-b")

        val result = repository.unpair()

        assertTrue(result.isSuccess)
        coVerify { pairingFunctions.unpair() }
    }

    @Test
    fun `unpair treats a null unpairedFrom as success`() = runTest {
        coEvery { pairingFunctions.unpair() } returns Result.success(null)

        val result = repository.unpair()

        assertTrue(result.isSuccess)
    }

    // ---- createOrReuseInviteCode -------------------------------------------

    @Test
    fun `createOrReuseInviteCode reuses an existing unexpired code invite without writing`() = runTest {
        val existing = pendingInviteDoc(
            code = "ABCDEF",
            toEmail = "",
            expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
            createdAt = System.currentTimeMillis()
        )
        stubOwnInvitesQuery(existing)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        assertEquals("ABCDEF", result.getOrNull()?.code)
        verify(exactly = 0) { invitationsCollection.document(any()) }
    }

    @Test
    fun `createOrReuseInviteCode writes a new invite when the existing one is expired`() = runTest {
        val expired = pendingInviteDoc(
            code = "OLDCOD",
            toEmail = "",
            expiresAt = System.currentTimeMillis() - HOUR_MILLIS,
            createdAt = System.currentTimeMillis() - HOUR_MILLIS
        )
        stubOwnInvitesQuery(expired)
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        assertNotEquals("OLDCOD", result.getOrNull()?.code)
        verify { newInviteRef.set(any()) }
    }

    /**
     * Knock-on of the profile gap, not a regression test for the fix itself.
     *
     * `writeNewInvite` takes `fromUserName` from the inviter's own profile document and
     * falls back to the raw email address. That name is what the share text says and what
     * the co-parent's incoming-invitation card shows, so while nothing wrote a profile the
     * invitation was degraded in exactly the same way the partner card was. These two cases
     * pin the dependency: the fallback is only reached when the profile carries no name.
     */
    @Test
    fun `a new invite carries the profile name once the profile has one`() = runTest {
        every { userSnapshot.getString("name") } returns "Alice Novak"
        stubOwnInvitesQuery()
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertEquals("Alice Novak", result.getOrNull()?.fromUserName)
    }

    @Test
    fun `a new invite falls back to the email address when the profile has no name`() = runTest {
        every { userSnapshot.getString("name") } returns null
        stubOwnInvitesQuery()
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertEquals("a@example.com", result.getOrNull()?.fromUserName)
    }

    @Test
    fun `createOrReuseInviteCode ignores an email invitation and picks the newest unexpired code invite`() =
        runTest {
            val emailInvite = pendingInviteDoc(
                code = "EMAIL1",
                toEmail = "other@example.com",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis() + 1
            )
            val expiredCodeInvite = pendingInviteDoc(
                code = "EXPIRD",
                toEmail = "",
                expiresAt = System.currentTimeMillis() - HOUR_MILLIS,
                createdAt = System.currentTimeMillis() - HOUR_MILLIS
            )
            val olderCodeInvite = pendingInviteDoc(
                code = "OLDER1",
                toEmail = "",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis() - 1_000
            )
            val newestCodeInvite = pendingInviteDoc(
                code = "NEWEST",
                toEmail = "",
                expiresAt = System.currentTimeMillis() + HOUR_MILLIS,
                createdAt = System.currentTimeMillis()
            )
            stubOwnInvitesQuery(emailInvite, expiredCodeInvite, olderCodeInvite, newestCodeInvite)

            val result = repository.createOrReuseInviteCode()

            assertTrue(result.isSuccess)
            assertEquals("NEWEST", result.getOrNull()?.code)
            verify(exactly = 0) { invitationsCollection.document(any()) }
        }

    /**
     * A calendar friend's and a professional's codes are written by a paired parent with an
     * empty `toEmail`, like a co-parent code. Reusing one handed that parent the grandmother's
     * code under "Add another co-parent", which the pairing callable then refuses.
     */
    @Test
    fun `createOrReuseInviteCode never reuses a guest, friend or professional code`() = runTest {
        val others = listOf("guest", "friend", "professional").map { kind ->
            pendingInviteDoc(code = kind.take(6).uppercase().padEnd(6, 'X'), toEmail = "").ofKind(kind)
        }
        stubOwnInvitesQuery(*others.toTypedArray())
        val newInviteRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document(any()) } returns newInviteRef
        every { newInviteRef.set(any()) } returns Tasks.forResult<Void>(null)

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isSuccess)
        val code = result.getOrNull()?.code.orEmpty()
        assertFalse(code in setOf("GUESTX", "FRIEND", "PROFES"))
        verify { newInviteRef.set(any()) }
    }

    // ---- revokeActiveInvite -------------------------------------------------

    @Test
    fun `revokeActiveInvite cancels only the code invite, not email invitations`() = runTest {
        val codeInviteRef = mockk<DocumentReference>(relaxed = true)
        val codeInvite = pendingInviteDoc(code = "ABCDEF", toEmail = "", reference = codeInviteRef)
        every { codeInviteRef.update("status", "cancelled") } returns Tasks.forResult<Void>(null)

        val emailInviteRef = mockk<DocumentReference>(relaxed = true)
        val emailInvite = pendingInviteDoc(
            code = "EMAIL1",
            toEmail = "other@example.com",
            reference = emailInviteRef
        )
        stubOwnInvitesQuery(codeInvite, emailInvite)

        val result = repository.revokeActiveInvite()

        assertTrue(result.isSuccess)
        verify { codeInviteRef.update("status", "cancelled") }
        verify(exactly = 0) { emailInviteRef.update(any<String>(), any()) }
    }

    @Test
    fun `revokeActiveInvite leaves guest, friend and professional codes alone`() = runTest {
        val codeInviteRef = mockk<DocumentReference>(relaxed = true)
        val codeInvite = pendingInviteDoc(code = "ABCDEF", toEmail = "", reference = codeInviteRef)
        every { codeInviteRef.update("status", "cancelled") } returns Tasks.forResult<Void>(null)
        val otherRefs = List(3) { mockk<DocumentReference>(relaxed = true) }
        val others = listOf("guest", "friend", "professional").zip(otherRefs) { kind, ref ->
            pendingInviteDoc(code = "GHJKMN", toEmail = "", reference = ref).ofKind(kind)
        }
        stubOwnInvitesQuery(codeInvite, *others.toTypedArray())

        val result = repository.revokeActiveInvite()

        assertTrue(result.isSuccess)
        verify { codeInviteRef.update("status", "cancelled") }
        otherRefs.forEach { ref -> verify(exactly = 0) { ref.update(any<String>(), any()) } }
    }

    // ---- runPairing error normalization -------------------------------------

    @Test
    fun `runPairing normalizes an unexpected Firestore failure to PairingError Unknown`() = runTest {
        val docRef = mockk<DocumentReference>(relaxed = true)
        every { invitationsCollection.document("invite-1") } returns docRef
        every { docRef.update("status", "rejected") } throws
            mockk<FirebaseFirestoreException>(relaxed = true)

        val result = repository.rejectIncoming("invite-1")

        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as PairingException).error is PairingError.Unknown)
    }

    @Test
    fun `runPairing passes a thrown PairingException through unchanged`() = runTest {
        every { authService.getCurrentUser() } returns null

        val result = repository.createOrReuseInviteCode()

        assertTrue(result.isFailure)
        assertEquals(
            PairingError.Unknown("Not signed in"),
            (result.exceptionOrNull() as PairingException).error
        )
    }
}
