package com.coparently.app.utils

import androidx.compose.runtime.mutableStateOf

/*
 * The parts of the app's date and time rules that need no platform (MON-24 phase 2, Task 11): the
 * reader's clock, the month's form inside a date, the unbreakable date and the skeletons the
 * screens name. The formatting itself is [ReaderDateText] (an `expect`); on Android its `actual`
 * calls `LocalizedDates.kt`'s `java.time` code, which stays in `androidMain` with every public
 * name it had in the app.
 */

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
        get() = followed.value ?: languageUses24Hour()

    /**
     * Follows the device's setting, [is24Hour] (on Android `ClockFormat.follow(context)` reads it,
     * whenever the app comes to the foreground).
     */
    fun follow(is24Hour: Boolean) {
        followed.value = is24Hour
    }
}

/** Whether the language the app speaks writes its short time on a 24-hour clock. */
internal expect fun languageUses24Hour(): Boolean

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
 * [text] — a formatted date — with its spaces made no-break, so a line never ends between a day
 * and its month ("4 | октября"). A space after a comma stays breakable — the weekday of a full
 * date ("суббота, 4 октября 2026 г.") may still take a line of its own when the rest does not fit —
 * except after a short weekday that opens the date ("Mi., 13. Mai", "сб, 16 трав.") and before a
 * time ("13. Mai, 20:00"): an abbreviation or a time alone on a line reads as a fragment.
 *
 * @param text a date as a formatter wrote it
 * @return the same text, unbreakable between its parts
 */
fun unbreakableDate(text: String): String = buildString(text.length) {
    text.forEachIndexed { index, c ->
        val afterComma = index > 0 && text[index - 1] == ','
        val breakable = afterComma &&
            !opensWithShortWeekday(text, index - 1) &&
            !startsWithTime(text, index + 1)
        append(if (c == ' ' && !breakable) NO_BREAK_SPACE else c)
    }
}

/** Whether the text before the comma at [commaIndex] is one short word that opens [text]: a weekday's abbreviation. */
private fun opensWithShortWeekday(text: String, commaIndex: Int): Boolean {
    val word = text.substring(0, commaIndex)
    return word.isNotEmpty() && word.length <= SHORT_WEEKDAY_MAX && word.none { it == ' ' || it == NO_BREAK_SPACE }
}

/** Whether a time of day ("20:00", "8:00 PM") starts at [index] of [text]. */
private fun startsWithTime(text: String, index: Int): Boolean {
    val digits = text.drop(index).takeWhile { it.isDigit() }.length
    return digits in 1..2 &&
        text.getOrNull(index + digits) == ':' &&
        text.getOrNull(index + digits + 1)?.isDigit() == true
}

/** The longest weekday abbreviation a formatter writes before a comma ("Mi.", "Wed", "сб", "Thu."). */
private const val SHORT_WEEKDAY_MAX = 5

/**
 * [pattern] — a date pattern a platform wrote for a skeleton — with the spaces between its fields
 * made no-break, as [unbreakableDate] does to the text: "MMM d" keeps "May 14" on one line, while
 * a space after a comma ("EEEE, MMM d") stays a place to break — but not after an abbreviated
 * weekday ("EEE, MMM d": "Wed, May 14" stays whole, never "Mi., | 13. Mai") nor before an hour
 * ("d MMM, HH:mm"). Spaces inside a quoted literal are the language's own words and are left alone.
 *
 * @param pattern a `java.time` or Unicode date pattern
 * @return the same pattern, unbreakable between its fields
 */
fun noBreakPattern(pattern: String): String = buildString(pattern.length) {
    var quoted = false
    pattern.forEachIndexed { index, c ->
        if (c == '\'') quoted = !quoted
        val afterComma = index > 0 && pattern[index - 1] == ','
        val breakable = afterComma && !commaJoinsItsFields(pattern, index - 1)
        append(if (c == ' ' && !quoted && !breakable) NO_BREAK_SPACE else c)
    }
}

/**
 * Whether the comma at [commaIndex] of [pattern] follows an abbreviated weekday (`E`, `EE`, `EEE`,
 * `c`/`e` up to three) or comes before an hour field: the two places a break would orphan a part.
 */
private fun commaJoinsItsFields(pattern: String, commaIndex: Int): Boolean {
    val before = pattern.getOrNull(commaIndex - 1)
    var run = 0
    while (commaIndex - 1 - run >= 0 && pattern[commaIndex - 1 - run] == before) run++
    val shortWeekday = before != null && before in WEEKDAY_FIELDS && run <= SHORT_FIELD_MAX
    val next = pattern.drop(commaIndex + 1).firstOrNull { it != ' ' }
    return shortWeekday || (next != null && next in HOUR_FIELDS)
}

private const val WEEKDAY_FIELDS = "Eec"
private const val HOUR_FIELDS = "hHkKjJ"
private const val SHORT_FIELD_MAX = 3

/**
 * [text] — a time of day as a formatter wrote it, "3:30 PM" — with every space no-break, so the
 * day-period marker never takes a line of its own ("3:30 | PM"). A narrow no-break space a platform
 * already wrote (U+202F) is kept.
 */
fun unbreakableTime(text: String): String = text.replace(' ', NO_BREAK_SPACE)

/**
 * [separator] — what stands between a date and its time, " · " — with its spaces made no-break, so
 * a date and its time stay one piece: never "12 May ·" at a line's end and "20:15" alone below it.
 */
fun unbreakableSeparator(separator: String): String = separator.replace(' ', NO_BREAK_SPACE)

/**
 * The separator between the parts of a meta line ("Pavel answered · 12 May · 20:15"): a no-break
 * space before the dot and a plain one after it, so a line may end on a dot but never start with
 * one.
 */
const val META_SEPARATOR: String = "\u00A0· "

/**
 * The comma between a deadline's day and its time ("Wed, May 13, 8:00 PM"): no-break, so the time
 * never takes a line of its own.
 */
const val ANSWER_BY_TIME_SEPARATOR: String = ",\u00A0"

/**
 * The separator of a time range within one day ("3:30 PM – 4:30 PM"): no-break on both sides of
 * the en dash, so the range is one piece and never leaves "– 16:30" or "16:30" alone on a line.
 */
const val TIME_RANGE_SEPARATOR: String = "\u00A0–\u00A0"

/**
 * The separator of a range across days ("Wed, May 13 – Thu, May 14"): a no-break space before the
 * en dash and a plain one after it, so a wrapped line starts with the end, never with the dash.
 */
const val DAY_RANGE_SEPARATOR: String = "\u00A0– "

private const val NO_BREAK_SPACE = '\u00A0'

/**
 * The skeleton for a day inside a sentence: day and month in full, no weekday, no year
 * ("3 октября", "am 3. Oktober"). A formatter's weekday is nominative, which breaks the case the
 * sentence around it asks for in Czech, Russian and Ukrainian ("поменяться на суббота").
 */
const val DAY_IN_SENTENCE = "MMMMd"

/** The skeleton for a day standing on its own or after a colon: with its weekday, no year. */
const val DAY_WITH_WEEKDAY = "MMMMEEEEd"
