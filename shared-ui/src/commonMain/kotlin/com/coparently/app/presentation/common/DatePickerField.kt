package com.coparently.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.coparently.app.presentation.theme.Spacing
import kotlinx.datetime.LocalDate

/**
 * How a form shows a date it opens a picker for: an outlined field with its label, the date in
 * the reader's format (or empty), and the calendar glyph.
 *
 * One anatomy for every date on a form (release audit R-7). The expense form's date had it since
 * D-7; a date of birth was an outlined *button* on My details, the child and pet forms and
 * onboarding's About you — a second look for the same control, and one whose label vanished once
 * a date was chosen.
 *
 * The field is disabled so it takes neither focus nor the keyboard, and drawn in the enabled
 * colours, because the disabled grey made the control look switched off. The tap is the
 * modifier's, announced as a button with the label as its action.
 *
 * Where the shell has native controls (the iPhone) and the caller gives [date] and [onDateChange],
 * the field is the label with the system's compact date pill beside it
 * ([NativeControls.CompactDate]), which opens the system's own calendar; [onClick] is then unused.
 * A caller that gives neither keeps the field and its picker there too. Android ignores both.
 *
 * @param label What the date is ("Date of birth").
 * @param value The date, already formatted for the reader, or null while none is chosen.
 * @param onClick Opens the picker.
 * @param enabled False while the form is saving.
 * @param modifier Applied to the field; it fills the width by default.
 * @param date The date as a day, or null while none is chosen, for the native pill.
 * @param onDateChange Receives the day picked in the native pill, or null to keep the field there.
 */
@Composable
// A label, the shown value, the picker, two states, and the day the iPhone's pill edits in place.
@Suppress("LongParameterList")
fun DatePickerField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    onDateChange: ((LocalDate) -> Unit)? = null
) {
    val native = nativeControls()
    if (native != null && onDateChange != null) {
        NativeDateRow(native, label, date, onDateChange.takeIf { enabled } ?: {}, modifier)
    } else {
        MaterialDateField(label, value, onClick, enabled, modifier)
    }
}

/** The label and the system's compact date pill on one line, as the iPhone's own forms show a date. */
@Composable
private fun NativeDateRow(
    native: NativeControls,
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        native.CompactDate(value = date, onChange = onDateChange, label = label, modifier = Modifier)
    }
}

/** The outlined field every form drew before native controls, and Android still draws. */
@Composable
private fun MaterialDateField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = { Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null) },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
    )
}
