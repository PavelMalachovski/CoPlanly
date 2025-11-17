package com.coparently.app.presentation.childinfo.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.SchoolInfo

/**
 * Editor for managing school information.
 * Provides form for entering school details.
 *
 * @param schoolInfo Current school information
 * @param onSave Callback when school info is saved
 */
@Composable
fun SchoolInfoEditor(
    schoolInfo: SchoolInfo?,
    onSave: (SchoolInfo?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(schoolInfo != null) }
    var name by remember { mutableStateOf(schoolInfo?.name ?: "") }
    var address by remember { mutableStateOf(schoolInfo?.address ?: "") }
    var phone by remember { mutableStateOf(schoolInfo?.phone ?: "") }
    var teacherName by remember { mutableStateOf(schoolInfo?.teacherName ?: "") }
    var teacherEmail by remember { mutableStateOf(schoolInfo?.teacherEmail ?: "") }
    var grade by remember { mutableStateOf(schoolInfo?.grade ?: "") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isEditing) {
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
                        text = "School Information",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("School Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("School Phone (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = grade,
                        onValueChange = { grade = it },
                        label = { Text("Grade (optional)") },
                        placeholder = { Text("e.g., 3rd Grade") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Teacher Information",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it },
                        label = { Text("Teacher Name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = teacherEmail,
                        onValueChange = { teacherEmail = it },
                        label = { Text("Teacher Email (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSave(null)
                                name = ""
                                address = ""
                                phone = ""
                                teacherName = ""
                                teacherEmail = ""
                                grade = ""
                                isEditing = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSave(
                                        SchoolInfo(
                                            name = name,
                                            address = address.ifBlank { null },
                                            phone = phone.ifBlank { null },
                                            teacherName = teacherName.ifBlank { null },
                                            teacherEmail = teacherEmail.ifBlank { null },
                                            grade = grade.ifBlank { null }
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add School Information")
            }
        }
    }
}

