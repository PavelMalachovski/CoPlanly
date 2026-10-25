package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.data.support.SupportRead
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.support.SupportTerms
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Payments and child support between two phones (25 October 2026, owner decisions), through the
 * production `PaymentStore` and `SupportAgreementStore` against the real `firestore.rules`.
 *
 * What it proves:
 * - **"I paid", then "Money arrived"**: Bob records October's settle-up, Alice's phone reads it as
 *   paid to her and confirms it, and Bob's reads it confirmed — each step a card in the thread.
 * - **Nobody says it for the other**: Alice cannot record that Bob paid, and Bob cannot confirm
 *   his own payment.
 * - **Child support is a proposal the other parent accepts as it stands**, and a month of it is
 *   paid and confirmed the same way.
 * - **A stranger reads neither.**
 *
 * What it cannot: the screens as drawn, and the reminder's notification.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentPaymentsTest : TwoParentTest() {

    private val familyId get() = FamilyKey.of(alice.uid, bob.uid)
    private val october = YearMonth.of(2026, 10)

    @Test
    fun aSettleUpIsPaidByOneParentAndConfirmedByTheOther() = runBlocking<Unit> {
        EmulatorEnvironment.step("payments: Alice cannot say Bob paid")
        assertFalse(
            "a payment recorded by its payee must be refused",
            runCatching {
                alice.firestore.collection("payments").document("${familyId}__expenses__${october}__CZK")
                    .set(
                        mapOf(
                            "familyId" to familyId,
                            "participants" to listOf(alice.uid, bob.uid).sorted(),
                            "kind" to "expenses",
                            "month" to october.toString(),
                            "currency" to "CZK",
                            "amount" to "450.00",
                            "payerUid" to bob.uid,
                            "payeeUid" to alice.uid,
                            "paidBy" to bob.uid,
                            "paidAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ).await()
            }.isSuccess
        )

        EmulatorEnvironment.step("payments: Bob says he paid October's settle-up")
        assertTrue(bob.paymentStore.markPaid(familyId, PaymentKind.EXPENSES, october, "CZK", BigDecimal("450")))
        val onAlice = awaitPayment(alice) { it.kind == PaymentKind.EXPENSES && !it.confirmed }
        assertEquals(bob.uid, onAlice.payerUid)
        assertEquals(BigDecimal("450.00"), onAlice.amount)
        awaitCard(alice, ActivityKind.SETTLE_UP_PAID)

        EmulatorEnvironment.step("payments: Bob cannot confirm his own payment; Alice can")
        assertFalse(bob.paymentStore.confirm(onAlice))
        assertTrue(alice.paymentStore.confirm(onAlice))
        awaitPayment(bob) { it.kind == PaymentKind.EXPENSES && it.confirmed }
        awaitCard(bob, ActivityKind.SETTLE_UP_CONFIRMED)
    }

    @Test
    fun childSupportIsProposedAcceptedAndPaidMonthByMonth() = runBlocking<Unit> {
        val terms = SupportTerms(BigDecimal("5000"), "CZK", 5, bob.uid, october)

        EmulatorEnvironment.step("support: Alice proposes, Bob accepts it as it stands")
        assertTrue(alice.supportStore.propose(familyId, terms, existing = null))
        val proposed = withTimeout(WAIT_MS) {
            bob.supportStore.observe(familyId).first { it is SupportRead.Known && it.agreement.proposal != null }
        } as SupportRead.Known
        assertEquals(alice.uid, proposed.agreement.proposal?.proposedBy)
        assertTrue(bob.supportStore.accept(proposed.agreement))
        val agreed = withTimeout(WAIT_MS) {
            alice.supportStore.observe(familyId).first { it is SupportRead.Known && it.agreement.agreement != null }
        } as SupportRead.Known
        assertEquals(BigDecimal("5000.00"), agreed.agreement.agreement?.amount)
        assertEquals(bob.uid, agreed.agreement.agreement?.payerUid)
        awaitCard(alice, ActivityKind.SUPPORT_ACCEPTED)

        EmulatorEnvironment.step("support: Bob pays October, Alice confirms it arrived")
        assertTrue(bob.paymentStore.markPaid(familyId, PaymentKind.SUPPORT, october, "CZK", BigDecimal("5000")))
        val paid = awaitPayment(alice) { it.kind == PaymentKind.SUPPORT }
        assertTrue(alice.paymentStore.confirm(paid))
        awaitPayment(bob) { it.kind == PaymentKind.SUPPORT && it.confirmed }
    }

    @Test
    fun aStrangerReadsNeitherPaymentsNorChildSupport() = runBlocking<Unit> {
        assertTrue(bob.paymentStore.markPaid(familyId, PaymentKind.EXPENSES, october, "CZK", BigDecimal("1")))
        val terms = SupportTerms(BigDecimal("1"), "CZK", 1, bob.uid, october)
        assertTrue(alice.supportStore.propose(familyId, terms, null))
        val carol = newParent("Carol")

        EmulatorEnvironment.step("payments: Carol tries to read the family's payment and agreement")
        listOf(
            "payments/${familyId}__expenses__${october}__CZK",
            "support_agreements/$familyId"
        ).forEach { path ->
            val failure = runCatching { carol.firestore.document(path).get().await() }.exceptionOrNull()
            assertTrue(
                "a stranger's read of $path was not refused (got $failure)",
                failure is FirebaseFirestoreException &&
                    failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }
    }

    private suspend fun awaitPayment(parent: EmulatorParent, condition: (Payment) -> Boolean): Payment =
        withTimeout(WAIT_MS) {
            parent.paymentStore.observe(familyId)
                .first { list -> list.orEmpty().any(condition) }
                .orEmpty()
                .first(condition)
        }

    private suspend fun awaitCard(reader: EmulatorParent, kind: ActivityKind): Message =
        withTimeout(WAIT_MS) {
            reader.messageRepository.observeMessages(ConversationKey.of(alice.uid, bob.uid))
                .first { messages -> messages.any { it.activity?.kind == kind } }
                .first { it.activity?.kind == kind }
        }

    private companion object {
        const val WAIT_MS = 30_000L
    }
}
