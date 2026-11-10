package com.coparently.app.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iPhone's date seam: zone-less wall clocks, and the owner's language rule for the locale. */
class ReaderDateTextIosTest {

    private val originalZone = NSTimeZone.defaultTimeZone

    @AfterTest
    fun restore() {
        NSTimeZone.setDefaultTimeZone(originalZone)
        preferredLanguagesForTest = null
    }

    private fun zone(name: String) = NSTimeZone.setDefaultTimeZone(NSTimeZone.timeZoneWithName(name)!!)

    @Test
    fun `a date reads the same after the default time zone changes`() {
        preferredLanguagesForTest = listOf("en-US")
        val day = LocalDate(2026, 3, 29)
        zone("Europe/Prague")
        val first = ReaderDateText.date(day, "yMMMd")
        zone("Europe/London")
        val second = ReaderDateText.date(day, "yMMMd")
        assertEquals(first, second)
        assertTrue(first.contains("29"), first)
    }

    @Test
    fun `a wall-clock time on a spring-forward night is printed as written`() {
        zone("Europe/Prague")
        assertEquals("02:30", ReaderDateText.time(LocalTime(2, 30), is24Hour = true))
    }

    @Test
    fun `dateWithTime follows the clock it is given`() {
        preferredLanguagesForTest = listOf("en-US")
        val text = ReaderDateText.dateWithTime(LocalDateTime(2026, 1, 5, 18, 0), "MMMd", " ", is24Hour = false)
        assertTrue(text.endsWith("6:00 PM"), text)
    }

    @Test
    fun `the reader's locale follows the owner's rule`() {
        preferredLanguagesForTest = listOf("sk-SK", "cs-CZ")
        assertEquals("en_150", readerLocale().localeIdentifier)
        preferredLanguagesForTest = listOf("cs-CZ")
        assertEquals("cs_CZ", readerLocale().localeIdentifier)
        preferredLanguagesForTest = listOf("de")
        assertEquals("de", readerLocale().localeIdentifier)
    }
}
