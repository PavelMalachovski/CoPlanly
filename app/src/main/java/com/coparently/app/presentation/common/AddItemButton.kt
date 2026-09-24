package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing

/**
 * The button that adds a row to a list a form holds: a medication, an allergy, a school, a
 * contact window (docs/AUDIT-2026-10-design.md D-20).
 *
 * Filled tonal, the tier between a form's one filled Save and its outlined alternatives. The app
 * used none before, so "add another" carried the same weight as "No thanks". One anatomy, so
 * every list adds the same way: a plus at the button icon size, then the label. The label names
 * the button, so the plus says nothing to TalkBack; it used to announce "Add" before "Add
 * medication".
 *
 * @param label What is added, e.g. "Add medication"
 * @param onClick Opens the row's editor
 * @param modifier Modifier for the button
 */
@Composable
fun AddItemButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(IconSizes.Small))
        Spacer(modifier = Modifier.width(Spacing.S))
        Text(label)
    }
}
