package com.coparently.app.utils

import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate
import java.util.Locale

/**
 * The days of a day swap as the reader says them — "Fri, Nov 6, Sat, Nov 7" — in [locale]'s own
 * field order ([localizedDate]). The one wording the selection bar, the sheet, the push and the
 * answer card share, so a day reads the same on both phones. [SWAP_DAY_SKELETON] and the shared
 * screens' `swapDateList(dates)` are in `:shared-ui` (MON-24 phase 2); these keep a locale for the push,
 * which words in its own context's locale.
 */
fun swapDateList(dates: List<LocalDate>, locale: Locale): String {
    val format = localizedDate(SWAP_DAY_SKELETON, locale)
    return dates.joinToString(", ") { it.format(format) }
}

/** [swapDateList] for the custody domain's kotlinx-datetime days (MON-24); the same text. */
@JvmName("swapDateListOfDays")
fun swapDateList(dates: List<kotlinx.datetime.LocalDate>, locale: Locale): String =
    swapDateList(dates.map { it.toJavaLocalDate() }, locale)
