package com.coparently.app.domain.money

import com.coparently.app.domain.serialization.JvmNumberLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [DecimalText] is `BigDecimal`'s money text without `BigDecimal`. The PDF, the CSV and the payment
 * QR print amounts through it, and the money documents store them as its plain text. These
 * vectors run on the JVM and on iOS; `DecimalTextParityTest` holds the JVM side to `BigDecimal`.
 */
class DecimalTextTest {

    @Test
    fun halfUpRoundsTheDecimalTextOfTheDouble() {
        // 2.675 is 2.67499999999999982236431605997495353221893310546875 in binary; the record
        // has always printed 2.68, because BigDecimal.valueOf reads the double's shortest text.
        assertEquals("2.68", DecimalText.halfUp(2.675, 2))
        assertEquals("0.01", DecimalText.halfUp(0.005, 2))
    }

    @Test
    fun halfUpReadsTheShortestDigitsOfEveryForm() {
        // Each was checked against BigDecimal on JDK 21. The text of the first is "1.23456789125E8",
        // of the second "9.99999999995E8" (a carry across the point), of the third
        // "0.30000000000000004": a platform printing other digits would round differently.
        assertEquals("123456789.13", DecimalText.halfUp(123456789.125, 2))
        assertEquals("1000000000.00", DecimalText.halfUp(999999999.995, 2))
        assertEquals("0.30", DecimalText.halfUp(0.1 + 0.2, 2))
        assertEquals("1.01", DecimalText.halfUp(1.005, 2))
    }

    @Test
    fun halfUpWritesNoExponent() {
        assertEquals("10000000.00", DecimalText.halfUp(1.0E7, 2))
        assertEquals("0.00", DecimalText.halfUp(1.0E-5, 2))
        assertEquals("123456789.12", DecimalText.halfUp(123456789.123, 2))
    }

    @Test
    fun halfUpRoundsANegativeAwayFromZero() {
        assertEquals("-2.68", DecimalText.halfUp(-2.675, 2))
        assertEquals("-0.01", DecimalText.halfUp(-0.005, 2))
    }

    @Test
    fun halfUpWritesNoNegativeZero() {
        assertEquals("0.00", DecimalText.halfUp(-0.001, 2))
        assertEquals("0.00", DecimalText.halfUp(-0.0, 2))
    }

    @Test
    fun halfUpPadsToTheScale() {
        assertEquals("450.00", DecimalText.halfUp(450.0, 2))
        assertEquals("1290.50", DecimalText.halfUp(1290.5, 2))
        assertEquals("3", DecimalText.halfUp(2.5, 0))
    }

    @Test
    fun halfUpRefusesWhatIsNotANumber() {
        assertFailsWith<NumberFormatException> { DecimalText.halfUp(Double.NaN, 2) }
        assertFailsWith<NumberFormatException> { DecimalText.halfUp(Double.POSITIVE_INFINITY, 2) }
    }

    @Test
    fun jvmDoubleTextLaysADoubleOutAsTheJvmDoes() {
        // An expense card's amount ("1250.5 CZK"): the JVM's layout, whatever the platform prints.
        // Nothing in [0.001, 0.01) here: on Android this is the runtime's own `toString`, which
        // before JDK 19 writes a few of those with a trailing zero (see `theIphoneLayoutIsJdk19s`).
        assertEquals("1250.5", JvmDoubleText.of(1250.5))
        assertEquals("42.0", JvmDoubleText.of(42.0))
        assertEquals("1.0E-4", JvmDoubleText.of(0.0001))
        assertEquals("9999999.0", JvmDoubleText.of(9_999_999.0))
        assertEquals("1.0E7", JvmDoubleText.of(10_000_000.0))
        assertEquals("1.25E7", JvmDoubleText.of(12_500_000.0))
        assertEquals("-3.5", JvmDoubleText.of(-3.5))
        assertEquals("0.0", JvmDoubleText.of(0.0))
    }

    @Test
    fun theIphoneLayoutIsJdk19s() {
        // What `JvmDoubleText` does with Kotlin/Native's digits: JDK 19's layout, so the older
        // FloatingDecimal's trailing zero (JDK-4511638) is not reproduced there.
        assertEquals("0.002", JvmNumberLayout.of("0.0020"))
        assertEquals("0.002", JvmNumberLayout.of("2.0E-3"))
        assertEquals("0.002", JvmNumberLayout.of("2e-03"))
        assertEquals("0.0025", JvmNumberLayout.of("0.0025"))
        assertEquals("0.0099", JvmNumberLayout.of("9.9e-03"))
        assertEquals("1.0E7", JvmNumberLayout.of("1e+07"))
        assertEquals("1250.5", JvmNumberLayout.of("1250.5"))
    }

    @Test
    fun plainWritesMinorUnitsAtTheirScale() {
        assertEquals("12.50", DecimalText.plain(1250, 2))
        assertEquals("0.05", DecimalText.plain(5, 2))
        assertEquals("-0.05", DecimalText.plain(-5, 2))
        assertEquals("0.00", DecimalText.plain(0, 2))
        assertEquals("1250", DecimalText.plain(1250, 0))
        assertEquals("-92233720368547758.08", DecimalText.plain(Long.MIN_VALUE, 2))
    }

    @Test
    fun parseReadsPlainTextIntoMinorUnits() {
        assertEquals(1250L, DecimalText.parse("12.5", 2))
        assertEquals(1250L, DecimalText.parse("12.50", 2))
        assertEquals(1200L, DecimalText.parse("12", 2))
        assertEquals(-325L, DecimalText.parse("-3.25", 2))
        assertEquals(50L, DecimalText.parse(".5", 2))
    }

    @Test
    fun parseRefusesWhatWouldNeedRounding() {
        assertNull(DecimalText.parse("12.505", 2))
    }

    @Test
    fun parseRefusesWhatIsNotADecimal() {
        assertNull(DecimalText.parse("abc", 2))
        assertNull(DecimalText.parse("", 2))
        assertNull(DecimalText.parse(" 12", 2))
        assertNull(DecimalText.parse("12,5", 2))
        assertNull(DecimalText.parse("NaN", 2))
    }

    @Test
    fun parseRefusesWhatALongCannotHold() {
        assertNull(DecimalText.parse("92233720368547758.08", 2))
        assertEquals(Long.MIN_VALUE, DecimalText.parse("-92233720368547758.08", 2))
    }

    @Test
    fun parseHalfUpReadsAStoredAmountIntoMinorUnits() {
        // What MinorUnits.fromText answered through BigDecimal, which the money documents read.
        assertEquals(45_000L, DecimalText.parseHalfUp("450.00", 2))
        assertEquals(45_000L, DecimalText.parseHalfUp("450", 2))
        assertEquals(500_050L, DecimalText.parseHalfUp("5000.5", 2))
        assertEquals(-125L, DecimalText.parseHalfUp("-1.25", 2))
        assertEquals(1250L, DecimalText.parseHalfUp("1.25E1", 2))
        assertEquals(0L, DecimalText.parseHalfUp("0.000", 2))
    }

    @Test
    fun parseHalfUpRoundsHalfAwayFromZero() {
        assertEquals(1235L, DecimalText.parseHalfUp("12.345", 2))
        assertEquals(1234L, DecimalText.parseHalfUp("12.3449", 2))
        assertEquals(1L, DecimalText.parseHalfUp("0.005", 2))
        assertEquals(-1L, DecimalText.parseHalfUp("-0.005", 2))
        assertEquals(0L, DecimalText.parseHalfUp("-0.004", 2))
        assertEquals(100L, DecimalText.parseHalfUp("0.995", 2))
        assertEquals(0L, DecimalText.parseHalfUp("1E-999999999", 2))
    }

    @Test
    fun parseHalfUpRefusesWhatIsNotANumberOrDoesNotFit() {
        assertNull(DecimalText.parseHalfUp("abc", 2))
        assertNull(DecimalText.parseHalfUp("", 2))
        assertNull(DecimalText.parseHalfUp("12,5", 2))
        assertNull(DecimalText.parseHalfUp(" 12", 2))
        assertNull(DecimalText.parseHalfUp("92233720368547758.08", 2))
        assertEquals(Long.MAX_VALUE, DecimalText.parseHalfUp("92233720368547758.07", 2))
        assertEquals(Long.MIN_VALUE, DecimalText.parseHalfUp("-92233720368547758.08", 2))
        assertNull(DecimalText.parseHalfUp("92233720368547758.075", 2))
        assertNull(DecimalText.parseHalfUp("1E999999999", 2))
    }

    @Test
    fun theSplitPercentIsWrittenWithoutTrailingZeros() {
        // ActivityFallbackText: BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + " %".
        assertEquals("50", DecimalText.plainStripped(5000, 2))
        assertEquals("50 %", DecimalText.plainStripped(5000, 2) + " %")
        assertEquals("33.33", DecimalText.plainStripped(3333, 2))
        assertEquals("33.5", DecimalText.plainStripped(3350, 2))
        assertEquals("100", DecimalText.plainStripped(10000, 2))
        assertEquals("0", DecimalText.plainStripped(0, 2))
    }

    @Test
    fun parsePositiveAmountDecidesByTheTextsOwnDecimals() {
        // As `BigDecimal.scale()` decides: "12.300" has three decimals, though its value fits two.
        assertEquals(500_000L, DecimalText.parsePositiveAmount("5000", 2))
        assertEquals(500_050L, DecimalText.parsePositiveAmount("5000.50", 2))
        assertEquals(100_000L, DecimalText.parsePositiveAmount("1e3", 2))
        assertEquals(1L, DecimalText.parsePositiveAmount("0.01", 2))
        assertNull(DecimalText.parsePositiveAmount("12.300", 2))
        assertNull(DecimalText.parsePositiveAmount("1E-3", 2))
    }

    @Test
    fun parsePositiveAmountRefusesZeroNegativesAndWhatDoesNotFitALong() {
        assertNull(DecimalText.parsePositiveAmount("0", 2))
        assertNull(DecimalText.parsePositiveAmount("-0.00", 2))
        assertNull(DecimalText.parsePositiveAmount("-5", 2))
        assertEquals(500L, DecimalText.parsePositiveAmount("+5", 2))
        assertEquals(Long.MAX_VALUE, DecimalText.parsePositiveAmount("92233720368547758.07", 2))
        assertNull(DecimalText.parsePositiveAmount("92233720368547758.08", 2))
        assertNull(DecimalText.parsePositiveAmount("1E999999999", 2))
        assertNull(DecimalText.parsePositiveAmount("12,5", 2))
    }
}
