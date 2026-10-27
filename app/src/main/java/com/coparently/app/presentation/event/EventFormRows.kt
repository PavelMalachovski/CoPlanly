package com.coparently.app.presentation.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coparently.app.R
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.Spacing

/**
 * One line of the event form's date and time group: "Starts", then the date and, for an event
 * with times, the time — each its own tap target that opens its picker (UX audit H5).
 *
 * It replaced two outlined date cards with a "→" that read as navigation and two tall time cards:
 * four blocks and a screen of scrolling for two facts. From the large-font scale the label sits
 * above the two values instead of squeezing them.
 *
 * @param label "Starts" or "Ends".
 * @param date The date, as the row shows it.
 * @param time The time as the row shows it, or null for an all-day event, which has none.
 * @param dateDescription What TalkBack says for the date button: the full date and what it does.
 * @param timeDescription What TalkBack says for the time button.
 * @param onDate Opens the date picker.
 * @param onTime Opens the time picker.
 */
@Composable
@Suppress("LongParameterList") // a label, two values, their two descriptions and two pickers
internal fun EventDateTimeRow(
    label: String,
    date: String,
    time: String?,
    dateDescription: String,
    timeDescription: String,
    onDate: () -> Unit,
    onTime: () -> Unit
) {
    val values: @Composable () -> Unit = {
        TextButton(onClick = onDate, modifier = Modifier.semantics { contentDescription = dateDescription }) {
            Text(date, style = MaterialTheme.typography.bodyLarge)
        }
        time?.let {
            TextButton(onClick = onTime, modifier = Modifier.semantics { contentDescription = timeDescription }) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    if (stacksAtLargeFont()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) { values() }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.L, end = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            values()
        }
    }
}

/**
 * A choice among a few fixed options, as a dialog of radio rows — what the event form's repeat and
 * reminder rows open (UX audit H5), where fifteen chips used to sit in the form itself.
 *
 * Picking an option answers at once and closes the dialog, as Settings' theme and language pickers
 * do; there is no separate confirm.
 *
 * @param title The question.
 * @param options Each option's value and label, in order.
 * @param selected The value chosen now.
 * @param onSelect Called with the value picked.
 * @param onDismiss Closes the dialog without a choice.
 */
@Composable
internal fun <T> EventOptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                onClick = { onSelect(value) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.event_form_cancel)) }
        }
    )
}

/** A row's current value at its trailing end, in the primary colour, as Settings shows one. */
@Composable
internal fun OptionValue(value: String?) {
    value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
}
