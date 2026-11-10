package com.coparently.app.presentation.expenses

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Currency formatter that tolerates an unknown code rather than crashing on it, in [locale] — the
 * process's default unless a caller words its text in a context of its own (a notice).
 *
 * In `:shared-ui`'s `androidMain` since MON-24 phase 2 (Task 11, slice 11b), unchanged, because
 * the Android half of the common money seam (`utils/ReaderMoneyText`) formats with it; the app's
 * screens, the support reminder and the screenshots call it as before.
 */
fun currencyFormat(currency: String, locale: Locale = Locale.getDefault()): NumberFormat =
    NumberFormat.getCurrencyInstance(locale).apply {
        runCatching { this.currency = Currency.getInstance(currency) }
    }
