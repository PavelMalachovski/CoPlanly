package com.coparently.app.utils

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaDayOfWeek
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.format.TextStyle
import java.util.Locale

/**
 * [readerName] writes, on Android, exactly what the custody screens wrote with `java.time` before
 * they moved (MON-24 phase 2, slice 11d): `DayOfWeek.getDisplayName(TextStyle.SHORT, FULL or
 * NARROW, Locale.getDefault())` — the contact windows' and the midweek chips' "EEE", the midweek
 * sentence's "EEEE", the conflict screen's "EEEEE" — for all seven days, in every language the app
 * ships and in the English fallback, `en-150`.
 */
class WeekdayNamesTest {

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
    fun `every day reads as java time named it`() {
        for (tag in listOf("en-150", "en", "cs", "de", "ru", "uk")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            for (day in DayOfWeek.entries) {
                for ((pattern, style) in STYLES) {
                    assertEquals(
                        "$tag $day $pattern",
                        day.toJavaDayOfWeek().getDisplayName(style, Locale.getDefault()),
                        day.readerName(pattern)
                    )
                }
            }
        }
    }

    private companion object {
        /** Each pattern the screens pass and the text style the call it replaced asked for. */
        val STYLES = listOf("EEE" to TextStyle.SHORT, "EEEE" to TextStyle.FULL, "EEEEE" to TextStyle.NARROW)
    }
}
