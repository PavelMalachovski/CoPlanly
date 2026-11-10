package com.coparently.app.presentation.expenses

import com.coparently.app.utils.ReaderMoneyText

/**
 * One currency's amounts in the reader's conventions, for the expense screens: what each held as a
 * `NumberFormat` from `currencyFormat(currency)` before they moved to `:shared-ui` (MON-24 phase 2,
 * slice 11e), now through the money seam, which on Android is that same formatter in the default
 * locale. The amounts are never cut (design item 15); this only writes them.
 *
 * @property currency The ISO 4217 code the amounts are in; an unknown one is written in the
 *   locale's own currency, as `currencyFormat` tolerates it.
 */
internal class MoneyFormat(val currency: String) {

    /** [value] as the expense screens hold it, a `Double`: `currencyFormat(currency).format(value)`. */
    fun format(value: Double): String = ReaderMoneyText.amount(value, currency)

    /**
     * [plain] decimal text ("450.00", a payment's minor units written with two places), as the
     * Expenses header formatted the `BigDecimal` it held; empty when it is not a number.
     */
    fun format(plain: String): String = ReaderMoneyText.amount(plain, currency).orEmpty()
}
