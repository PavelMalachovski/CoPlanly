package com.coparently.app.presentation.custody

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.components.TimePickerDialog
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.dimensions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Where a new window's times start, before the parent picks: the classic after-school afternoon. */
private val DEFAULT_WINDOW_START: LocalTime = LocalTime.of(15, 0)

/** See [DEFAULT_WINDOW_START]. */
private val DEFAULT_WINDOW_END: LocalTime = LocalTime.of(19, 0)

/** How a window's times are written on screen: 24-hour, as the time picker uses. */
private val WINDOW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The contact windows of the pattern being set up (MON-6b), under every pattern type.
 *
 * A window is part of a day with the parent who does not have that day — "every Wednesday
 * 15:00–19:00". It sits on top of the whole-day pattern and changes no day's owner, which is why
 * the section says so in its hint: a parent reading "add a window" must not think they are
 * moving the overnight. The MON-6 midweek day above it (for every-other-weekend) is the
 * whole-day shape and stays exactly as it was; its warning now points here for the
 * afternoon-only case instead of to `CUSTOM`, which could never express one.
 *
 * Each window is listed by the week of the cycle and weekday it falls on, computed from the
 * pattern's start date — so moving the start date re-labels the windows rather than silently
 * moving them to other weekdays behind the parent's back.
 *
 * @param uiState The form: its windows, its start date (day 0) and its cycle length.
 * @param parentNames Resolves a slot to that parent's name.
 * @param onAdd Adds the windows a confirmed draft describes.
 * @param onRemove Removes one window.
 */
@Composable
fun ContactWindowsSection(
    uiState: CustodySetupUiState,
    parentNames: ParentNames,
    onAdd: (ContactWindowDraft) -> Unit,
    onRemove: (ContactWindow) -> Unit
) {
    val dims = dimensions()
    val windows = uiState.contactWindows
    var showEditor by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.custody_windows_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = dims.paddingSmall)
        )
        Text(
            text = stringResource(R.string.custody_windows_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(dims.paddingSmall))

        if (windows.isEmpty()) {
            Text(
                text = stringResource(R.string.custody_windows_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        windows.forEach { window ->
            ContactWindowRow(
                window = window,
                dayLabel = cycleDayLabel(uiState.startDate, window.dayIndex),
                parentName = parentNames.labelFor(window.parent),
                onRemove = { onRemove(window) }
            )
        }

        OutlinedButton(
            onClick = { showEditor = true },
            modifier = Modifier.padding(vertical = dims.paddingSmall)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(IconSizes.Small))
            Spacer(modifier = Modifier.width(Spacing.S))
            Text(stringResource(R.string.custody_windows_add))
        }
        Spacer(modifier = Modifier.height(dims.paddingMedium))
    }

    if (showEditor) {
        ContactWindowDialog(
            startDate = uiState.startDate,
            cycleDays = uiState.cycleDays,
            parentNames = parentNames,
            onConfirm = { draft ->
                onAdd(draft)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

/** One window: a dot in its parent's colour, what and when, and a remove action. */
@Composable
private fun ContactWindowRow(
    window: ContactWindow,
    dayLabel: String,
    parentName: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(ParentColors.fill(window.parent), CircleShape)
        )
        Spacer(modifier = Modifier.width(Spacing.S))
        Text(
            text = stringResource(
                R.string.custody_window_summary,
                dayLabel,
                window.start.format(WINDOW_TIME),
                window.end.format(WINDOW_TIME),
                parentName
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.custody_window_remove)
            )
        }
    }
}

/** "Week 2, Wed" for a cycle day, from the pattern's start date. */
@Composable
private fun cycleDayLabel(startDate: LocalDate, dayIndex: Int): String = stringResource(
    R.string.custody_window_day_label,
    dayIndex / ContactWindowDraft.DAYS_PER_WEEK + 1,
    startDate.plusDays(dayIndex.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
)

/**
 * The editor for a new window: weekday, which week of the cycle (or every week), from and to,
 * and which parent. Refuses to confirm a window that does not end after it starts — the rule
 * `ContactWindow` enforces — and says why rather than greying the button out in silence.
 */
@Composable
private fun ContactWindowDialog(
    startDate: LocalDate,
    cycleDays: Int,
    parentNames: ParentNames,
    onConfirm: (ContactWindowDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var weekday by rememberSaveable { mutableStateOf(DayOfWeek.WEDNESDAY) }
    var week by rememberSaveable { mutableStateOf<Int?>(null) }
    var start by rememberSaveable { mutableStateOf(DEFAULT_WINDOW_START) }
    var end by rememberSaveable { mutableStateOf(DEFAULT_WINDOW_END) }
    var parent by rememberSaveable { mutableStateOf(ContactWindow.SLOT_TWO) }
    var picking by remember { mutableStateOf<TimeField?>(null) }
    val draft = ContactWindowDraft(weekday, week, start, end, parent)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custody_window_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WeekdayChips(selected = weekday, onSelect = { weekday = it })
                WeekChips(weeks = ContactWindowDraft.weeksIn(cycleDays), selected = week, onSelect = { week = it })
                TimeButtons(draft = draft, onPick = { picking = it })
                ParentChips(selected = parent, parentNames = parentNames, onSelect = { parent = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = draft.toWindows(startDate, cycleDays).isNotEmpty()
            ) {
                Text(stringResource(R.string.custody_window_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custody_cancel))
            }
        }
    )

    when (picking) {
        TimeField.START -> TimePickerDialog(
            initialTime = start,
            onTimeSelected = { start = it },
            onDismiss = { picking = null }
        )
        TimeField.END -> TimePickerDialog(
            initialTime = end,
            onTimeSelected = { end = it },
            onDismiss = { picking = null }
        )
        null -> Unit
    }
}

/** Monday to Sunday, named in the reader's language. */
@Composable
private fun WeekdayChips(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    DialogLabel(R.string.custody_window_weekday)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = selected == day,
                onClick = { onSelect(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
            )
        }
    }
}

/**
 * "Every week" and one chip per week of the cycle; absent for a one-week cycle, where the
 * choice would be a set of one (the FAM-1 rule: an affordance appears at two).
 */
@Composable
private fun WeekChips(weeks: Int, selected: Int?, onSelect: (Int?) -> Unit) {
    if (weeks <= 1) return
    DialogLabel(R.string.custody_window_week)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.custody_window_every_week)) }
        )
        for (index in 0 until weeks) {
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(R.string.custody_window_week_n, index + 1)) }
            )
        }
    }
}

/** From and To, each opening the time picker; says why an inverted pair cannot be added. */
@Composable
private fun TimeButtons(draft: ContactWindowDraft, onPick: (TimeField) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = Modifier.padding(top = Spacing.M)
    ) {
        OutlinedButton(onClick = { onPick(TimeField.START) }) {
            Text(stringResource(R.string.custody_window_from, draft.start.format(WINDOW_TIME)))
        }
        OutlinedButton(onClick = { onPick(TimeField.END) }) {
            Text(stringResource(R.string.custody_window_to, draft.end.format(WINDOW_TIME)))
        }
    }
    if (!draft.isValid) {
        Text(
            text = stringResource(R.string.custody_window_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = Spacing.XS)
        )
    }
}

/** The two parents, by name and colour; a window may name either. */
@Composable
private fun ParentChips(selected: String, parentNames: ParentNames, onSelect: (String) -> Unit) {
    DialogLabel(R.string.custody_window_with)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        listOf(ContactWindow.SLOT_ONE, ContactWindow.SLOT_TWO).forEach { slot ->
            FilterChip(
                selected = selected == slot,
                onClick = { onSelect(slot) },
                label = { Text(parentNames.labelFor(slot)) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(ParentColors.fill(slot), CircleShape)
                    )
                }
            )
        }
    }
}

/** Which of the two times the picker is open for. */
private enum class TimeField { START, END }

/** A small label over one of the dialog's chip rows. */
@Composable
private fun DialogLabel(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.M, bottom = Spacing.XS)
    )
}
