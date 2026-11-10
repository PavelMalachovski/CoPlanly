package com.coparently.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

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
 * @param label What the date is ("Date of birth").
 * @param value The date, already formatted for the reader, or null while none is chosen.
 * @param onClick Opens the picker.
 * @param enabled False while the form is saving.
 * @param modifier Applied to the field; it fills the width by default.
 */
@Composable
fun DatePickerField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
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
