package com.coparently.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * L-7: the Day and Week views' hour gutter follows the reader's clock.
 *
 * Plain JVM: `hourLabel` is two fixed `java.time` patterns, so the JDK's locale data is the same
 * data Android formats with.
 */
class HourLabelTest {

    @Test
    fun `a 24-hour clock keeps the bare two-digit hour`() {
        listOf(Locale.ENGLISH, Locale.forLanguageTag("cs"), Locale.GERMAN).forEach { locale ->
            assertEquals("00", hourLabel(0, locale, is24Hour = true))
            assertEquals("09", hourLabel(9, locale, is24Hour = true))
            assertEquals("13", hourLabel(13, locale, is24Hour = true))
            assertEquals("23", hourLabel(23, locale, is24Hour = true))
        }
    }

    @Test
    fun `a 12-hour clock names the hour with its day period`() {
        assertEquals("12 AM", hourLabel(0, Locale.US, is24Hour = false))
        assertEquals("9 AM", hourLabel(9, Locale.US, is24Hour = false))
        assertEquals("12 PM", hourLabel(12, Locale.US, is24Hour = false))
        assertEquals("1 PM", hourLabel(13, Locale.US, is24Hour = false))
    }

    @Test
    fun `the day-period marker is the language's own`() {
        val czech = Locale.forLanguageTag("cs")
        assertEquals("9 dop.", hourLabel(9, czech, is24Hour = false))
        assertEquals("1 odp.", hourLabel(13, czech, is24Hour = false))
    }

    @Test
    fun `no 12-hour label shows an hour past twelve`() {
        (0..23).forEach { hour ->
            val number = hourLabel(hour, Locale.US, is24Hour = false).substringBefore(' ').toInt()
            assertTrue("$hour -> $number", number in 1..12)
        }
    }
}
