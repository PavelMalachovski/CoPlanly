package com.coparently.app.utils

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
 */
fun localizedDate(skeleton: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    runCatching { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
        .getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

/** The locale's short time style: 24-hour or 12-hour as the language expects. */
fun shortTime(locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

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
