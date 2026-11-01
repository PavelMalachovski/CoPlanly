package com.coparently.app.domain.payment

import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.assertEquals

/** [mod97] against `BigInteger.mod`, which `Iban` calls today, over 10 000 random 30-digit strings. */
class Mod97ParityTest {

    @Test
    fun matchesBigIntegerOnRandomDigits() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val digits = String(CharArray(DIGITS) { '0' + random.nextInt(10) })
            assertEquals(BigInteger(digits).mod(NINETY_SEVEN).toInt(), mod97(digits), digits)
        }
    }

    @Test
    fun matchesBigIntegerOnEveryLengthAnIbanReaches() {
        val random = Random(SEED)
        // An IBAN has at most 34 characters, and a letter becomes two digits.
        (1..MAX_DIGITS).forEach { length ->
            val digits = String(CharArray(length) { '0' + random.nextInt(10) })
            assertEquals(BigInteger(digits).mod(NINETY_SEVEN).toInt(), mod97(digits), digits)
        }
    }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val DIGITS = 30
        const val MAX_DIGITS = 68
        val NINETY_SEVEN: BigInteger = BigInteger.valueOf(97)
    }
}
