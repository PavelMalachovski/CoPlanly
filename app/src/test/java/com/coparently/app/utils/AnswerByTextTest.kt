package com.coparently.app.utils

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * How a request's deadline is said (phase 3a): the weekday, day and month in the reader's
 * language, then the time on the reader's own clock, in the reader's own zone.
 *
 * Robolectric, because the date comes from the platform's pattern for the language
 * (`DateFormat.getBestDateTimePattern`), which a plain JVM test does not have: there
 * `localizedDate` falls back to the medium style, which still names a day and a month but is not
 * what a phone shows. The assertions are the ones that hold either way — the day, the time and
 * where the time sits — so a platform difference cannot redden them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AnswerByTextTest {

    private val prague = ZoneId.of("Europe/Prague")

    /** Tuesday 13 October 2026, 10:00 in Prague. */
    private val deadline = LocalDateTime.of(2026, 10, 13, 10, 0).atZone(prague).toInstant().toEpochMilli()

    @Test
    fun `a deadline reads its weekday, day, month and the reader's time`() {
        val text = answerByText(deadline, prague, Locale.ENGLISH, is24Hour = true)

        assertTrue("\"$text\" names the day", text.contains("13"))
        assertTrue("\"$text\" names the time", text.contains("10:00"))
        assertTrue("\"$text\" puts the time after the date, set off by a comma", text.endsWith(", 10:00"))
    }

    @Test
    fun `the 12-hour clock says AM or PM`() {
        val text = answerByText(deadline, prague, Locale.ENGLISH, is24Hour = false)

        assertTrue("\"$text\" names the time", text.contains("10:00"))
        assertTrue("\"$text\" says AM", text.contains("AM"))
        assertTrue("\"$text\" puts the time after the date, set off by a comma", text.endsWith(", 10:00 AM"))
    }

    @Test
    fun `the day and the hour are the reader's zone's, not the sender's`() {
        // 01:30 on the 14th in Prague is 19:30 on the 13th in New York: the zone moves the date
        // as well as the hour.
        val lateInPrague = LocalDateTime.of(2026, 10, 14, 1, 30).atZone(prague).toInstant().toEpochMilli()
        val inPrague = answerByText(lateInPrague, prague, Locale.ENGLISH, is24Hour = true)
        val inNewYork = answerByText(lateInPrague, ZoneId.of("America/New_York"), Locale.ENGLISH, is24Hour = true)

        assertTrue("\"$inPrague\" is the 14th", inPrague.contains("14") && inPrague.endsWith(", 01:30"))
        assertTrue("\"$inNewYork\" is the 13th", inNewYork.contains("13") && inNewYork.endsWith(", 19:30"))
    }
}
