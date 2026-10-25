package com.coparently.app.data.payment

import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Date

/** `payments/{id}` as the rules bound it (25 October 2026). */
class PaymentDocumentTest {

    private val payment = Payment(
        id = "alice__bob__expenses__2026-10__CZK",
        familyId = "alice__bob",
        kind = PaymentKind.EXPENSES,
        month = YearMonth.of(2026, 10),
        currency = "CZK",
        amount = BigDecimal("450"),
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
    fun `a stored payment reads back, its confirmation included`() {
        val stored = PaymentDocument.paid(payment, Timestamp(Date(1_000L))) +
            PaymentDocument.confirmed("alice", Timestamp(Date(2_000L)))
        val read = PaymentDocument.read(payment.id, stored)
        val expected = payment.copy(amount = BigDecimal("450.00"), paidAtMillis = 1_000L, confirmedAtMillis = 2_000L)
        assertEquals(expected, read)
    }

    @Test
    fun `a payment of a kind this build does not know is not read`() {
        val stored = PaymentDocument.paid(payment, Timestamp(Date(1_000L))) + ("kind" to "rent")
        assertNull(PaymentDocument.read(payment.id, stored))
    }
}
