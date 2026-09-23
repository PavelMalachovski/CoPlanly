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
 * What this country calls the regions its holidays vary by — "State"/"Bundesland" for Germany —
 * or null for a country with none, which is every other country today.
 *
 * Per country rather than one generic "Region" label, because the word a German parent looks
 * for is the Land; a second country with regions would bring its own word.
 */
@StringRes
fun HolidayCountry.regionLabelRes(): Int? = when (this) {
    HolidayCountry.GERMANY -> R.string.holiday_region_label_de
    else -> null
}

/**
 * What region [code] is called on screen, or null for a code this build has no name for.
 *
 * Keyed on the stored code rather than on the domain's `GermanState`, which is internal to the
 * holiday tables; the codes are the ISO 3166-2 suffixes `HolidayCountry.regions` lists.
 */
@StringRes
fun holidayRegionLabelRes(code: String): Int? = REGION_LABELS[code]

/** Germany's sixteen Länder by ISO 3166-2 suffix; see `GermanState` for what each adds. */
private val REGION_LABELS: Map<String, Int> = mapOf(
    "BB" to R.string.region_de_bb,
    "BE" to R.string.region_de_be,
    "BW" to R.string.region_de_bw,
    "BY" to R.string.region_de_by,
    "HB" to R.string.region_de_hb,
    "HE" to R.string.region_de_he,
    "HH" to R.string.region_de_hh,
    "MV" to R.string.region_de_mv,
    "NI" to R.string.region_de_ni,
    "NW" to R.string.region_de_nw,
    "RP" to R.string.region_de_rp,
    "SH" to R.string.region_de_sh,
    "SL" to R.string.region_de_sl,
    "SN" to R.string.region_de_sn,
    "ST" to R.string.region_de_st,
    "TH" to R.string.region_de_th
)

/**
 * The name to show for [regionCode] in this country — the region's own name, or "Nationwide
 * only" when there is none.
 */
@Composable
fun HolidayCountry.regionName(regionCode: String?): String {
    val res = regionOrNull(regionCode)?.let { holidayRegionLabelRes(it) }
    return stringResource(res ?: R.string.holiday_region_none)
}

/**
 * The sentence under a country picker saying what choosing [this] actually draws (MON-13).
 *
 * One function for the wizard's chips and the Settings dialog, so the two cannot drift on **what
 * is admitted**. It reads [HolidayCountry.coverageIn], which is derived from the calendar the
 * grid would draw, so the note cannot promise school vacations a provider does not return —
 * Germany has them only with a Land — or call Ukraine's holidays "not in the app yet" when the
 * truth is that martial law suspended them.
 *
 * Where a country's school vacations are drawn but part of them is not — Czechia's and Slovakia's
 * spring breaks, Austria's semester and summer breaks, all set per region — the sentence says
 * which part, per country ([schoolNoteRes]). For a country with regions it also says whether the
 * region's own days are in: "nationwide only" would under-state a Bavarian calendar that has
 * Epiphany and the Bavarian school holidays on it.
 *
 * @param regionCode The region stored for this country, if any; ignored when it is not one of
 *   the country's regions.
 */
@Composable
fun HolidayCountry.coverageNote(regionCode: String? = null): String {
    val name = stringResource(labelRes())
    val region = regionOrNull(regionCode)
    return when (coverageIn(region)) {
        HolidayCoverage.PUBLIC_AND_SCHOOL -> when (region) {
            null -> stringResource(schoolNoteRes())
            else -> stringResource(R.string.country_holidays_with_region, regionName(region))
        }
        HolidayCoverage.PUBLIC_ONLY -> when {
            regions.isEmpty() -> stringResource(R.string.country_holidays_public_only, name)
            else -> stringResource(R.string.country_holidays_pick_region)
        }
        HolidayCoverage.SUSPENDED -> stringResource(R.string.country_holidays_suspended, name)
        HolidayCoverage.NONE -> stringResource(R.string.country_holidays_unavailable, name)
    }
}

/**
 * The note for a country whose nationwide calendar has school vacations, naming what part of the
 * school calendar is left out because it is set per region. Only the countries whose providers
 * return vacations without a region reach this; anything else gets the plain sentence.
 */
@StringRes
private fun HolidayCountry.schoolNoteRes(): Int = when (this) {
    HolidayCountry.SLOVAKIA -> R.string.country_holidays_school_sk
    HolidayCountry.AUSTRIA -> R.string.country_holidays_school_at
    else -> R.string.country_holidays_supported
}

/**
 * Picks the country whose public holidays the calendar draws (MON-13).
 *
 * Shared by the onboarding wizard's profile step and the Settings row so the two cannot drift on
 * what is offered — and, more importantly, on **what is admitted**. The supporting line under
 * the chips ([coverageNote]) states outright what the chosen country's calendar contains: public
 * holidays and school vacations for Czechia, Slovakia and Austria (each saying which regional
 * part is left out), public holidays for Germany with its Land's school vacations once a Land is
 * chosen, public holidays alone for Russia, and nothing — with the reason — for Ukraine and
 * "Other". A picker that offered a country and then quietly drew less than it implied would be
 * the affordance design rule 8 forbids, and one that drew *Czech* holidays for a German family is
 * the bug this whole item exists to fix.
 *
 * The region chips (MON-13, regional half) appear only when the chosen country has regions —
 * Germany's sixteen Länder, which add their own public holidays and school vacations — and only
 * when the caller takes a region at all, so a picker that has no region to store never offers
 * one.
 *
 * @param selected The country currently stored on the profile.
 * @param onSelect Called with the new country; the caller persists it.
 * @param selectedRegion The region currently chosen, or null for the nationwide calendar.
 * @param onSelectRegion Called with the new region (null for "nationwide only"); null hides the
 *   region chips entirely.
 */
@Composable
fun CountryPicker(
    selected: HolidayCountry,
    onSelect: (HolidayCountry) -> Unit,
    modifier: Modifier = Modifier,
    selectedRegion: String? = null,
    onSelectRegion: ((String?) -> Unit)? = null
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

        val regionLabel = selected.regionLabelRes()
        if (onSelectRegion != null && regionLabel != null && selected.regions.isNotEmpty()) {
            RegionChips(
                label = stringResource(regionLabel),
                regions = selected.regions,
                selectedRegion = selected.regionOrNull(selectedRegion),
                onSelectRegion = onSelectRegion
            )
        }

        Text(
            text = selected.coverageNote(selectedRegion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * The region chips under the country chips: "nationwide only" first, then each region by name.
 *
 * "Nationwide only" is a real choice, not an unset state — it is what every account had before
 * the field existed, and a parent unsure of the rule is better off with nine true days than with
 * a state's days they do not have.
 */
@Composable
private fun RegionChips(
    label: String,
    regions: List<String>,
    selectedRegion: String?,
    onSelectRegion: (String?) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilterChip(
            selected = selectedRegion == null,
            onClick = { onSelectRegion(null) },
            label = { Text(stringResource(R.string.holiday_region_none)) }
        )
        regions.forEach { code ->
            val name = holidayRegionLabelRes(code)?.let { stringResource(it) } ?: code
            FilterChip(
                selected = selectedRegion == code,
                onClick = { onSelectRegion(code) },
                label = { Text(name) }
            )
        }
    }
}
