package com.coparently.app.presentation.common

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
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.holidays.HolidayCoverage
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.country_at
import com.coparently.app.resources.country_cz
import com.coparently.app.resources.country_de
import com.coparently.app.resources.country_holidays_pick_region
import com.coparently.app.resources.country_holidays_public_only
import com.coparently.app.resources.country_holidays_school_at
import com.coparently.app.resources.country_holidays_school_sk
import com.coparently.app.resources.country_holidays_supported
import com.coparently.app.resources.country_holidays_suspended
import com.coparently.app.resources.country_holidays_unavailable
import com.coparently.app.resources.country_holidays_with_region
import com.coparently.app.resources.country_holidays_with_region_generic
import com.coparently.app.resources.country_holidays_with_region_sk
import com.coparently.app.resources.country_other
import com.coparently.app.resources.country_ru
import com.coparently.app.resources.country_sk
import com.coparently.app.resources.country_ua
import com.coparently.app.resources.holiday_region_label
import com.coparently.app.resources.holiday_region_label_de
import com.coparently.app.resources.holiday_region_label_sk
import com.coparently.app.resources.holiday_region_none
import com.coparently.app.resources.holiday_region_settings_summary
import com.coparently.app.resources.holiday_region_settings_summary_generic
import com.coparently.app.resources.holiday_region_settings_summary_sk
import com.coparently.app.resources.region_de_bb
import com.coparently.app.resources.region_de_be
import com.coparently.app.resources.region_de_bw
import com.coparently.app.resources.region_de_by
import com.coparently.app.resources.region_de_hb
import com.coparently.app.resources.region_de_he
import com.coparently.app.resources.region_de_hh
import com.coparently.app.resources.region_de_mv
import com.coparently.app.resources.region_de_ni
import com.coparently.app.resources.region_de_nw
import com.coparently.app.resources.region_de_rp
import com.coparently.app.resources.region_de_sh
import com.coparently.app.resources.region_de_sl
import com.coparently.app.resources.region_de_sn
import com.coparently.app.resources.region_de_st
import com.coparently.app.resources.region_de_th
import com.coparently.app.resources.region_sk_bc
import com.coparently.app.resources.region_sk_bl
import com.coparently.app.resources.region_sk_ki
import com.coparently.app.resources.region_sk_ni
import com.coparently.app.resources.region_sk_pv
import com.coparently.app.resources.region_sk_ta
import com.coparently.app.resources.region_sk_tc
import com.coparently.app.resources.region_sk_zi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What this country is called on screen.
 *
 * The mapping lives here rather than on the enum because `HolidayCountry` is a domain type and
 * `Res` is not available to it — the same split `FamilyKind` and `ParentLabels` already use.
 */
fun HolidayCountry.labelRes(): StringResource = when (this) {
    HolidayCountry.CZECHIA -> Res.string.country_cz
    HolidayCountry.SLOVAKIA -> Res.string.country_sk
    HolidayCountry.GERMANY -> Res.string.country_de
    HolidayCountry.AUSTRIA -> Res.string.country_at
    HolidayCountry.UKRAINE -> Res.string.country_ua
    HolidayCountry.RUSSIA -> Res.string.country_ru
    HolidayCountry.OTHER -> Res.string.country_other
}

/**
 * The words a country with regions uses for them: what the picker is titled, what the Settings
 * row says the choice does, and the note shown once one is chosen (`%1$s` is the region's name).
 */
private data class RegionWording(
    val label: StringResource,
    val summary: StringResource,
    val withRegion: StringResource
)

/**
 * The [RegionWording] for this country, or null for a country with no regions.
 *
 * Per country, because the word a German parent looks for is the Land and a Slovak parent's is
 * the kraj — and what a region adds differs too: a Land adds public holidays and school
 * vacations, a kraj only its spring week. Any other country that gains [HolidayCountry.regions]
 * gets the generic wording, so the picker appears for it without a change here (MON-13).
 */
private fun HolidayCountry.regionWording(): RegionWording? = when {
    regions.isEmpty() -> null
    this == HolidayCountry.GERMANY -> RegionWording(
        label = Res.string.holiday_region_label_de,
        summary = Res.string.holiday_region_settings_summary,
        withRegion = Res.string.country_holidays_with_region
    )
    this == HolidayCountry.SLOVAKIA -> RegionWording(
        label = Res.string.holiday_region_label_sk,
        summary = Res.string.holiday_region_settings_summary_sk,
        withRegion = Res.string.country_holidays_with_region_sk
    )
    else -> RegionWording(
        label = Res.string.holiday_region_label,
        summary = Res.string.holiday_region_settings_summary_generic,
        withRegion = Res.string.country_holidays_with_region_generic
    )
}

/**
 * What this country calls the regions its holidays vary by — "State"/"Bundesland" for Germany,
 * "Region (kraj)"/"Kraj" for Slovakia — or null for a country with none.
 */
fun HolidayCountry.regionLabelRes(): StringResource? = regionWording()?.label

/** What the Settings row says choosing a region adds, or null for a country with none. */
fun HolidayCountry.regionSummaryRes(): StringResource? = regionWording()?.summary

/**
 * What region [code] of this country is called on screen, or null for a code this build has no
 * name for.
 *
 * Keyed on the country *and* the stored code — `NI` is Lower Saxony in Germany and Nitra in
 * Slovakia — rather than on the domain's `GermanState`/`SlovakRegion`, which are internal to the
 * holiday tables; the codes are the ISO 3166-2 suffixes `HolidayCountry.regions` lists.
 */
fun HolidayCountry.regionNameRes(code: String): StringResource? = when (this) {
    HolidayCountry.GERMANY -> GERMAN_REGION_LABELS[code]
    HolidayCountry.SLOVAKIA -> SLOVAK_REGION_LABELS[code]
    else -> null
}

/**
 * Slovakia's eight kraje by ISO 3166-2 suffix; see `SlovakRegion` for what each adds. Their names
 * are proper names in Slovak, the form a Slovak school letter uses, and are not translated.
 */
private val SLOVAK_REGION_LABELS: Map<String, StringResource> = mapOf(
    "BC" to Res.string.region_sk_bc,
    "BL" to Res.string.region_sk_bl,
    "KI" to Res.string.region_sk_ki,
    "NI" to Res.string.region_sk_ni,
    "PV" to Res.string.region_sk_pv,
    "TA" to Res.string.region_sk_ta,
    "TC" to Res.string.region_sk_tc,
    "ZI" to Res.string.region_sk_zi
)

/** Germany's sixteen Länder by ISO 3166-2 suffix; see `GermanState` for what each adds. */
private val GERMAN_REGION_LABELS: Map<String, StringResource> = mapOf(
    "BB" to Res.string.region_de_bb,
    "BE" to Res.string.region_de_be,
    "BW" to Res.string.region_de_bw,
    "BY" to Res.string.region_de_by,
    "HB" to Res.string.region_de_hb,
    "HE" to Res.string.region_de_he,
    "HH" to Res.string.region_de_hh,
    "MV" to Res.string.region_de_mv,
    "NI" to Res.string.region_de_ni,
    "NW" to Res.string.region_de_nw,
    "RP" to Res.string.region_de_rp,
    "SH" to Res.string.region_de_sh,
    "SL" to Res.string.region_de_sl,
    "SN" to Res.string.region_de_sn,
    "ST" to Res.string.region_de_st,
    "TH" to Res.string.region_de_th
)

/**
 * The name to show for [regionCode] in this country — the region's own name, or "Nationwide
 * only" when there is none.
 */
@Composable
fun HolidayCountry.regionName(regionCode: String?): String {
    val res = regionOrNull(regionCode)?.let { regionNameRes(it) }
    return stringResource(res ?: Res.string.holiday_region_none)
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
 * Where a country's school vacations are drawn but part of them is not — Czechia's spring break,
 * Austria's semester and summer breaks, Slovakia's spring holidays until a kraj is chosen, all set
 * per region — the sentence says which part, per country ([schoolNoteRes]). For a country with
 * regions it also says whether the region's own days are in, in that country's words
 * ([regionWording]): "nationwide only" would under-state a Bavarian calendar that has Epiphany and
 * the Bavarian school holidays on it, or a Bratislava one with its spring week.
 *
 * @param regionCode The region stored for this country, if any; ignored when it is not one of
 *   the country's regions.
 */
@Composable
fun HolidayCountry.coverageNote(regionCode: String? = null): String {
    val name = stringResource(labelRes())
    val region = regionOrNull(regionCode)
    val withRegion = regionWording()?.withRegion
    return when (coverageIn(region)) {
        HolidayCoverage.PUBLIC_AND_SCHOOL -> when {
            region == null || withRegion == null -> stringResource(schoolNoteRes())
            else -> stringResource(withRegion, regionName(region))
        }
        HolidayCoverage.PUBLIC_ONLY -> when {
            regions.isEmpty() -> stringResource(Res.string.country_holidays_public_only, name)
            else -> stringResource(Res.string.country_holidays_pick_region)
        }
        HolidayCoverage.SUSPENDED -> stringResource(Res.string.country_holidays_suspended, name)
        HolidayCoverage.NONE -> stringResource(Res.string.country_holidays_unavailable, name)
    }
}

/**
 * The note for a country whose nationwide calendar has school vacations, naming what part of the
 * school calendar is left out because it is set per region. Only the countries whose providers
 * return vacations without a region reach this; anything else gets the plain sentence.
 */
private fun HolidayCountry.schoolNoteRes(): StringResource = when (this) {
    HolidayCountry.SLOVAKIA -> Res.string.country_holidays_school_sk
    HolidayCountry.AUSTRIA -> Res.string.country_holidays_school_at
    else -> Res.string.country_holidays_supported
}

/**
 * Picks the country whose public holidays the calendar draws (MON-13).
 *
 * Shared by the onboarding wizard's profile step and the Settings row so the two cannot drift on
 * what is offered — and, more importantly, on **what is admitted**. The supporting line under
 * the chips ([coverageNote]) states outright what the chosen country's calendar contains: public
 * holidays and school vacations for Czechia, Slovakia and Austria (each saying which regional
 * part is left out — for Slovakia, until a kraj is chosen), public holidays for Germany with its
 * Land's school vacations once a Land is chosen, public holidays alone for Russia, and nothing —
 * with the reason — for Ukraine and
 * "Other". A picker that offered a country and then quietly drew less than it implied would be
 * the affordance design rule 8 forbids, and one that drew *Czech* holidays for a German family is
 * the bug this whole item exists to fix.
 *
 * The region chips (MON-13, regional half) appear only when the chosen country has regions —
 * Germany's sixteen Länder, which add their own public holidays and school vacations, and
 * Slovakia's eight kraje, which add their spring holidays — and only when the caller takes a
 * region at all, so a picker that has no region to store never offers one.
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
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
                country = selected,
                label = stringResource(regionLabel),
                selectedRegion = selected.regionOrNull(selectedRegion),
                onSelectRegion = onSelectRegion
            )
        }

        Text(
            text = selected.coverageNote(selectedRegion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.S)
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
    country: HolidayCountry,
    label: String,
    selectedRegion: String?,
    onSelectRegion: (String?) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.M, bottom = Spacing.XS)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = Modifier.fillMaxWidth()
    ) {
        FilterChip(
            selected = selectedRegion == null,
            onClick = { onSelectRegion(null) },
            label = { Text(stringResource(Res.string.holiday_region_none)) }
        )
        country.regions.forEach { code ->
            val name = country.regionNameRes(code)?.let { stringResource(it) } ?: code
            FilterChip(
                selected = selectedRegion == code,
                onClick = { onSelectRegion(code) },
                label = { Text(name) }
            )
        }
    }
}
