package com.coparently.app.utils

import com.coparently.app.presentation.expenses.currencyFormat

/** `BigDecimal` and [currencyFormat], as the chat's money cards formatted an amount. */
actual object ReaderMoneyText {

    actual fun amount(amount: String, currency: String?): String? {
        val value = amount.toBigDecimalOrNull() ?: return null
        return currency?.let { currencyFormat(it).format(value) } ?: value.toPlainString()
    }
}
