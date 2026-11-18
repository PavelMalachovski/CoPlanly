package com.coparently.app.presentation.widget

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The dates and times the Today widget writes, in the reader's language and on the reader's clock.
 *
 * A seam rather than `ReaderDateText`, because the widget is worded with no composition and, on the
 * iPhone, in a process of its own: Android's half is the app's `AndroidWidgetClock`, which calls the
 * `java.time` functions the widget called before its wording moved here (`localizedDate` and
 * `shortTime`), in the language of the context the widget is worded in and with that context's
 * 12/24-hour setting.
 */
interface WidgetClock {
    /** The title's date, as Android's localizedDate("MMMEEEd") writes it ("Thu, 19 Nov"). */
    fun titleDate(date: LocalDate): String

    /** A time of day on the reader's clock, as the today card writes it. */
    fun time(time: LocalTime): String
}
