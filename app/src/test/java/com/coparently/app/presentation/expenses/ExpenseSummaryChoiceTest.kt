package com.coparently.app.presentation.expenses

import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UX audit M7: which currency's summary the Expenses screen shows, and which rows leave their split
 * unsaid because it is the family's agreement.
 */
class ExpenseSummaryChoiceTest {

    private val sixtyForty = SplitRatio(6_000)

    @Test
    fun `a row divided exactly as agreed says nothing about its split`() {
        assertTrue(dividesAsAgreed(expense(splitBasisPoints = 6_000), splitKnown = true, agreed = sixtyForty))
    }

    @Test
    fun `a row priced differently from the agreement still says so`() {
        assertFalse(dividesAsAgreed(expense(splitBasisPoints = 5_000), splitKnown = true, agreed = sixtyForty))
    }

    @Test
    fun `a row from before any agreement divided evenly and hides only under an even agreement`() {
        val old = expense(splitBasisPoints = null)
        assertTrue(dividesAsAgreed(old, splitKnown = true, agreed = SplitRatio.EVEN))
        assertFalse(dividesAsAgreed(old, splitKnown = true, agreed = sixtyForty))
    }

    @Test
    fun `nothing is hidden while the agreement or the parents' slots are unknown`() {
        val row = expense(splitBasisPoints = 6_000)
        assertFalse(dividesAsAgreed(row, splitKnown = true, agreed = null))
        assertFalse(dividesAsAgreed(row, splitKnown = false, agreed = sixtyForty))
    }

    @Test
    fun `an expense not shared between the two parents always says how it divides`() {
        assertFalse(
            dividesAsAgreed(expense(splitBasisPoints = 6_000, splitBetween = listOf("a")), true, sixtyForty)
        )
    }

    @Test
    fun `the picked currency is shown while the month holds it, else the first`() {
        val balances = listOf(balance("CZK"), balance("EUR"))
        assertEquals("EUR", shownSummary(balances, "EUR")?.currency)
        assertEquals("CZK", shownSummary(balances, "USD")?.currency)
        assertEquals("CZK", shownSummary(balances, null)?.currency)
        assertNull(shownSummary(emptyList(), "EUR"))
    }

    private fun expense(splitBasisPoints: Int?, splitBetween: List<String> = listOf("a", "b")) = Expense(
        id = "e",
        title = "Shoes",
        amount = 100.0,
        category = ExpenseCategory.entries.first(),
        paidBy = "a",
        splitBetween = splitBetween,
        splitBasisPoints = splitBasisPoints
    )

    private fun balance(currency: String) = CurrencyBalance(
        currency = currency,
        balance = ExpenseBalance(momPaid = 0.0, dadPaid = 0.0, total = 0.0, netForCurrentUser = 0.0, splitKnown = true)
    )
}
