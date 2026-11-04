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
 */
fun swapDateList(dates: List<LocalDate>): String =
    dates.joinToString(", ") { ReaderDateText.date(it, SWAP_DAY_SKELETON) }
