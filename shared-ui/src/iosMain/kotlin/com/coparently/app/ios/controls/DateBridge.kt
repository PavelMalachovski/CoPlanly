package com.coparently.app.ios.controls

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.defaultTimeZone

/*
 * How a day and a time cross into UIKit and back (MON-24 phase 3, part C, Task 3): as the calendar
 * day and the clock time the parent saw on the picker, through the calendar's components in the
 * phone's current zone, never through UTC millis. That is the guarantee `PickerDates` gives on
 * Android: no day is shifted east or west of Greenwich.
 */

/**
 * The calendar every picker and every conversion here uses: the Gregorian calendar in the phone's
 * current zone and language. Gregorian rather than the phone's own calendar, because a day here is a
 * kotlinx-datetime [LocalDate], which is ISO: under a Buddhist or Japanese calendar the components'
 * year would be another number for the same day. A new one each time, so a change of zone is read.
 */
internal fun pickerCalendar(): NSCalendar =
    NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
        timeZone = NSTimeZone.defaultTimeZone
        locale = NSLocale.currentLocale
    }

/** The instant a picker must be given to show [date]: noon of that day, clear of a DST change. */
internal fun nsDateFor(date: LocalDate): NSDate {
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.number.toLong()
        day = date.day.toLong()
        hour = NOON.toLong()
    }
    return checkNotNull(pickerCalendar().dateFromComponents(components)) { "No instant for $date" }
}

/** The day a picker shows for [date]: its calendar day in the phone's current zone. */
internal fun localDateOf(date: NSDate): LocalDate {
    val components = pickerCalendar().components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = date
    )
    return LocalDate(components.year.toInt(), components.month.toInt(), components.day.toInt())
}

/**
 * The instant a time picker must be given to show [time]: that clock time on a fixed day far from
 * any DST change (the day is not shown; only the hour and minute are read back).
 */
internal fun nsDateFor(time: LocalTime): NSDate {
    val components = NSDateComponents().apply {
        year = TIME_ANCHOR_YEAR.toLong()
        month = 1
        day = TIME_ANCHOR_DAY.toLong()
        hour = time.hour.toLong()
        minute = time.minute.toLong()
    }
    return checkNotNull(pickerCalendar().dateFromComponents(components)) { "No instant for $time" }
}

/** The hour and minute a time picker shows for [date], in the phone's current zone. */
internal fun localTimeOf(date: NSDate): LocalTime {
    val components = pickerCalendar().components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = date)
    return LocalTime(components.hour.toInt(), components.minute.toInt())
}

/** The hour a day is anchored at when it crosses into UIKit. */
private const val NOON = 12

/** The year of the fixed day a time is anchored on. */
private const val TIME_ANCHOR_YEAR = 2001

/** The day of January a time is anchored on (the 15th: no zone changes its offset then). */
private const val TIME_ANCHOR_DAY = 15
