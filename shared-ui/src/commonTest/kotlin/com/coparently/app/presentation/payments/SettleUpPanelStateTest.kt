package com.coparently.app.presentation.payments

import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettleUpPanelStateTest {

    private val month = YearMonth(2026, 10)

    private fun payment(payer: String, confirmedAt: Long? = null) = Payment(
        id = "fam__expenses__2026-10__CZK",
        familyId = "fam",
        kind = PaymentKind.EXPENSES,
        month = month,
        currency = "CZK",
        amountMinor = 62_000,
        payerUid = payer,
        payeeUid = if (payer == ME) BOB else ME,
        paidAtMillis = PAID_AT,
        confirmedAtMillis = confirmedAt
    )

    private fun state(net: Double, payment: Payment? = null, splitKnown: Boolean = true) =
        settleUpPanelState(net, splitKnown, "CZK", month, payment, ME)

    @Test
    fun `owed to me with nothing recorded asks to settle`() {
        assertEquals(SettleUpPanelState.AskToSettle(month, "CZK", 62_000, 620.0), state(620.0))
    }

    @Test
    fun `owed by me with nothing recorded offers the payment`() {
        assertEquals(SettleUpPanelState.Pay(month, "CZK", 62_000), state(-620.0))
    }

    @Test
    fun `a payment I said I made waits for the co-parent`() {
        assertEquals(
            SettleUpPanelState.WaitingForConfirm(month, "CZK", 62_000, PAID_AT),
            state(-620.0, payment(ME))
        )
    }

    @Test
    fun `a payment the co-parent says they made asks me to confirm`() {
        val paid = payment(BOB)
        assertEquals(SettleUpPanelState.Confirm(month, "CZK", 62_000, PAID_AT, paid), state(620.0, paid))
    }

    @Test
    fun `a confirmed payment is settled on both phones`() {
        assertEquals(SettleUpPanelState.Settled(month, "CZK", 62_000), state(620.0, payment(BOB, CONFIRMED_AT)))
        assertEquals(SettleUpPanelState.Settled(month, "CZK", 62_000), state(-620.0, payment(ME, CONFIRMED_AT)))
    }

    @Test
    fun `a recorded payment decides whatever the balance now reads`() {
        assertEquals(
            SettleUpPanelState.WaitingForConfirm(month, "CZK", 62_000, PAID_AT),
            state(0.0, payment(ME))
        )
    }

    @Test
    fun `nothing owed draws no panel`() {
        assertNull(state(0.0))
        assertNull(state(0.004))
        assertNull(state(-0.004))
    }

    @Test
    fun `a split nobody can work out draws no panel`() {
        assertNull(state(620.0, splitKnown = false))
    }

    @Test
    fun `the amount is rounded half up to hundredths`() {
        assertEquals(SettleUpPanelState.Pay(month, "CZK", 1_235), state(-12.345))
        assertEquals(SettleUpPanelState.Pay(month, "CZK", 1_000_000_000), state(-10_000_000.0))
    }

    private companion object {
        const val ME = "alice"
        const val BOB = "bob"
        const val PAID_AT = 1_795_000_000_000L
        const val CONFIRMED_AT = 1_795_100_000_000L
    }
}
