package com.coparently.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.holidays.HolidayCoverage

/**
 * What this country is called on screen.
 *
 * The mapping lives here rather than on the enum because `HolidayCountry` is a domain type and
 * `R` is not available to it — the same split `FamilyKind` and `ParentLabels` already use.
 */
@StringRes
fun HolidayCountry.labelRes(): Int = when (this) {
    HolidayCountry.CZECHIA -> R.string.country_cz
    HolidayCountry.SLOVAKIA -> R.string.country_sk
    HolidayCountry.GERMANY -> R.string.country_de
    HolidayCountry.AUSTRIA -> R.string.country_at
    HolidayCountry.UKRAINE -> R.string.country_ua
    HolidayCountry.RUSSIA -> R.string.country_ru
    HolidayCountry.OTHER -> R.string.country_other
}

/**
 * The sentence under a country picker saying what choosing [this] actually draws (MON-13).
 *
 * One function for the wizard's chips and the Settings dialog, so the two cannot drift on **what
 * is admitted**. It reads [HolidayCountry.coverage], which is derived from the provider, so the
 * note cannot promise school vacations a provider does not return — only Czechia has them — or
 * call Ukraine's holidays "not in the app yet" when the truth is that martial law suspended them.
 */
@Composable
fun HolidayCountry.coverageNote(): String {
    val name = stringResource(labelRes())
    return when (coverage) {
        HolidayCoverage.PUBLIC_AND_SCHOOL -> stringResource(R.string.country_holidays_supported)
        HolidayCoverage.PUBLIC_ONLY -> stringResource(R.string.country_holidays_public_only, name)
        HolidayCoverage.SUSPENDED -> stringResource(R.string.country_holidays_suspended, name)
        HolidayCoverage.NONE -> stringResource(R.string.country_holidays_unavailable, name)
    }
}

/**
 * Picks the country whose public holidays the calendar draws (MON-13).
 *
 * Shared by the onboarding wizard's profile step and the Settings row so the two cannot drift on
 * what is offered — and, more importantly, on **what is admitted**. The supporting line under
 * the chips ([coverageNote]) states outright what the chosen country's calendar contains: public
 * holidays and school vacations for Czechia, public holidays alone for four others, and nothing
 * — with the reason — for Ukraine and "Other". A picker that offered a country and then quietly
 * drew less than it implied would be the affordance design rule 8 forbids, and one that drew
 * *Czech* holidays for a German family is the bug this whole item exists to fix.
 *
 * @param selected The country currently stored on the profile.
 * @param onSelect Called with the new country; the caller persists it.
 */
@Composable
fun CountryPicker(
    selected: HolidayCountry,
    onSelect: (HolidayCountry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HolidayCountry.entries.forEach { country ->
                FilterChip(
                    selected = selected == country,
                    onClick = { onSelect(country) },
                    label = { Text(stringResource(country.labelRes())) }
                )
            }
        }

        Text(
            text = selected.coverageNote(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
