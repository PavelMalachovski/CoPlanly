package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalTime

/**
 * A lesson's `BeginTime` or `EndTime` (MON-8), read as `LocalTime.parse(text, ofPattern("H:mm"))`
 * read it before MON-24, and failing that as `LocalTime.parse(text)` (`ISO_LOCAL_TIME`).
 *
 * The pattern `H:mm`, parsed strictly and resolved smartly, as `java.time` documents it: the hour
 * is one to nineteen ASCII digits with no sign, a colon, the minute exactly two ASCII digits, and
 * nothing after; hour 0–23 and minute 0–59, except that `24:00` is midnight. `BakalariTimeParityTest`
 * holds both halves to `java.time`.
 */
internal object BakalariTime {

    private const val MAX_HOUR_DIGITS = 19
    private const val HOURS_PER_DAY = 24
    private const val MAX_MINUTE = 59
    private const val MINUTE_DIGITS = 2

    /** [text] as a time of day, or null when neither form reads it. */
    fun parse(text: String): LocalTime? =
        hourMinute(text) ?: try {
            IsoText.parseTime(text)
        } catch (ignored: IllegalArgumentException) {
            null
        }

    /** [text] in the pattern `H:mm`, or null when the pattern refuses it. */
    private fun hourMinute(text: String): LocalTime? {
        val hourDigits = text.takeWhile { it in '0'..'9' }
        val minuteDigits = text.substring(hourDigits.length).removePrefix(":")
        val shaped = hourDigits.length in 1..MAX_HOUR_DIGITS &&
            text.length == hourDigits.length + 1 + MINUTE_DIGITS && text[hourDigits.length] == ':' &&
            minuteDigits.all { it in '0'..'9' }
        // Leading zeros are any number of digits of nothing; past two significant digits the hour
        // is out of range however many there are.
        val significant = hourDigits.trimStart('0')
        if (!shaped || significant.length > 2) return null
        val hour = significant.toIntOrNull() ?: 0
        val minute = minuteDigits.toInt()
        return when {
            minute > MAX_MINUTE -> null
            hour < HOURS_PER_DAY -> LocalTime(hour, minute)
            hour == HOURS_PER_DAY && minute == 0 -> LocalTime(0, 0)
            else -> null
        }
    }
}
