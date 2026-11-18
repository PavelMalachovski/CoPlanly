package com.coparently.app.presentation.widget

import android.content.Context
import android.text.format.DateFormat
import com.coparently.app.utils.format
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Locale

/**
 * [WidgetClock] on Android: the `java.time` calls the widget made before its wording moved to
 * `:shared-ui` — [localizedDate] for the title and [shortTime] for a time of day — in [context]'s
 * language and on [context]'s 12/24-hour clock, read from the device here
 * ([DateFormat.is24HourFormat]): a widget is drawn without `MainActivity` having run, so
 * `ClockFormat` may not have followed the setting.
 *
 * @param context The context the widget is worded in (`inAppLanguage()` in production).
 */
class AndroidWidgetClock(private val context: Context) : WidgetClock {

    private val locale: Locale get() = context.resources.configuration.locales[0]

    override fun titleDate(date: LocalDate): String = date.format(localizedDate(TITLE_SKELETON, locale))

    override fun time(time: LocalTime): String =
        time.format(shortTime(locale, DateFormat.is24HourFormat(context)))

    private companion object {
        /** "Thu, 19 Nov": the weekday, the day and the month, in the language's own order. */
        const val TITLE_SKELETON = "MMMEEEd"
    }
}
