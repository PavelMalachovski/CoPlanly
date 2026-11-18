package com.coparently.app.ios

import com.coparently.app.presentation.widget.WidgetClock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.timeZoneWithName

/**
 * The Today widget's [WidgetClock] on the iPhone (phase 4 part D): Foundation's wording in the
 * language [languageTag] names — the app's resolved language (`readerLanguageTag`), the one the
 * snapshot is worded in — on the Gregorian calendar, as Android's `AndroidWidgetClock` writes with
 * `java.time`.
 *
 * The title's date is the template `MMMEEEd`, Android's `localizedDate("MMMEEEd")` skeleton, which
 * Foundation orders and punctuates for the language ("Thu, 19 Nov", "čt 19. 11."); a time is
 * Foundation's short time style in that language, its 12- or 24-hour clock the language's. The
 * values are zone-less wall clocks, so every formatter runs in UTC: the phone's zone, and a
 * daylight-saving gap in it, never shifts what is written.
 *
 * @param languageTag The language the snapshot is worded in (`cs`, `en-150`, …).
 */
class IosWidgetClock(languageTag: String) : WidgetClock {

    private val locale = NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(languageTag))

    private val dates: NSDateFormatter = NSDateFormatter().apply {
        locale = this@IosWidgetClock.locale
        calendar = gregorian
        timeZone = utc
        setLocalizedDateFormatFromTemplate(TITLE_TEMPLATE)
    }

    private val times: NSDateFormatter = NSDateFormatter().apply {
        locale = this@IosWidgetClock.locale
        calendar = gregorian
        timeZone = utc
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }

    override fun titleDate(date: LocalDate): String =
        nsDate(LocalDateTime(date, LocalTime(0, 0)))?.let { dates.stringFromDate(it) } ?: date.toString()

    override fun time(time: LocalTime): String =
        nsDate(LocalDateTime(REFERENCE_DATE, time))?.let { times.stringFromDate(it) } ?: time.toString()

    private companion object {
        /** Android's skeleton for the title's date (`TodayWidgetText`'s `widget_today_title`). */
        const val TITLE_TEMPLATE = "MMMEEEd"

        /** Any day: a time's text does not depend on it. */
        val REFERENCE_DATE = LocalDate(2000, 1, 1)

        val utc: NSTimeZone = NSTimeZone.timeZoneWithName("UTC")!!

        val gregorian: NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
            timeZone = utc
        }

        /** [dateTime] as the instant it names in UTC, or null when the calendar cannot build it. */
        fun nsDate(dateTime: LocalDateTime): NSDate? = gregorian.dateFromComponents(dateTime.toNSDateComponents())
    }
}
