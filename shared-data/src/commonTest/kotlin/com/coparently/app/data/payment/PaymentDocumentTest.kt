package com.coparently.app.data.payment

import com.coparently.app.data.remote.firebase.FirestoreTimestamp
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `payments/{id}` as the rules bound it (25 October 2026). */
class PaymentDocumentTest {

    private val payment = Payment(
        id = "alice__bob__expenses__2026-10__CZK",
        familyId = "alice__bob",
        kind = PaymentKind.EXPENSES,
        month = YearMonth(2026, 10),
        currency = "CZK",
        amountMinor = 45_000L,
        payerUid = "bob",
        payeeUid = "alice"
    )

    @Test
    fun `the payer writes the payment with sorted participants and a two-decimal amount`() {
        val written = PaymentDocument.paid(payment, paidAt = "server-time")
        assertEquals(listOf("alice", "bob"), written["participants"])
        assertEquals("450.00", written["amount"])
        assertEquals("expenses", written["kind"])
        assertEquals("2026-10", written["month"])
        assertEquals("bob", written["paidBy"])
        assertEquals("server-time", written["paidAt"])
    }

    @Test
    fun `a stored payment reads back - its confirmation included`() {
        val stored = PaymentDocument.paid(payment, FirestoreTimestamp.fromEpochMillis(1_000L)) +
            PaymentDocument.confirmed("alice", FirestoreTimestamp.fromEpochMillis(2_000L))
        val read = PaymentDocument.read(payment.id, stored)
        val expected = payment.copy(amountMinor = 45_000L, paidAtMillis = 1_000L, confirmedAtMillis = 2_000L)
        assertEquals(expected, read)
    }

    @Test
    fun `the month and the amount are read as java time and BigDecimal read them`() {
        val stored = PaymentDocument.paid(payment, FirestoreTimestamp.fromEpochMillis(1_000L))
        assertEquals(1_235L, PaymentDocument.read(payment.id, stored + ("amount" to "12.345"))?.amountMinor)
        assertNull(PaymentDocument.read(payment.id, stored + ("amount" to "12,34")))
        assertNull(PaymentDocument.read(payment.id, stored + ("month" to "2026-1")))
        assertNull(PaymentDocument.read(payment.id, stored + ("month" to "+2026-10")))
        assertEquals(YearMonth(12_026, 10), PaymentDocument.read(payment.id, stored + ("month" to "+12026-10"))?.month)
    }

    @Test
    fun `a payment of a kind this build does not know is not read`() {
        val stored = PaymentDocument.paid(payment, FirestoreTimestamp.fromEpochMillis(1_000L)) + ("kind" to "rent")
        assertNull(PaymentDocument.read(payment.id, stored))
    }
}
