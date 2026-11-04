package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * The iPhone's current locale, renewed when Compose's locale changes. Which locale the iPhone
 * app's own language choice sets is phase 3's to decide.
 */
@Composable
internal actual fun rememberReaderDates(): ReaderDates {
    val composeLocale = Locale.current
    return remember(composeLocale) { FoundationDates(NSLocale.currentLocale) }
}

/**
 * Foundation's wording in [readerLocale]: the medium date style and the full day name, on the
 * Gregorian calendar that `java.time`'s ISO dates use on Android.
 */
private class FoundationDates(private val readerLocale: NSLocale) : ReaderDates {

    private val gregorian = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)

    override fun date(date: LocalDate): String {
        val instant = gregorian.dateFromComponents(date.toNSDateComponents()) ?: return date.toString()
        val formatter = NSDateFormatter()
        formatter.locale = readerLocale
        formatter.calendar = gregorian
        formatter.dateStyle = NSDateFormatterMediumStyle
        formatter.timeStyle = NSDateFormatterNoStyle
        return formatter.stringFromDate(instant)
    }

    override fun weekday(day: DayOfWeek): String {
        val formatter = NSDateFormatter()
        formatter.locale = readerLocale
        formatter.calendar = gregorian
        // Foundation counts from Sunday (index 0), ISO from Monday (1) to Sunday (7).
        val name = formatter.weekdaySymbols?.getOrNull(day.isoDayNumber % DAYS_IN_WEEK) as? String
            ?: day.name.lowercase()
        return name.replaceFirstChar { it.titlecase() }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
