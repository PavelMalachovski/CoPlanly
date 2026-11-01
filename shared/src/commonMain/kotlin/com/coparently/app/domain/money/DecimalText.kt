package com.coparently.app.domain.money

/**
 * Money as decimal text, with the answers `java.math.BigDecimal` gives today and without
 * `BigDecimal`, which iOS does not have.
 *
 * The communication record, the payment QR codes and the chat's money cards print amounts through
 * it, and the money documents (`payments`, `support_agreements`, the settle-up cards) store them as
 * its plain text: two parents' phones and an older build read the same strings, so each function
 * names the `BigDecimal` call it stands for, and `DecimalTextParityTest` holds it to that call.
 *
 * Everything is done on the digits as text, so no value is ever rounded through a binary fraction.
 */
object DecimalText {

    private const val MAX_LONG_DIGITS = 19
    private const val MAX_EXPONENT_DIGITS = 10
    private const val ROUND_UP_FROM = '5'

    /**
     * [value] rounded half away from zero to [scale] decimals, in plain notation:
     * `BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString()`.
     *
     * Like `BigDecimal.valueOf`, it rounds the double's shortest decimal text, not its exact binary
     * value: 2.675 is stored as 2.67499999…, its text is "2.675", and the record has always printed
     * 2.68 for it. `round(value * 100)` would print 2.67. A platform that writes the text with an
     * exponent ("1.0E7", "1e+21") is read the same way.
     *
     * @throws NumberFormatException for NaN or an infinity, as `BigDecimal.valueOf` throws.
     */
    fun halfUp(value: Double, scale: Int): String {
        require(scale >= 0) { "scale must not be negative: $scale" }
        if (!value.isFinite()) throw NumberFormatException("not a finite number: $value")
        val decimal = parseDecimal(value.toString())
            ?: throw NumberFormatException("unexpected text for a double: $value")
        // The number is `digits` × 10^exponent; its units at `scale` are digits × 10^shift.
        val shift = decimal.exponent + scale
        val units = if (shift >= 0) {
            decimal.digits + "0".repeat(shift.toInt())
        } else {
            val drop = -shift
            if (drop > decimal.digits.length) {
                "0"
            } else {
                val kept = decimal.digits.substring(0, decimal.digits.length - drop.toInt()).ifEmpty { "0" }
                val firstDropped = decimal.digits[decimal.digits.length - drop.toInt()]
                if (firstDropped >= ROUND_UP_FROM) incremented(kept) else kept
            }
        }
        return withPoint(units.trimStart('0').ifEmpty { "0" }, scale, decimal.negative)
    }

    /**
     * [units] minor units at [scale] decimals, in plain notation:
     * `BigDecimal.valueOf(units, scale).toPlainString()` — `plain(1250, 2)` is "12.50".
     */
    fun plain(units: Long, scale: Int): String {
        require(scale >= 0) { "scale must not be negative: $scale" }
        return withPoint(units.toString().removePrefix("-"), scale, units < 0)
    }

    /**
     * [plain] without trailing zeros after the point, nor the point when nothing follows it:
     * `BigDecimal.valueOf(units, scale).stripTrailingZeros().toPlainString()` — 5000 basis points
     * at scale 2 are "50", 3350 are "33.5", and nothing is "0". A split proposal's percent is
     * written this way.
     */
    fun plainStripped(units: Long, scale: Int): String {
        val text = plain(units, scale)
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }

    /**
     * The minor units at [scale] decimals that [plain] reads as, or null when it is not a decimal
     * number, needs rounding to fit [scale], or does not fit a Long. It reads what
     * `plain.toBigDecimalOrNull()` reads — a sign, digits with at most one point, and an optional
     * exponent; no spaces, no grouping, no comma — and answers
     * `setScale(scale).unscaledValue().longValueExact()`: "12.5" at scale 2 is 1250, "12.505" is
     * null.
     */
    fun parse(plain: String, scale: Int): Long? {
        require(scale >= 0) { "scale must not be negative: $scale" }
        val decimal = parseDecimal(plain) ?: return null
        val units = exactUnits(decimal.digits.trimStart('0'), decimal.exponent + scale) ?: return null
        return ((if (decimal.negative) "-" else "") + units).toLongOrNull()
    }

    /**
     * [digits] × 10^[shift] as the digits of a whole number, or null when the shift would cut off a
     * digit that is not zero, or leave more digits than a Long holds.
     */
    private fun exactUnits(digits: String, shift: Long): String? = when {
        digits.isEmpty() -> "0"
        shift >= 0 -> if (digits.length + shift > MAX_LONG_DIGITS) null else digits + "0".repeat(shift.toInt())
        -shift > digits.length -> null
        else -> {
            val cut = digits.length + shift.toInt()
            if (digits.substring(cut).all { it == '0' }) digits.substring(0, cut).ifEmpty { "0" } else null
        }
    }

    /** [units] (digits, no sign) with a point before its last [scale] digits, and the sign unless it is zero. */
    private fun withPoint(units: String, scale: Int, negative: Boolean): String {
        val padded = units.padStart(scale + 1, '0')
        val whole = padded.substring(0, padded.length - scale)
        val fraction = padded.substring(padded.length - scale)
        val sign = if (negative && units.any { it != '0' }) "-" else ""
        return if (scale == 0) sign + whole else "$sign$whole.$fraction"
    }

    /** [digits] plus one, carried as text. */
    private fun incremented(digits: String): String {
        val chars = digits.toCharArray()
        var i = chars.size - 1
        while (i >= 0) {
            if (chars[i] == '9') {
                chars[i] = '0'
                i--
            } else {
                chars[i] = chars[i] + 1
                return chars.concatToString()
            }
        }
        return "1" + chars.concatToString()
    }

    /** A decimal number as `digits` × 10^`exponent`, with its sign. */
    private class Decimal(val negative: Boolean, val digits: String, val exponent: Long)

    /**
     * [text] as `toBigDecimalOrNull()` reads it: `[+-]?(digits[.digits?]|.digits)([eE][+-]?digits)?`,
     * where a digit is any Unicode decimal digit (`Char.isDigit`), as Kotlin's check and
     * `BigDecimal(String)` both take it, read as its value; no spaces, no grouping, no comma. The
     * exponent follows `BigDecimal`'s limits — at most ten digits after its leading zeros, inside
     * an Int — and so does the scale it leaves (fraction digits minus exponent, inside an Int), so a
     * text `BigDecimal` refuses is refused here, even a zero.
     */
    private fun parseDecimal(text: String): Decimal? {
        val negative = text.startsWith('-')
        val body = if (negative || text.startsWith('+')) text.substring(1) else text
        val e = body.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (e < 0) body else body.substring(0, e)
        val exponent = if (e < 0) 0L else exponentOf(body.substring(e + 1))
        val parts = mantissa.split('.')
        val fraction = parts.getOrNull(1).orEmpty()
        val scale = fraction.length - (exponent ?: 0L)
        val valid = exponent != null && parts.size <= 2 && parts.any { it.isNotEmpty() } &&
            parts.all { part -> part.all { it.isDigit() } } && scale in Int.MIN_VALUE..Int.MAX_VALUE
        return if (valid) {
            val digits = (parts[0] + fraction).map { it.digitToInt() }.joinToString("")
            Decimal(negative, digits, exponent = -scale)
        } else {
            null
        }
    }

    /** An exponent's value (`[+-]?digits`), or null where `BigDecimal` refuses it. */
    private fun exponentOf(text: String): Long? {
        val negative = text.startsWith('-')
        val digits = if (negative || text.startsWith('+')) text.substring(1) else text
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        val magnitude = digits.map { it.digitToInt() }.joinToString("").trimStart('0')
        if (magnitude.length > MAX_EXPONENT_DIGITS) return null
        val value = magnitude.ifEmpty { "0" }.toLong().let { if (negative) -it else it }
        return value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
    }
}
