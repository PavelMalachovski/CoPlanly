package com.coparently.app.presentation.home

import com.coparently.app.data.family.FamilyView
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.model.ExpenseCategory
import com.coparently.app.domain.money.SupportedCurrency
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * The month's money that Home's tile and the Family tab's card both show: the family on screen's
 * own expenses, this month only, never summed across currencies.
 */
class MonthMoneyTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val bobFamily = FamilyView(me = "u1", onScreen = "u1__u2", others = setOf("u1__u3"))

    private fun spend(
        id: String,
        amount: Double,
        currency: String = "CZK",
        familyId: String? = "u1__u2",
        date: LocalDate = today
    ) = Expense(
        id = id,
        title = id,
        amount = amount,
        currency = currency,
        category = ExpenseCategory.CLOTHING,
        paidBy = "u1",
        splitBetween = listOf("u1", "u2"),
        date = date,
        createdByFirebaseUid = "u1",
        familyId = familyId
    )

    @Test
    fun `only this month's expenses of the family on screen are counted`() {
        val expenses = listOf(
            spend("a", 1_000.0),
            spend("other family", 500.0, familyId = "u1__u3"),
            spend("last month", 700.0, date = today.minusMonths(1))
        )

        val roles = mapOf("u1" to "mom", "u2" to "dad")
        val money = MonthMoney.of(expenses, bobFamily, today, SupportedCurrency.CZK, "u1", roles)

        assertEquals(listOf(CurrencyAmount("CZK", 1_000.0)), money.spend.byCurrency)
        assertEquals(1_000.0, money.balances.single().balance.total, 0.0)
    }

    @Test
    fun `currencies are never added together, and the larger comes first`() {
        val spend = MonthMoney.spendOf(listOf(spend("a", 40.0, "EUR"), spend("b", 900.0)), SupportedCurrency.CZK)

        assertEquals(listOf(CurrencyAmount("CZK", 900.0), CurrencyAmount("EUR", 40.0)), spend.byCurrency)
    }

    @Test
    fun `an empty month reads zero in the fallback currency`() {
        assertEquals(
            listOf(CurrencyAmount("EUR", 0.0)),
            MonthMoney.spendOf(emptyList(), SupportedCurrency.EUR).byCurrency
        )
    }
}
