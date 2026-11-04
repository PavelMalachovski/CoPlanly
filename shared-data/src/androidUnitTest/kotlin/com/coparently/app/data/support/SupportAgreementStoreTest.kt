package com.coparently.app.data.support

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.ReadSource
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Child support (CLAUDE.md item 49) through the Firestore seam since MON-24 phase 2 (Task 8e): a
 * first proposal creates the document, a later one updates it, an acceptance copies the terms and
 * deletes the proposal, and a failed listener or server read says it could not read.
 */
class SupportAgreementStoreTest {

    private val firestore = RecordingFirestoreClient()
    private var signedIn: String? = ALICE
    private val auth = mockk<SignedInUser> { every { getCurrentUid() } answers { signedIn } }
    private val userRepository = mockk<UserRepository> {
        coEvery { getCurrentUser() } returns mockk(relaxed = true) { every { name } returns "Alice" }
    }
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)
    private val store = SupportAgreementStore(firestore, auth, userRepository, announcer)
    private val path = DocumentPath("support_agreements", FAMILY)
    private val terms = SupportTerms(500_000L, "CZK", 5, BOB, YearMonth(2026, 10))

    @Test
    fun `a first proposal creates the document with its participants`() = runTest {
        assertTrue(store.propose(FAMILY, terms, existing = null))

        val set = firestore.operations.filterIsInstance<Operation.Set>().single()
        assertEquals(path, set.path)
        assertEquals(SetMerge.Overwrite, set.merge)
        assertEquals(listOf("participants", "proposal", "lastModifiedBy"), set.data.keys.toList())
        assertEquals(listOf(ALICE, BOB), set.data["participants"])
        assertEquals(ALICE, set.data["lastModifiedBy"])
        @Suppress("UNCHECKED_CAST")
        val proposal = set.data["proposal"] as Map<String, Any?>
        assertEquals(
            SupportAgreementDocument.termsMap(terms) + ("proposedBy" to ALICE),
            proposal - "proposedAtMillis"
        )
        assertTrue(proposal["proposedAtMillis"] is Long)
        coVerify(exactly = 1) {
            announcer.announce(match { it.kind == ActivityKind.SUPPORT_PROPOSED }, "Alice", false, BOB)
        }
    }

    @Test
    fun `a later proposal updates the document and the co-parent's waiting one is not overwritten`() = runTest {
        firestore.put(path, mapOf("participants" to listOf(ALICE, BOB)))
        val existing = SupportAgreement(FAMILY, listOf(ALICE, BOB))

        assertTrue(store.propose(FAMILY, terms, existing))
        val update = firestore.operations.filterIsInstance<Operation.Update>().single()
        assertEquals(listOf("proposal", "lastModifiedBy"), update.fields.keys.toList())

        val theirs = existing.copy(proposal = SupportProposal(terms, BOB, 1L))
        assertFalse(store.propose(FAMILY, terms, theirs))
        assertEquals(1, firestore.writes.size)
    }

    @Test
    fun `an acceptance copies the terms and deletes the proposal - a decline only deletes it`() = runTest {
        firestore.put(path, mapOf("participants" to listOf(ALICE, BOB)))
        val waiting = SupportAgreement(FAMILY, listOf(ALICE, BOB), proposal = SupportProposal(terms, BOB, 1L))

        assertTrue(store.accept(waiting))
        assertTrue(store.clearProposal(waiting))

        val (accepted, declined) = firestore.operations.filterIsInstance<Operation.Update>()
        assertEquals(
            mapOf(
                "agreement" to SupportAgreementDocument.termsMap(terms),
                "proposal" to ServerValue.Delete,
                "lastModifiedBy" to ALICE
            ),
            accepted.fields
        )
        assertEquals(mapOf("proposal" to ServerValue.Delete, "lastModifiedBy" to ALICE), declined.fields)
        listOf(ActivityKind.SUPPORT_ACCEPTED, ActivityKind.SUPPORT_DECLINED).forEach { kind ->
            coVerify(exactly = 1) { announcer.announce(match { it.kind == kind }, any(), any(), BOB) }
        }
    }

    @Test
    fun `the agreement is read from the server - none when there is no document - null when it failed`() = runTest {
        assertEquals(SupportRead.None, store.readFromServer(FAMILY))
        assertEquals(Operation.Get(path, ReadSource.SERVER), firestore.operations.last())

        firestore.put(
            path,
            mapOf("participants" to listOf(ALICE, BOB), "agreement" to SupportAgreementDocument.termsMap(terms))
        )
        assertEquals(
            SupportRead.Known(SupportAgreement(FAMILY, listOf(ALICE, BOB), agreement = terms)),
            store.readFromServer(FAMILY)
        )

        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE))
        assertNull(store.readFromServer(FAMILY))
    }

    @Test
    fun `the listener says none - known - and unavailable for an unreadable document or a failure`() = runTest {
        val known = mapOf("participants" to listOf(ALICE, BOB))
        firestore.documentFlow = {
            flow {
                emit(StoredDocument(FAMILY, null))
                emit(StoredDocument(FAMILY, known))
                emit(StoredDocument(FAMILY, mapOf("agreement" to emptyMap<String, Any?>())))
                emit(StoredDocument(FAMILY, known))
                throw RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED)
            }
        }

        val seen = store.observe(FAMILY).take(5).toList()

        val agreed = SupportRead.Known(SupportAgreement(FAMILY, listOf(ALICE, BOB)))
        assertEquals(listOf(SupportRead.None, agreed, SupportRead.Unavailable, agreed, SupportRead.Unavailable), seen)
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice__bob"
    }
}
