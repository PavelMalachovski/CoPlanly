package com.coparently.app.utils

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterPercentStyle
import platform.Foundation.currentLocale

/**
 * Foundation's currency style in the phone's current locale; the amount read as an
 * `NSDecimalNumber` in the POSIX locale (a point, never the phone's comma), so no binary rounding
 * touches it. Which locale the iPhone app's own language choice sets is phase 3's to decide, as for
 * the dates.
 */
actual object ReaderMoneyText {

    actual fun amount(amount: String, currency: String?): String? {
        val match = DECIMAL.matchEntire(amount) ?: return null
        val exponentText = match.groupValues[EXPONENT_GROUP]
        val exponent = if (exponentText.isEmpty()) 0 else exponentText.toIntOrNull()
        // Android writes `BigDecimal.toPlainString()`, which keeps the scale ("12.50", never
        // "12.5"); `NSDecimalNumber` normalises it away, so the plain text is written here.
        val plain = exponent?.let { plainText(match, it) }
        return if (plain == null || currency == null) plain else currencyText(amount, currency) ?: plain
    }

    /** Foundation's currency style over the `Double`, as Android's `NumberFormat` formats one. */
    actual fun amount(value: Double, currency: String): String =
        currencyFormatter(currency).stringFromNumber(NSNumber(double = value)).orEmpty()

    actual fun percent(fraction: Double): String {
        val formatter = NSNumberFormatter().apply {
            locale = NSLocale.currentLocale
            numberStyle = NSNumberFormatterPercentStyle
            maximumFractionDigits = 0u
        }
        return formatter.stringFromNumber(NSNumber(double = fraction)).orEmpty()
    }

    private fun currencyFormatter(currency: String) = NSNumberFormatter().apply {
        locale = NSLocale.currentLocale
        numberStyle = NSNumberFormatterCurrencyStyle
        currencyCode = currency
    }

    private fun currencyText(amount: String, currency: String): String? {
        val value = NSDecimalNumber(string = amount, locale = NSLocale(localeIdentifier = "en_US_POSIX"))
        return currencyFormatter(currency).stringFromNumber(value)
    }

    /**
     * What `BigDecimal(text).toPlainString()` writes: the unscaled digits without leading zeros,
     * the scale the text gave (the digits after the point less the exponent), zeros appended for a
     * negative scale, and a minus sign only before a value that is not zero. Null for a scale that
     * does not fit an `Int`, which `BigDecimal` refuses ("Scale out of range"): "1e-2147483648".
     */
    private fun plainText(match: MatchResult, exponent: Int): String? {
        val sign = match.groupValues[1]
        val mantissa = match.groupValues[2]
        val point = mantissa.indexOf('.')
        val integer = if (point < 0) mantissa else mantissa.substring(0, point)
        val fraction = if (point < 0) "" else mantissa.substring(point + 1)
        val digits = (integer + fraction).trimStart('0').ifEmpty { "0" }
        val wideScale = fraction.length.toLong() - exponent
        if (wideScale !in Int.MIN_VALUE..Int.MAX_VALUE) return null
        val scale = wideScale.toInt()
        val body = when {
            scale <= 0 -> if (digits == "0") "0" else digits + "0".repeat(-scale)
            digits.length > scale -> digits.substring(0, digits.length - scale) + "." +
                digits.substring(digits.length - scale)
            else -> "0." + "0".repeat(scale - digits.length) + digits
        }
        return if (sign == "-" && digits != "0") "-$body" else body
    }

    /** The exponent's digits in [DECIMAL]. */
    private const val EXPONENT_GROUP = 4

    /** What `toBigDecimalOrNull` reads: a sign, digits with at most one point, an exponent. */
    private val DECIMAL = Regex("""([+-]?)(\d+\.?\d*|\.\d+)([eE]([+-]?\d+))?""")
}
