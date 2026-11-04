package com.coparently.app.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The shared screens' date seam writes, on Android, exactly what `LocalizedDates.kt`'s `java.time`
 * functions wrote for the screens before they moved (MON-24 phase 2, Task 11), in every language
 * the app ships and on both clocks.
 */
class ReaderDateTextTest {

    private lateinit var saved: Locale

    @Before
    fun saveLocale() {
        saved = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(saved)
    }

    @Test
    fun `dates write what LocalizedDates wrote`() = inEveryLanguage { tag ->
        for (skeleton in listOf("EEEd", DAY_IN_SENTENCE, DAY_WITH_WEEKDAY, "yMMMM", "yMMMd")) {
            assertEquals(tag, day.toJavaLocalDate().format(localizedDate(skeleton)), ReaderDateText.date(day, skeleton))
            assertEquals(
                tag,
                moment.toJavaLocalDateTime().format(dateWithTime(skeleton)),
                ReaderDateText.dateWithTime(moment, skeleton)
            )
        }
        assertEquals(
            tag,
            java.time.YearMonth.of(2026, 10).atDay(1).format(localizedDate("yMMMM")),
            ReaderDateText.month(YearMonth(2026, 10), "yMMMM")
        )
        assertEquals(
            tag,
            java.time.YearMonth.of(2026, 10).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
            ReaderDateText.pattern(YearMonth(2026, 10), "LLLL yyyy")
        )
        assertEquals(
            tag,
            day.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
            ReaderDateText.pattern(day, "EEE")
        )
        assertEquals(
            tag,
            isoDateText("2026-10-03", DAY_IN_SENTENCE),
            ReaderDateText.isoDate("2026-10-03", DAY_IN_SENTENCE)
        )
        assertEquals("not a date", ReaderDateText.isoDate("not a date", DAY_IN_SENTENCE))
        assertEquals(
            tag,
            day.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            readerMediumDate(day)
        )
        assertEquals(
            tag,
            day.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
            readerLongDate(day)
        )
    }

    @Test
    fun `times write what LocalizedDates wrote - on both clocks`() = inEveryLanguage { tag ->
        for (is24 in listOf(true, false)) {
            val time = LocalTime(9, 5)
            assertEquals(
                tag,
                time.toJavaLocalTime().format(shortTime(is24Hour = is24)),
                ReaderDateText.time(time, is24)
            )
            assertEquals(
                tag,
                moment.toJavaLocalDateTime().format(shortTime(is24Hour = is24)),
                ReaderDateText.time(moment, is24)
            )
            for (hour in 0..23) assertEquals(tag, hourLabel(hour, is24Hour = is24), ReaderDateText.hour(hour, is24))
            val millis = 1_792_000_000_000L
            assertEquals(
                tag,
                answerByText(millis, ZoneId.of("Europe/Prague"), is24Hour = is24),
                ReaderDateText.answerBy(millis, TimeZone.of("Europe/Prague"), is24)
            )
        }
    }

    private fun inEveryLanguage(check: (tag: String) -> Unit) {
        for (tag in listOf("en-150", "en", "cs", "de", "ru", "uk")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            check(tag)
        }
    }

    private val day = LocalDate(2026, 10, 3)
    private val moment = LocalDateTime(2026, 10, 3, 15, 30)
}
