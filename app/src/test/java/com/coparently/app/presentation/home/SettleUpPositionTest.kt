package com.coparently.app.presentation.home

import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.expenses.ExpenseBalance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's settle-up line names every currency with an open balance, not only the largest one.
 */
class SettleUpPositionTest {

    private fun balance(currency: String, net: Double, splitKnown: Boolean = true) = CurrencyBalance(
        currency = currency,
        balance = ExpenseBalance(
            momPaid = 0.0,
            dadPaid = 0.0,
            total = 0.0,
            netForCurrentUser = net,
            splitKnown = splitKnown
        )
    )

    @Test
    fun `every currency owed to this parent is reported, largest first`() {
        val position = SettleUpPosition.of(listOf(balance("EUR", 58.0), balance("CZK", 1_416.0)))

        assertEquals(
            listOf(CurrencyAmount("CZK", 1_416.0), CurrencyAmount("EUR", 58.0)),
            position.owedToYou
        )
        assertTrue(position.youOwe.isEmpty())
    }

    @Test
    fun `opposite directions land on opposite sides, as positive amounts`() {
        val position = SettleUpPosition.of(listOf(balance("CZK", 200.0), balance("EUR", -30.0)))

        assertEquals(listOf(CurrencyAmount("CZK", 200.0)), position.owedToYou)
        assertEquals(listOf(CurrencyAmount("EUR", 30.0)), position.youOwe)
    }

    @Test
    fun `settled and unknown splits say nothing`() {
        val position = SettleUpPosition.of(
            listOf(balance("CZK", 0.004), balance("EUR", 99.0, splitKnown = false))
        )

        assertTrue(position.settled)
    }
}
