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

/**
 * The skeleton for a day inside a sentence: day and month in full, no weekday, no year
 * ("3 октября", "am 3. Oktober"). A formatter's weekday is nominative, which breaks the case the
 * sentence around it asks for in Czech, Russian and Ukrainian ("поменяться на суббота").
 */
const val DAY_IN_SENTENCE = "MMMMd"

/** The skeleton for a day standing on its own or after a colon: with its weekday, no year. */
const val DAY_WITH_WEEKDAY = "MMMMEEEEd"
