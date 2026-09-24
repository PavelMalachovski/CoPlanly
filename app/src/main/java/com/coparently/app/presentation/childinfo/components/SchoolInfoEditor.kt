package com.coparently.app.presentation.childinfo.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.coparently.app.R
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.presentation.common.AddItemButton
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.theme.Spacing

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
    var confirmClear by remember { mutableStateOf(false) }

    // "Clear" empties six fields at once and sits right beside Save, with nothing between a slip
    // of the thumb and retyping a school's whole record — so it asks first, but only when there
    // is something to lose.
    val clear = {
        onSave(null)
        name = ""
        address = ""
        phone = ""
        teacherName = ""
        teacherEmail = ""
        grade = ""
        isEditing = false
    }
    val hasContent = listOf(name, address, phone, teacherName, teacherEmail, grade).any { it.isNotBlank() }
    if (confirmClear) {
        ConfirmationDialog(
            title = stringResource(R.string.childinfo_school_clear_title),
            message = stringResource(R.string.childinfo_school_clear_message),
            confirmText = stringResource(R.string.childinfo_clear),
            dismissText = stringResource(R.string.childinfo_cancel),
            isDestructive = true,
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                clear()
            }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
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
                        .padding(Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    Text(
                        text = stringResource(R.string.childinfo_section_school),
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.childinfo_school_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.childinfo_address_optional_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.childinfo_school_phone_optional_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = grade,
                        onValueChange = { grade = it },
                        label = { Text(stringResource(R.string.childinfo_grade_optional_label)) },
                        placeholder = { Text(stringResource(R.string.childinfo_grade_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S))

                    Text(
                        text = stringResource(R.string.childinfo_section_teacher),
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it },
                        label = { Text(stringResource(R.string.childinfo_teacher_name_optional_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = teacherEmail,
                        onValueChange = { teacherEmail = it },
                        label = { Text(stringResource(R.string.childinfo_teacher_email_optional_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                    ) {
                        OutlinedButton(
                            onClick = { if (hasContent) confirmClear = true else clear() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.childinfo_clear))
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
                            Text(stringResource(R.string.childinfo_save))
                        }
                    }
                }
            }
        } else {
            AddItemButton(
                label = stringResource(R.string.childinfo_add_school_info),
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
