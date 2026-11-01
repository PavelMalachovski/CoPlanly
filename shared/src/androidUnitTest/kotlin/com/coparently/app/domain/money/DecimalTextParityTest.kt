package com.coparently.app.domain.money

import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * [DecimalText] against `BigDecimal`, the type the record, the payment QR and the money documents
 * use today: a fixed corpus and 10 000 seeded random amounts from 0 to 1.0E9 with up to four
 * decimals, negatives included, plus random doubles of every magnitude.
 */
class DecimalTextParityTest {

    @Test
    fun halfUpMatchesBigDecimalOnTheCorpus() {
        HALF_UP_CORPUS.forEach { value -> SCALES.forEach { scale -> assertHalfUp(value, scale) } }
    }

    @Test
    fun halfUpMatchesBigDecimalOnRandomAmounts() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val decimals = random.nextInt(0, MAX_DECIMALS + 1)
            val units = random.nextLong(0, MAX_AMOUNT * pow10(decimals) + 1)
            val text = BigDecimal.valueOf(units, decimals).toPlainString()
            val value = text.toDouble().let { if (random.nextBoolean()) -it else it }
            assertHalfUp(value, MONEY_SCALE)
        }
    }

    @Test
    fun halfUpMatchesBigDecimalOnRandomDoubles() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val value = Double.fromBits(random.nextLong())
            if (value.isFinite()) assertHalfUp(value, random.nextInt(0, MAX_SCALE + 1))
        }
    }

    @Test
    fun plainMatchesBigDecimal() {
        val random = Random(SEED)
        val corpus = listOf(0L, 1L, -1L, 5L, -5L, 1250L, 5000L, 3333L, Long.MAX_VALUE, Long.MIN_VALUE)
        val anyLong = List(RANDOM_CASES) { random.nextLong() }
        val amounts = List(RANDOM_CASES) { random.nextLong(-MAX_UNITS, MAX_UNITS) }
        (corpus + anyLong + amounts).forEach { units ->
            SCALES.forEach { scale ->
                assertEquals(
                    BigDecimal.valueOf(units, scale).toPlainString(),
                    DecimalText.plain(units, scale),
                    "plain($units, $scale)"
                )
                assertEquals(
                    BigDecimal.valueOf(units, scale).stripTrailingZeros().toPlainString(),
                    DecimalText.plainStripped(units, scale),
                    "plainStripped($units, $scale)"
                )
            }
        }
    }

    @Test
    fun parseMatchesBigDecimalOnTheCorpus() {
        PARSE_CORPUS.forEach { text -> SCALES.forEach { scale -> assertParse(text, scale) } }
    }

    @Test
    fun parseMatchesBigDecimalOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val units = random.nextLong(-MAX_UNITS, MAX_UNITS)
            val scale = random.nextInt(0, MAX_SCALE + 1)
            assertParse(BigDecimal.valueOf(units, scale).toPlainString(), MONEY_SCALE)
            assertParse(random.decimalish(), random.nextInt(0, MAX_SCALE + 1))
        }
    }

    private fun assertHalfUp(value: Double, scale: Int) {
        assertEquals(
            BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString(),
            DecimalText.halfUp(value, scale),
            "halfUp($value, $scale)"
        )
    }

    /**
     * What the money documents read today: `toBigDecimalOrNull()`, exactly at [scale], in a Long.
     *
     * The two early answers are `setScale`'s own, reached without it: `setScale` builds 10^n to
     * rescale, and for "1E999999999" n is a billion, which runs for hours. A value with more
     * decimals than [scale] after its trailing zeros go needs rounding (`ArithmeticException`), and
     * one with more than 19 integer digits does not fit a Long (`longValueExact` throws).
     */
    private fun assertParse(text: String, scale: Int) {
        val expected = text.toBigDecimalOrNull()?.let { decimal ->
            val stripped = decimal.stripTrailingZeros()
            when {
                stripped.signum() == 0 -> 0L
                stripped.scale() > scale -> null
                stripped.precision() - stripped.scale() > MAX_LONG_DIGITS -> null
                else -> runCatching { decimal.setScale(scale).unscaledValue().longValueExact() }.getOrNull()
            }
        }
        assertEquals(expected, DecimalText.parse(text, scale), "parse(\"$text\", $scale)")
    }

    /** Text built from what a decimal is made of, so near-misses come up as often as numbers. */
    private fun Random.decimalish(): String = buildString {
        repeat(nextInt(0, MAX_TEXT)) { append(DECIMAL_CHARS[nextInt(DECIMAL_CHARS.length)]) }
    }

    private fun pow10(exponent: Int): Long = (1..exponent).fold(1L) { acc, _ -> acc * 10 }

    private companion object {
        const val SEED = 20261101
        const val RANDOM_CASES = 10_000
        const val MONEY_SCALE = 2
        const val MAX_SCALE = 4
        const val MAX_DECIMALS = 4
        const val MAX_AMOUNT = 1_000_000_000L
        const val MAX_UNITS = 100_000_000_000_000L
        const val MAX_TEXT = 12
        const val MAX_LONG_DIGITS = 19
        const val DECIMAL_CHARS = "0123456789012345678901234567890123456789..--++eE "
        val SCALES = 0..MAX_SCALE

        val HALF_UP_CORPUS = listOf(
            0.0, -0.0, 0.005, -0.005, 0.015, 0.025, 0.125, 0.995, 1.005, 2.675, -2.675, 2.665, 1.0E7, 1.0E-5,
            1.0E-3, 0.001, 9.995, 99.995, 999999999.995, 1.0E9, 123456789.123, 450.0, 1290.5, 12.3, 0.1, 0.2,
            0.30000000000000004, 1.0E21, 1.0E22, 1.0E23, 2.0E-3, 4.35, 4.345, 1.0E-300, 1.0E300,
            Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, 5.0E-324, 0.5, 1.5, 2.5, -0.5, 9.5
        )

        val PARSE_CORPUS = listOf(
            "12.5", "12.50", "12.505", "12.500000", "12", "-3.25", "+3.25", ".5", "5.", "-.5", "abc", "", " 12",
            "12 ", "12,5", "NaN", "Infinity", "-Infinity", "1e2", "1E+2", "1.5E-1", "1.25E-1", "1E", "1E+", "e5",
            ".", "-", "+", "--1", "0x1A", "1.5d", "1.5f", "92233720368547758.07", "92233720368547758.08",
            "-92233720368547758.08", "-92233720368547758.09", "9223372036854775807", "0E-2147483648",
            "0E+2147483647", "1E2147483648", "1E+0000000000005", "1E-00000000000000000002", "0.000",
            "-0", "-0.00", "00012.50", "1.2.3", "1e2.5",
            // Kotlin's check and BigDecimal take any Unicode decimal digit, as its value.
            "١٢", "١٢.٥", "1E٢", "١E-١", "1٢3.4", "١,٢", "١٢٣٤٥", "٠.٠١",
        )
    }
}
