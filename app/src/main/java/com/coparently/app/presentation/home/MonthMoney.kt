package com.coparently.app.presentation.home

import com.coparently.app.data.family.FamilyView
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.expenses.calculateExpenseBalancesByCurrency
import com.coparently.app.domain.model.Expense
import com.coparently.app.domain.money.SupportedCurrency
import java.time.LocalDate

/**
 * This month's money in the family on screen: what was spent per currency and who owes whom.
 *
 * One computation for Home's spend tile and the Family tab's money card (new-user review,
 * October 2026), so the two can never disagree about the same month.
 *
 * @property spend This month's spend, one subtotal per currency
 * @property balances This month's settle-up position, per currency
 */
data class MonthMoney(val spend: MonthSpend, val balances: List<CurrencyBalance>) {
    companion object {
        /**
         * The money of [today]'s month among [expenses], counting only what belongs to the family
         * on screen ([FamilyView.counts]): an expense that is "only mine" or from an earlier family
         * is listed on Expenses, marked, and added into no figure here (owner decision, October
         * 2026).
         *
         * @param fallbackCurrency The currency an empty month is shown in, so it reads "0" rather
         *   than blank
         * @param userId The signed-in parent, whose side of the balance is reported
         * @param roleByUid Each parent's slot, to attribute who paid what
         */
        @Suppress("LongParameterList") // the month, its expenses and the four facts that price them
        fun of(
            expenses: List<Expense>,
            view: FamilyView,
            today: LocalDate,
            fallbackCurrency: SupportedCurrency,
            userId: String,
            roleByUid: Map<String, String>
        ): MonthMoney {
            val month = thisMonthCounted(expenses, view, today)
            return MonthMoney(
                spend = spendOf(month, fallbackCurrency),
                balances = calculateExpenseBalancesByCurrency(month, userId, roleByUid)
            )
        }

        /** The expenses dated in [today]'s month that count for the family on screen. */
        fun thisMonthCounted(expenses: List<Expense>, view: FamilyView, today: LocalDate): List<Expense> =
            expenses
                .filter { it.date.year == today.year && it.date.month == today.month }
                .filter { view.counts(it.familyId, it.createdByFirebaseUid) }

        /**
         * [expenses]' spend, one subtotal per currency, largest first — never summed across
         * currencies — or a zero in [fallbackCurrency] when there are none.
         */
        fun spendOf(expenses: List<Expense>, fallbackCurrency: SupportedCurrency): MonthSpend {
            val byCurrency = expenses.groupBy { it.currency }
                .map { (currency, group) -> CurrencyAmount(currency, group.sumOf { it.amount }) }
                .sortedByDescending { it.amount }
            return MonthSpend(byCurrency.ifEmpty { listOf(CurrencyAmount(fallbackCurrency.code, 0.0)) })
        }
    }
}
