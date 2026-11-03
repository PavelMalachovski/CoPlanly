package com.coparently.app.data.payment

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.FirestoreTimestamp
import com.coparently.app.data.remote.firebase.ReadSource
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.ServerValue
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
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
 * "I paid" and "Money arrived" (CLAUDE.md item 48) through the Firestore seam since MON-24 phase 2
 * (Task 8e): the document id the rules rebuild, an overwrite with the server's clock for `paidAt`,
 * an update of the two confirmation fields only, a server read that says when it could not answer,
 * and the query keyed on `participants` and `familyId`.
 */
class PaymentStoreTest {

    private val firestore = RecordingFirestoreClient()
    private var signedIn: String? = BOB
    private val auth = mockk<SignedInUser> { every { getCurrentUid() } answers { signedIn } }
    private val userRepository = mockk<UserRepository> {
        coEvery { getCurrentUser() } returns mockk(relaxed = true) { every { name } returns "Bob" }
    }
    private val announcer = mockk<ActivityAnnouncer>(relaxed = true)
    private val store = PaymentStore(firestore, auth, userRepository, announcer)

    @Test
    fun `the payer's word is the whole document under the id the rules rebuild`() = runTest {
        assertTrue(store.markPaid(FAMILY, PaymentKind.EXPENSES, OCTOBER, "CZK", 45_000L))

        val set = firestore.operations.filterIsInstance<Operation.Set>().single()
        assertEquals(DocumentPath("payments", "alice__bob__expenses__2026-10__CZK"), set.path)
        assertEquals(SetMerge.Overwrite, set.merge)
        assertEquals(
            mapOf(
                "familyId" to FAMILY,
                "participants" to listOf(ALICE, BOB),
                "kind" to "expenses",
                "month" to "2026-10",
                "currency" to "CZK",
                "amount" to "450.00",
                "payerUid" to BOB,
                "payeeUid" to ALICE,
                "paidBy" to BOB,
                "paidAt" to ServerValue.ServerTimestamp
            ),
            set.data
        )
        coVerify(exactly = 1) {
            announcer.announce(
                match { it.kind == ActivityKind.SETTLE_UP_PAID && it.title == "2026-10" && it.whenIso == "2026-10-01" },
                "Bob",
                false,
                ALICE
            )
        }
    }

    @Test
    fun `a payment that could not be stored posts no card`() = runTest {
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))

        assertFalse(store.markPaid(FAMILY, PaymentKind.SUPPORT, OCTOBER, "CZK", 500_000L))
        coVerify(exactly = 0) { announcer.announce(any(), any(), any(), any()) }
    }

    @Test
    fun `only the payee confirms - with an update of the confirmation alone`() = runTest {
        val paid = payment()
        assertFalse(store.confirm(paid))

        signedIn = ALICE
        firestore.put(DocumentPath("payments", paid.id), mapOf("payerUid" to BOB))
        assertTrue(store.confirm(paid))

        val update = firestore.operations.filterIsInstance<Operation.Update>().single()
        assertEquals(DocumentPath("payments", paid.id), update.path)
        assertEquals(mapOf("confirmedBy" to ALICE, "confirmedAt" to ServerValue.ServerTimestamp), update.fields)
        assertFalse(store.confirm(paid.copy(confirmedAtMillis = 1L)))
    }

    @Test
    fun `the server's answer is read from the server and a failure is said`() = runTest {
        val paid = payment()
        firestore.put(
            DocumentPath("payments", paid.id),
            PaymentDocument.paid(paid, FirestoreTimestamp.fromEpochMillis(1_000L))
        )

        assertEquals(paid.copy(paidAtMillis = 1_000L), store.readFromServer(paid.id).getOrThrow())
        assertEquals(Operation.Get(DocumentPath("payments", paid.id), ReadSource.SERVER), firestore.operations.last())
        assertNull(store.readFromServer("missing").getOrThrow())

        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.UNAVAILABLE))
        assertTrue(store.readFromServer(paid.id).isFailure)
    }

    @Test
    fun `the family's payments are listened to by participant and family - and a failure reads as none`() = runTest {
        val paid = payment()
        firestore.queryFlow = {
            flow {
                emit(listOf(StoredDocument(paid.id, PaymentDocument.paid(paid, FirestoreTimestamp(1L, 0)))))
                throw RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED)
            }
        }

        val seen = store.observe(FAMILY).take(2).toList()

        assertEquals(listOf(listOf(paid.copy(paidAtMillis = 1_000L)), null), seen)
        val query = firestore.operations.filterIsInstance<Operation.ObserveQuery>().single().query
        assertEquals(
            DocumentQuery("payments").whereArrayContains("participants", BOB).whereEqualTo("familyId", FAMILY),
            query
        )
    }

    @Test
    fun `nobody signed in reads nothing and writes nothing`() = runTest {
        signedIn = null

        assertNull(store.observe(FAMILY).first())
        assertFalse(store.markPaid(FAMILY, PaymentKind.EXPENSES, OCTOBER, "CZK", 1L))
        assertTrue(firestore.operations.isEmpty())
    }

    private fun payment() = Payment(
        id = "alice__bob__expenses__2026-10__CZK",
        familyId = FAMILY,
        kind = PaymentKind.EXPENSES,
        month = OCTOBER,
        currency = "CZK",
        amountMinor = 45_000L,
        payerUid = BOB,
        payeeUid = ALICE
    )

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice__bob"
        val OCTOBER = YearMonth(2026, 10)
    }
}
