package com.coparently.app.utils

import java.time.LocalDate
import java.util.Locale

/**
 * The days of a day swap as the reader says them — "Fri, Nov 6, Sat, Nov 7" — in [locale]'s own
 * field order ([localizedDate]). The one wording the selection bar, the sheet, the push and the
 * answer card share, so a day reads the same on both phones.
 */
fun swapDateList(dates: List<LocalDate>, locale: Locale): String {
    val format = localizedDate(SWAP_DAY_SKELETON, locale)
    return dates.joinToString(", ") { it.format(format) }
}

/** A swapped day: weekday, day of month and short month. */
const val SWAP_DAY_SKELETON = "EEEdMMM"
