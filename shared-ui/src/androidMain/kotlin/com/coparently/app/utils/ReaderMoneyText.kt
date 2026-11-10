package com.coparently.app.utils

import com.coparently.app.presentation.expenses.currencyFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * `BigDecimal` and [currencyFormat], as the chat's money cards formatted an amount; [currencyFormat]
 * over the `Double` itself, as the expense screens and Home's money tile did; and the default
 * locale's percent instance, as the analytics did.
 */
actual object ReaderMoneyText {

    actual fun amount(amount: String, currency: String?): String? {
        val value = amount.toBigDecimalOrNull() ?: return null
        return currency?.let { currencyFormat(it).format(value) } ?: value.toPlainString()
    }

    actual fun amount(value: Double, currency: String): String = currencyFormat(currency).format(value)

    actual fun percent(fraction: Double): String =
        NumberFormat.getPercentInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }.format(fraction)
}
