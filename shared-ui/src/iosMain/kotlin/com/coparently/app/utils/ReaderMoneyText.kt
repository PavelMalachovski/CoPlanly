package com.coparently.app.utils

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

/**
 * Foundation's currency style in the phone's current locale; the amount read as an
 * `NSDecimalNumber`, so no binary rounding touches it. Which locale the iPhone app's own language
 * choice sets is phase 3's to decide, as for the dates.
 */
actual object ReaderMoneyText {

    actual fun amount(amount: String, currency: String?): String? {
        if (!DECIMAL.matches(amount)) return null
        val value = NSDecimalNumber(string = amount)
        if (currency == null) return value.stringValue
        val formatter = NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterCurrencyStyle
            currencyCode = currency
        }
        return formatter.stringFromNumber(value) ?: value.stringValue
    }

    /** What `toBigDecimalOrNull` reads: a sign, digits with at most one point, an exponent. */
    private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")
}
