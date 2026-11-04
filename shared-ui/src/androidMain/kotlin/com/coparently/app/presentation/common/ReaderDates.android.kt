package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The configuration's first locale, as `UiText` read it before it moved here: in composition
 * `LocalConfiguration`, which follows the Activity, so AppCompat's per-app choice.
 */
@Composable
internal actual fun rememberReaderDates(): ReaderDates {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { JavaTimeDates(locale) }
}

/** `java.time`'s wording in [locale]: the medium localized date, the full day name. */
private class JavaTimeDates(private val locale: Locale) : ReaderDates {

    override fun date(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date.toJavaLocalDate())

    override fun weekday(day: DayOfWeek): String =
        day.toJavaDayOfWeek().getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
}
