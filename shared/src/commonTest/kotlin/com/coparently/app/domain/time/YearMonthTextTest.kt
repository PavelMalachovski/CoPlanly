package com.coparently.app.domain.time

import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The month text a payment, child support and a settle-up card carry (MON-24 phase 2, Task 8e):
 * what `java.time.YearMonth` wrote and read before, as literals computed from it, so the iPhone is
 * held to the same bytes. `YearMonthTextParityTest` compares the two on random input.
 */
class YearMonthTextTest {

    @Test
    fun aMonthIsWrittenAsYearDashMonth() {
        assertEquals("2026-10", IsoText.yearMonth(YearMonth(2026, 10)))
        assertEquals("2026-01", IsoText.yearMonth(YearMonth(2026, 1)))
        assertEquals("9999-12", IsoText.yearMonth(YearMonth(9999, 12)))
    }

    @Test
    fun aYearOutsideFourDigitsIsWrittenAsJavaTimeWritesIt() {
        assertEquals("0000-03", IsoText.yearMonth(YearMonth(0, 3)))
        assertEquals("0999-03", IsoText.yearMonth(YearMonth(999, 3)))
        assertEquals("-0001-03", IsoText.yearMonth(YearMonth(-1, 3)))
        assertEquals("-0999-03", IsoText.yearMonth(YearMonth(-999, 3)))
        assertEquals("-1000-03", IsoText.yearMonth(YearMonth(-1000, 3)))
        // No plus sign: where java.time's YearMonth differs from its LocalDate and from kotlinx.
        assertEquals("10000-03", IsoText.yearMonth(YearMonth(10_000, 3)))
        assertEquals("-10000-03", IsoText.yearMonth(YearMonth(-10_000, 3)))
    }

    @Test
    fun aMonthIsReadAsJavaTimeReadsIt() {
        assertEquals(YearMonth(2026, 10), IsoText.parseYearMonth("2026-10"))
        assertEquals(YearMonth(-1, 1), IsoText.parseYearMonth("-0001-01"))
        assertEquals(YearMonth(0, 1), IsoText.parseYearMonth("0000-01"))
        assertEquals(YearMonth(12_026, 10), IsoText.parseYearMonth("+12026-10"))
    }

    @Test
    fun whatJavaTimeRefusesIsRefused() {
        listOf(
            "", "2026-1", "2026-13", "2026-00", "26-10", "2026-10-01", " 2026-10", "2026-10 ", "2026/10",
            "+2026-10", "12026-10", "-0000-01", "٢٠٢٦-١٠"
        ).forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { IsoText.parseYearMonth(text) }
        }
    }
}
