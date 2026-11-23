package com.coparently.app.utils

import kotlinx.datetime.LocalDate

/** A swapped day: weekday, day of month and short month. */
const val SWAP_DAY_SKELETON = "EEEdMMM"

/**
 * The days of a day swap as the reader says them — "Fri, Nov 6, Sat, Nov 7" — in the language the
 * app speaks ([ReaderDateText.date] with [SWAP_DAY_SKELETON]). The one wording the selection bar, the
 * sheet, the push and the answer card share, so a day reads the same on both phones.
 *
 * The shared screens' form (MON-24 phase 2, slice 11c). On Android it is the app's
 * `swapDateList(dates, locale)` in `Locale.getDefault()`, which is the locale the calendar's screens
 * passed (`LocalConfiguration`'s first, which the app's rule keeps equal to it); a push, worded outside
 * composition, keeps calling that one with the locale of its own context.
 *
 * On screen a line breaks only between two days, never inside one: each day's spaces are no-break
 * ([unbreakableSwapDay]), so "May" and "14" or "13." and "Mai" stay together on narrow columns and
 * at large fonts. The push keeps the formatter's text, which a notification lays out itself.
 */
fun swapDateList(dates: List<LocalDate>): String =
    dates.joinToString(", ") { unbreakableSwapDay(ReaderDateText.date(it, SWAP_DAY_SKELETON)) }

/**
 * [day] — one short swap day as a formatter wrote it ("Wed, May 13", "Mi., 13. Mai") — with every
 * space no-break, the one after the weekday's comma included: a swapped day is short enough to
 * keep whole, and a weekday alone at a line's end read as a day of its own.
 */
internal fun unbreakableSwapDay(day: String): String = day.replace(' ', NO_BREAK_SPACE)

/** U+00A0, which a line never ends at. */
private const val NO_BREAK_SPACE = '\u00A0'
