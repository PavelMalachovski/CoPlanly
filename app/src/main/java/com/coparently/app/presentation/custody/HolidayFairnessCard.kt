package com.coparently.app.presentation.custody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.FairnessOccasion
import com.coparently.app.domain.custody.FairnessRow
import com.coparently.app.domain.custody.HolidayFairness
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.ParentColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The two schema slots, in the order every summary line lists them. */
private val SLOTS = listOf("mom", "dad")

/**
 * Holiday fairness at a glance (MON-20): nights per parent and who has each holiday, birthday and
 * school break, for this year or the next.
 *
 * **Read-only.** It counts what the calendar already shows — accepted swaps and seasonal layers
 * included, contact afternoons not counted as nights — and changes nothing. "Propose a change"
 * opens the seasonal-schedule editor, whose result is a proposal the co-parent must accept.
 * Parents are named through [ParentNames] and marked with [ParentColors], never "Mom"/"Dad".
 *
 * @param state The year and its summary.
 * @param parentNames Resolves a slot to that parent's name.
 * @param onSelectYear Switches the year.
 * @param onProposeChange Opens the editor, or null when there is no schedule to change.
 */
@Composable
fun HolidayFairnessCard(
    state: FairnessUiState,
    parentNames: ParentNames,
    onSelectYear: (Int) -> Unit,
    onProposeChange: (() -> Unit)?
) {
    var showAllHolidays by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel(text = stringResource(R.string.fairness_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.years.forEach { year ->
                FilterChip(
                    selected = state.year == year,
                    onClick = { onSelectYear(year) },
                    label = { Text(year.toString()) }
                )
            }
        }
        val fairness = state.fairness
        if (fairness == null) {
            Text(
                text = stringResource(R.string.fairness_no_schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FairnessRows(fairness, parentNames, showAllHolidays)
            TextButton(onClick = { showAllHolidays = !showAllHolidays }) {
                val toggle = if (showAllHolidays) R.string.fairness_hide_holidays else R.string.fairness_show_holidays
                Text(stringResource(toggle))
            }
            Text(
                text = stringResource(R.string.fairness_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        onProposeChange?.let {
            OutlinedButton(onClick = it) { Text(stringResource(R.string.fairness_propose)) }
        }
    }
}

/** The nights line and one row per occasion; other public holidays only when [showAll]. */
@Composable
private fun FairnessRows(fairness: HolidayFairness, parentNames: ParentNames, showAll: Boolean) {
    val rows = fairness.rows.filter { showAll || it.occasion !is FairnessOccasion.PublicHoliday }
    SectionGroup {
        SectionRow(
            title = stringResource(R.string.fairness_nights),
            icon = Icons.Default.Nightlight,
            trailing = { SlotCounts(fairness.nightsBySlot, parentNames) }
        )
        rows.forEach { row ->
            Divider()
            SectionRow(
                title = occasionLabel(row.occasion),
                supporting = datesLabel(row),
                trailing = {
                    SlotCounts(row.daysBySlot, parentNames, singleDay = row.dates.start == row.dates.endInclusive)
                }
            )
        }
    }
}

/**
 * Who has it: for a single day the parent's name with their colour, for a range each parent's
 * day count. A day nobody has — no schedule reaches it — reads as not set.
 */
@Composable
private fun SlotCounts(counts: Map<String, Int>, parentNames: ParentNames, singleDay: Boolean = false) {
    val present = SLOTS.filter { (counts[it] ?: 0) > 0 }
    if (present.isEmpty()) {
        Text(stringResource(R.string.fairness_not_set), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        present.forEach { slot ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ParentColors.fill(slot), CircleShape)
                )
                val label = parentNames.labelFor(slot)
                Text(
                    text = if (singleDay) label else "$label ${counts[slot]}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ParentColors.text(slot),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** An occasion's name: fixed ones from resources, the rest as their data says. */
@Composable
private fun occasionLabel(occasion: FairnessOccasion): String = when (occasion) {
    FairnessOccasion.ChristmasEve -> stringResource(R.string.fairness_christmas_eve)
    FairnessOccasion.ChristmasDay -> stringResource(R.string.fairness_christmas_day)
    FairnessOccasion.NewYearsDay -> stringResource(R.string.fairness_new_year)
    FairnessOccasion.Easter -> stringResource(R.string.fairness_easter)
    is FairnessOccasion.Birthday -> stringResource(R.string.fairness_birthday, occasion.childName)
    is FairnessOccasion.PublicHoliday -> localName(
        occasion.holiday.localLanguage,
        occasion.holiday.nameLocal,
        occasion.holiday.nameEn
    )
    is FairnessOccasion.SchoolVacation -> localName(occasion.localLanguage, occasion.nameLocal, occasion.nameEn)
}

/** The local name when the device speaks its language, English otherwise — `MonthView`'s rule. */
private fun localName(language: String, local: String, english: String): String =
    if (Locale.getDefault().language == language) local else english

private fun datesLabel(row: FairnessRow): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val start = row.dates.start.format(format)
    if (row.dates.start == row.dates.endInclusive) return start
    return "$start – ${row.dates.endInclusive.format(format)}"
}
