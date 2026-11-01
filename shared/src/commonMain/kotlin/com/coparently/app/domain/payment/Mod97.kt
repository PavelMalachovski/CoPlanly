package com.coparently.app.domain.payment

/** The digits folded in at a time: 9 of them after a remainder below 97 still fit a Long. */
private const val CHUNK_DIGITS = 9
private const val MODULUS = 97L
private const val DECIMAL_RADIX = 10L

/**
 * The remainder of the decimal number [digits] divided by 97, the ISO 13616 check an IBAN passes
 * when it is 1 — what `BigInteger(digits).mod(BigInteger.valueOf(97))` gives, without `BigInteger`.
 *
 * An IBAN's rearranged digits run to 30 and more, past what a Long holds, so the number is folded
 * in nine digits at a time: the remainder so far, shifted left by the chunk, plus the chunk, stays
 * below 97 × 10^9.
 *
 * @throws IllegalArgumentException when [digits] is empty or holds anything but ASCII digits.
 */
fun mod97(digits: String): Int {
    require(digits.isNotEmpty() && digits.all { it in '0'..'9' }) { "not a decimal number: $digits" }
    var remainder = 0L
    digits.chunked(CHUNK_DIGITS).forEach { chunk ->
        val shift = (1..chunk.length).fold(1L) { acc, _ -> acc * DECIMAL_RADIX }
        remainder = (remainder * shift + chunk.toLong()) % MODULUS
    }
    return remainder.toInt()
}
