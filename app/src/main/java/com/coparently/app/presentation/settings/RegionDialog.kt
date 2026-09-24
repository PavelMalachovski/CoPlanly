package com.coparently.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.presentation.common.coverageNote
import com.coparently.app.presentation.common.regionLabelRes
import com.coparently.app.presentation.common.regionNameRes

/**
 * Picks the region whose own public holidays are added to [country]'s (MON-13, regional half).
 *
 * The same anatomy as the country dialog in `SettingsScreen`, and reached only from a row that
 * exists only when the country has regions. "Nationwide only" leads the list because it is a
 * real answer — the one every account had before the field existed — not an unset state.
 * Scrolls, since sixteen Länder and that row do not fit a small screen. Titled in the country's
 * own word for its regions (a German Land, a Slovak kraj).
 */
@Composable
internal fun RegionDialog(
    country: HolidayCountry,
    selected: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable(selected) { mutableStateOf(selected) }
    val options: List<String?> = listOf<String?>(null) + country.regions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(country.regionLabelRes() ?: R.string.country_label)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { code ->
                    val label = code?.let { country.regionNameRes(it) }?.let { stringResource(it) }
                        ?: code
                        ?: stringResource(R.string.holiday_region_none)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = chosen == code, role = Role.RadioButton) {
                                chosen = code
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = chosen == code,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label)
                    }
                }
                Text(
                    text = country.coverageNote(chosen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) {
                Text(stringResource(R.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_family_kind_cancel))
            }
        }
    )
}
