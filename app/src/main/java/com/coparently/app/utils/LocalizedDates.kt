package com.coparently.app.utils

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A date formatter in [locale]'s own field order for [skeleton] (docs/AUDIT-2026-10-design.md
 * D-18). "EEEd" is "Wed 23" in English and "St 23." in Czech; "MMMMEEEEd" is "Saturday,
 * October 3" and "суббота, 3 октября".
 *
 * A skeleton names the fields, and the platform orders and punctuates them for the language. A
 * fixed pattern such as "EEEE, MMM dd, yyyy" put English word order around Russian words
 * ("четверг, окт. 01, 2026"). Built per call, never held in a top-level `val`, which would
 * capture the locale once per process and keep the previous language after Settings →
 * Language. Time is kept out of the skeleton on purpose: the platform's preferred-hour pattern
 * can contain letters `java.time` on older Android cannot parse. Use [shortTime] for the time.
 * Falls back to the medium date style if the platform pattern is refused anyway, or where there
 * is no platform (a plain JVM test).
 *
 * The platform's pattern goes through [monthInFormatForm] first: it can name the month in its
 * stand-alone form (`LLL`/`LLLL`) beside a day, which is the nominative in Russian and Ukrainian
 * ("25 вересень" for "25 вересня").
 */
fun localizedDate(skeleton: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    runCatching {
        DateTimeFormatter.ofPattern(monthInFormatForm(DateFormat.getBestDateTimePattern(locale, skeleton)), locale)
    }.getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

/**
 * [pattern] with a stand-alone month (`L`) written in its format form (`M`) whenever the pattern
 * also carries a day of the month (`d`) — a month inside a date is declined ("25 сентября",
 * "25. září"), a month on its own is not ("září 2026"). Quoted literals are left alone.
 *
 * @param pattern a `java.time` date pattern
 * @return the pattern, with `L` turned into `M` when a day stands beside it
 */
fun monthInFormatForm(pattern: String): String {
    var quoted = false
    var hasDay = false
    for (c in pattern) {
        if (c == '\'') quoted = !quoted else if (!quoted && c == 'd') hasDay = true
    }
    if (!hasDay) return pattern
    quoted = false
    return buildString(pattern.length) {
        for (c in pattern) {
            if (c == '\'') quoted = !quoted
            append(if (!quoted && c == 'L') 'M' else c)
        }
    }
}

/**
 * The time of day as the reader's clock shows it: "15:30" or "3:30 PM" (release audit R-9, owner
 * decision — the reader's setting, not one format for everybody).
 *
 * The app used to print both on one screen: fixed "HH:mm" in twenty places and the locale's
 * short style in the rest, so Home's week said "10:00 AM" over a today card saying "15:30". Every
 * time the app shows goes through here now. Two fixed patterns rather than the platform's
 * preferred-hour skeleton, whose letters `java.time` on older Android cannot always parse.
 *
 * @param locale The language, for the day-period marker of a 12-hour clock
 * @param is24Hour Whether the reader's clock is 24-hour; [ClockFormat] by default
 */
fun shortTime(
    locale: Locale = Locale.getDefault(),
    is24Hour: Boolean = ClockFormat.is24Hour
): DateTimeFormatter =
    if (is24Hour) {
        DateTimeFormatter.ofPattern(PATTERN_24_HOUR, locale)
    } else {
        DateTimeFormatter.ofPattern(PATTERN_12_HOUR, locale)
    }

/**
 * A date in [locale]'s own order for [skeleton], then [separator], then the time on the reader's
 * clock ([shortTime]) — "Wed, Sep 2 · 3:30 PM", "st 2. 9. · 15:30".
 */
fun dateWithTime(
    skeleton: String,
    separator: String = " · ",
    locale: Locale = Locale.getDefault()
): DateTimeFormatter =
    DateTimeFormatterBuilder()
        .append(localizedDate(skeleton, locale))
        .appendLiteral(separator)
        .append(shortTime(locale))
        .toFormatter(locale)

/**
 * [text] — a formatted date — with its spaces made no-break, so a line never ends between a day
 * and its month ("4 | октября"). A space after a comma stays breakable: the weekday of a full
 * date ("суббота, 4 октября 2026 г.") may still take a line of its own when the rest does not fit.
 *
 * @param text a date as a formatter wrote it
 * @return the same text, unbreakable between its parts
 */
fun unbreakableDate(text: String): String = buildString(text.length) {
    text.forEachIndexed { index, c ->
        val afterComma = index > 0 && text[index - 1] == ','
        append(if (c == ' ' && !afterComma) NO_BREAK_SPACE else c)
    }
}

private const val NO_BREAK_SPACE = ' '
private const val PATTERN_24_HOUR = "HH:mm"
private const val PATTERN_12_HOUR = "h:mm a"

/**
 * Whether the reader's clock is 24-hour: the device's "Use 24-hour format" setting, which
 * `MainActivity` and the widget read through [follow] — or, until one of them has, whatever the
 * language expects, which is also what a JVM test and a screenshot see.
 *
 * Held in Compose state, so a screen that formatted a time recomposes when the setting changes
 * while the app was in the background.
 */
object ClockFormat {

    private val followed = mutableStateOf<Boolean?>(null)

    /** Whether times are written on a 24-hour clock. */
    val is24Hour: Boolean
        get() = followed.value ?: localeUses24Hour(Locale.getDefault())

    /** Reads the device setting; called whenever the app comes to the foreground. */
    fun follow(context: Context) {
        followed.value = DateFormat.is24HourFormat(context)
    }

    /** Whether [locale]'s own short time style is 24-hour. */
    fun localeUses24Hour(locale: Locale): Boolean =
        runCatching {
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                null,
                FormatStyle.SHORT,
                IsoChronology.INSTANCE,
                locale
            ).contains('H')
        }.getOrDefault(true)
}

/**
 * An ISO date (`2026-10-03`, the form dates travel in) written the way [locale] writes
 * [skeleton], or [iso] unchanged when it is not a date — a payload from another build must still
 * say something rather than nothing.
 */
fun isoDateText(iso: String?, skeleton: String, locale: Locale = Locale.getDefault()): String {
    val text = iso.orEmpty()
    return try {
        LocalDate.parse(text).format(localizedDate(skeleton, locale))
    } catch (e: DateTimeParseException) {
        text
    }
}

/**
 * The skeleton for a day inside a sentence: day and month in full, no weekday, no year
 * ("3 октября", "am 3. Oktober"). A formatter's weekday is nominative, which breaks the case the
 * sentence around it asks for in Czech, Russian and Ukrainian ("поменяться на суббота").
 */
const val DAY_IN_SENTENCE = "MMMMd"

/** The skeleton for a day standing on its own or after a colon: with its weekday, no year. */
const val DAY_WITH_WEEKDAY = "MMMMEEEEd"
