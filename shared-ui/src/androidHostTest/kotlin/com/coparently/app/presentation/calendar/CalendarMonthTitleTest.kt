package com.coparently.app.presentation.calendar

import com.coparently.app.presentation.calendar.components.calendarMonthTitle
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

/**
 * The calendar header's title (calendar K1): the month with its year, always, this year's
 * included — the reverse of D-3's "the year only outside this one".
 */
class CalendarMonthTitleTest {

    @Test
    fun `this year's month carries its year`() {
        withLocale(Locale.ENGLISH) {
            val title = calendarMonthTitle(LocalDate(2026, 11, 1))
            assertEquals("November", title.month)
            assertEquals("2026", title.year)
            assertEquals("November 2026", title.text)
        }
    }

    @Test
    fun `another year's month carries its own year`() {
        withLocale(Locale.ENGLISH) {
            assertEquals("January 2027", calendarMonthTitle(LocalDate(2027, 1, 15)).text)
        }
    }

    @Test
    fun `the month name is capitalised where the language writes it in lower case`() {
        withLocale(Locale.forLanguageTag("cs")) {
            assertEquals("Listopad 2026", calendarMonthTitle(LocalDate(2026, 11, 3)).text)
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val before = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(before)
        }
    }
}
