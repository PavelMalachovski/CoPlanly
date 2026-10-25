package com.coparently.app.domain.payments

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

/** "I paid" and "Money arrived" (25 October 2026): the id the rules rebuild, and each reader's state. */
class PaymentTest {

    private val paid = Payment(
        id = PaymentId.of(FAMILY, PaymentKind.EXPENSES, YearMonth.of(2026, 10), "CZK"),
        familyId = FAMILY,
        kind = PaymentKind.EXPENSES,
        month = YearMonth.of(2026, 10),
        currency = "CZK",
        amount = BigDecimal("450.00"),
        payerUid = BOB,
        payeeUid = ALICE
    )

    @Test
    fun `the id spells the family, the kind, the month and the currency`() {
        assertEquals("alice__bob__expenses__2026-10__CZK", paid.id)
        assertEquals(
            "alice__bob__support__2026-01__EUR",
            PaymentId.of(FAMILY, PaymentKind.SUPPORT, YearMonth.of(2026, 1), "EUR")
        )
    }

    @Test
    fun `each parent reads the payment from their own side`() {
        assertEquals(PaymentState.NONE, PaymentState.of(null, ALICE))
        assertEquals(PaymentState.PAID_BY_ME, PaymentState.of(paid, BOB))
        assertEquals(PaymentState.PAID_TO_ME, PaymentState.of(paid, ALICE))
        val confirmed = paid.copy(confirmedAtMillis = 1L)
        assertEquals(PaymentState.CONFIRMED, PaymentState.of(confirmed, BOB))
        assertEquals(PaymentState.CONFIRMED, PaymentState.of(confirmed, ALICE))
    }

    @Test
    fun `a kind survives its wire value, and an unknown one is not read`() {
        PaymentKind.entries.forEach { assertEquals(it, PaymentKind.fromWire(it.wire)) }
        assertEquals(null, PaymentKind.fromWire("rent"))
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val FAMILY = "alice__bob"
    }
}
