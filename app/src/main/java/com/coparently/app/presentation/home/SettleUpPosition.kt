package com.coparently.app.presentation.home

import com.coparently.app.domain.expenses.CurrencyBalance
import kotlin.math.abs

/**
 * Where this parent stands for a month, currency by currency, sorted into the two directions a
 * sentence can say: what they are owed and what they owe.
 *
 * Home's spend tile used to report only the **largest** single balance, so a month owing
 * 1 416 CZK and 58 € to this parent said "You are owed 1 416,00 CZK" and nothing about the
 * euros. The app does no FX conversion, so every currency is reported on its own — never summed.
 *
 * @property owedToYou Amounts the co-parent owes this parent, largest first, never negative
 * @property youOwe Amounts this parent owes the co-parent, largest first, never negative
 */
data class SettleUpPosition(
    val owedToYou: List<CurrencyAmount>,
    val youOwe: List<CurrencyAmount>
) {
    /** Whether nothing is owed either way. */
    val settled: Boolean get() = owedToYou.isEmpty() && youOwe.isEmpty()

    companion object {
        /** Below this a balance is settled — matches the Expenses screen, so the two never disagree. */
        private const val SETTLED_EPSILON = 0.01

        /**
         * The position [balances] describe. Only balances whose split could be worked out count —
         * while unpaired there is one parent on record, and a debt figure would be invented.
         *
         * @param balances this month's balances, one per currency
         * @return the amounts owed each way
         */
        fun of(balances: List<CurrencyBalance>): SettleUpPosition {
            val open = balances
                .filter { it.balance.splitKnown && abs(it.balance.netForCurrentUser) >= SETTLED_EPSILON }
                .sortedByDescending { abs(it.balance.netForCurrentUser) }
            val (owed, owing) = open.partition { it.balance.netForCurrentUser > 0 }
            return SettleUpPosition(
                owedToYou = owed.map { CurrencyAmount(it.currency, it.balance.netForCurrentUser) },
                youOwe = owing.map { CurrencyAmount(it.currency, -it.balance.netForCurrentUser) }
            )
        }
    }
}
