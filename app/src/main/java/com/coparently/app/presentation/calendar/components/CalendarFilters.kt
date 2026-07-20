package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi as FoundationExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.ParentFilter
import com.coparently.app.presentation.theme.CoPlanlyColors
import com.coparently.app.presentation.theme.dimensions

/**
 * Segmented control for switching between "Mom", "Both" and "Dad" calendar views.
 * Implements the roadmap items "Switch between You and Him view" and
 * "Have selected mom and dad at the same time".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentFilterBar(
    selected: ParentFilter,
    onSelected: (ParentFilter) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = dimensions()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dims.paddingMedium, vertical = dims.paddingSmall / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(
            modifier = Modifier.weight(1f)
        ) {
            val options = listOf(
                Triple(ParentFilter.MOM, "Mom", CoPlanlyColors.MomPink),
                Triple(ParentFilter.BOTH, "Both", MaterialTheme.colorScheme.primary),
                Triple(ParentFilter.DAD, "Dad", CoPlanlyColors.DadBlue)
            )
            options.forEachIndexed { index, (filter, label, color) ->
                SegmentedButton(
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = color.copy(alpha = 0.15f),
                        activeContentColor = color,
                        activeBorderColor = color
                    ),
                    icon = {}
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected == filter) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        IconButton(onClick = onFilterClick) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter event types",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Bottom sheet with event type visibility toggles, custom type creation
 * and the holiday visibility switch.
 */
@OptIn(ExperimentalMaterial3Api::class, FoundationExperimentalLayoutApi::class)
@Composable
fun EventTypeFilterSheet(
    allEventTypes: List<String>,
    hiddenEventTypes: Set<String>,
    showHolidays: Boolean,
    onToggleType: (String) -> Unit,
    onAddCustomType: (String) -> Unit,
    onShowHolidaysChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val dims = dimensions()
    var newTypeName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.paddingMedium)
                .padding(bottom = dims.paddingMedium * 2),
            verticalArrangement = Arrangement.spacedBy(dims.paddingMedium)
        ) {
            Text(
                text = "Event types",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Hidden types are not shown in the calendar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allEventTypes.forEach { type ->
                    val isVisible = type !in hiddenEventTypes
                    FilterChip(
                        selected = isVisible,
                        onClick = { onToggleType(type) },
                        label = {
                            Text(type.replaceFirstChar { it.uppercase() })
                        },
                        leadingIcon = if (isVisible) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Visible",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            // Add custom event type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTypeName,
                    onValueChange = { newTypeName = it },
                    label = { Text("New event type") },
                    placeholder = { Text("e.g., Music lessons") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        if (newTypeName.isNotBlank()) {
                            onAddCustomType(newTypeName)
                            newTypeName = ""
                        }
                    },
                    enabled = newTypeName.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Add")
                }
            }

            // Holidays toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Czech holidays",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Public holidays and school vacations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showHolidays,
                    onCheckedChange = onShowHolidaysChange
                )
            }
        }
    }
}
