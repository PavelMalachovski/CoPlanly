package com.coparently.app.data.money

import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The payment types hold minor units since MON-24 (45000 is 450.00), where they held a
 * `BigDecimal`. What the readers accepted then, they accept now: any text `toBigDecimalOrNull()`
 * reads, rounded half up to two decimals — which is what `PaymentQr.amountText` wrote it as before
 * (`setScale(2, HALF_UP)`), and what a card, a QR code and the stored payment showed.
 */
class MinorUnitsTest {

    @Test
    fun `a stored two-decimal amount is its cents`() {
        assertEquals(45_000L, MinorUnits.fromText("450.00"))
        assertEquals(500_000L, MinorUnits.fromText("5000.00"))
        assertEquals(1L, MinorUnits.fromText("0.01"))
        assertEquals(0L, MinorUnits.fromText("0.00"))
        assertEquals(10L, MinorUnits.fromText("0.10"))
        assertEquals(-1_234L, MinorUnits.fromText("-12.34"))
    }

    @Test
    fun `only an overflow is no amount, never an error`() {
        // The first version caught everything, and on API 26–30 BigInteger.longValueExact (API 31)
        // threw NoSuchMethodError, which read every stored amount as missing. A JVM test cannot
        // run at that API level; it can hold the catch to the overflow alone.
        val broken = object : BigDecimal("450.00") {
            override fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal =
                throw NoSuchMethodError("simulated: a call this API level does not have")

            // Kotlin's Number declares these two abstract; BigDecimal leaves them to Number.
            override fun toByte(): Byte = toInt().toByte()
            override fun toShort(): Short = toInt().toShort()
        }
        assertFailsWith<NoSuchMethodError> { MinorUnits.fromDecimal(broken) }
        assertNull(MinorUnits.fromDecimal(BigDecimal("1E30")))
    }

    @Test
    fun `whatever toBigDecimalOrNull read is still read`() {
        assertEquals(45_000L, MinorUnits.fromText("450"))
        assertEquals(129_050L, MinorUnits.fromText("1290.5"))
        assertEquals(100_000L, MinorUnits.fromText("1E3"))
        assertEquals(500L, MinorUnits.fromText("+5"))
        assertEquals(-500L, MinorUnits.fromText("-5"))
    }

    @Test
    fun `more than two places round half up, as amountText wrote them`() {
        assertEquals(1_235L, MinorUnits.fromText("12.345"))
        assertEquals(1_234L, MinorUnits.fromText("12.3449"))
        assertEquals(1_230L, MinorUnits.fromText("12.300"))
        assertEquals(-1_235L, MinorUnits.fromText("-12.345"))
        assertEquals(
            BigDecimal("12.345").setScale(2, RoundingMode.HALF_UP).toPlainString(),
            BigDecimal.valueOf(MinorUnits.fromText("12.345")!!, 2).toPlainString()
        )
    }

    @Test
    fun `what is not a number, or does not fit, is nothing`() {
        assertNull(MinorUnits.fromText(null))
        assertNull(MinorUnits.fromText(""))
        assertNull(MinorUnits.fromText("abc"))
        assertNull(MinorUnits.fromText("12,50"))
        assertNull(MinorUnits.fromText("1E30"))
    }

    @Test
    fun `a double is read as BigDecimal valueOf reads it`() {
        // 2.675 is stored as 2.67499999…; BigDecimal.valueOf reads its text, "2.675".
        assertEquals(268L, MinorUnits.fromDouble(2.675))
        assertEquals(4_500L, MinorUnits.fromDouble(45.0))
        assertEquals(1L, MinorUnits.fromDouble(0.005))
    }

    @Test
    fun `minor units format as the decimal they stand for`() {
        assertEquals(BigDecimal("450.00"), MinorUnits.toDecimal(45_000L))
        assertEquals("0.05", MinorUnits.toDecimal(5L).toPlainString())
    }
}
