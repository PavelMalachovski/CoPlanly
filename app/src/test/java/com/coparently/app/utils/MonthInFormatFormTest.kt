package com.coparently.app.utils

import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals

/**
 * A month beside a day is written in its format (declined) form, never the stand-alone one the
 * platform's best pattern can hand back — "25 вересня", not "25 вересень".
 */
class MonthInFormatFormTest {

    private val date = LocalDate.of(2026, 9, 25)

    private fun format(pattern: String, language: String): String =
        date.format(DateTimeFormatter.ofPattern(monthInFormatForm(pattern), Locale.forLanguageTag(language)))

    @Test
    fun `a stand-alone month beside a day becomes the format form`() {
        assertEquals("EEEE d. MMM y", monthInFormatForm("EEEE d. LLL y"))
        assertEquals("d MMMM", monthInFormatForm("d LLLL"))
    }

    @Test
    fun `a month without a day keeps its stand-alone form`() {
        assertEquals("LLLL y", monthInFormatForm("LLLL y"))
    }

    @Test
    fun `quoted literals are left alone`() {
        assertEquals("d MMMM 'L'", monthInFormatForm("d LLLL 'L'"))
        assertEquals("'d' LLLL", monthInFormatForm("'d' LLLL"))
    }

    @Test
    fun `a full month inside a date is declined in every language`() {
        // The weekday is left out of the comparison: its spelling (the Ukrainian apostrophe)
        // moves between the CLDR versions of the JDKs this runs on; the month is the subject.
        assertEquals("25. září 2026", format("d. LLLL y", "cs"))
        assertEquals("25 сентября 2026", format("d LLLL y", "ru"))
        assertEquals("25 вересня 2026", format("d LLLL y", "uk"))
        assertEquals("25. September 2026", format("d. LLLL y", "de"))
        assertEquals("September 25, 2026", format("LLLL d, y", "en"))
    }
}
