package com.coparently.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.holidays.CzechSchoolBreak
import com.coparently.app.domain.holidays.Holiday
import java.util.Locale

/**
 * The one rule for naming a public holiday or school vacation on screen (L-8, September 2026).
 *
 * 1. The calendar's own name ([nameLocal]) when the device speaks its language ([localLanguage]) —
 *    the rule `MonthView` applied first, so a Czech reader sees exactly what the table holds.
 * 2. Otherwise, a recurring Czech school break ([CzechSchoolBreak]) in the reader's language from
 *    this app's resources, so a German reader sees "Weihnachtsferien" rather than the English name.
 * 3. Otherwise the English name, the fallback every locale had before.
 *
 * Every screen that names a holiday — Day view's label, the month cell's description, the holiday
 * fairness card, the seasonal layer's suggestions — goes through here, so the rule cannot differ
 * between them.
 */
@Composable
fun holidayDisplayName(nameEn: String, nameLocal: String, localLanguage: String): String {
    if (Locale.getDefault().language == localLanguage) return nameLocal
    val schoolBreak = CzechSchoolBreak.of(localLanguage, nameLocal) ?: return nameEn
    return stringResource(schoolBreak.nameRes())
}

/** [holidayDisplayName] for a [Holiday]. */
@Composable
fun Holiday.displayName(): String = holidayDisplayName(nameEn, nameLocal, localLanguage)

/** The string resource naming a Czech school break in the reader's language. */
@StringRes
fun CzechSchoolBreak.nameRes(): Int = when (this) {
    CzechSchoolBreak.SUMMER -> R.string.holiday_break_cz_summer
    CzechSchoolBreak.CHRISTMAS -> R.string.holiday_break_cz_christmas
    CzechSchoolBreak.AUTUMN -> R.string.holiday_break_cz_autumn
    CzechSchoolBreak.EASTER -> R.string.holiday_break_cz_easter
}
