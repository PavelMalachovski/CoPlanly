package com.coparently.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An amount with no currency is written as `BigDecimal.toPlainString()` writes it on Android, on
 * every platform: the scale kept, a sign only before a value that is not zero.
 */
class ReaderMoneyPlainTextTest {

    @Test
    fun `the plain text keeps the scale BigDecimal keeps`() {
        val cases = mapOf(
            "12.50" to "12.50",
            "12.5" to "12.5",
            "+5" to "5",
            "007" to "7",
            "5." to "5",
            ".5" to "0.5",
            "00.10" to "0.10",
            "-0.0" to "0.0",
            "-3.25" to "-3.25",
            "1e3" to "1000",
            "1.5e1" to "15",
            "25e-3" to "0.025",
            "0e-2" to "0.00",
            "0e2" to "0",
        )
        cases.forEach { (text, plain) -> assertEquals(plain, ReaderMoneyText.amount(text, null), text) }
    }

    @Test
    fun `text that is not a number reads as nothing`() {
        listOf("", "abc", "1,50", "1.2.3", "e5").forEach { assertNull(ReaderMoneyText.amount(it, null), it) }
    }

    @Test
    fun `a scale that does not fit an Int reads as nothing as BigDecimal refuses it`() {
        // The scale is the digits after the point less the exponent: 0 + 2^31 and 1 + (2^31 - 1).
        listOf("1e-2147483648", "1.5e-2147483647").forEach { assertNull(ReaderMoneyText.amount(it, null), it) }
    }
}
