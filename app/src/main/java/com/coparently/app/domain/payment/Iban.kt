package com.coparently.app.domain.payment

import java.math.BigInteger

/**
 * An IBAN as a parent types it, checked the way a bank checks it (ISO 13616): two letters, two
 * check digits, up to 30 letters and digits, and the mod-97 remainder of 1.
 *
 * Only the format and the check digits are known here, not whether the account exists — what a
 * typo needs, and all the app can say without asking a bank.
 */
object Iban {

    private val SHAPE = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")
    private val MOD = BigInteger.valueOf(97)
    private const val CHECK_CHARS = 4
    private const val GROUP = 4
    private const val LETTER_BASE = 10

    /** [input] without spaces and in capitals when it is a valid IBAN, otherwise null. */
    fun normalize(input: String?): String? {
        val compact = input.orEmpty().filterNot { it.isWhitespace() }.uppercase()
        if (!SHAPE.matches(compact)) return null
        val rearranged = compact.drop(CHECK_CHARS) + compact.take(CHECK_CHARS)
        val digits = buildString {
            rearranged.forEach { ch -> if (ch.isDigit()) append(ch) else append(ch - 'A' + LETTER_BASE) }
        }
        return compact.takeIf { BigInteger(digits).mod(MOD) == BigInteger.ONE }
    }

    /** The country an IBAN's account is held in: its first two letters. */
    fun countryOf(iban: String): String = iban.take(2)

    /** [iban] in groups of four, as banks print it. */
    fun grouped(iban: String): String = iban.chunked(GROUP).joinToString(" ")
}
