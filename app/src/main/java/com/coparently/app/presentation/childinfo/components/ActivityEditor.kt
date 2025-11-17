package com.coparently.app.presentation.childinfo.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.Activity

/**
 * Editor for managing a list of child activities.
 * Provides add, edit, and delete functionality for activities.
 *
 * @param activities Current list of activities
 * @param onAdd Callback when activity is added
 * @param onEdit Callback when activity is edited
 * @param onRemove Callback when activity is removed
 */
@Composable
fun ActivityEditor(
    activities: List<Activity>,
    onAdd: (Activity) -> Unit,
    onEdit: (Int, Activity) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAddingNew by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // List of existing activities
        activities.forEachIndexed { index, activity ->
            AnimatedVisibility(
                visible = editingIndex != index,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ActivityCard(
                    activity = activity,
                    onEdit = { editingIndex = index },
                    onDelete = { onRemove(index) }
                )
            }

            // Edit form
            if (editingIndex == index) {
                ActivityForm(
                    activity = activity,
                    onSave = {
                        onEdit(index, it)
                        editingIndex = null
                    },
                    onCancel = { editingIndex = null }
                )
            }
        }

        // Add new activity form
        AnimatedVisibility(
            visible = isAddingNew,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ActivityForm(
                onSave = {
                    onAdd(it)
                    isAddingNew = false
                },
                onCancel = { isAddingNew = false }
            )
        }

        // Add button
        if (!isAddingNew && editingIndex == null) {
            OutlinedButton(
                onClick = { isAddingNew = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Activity")
            }
        }
    }
}

/**
 * Card displaying activity information.
 */
@Composable
private fun ActivityCard(
    activity: Activity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = activity.schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!activity.location.isNullOrBlank()) {
                    Text(
                        text = "📍 ${activity.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!activity.contactPerson.isNullOrBlank()) {
                    Text(
                        text = "👤 ${activity.contactPerson}" +
                                if (!activity.contactPhone.isNullOrBlank()) " • ${activity.contactPhone}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Form for adding or editing an activity.
 */
@Composable
private fun ActivityForm(
    activity: Activity? = null,
    onSave: (Activity) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(activity?.name ?: "") }
    var schedule by remember { mutableStateOf(activity?.schedule ?: "") }
    var location by remember { mutableStateOf(activity?.location ?: "") }
    var contactPerson by remember { mutableStateOf(activity?.contactPerson ?: "") }
    var contactPhone by remember { mutableStateOf(activity?.contactPhone ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (activity == null) "New Activity" else "Edit Activity",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Activity Name") },
                placeholder = { Text("e.g., Soccer, Piano lessons") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = schedule,
                onValueChange = { schedule = it },
                label = { Text("Schedule") },
                placeholder = { Text("e.g., Mon & Wed, 4-5 PM") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = contactPerson,
                onValueChange = { contactPerson = it },
                label = { Text("Contact Person (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Contact Phone (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && schedule.isNotBlank()) {
                            onSave(
                                Activity(
                                    name = name,
                                    schedule = schedule,
                                    location = location.ifBlank { null },
                                    contactPerson = contactPerson.ifBlank { null },
                                    contactPhone = contactPhone.ifBlank { null }
                                )
                            )
                        }
                    },
                    enabled = name.isNotBlank() && schedule.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

