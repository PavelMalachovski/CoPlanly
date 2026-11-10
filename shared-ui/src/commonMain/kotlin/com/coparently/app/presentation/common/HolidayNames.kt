package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import com.coparently.app.domain.holidays.CzechSchoolBreak
import com.coparently.app.domain.holidays.Holiday
import com.coparently.app.resources.Res
import com.coparently.app.resources.holiday_break_cz_autumn
import com.coparently.app.resources.holiday_break_cz_christmas
import com.coparently.app.resources.holiday_break_cz_easter
import com.coparently.app.resources.holiday_break_cz_summer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    if (readerLanguage() == localLanguage) return nameLocal
    val schoolBreak = CzechSchoolBreak.of(localLanguage, nameLocal) ?: return nameEn
    return stringResource(schoolBreak.nameRes())
}

/** [holidayDisplayName] for a [Holiday]. */
@Composable
fun Holiday.displayName(): String = holidayDisplayName(nameEn, nameLocal, localLanguage)

/** The string resource naming a Czech school break in the reader's language. */
fun CzechSchoolBreak.nameRes(): StringResource = when (this) {
    CzechSchoolBreak.SUMMER -> Res.string.holiday_break_cz_summer
    CzechSchoolBreak.CHRISTMAS -> Res.string.holiday_break_cz_christmas
    CzechSchoolBreak.AUTUMN -> Res.string.holiday_break_cz_autumn
    CzechSchoolBreak.EASTER -> Res.string.holiday_break_cz_easter
}

/**
 * The language the app speaks, as an ISO 639 code: on Android `Locale.getDefault().language`, the
 * locale `AppLocale.follow` sets (what this rule read before it moved); on the iPhone the current
 * locale's language.
 */
internal expect fun readerLanguage(): String
