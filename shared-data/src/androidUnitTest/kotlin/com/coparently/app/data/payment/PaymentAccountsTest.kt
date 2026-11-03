package com.coparently.app.data.payment

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The IBAN each parent is paid on (CLAUDE.md item 45) through the Firestore seam since MON-24 phase
 * 2 (Task 8e): a merge of one key, a delete for none, and a listener that reads anything but a
 * valid IBAN — a number included — as none.
 */
class PaymentAccountsTest {

    private val firestore = RecordingFirestoreClient()
    private var signedIn: String? = ALICE
    private val auth = mockk<SignedInUser> { every { getCurrentUid() } answers { signedIn } }
    private val accounts = PaymentAccounts(firestore, auth)

    @Test
    fun `an IBAN is merged into the profile normalized - and none deletes the field`() = runTest {
        assertTrue(accounts.setMine("cz65 0800 0000 1920 0014 5399"))
        assertTrue(accounts.setMine(null))

        val (set, cleared) = firestore.operations.filterIsInstance<Operation.Set>()
        assertEquals(DocumentPath("users", ALICE), set.path)
        assertEquals(SetMerge.Merge, set.merge)
        assertEquals(mapOf("paymentIban" to "CZ6508000000192000145399"), set.data)
        assertEquals(mapOf("paymentIban" to ServerValue.Delete), cleared.data)
    }

    @Test
    fun `a write that fails or has nobody signed in says so`() = runTest {
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))
        assertFalse(accounts.setMine("CZ6508000000192000145399"))

        signedIn = null
        assertFalse(accounts.setMine("CZ6508000000192000145399"))
        assertNull(accounts.observeMine().first())
    }

    @Test
    fun `the listener reads a valid IBAN - and none for a number - a missing field or a failure`() = runTest {
        firestore.documentFlow = { path ->
            flow {
                emit(StoredDocument(path.id, mapOf("paymentIban" to "CZ6508000000192000145399")))
                emit(StoredDocument(path.id, mapOf("paymentIban" to 42L)))
                emit(StoredDocument(path.id, mapOf("paymentIban" to "CZ6508000000192000145399")))
                throw RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED)
            }
        }

        val seen = accounts.observeIban(BOB).take(4).toList()

        assertEquals(listOf("CZ6508000000192000145399", null, "CZ6508000000192000145399", null), seen)
        assertEquals(Operation.ObserveDocument(DocumentPath("users", BOB)), firestore.operations.single())
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
