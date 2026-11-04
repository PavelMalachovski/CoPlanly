package com.coparently.app.utils

import kotlinx.datetime.YearMonth

/**
 * [month] as the reader's language names it, capitalised: "October 2026", "Říjen 2026" — the
 * month's own form "LLLL yyyy" in the language the app speaks, with its first letter in title
 * case, as the Expenses month header, the expense form's other-month warning and child support's
 * months write it (MON-24 phase 2, slice 11e).
 *
 * Kotlin's `titlecase()` writes what `titlecase(Locale.getDefault())` wrote before the screens
 * moved, because none of the five languages, nor `en-150`, cases a letter of its own; an initial
 * already in capitals is left as it is, as the `isLowerCase()` guard left it.
 */
fun readerMonthTitle(month: YearMonth): String =
    ReaderDateText.pattern(month, "LLLL yyyy").replaceFirstChar { it.titlecase() }
