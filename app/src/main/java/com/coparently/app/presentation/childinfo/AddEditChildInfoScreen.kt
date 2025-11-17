package com.coparently.app.presentation.childinfo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for adding or editing child information.
 * Provides form for entering child details, medications, activities, etc.
 *
 * @param childInfoId ID of the child info to edit, or "new" for creating new
 * @param onNavigateBack Navigation callback
 * @param viewModel ViewModel for child info operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditChildInfoScreen(
    childInfoId: String?,
    onNavigateBack: () -> Unit,
    viewModel: ChildInfoViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current

    // State for form fields
    var childName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing child info if editing
    LaunchedEffect(childInfoId) {
        if (childInfoId != null && childInfoId != "new") {
            viewModel.loadChildInfoById(childInfoId)
        }
    }

    // Observe current child info for editing
    val currentChildInfo by viewModel.currentChildInfo.collectAsState()

    // Update form when child info loads
    LaunchedEffect(currentChildInfo) {
        currentChildInfo?.let { info ->
            childName = info.childName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (childInfoId == "new") "Add Child Info" else "Edit Child Info")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Information Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Basic Information",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        label = { Text("Child's Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )

                    Text(
                        text = "Date of Birth (Coming Soon)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Medications Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Medications",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add medications functionality coming soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Activities Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Activities & Classes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add activities functionality coming soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Allergies Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Allergies",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add allergies functionality coming soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (childName.isNotBlank()) {
                        isSaving = true
                        viewModel.upsertChildInfo(
                            id = if (childInfoId == "new") null else childInfoId,
                            childName = childName,
                            dateOfBirth = null,
                            medications = emptyList(),
                            activities = emptyList(),
                            allergies = emptyList(),
                            medicalNotes = null,
                            emergencyContacts = emptyList(),
                            schoolInfo = null
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = childName.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (childInfoId == "new") "Add Child" else "Save Changes")
            }

            // Note about features
            Text(
                text = "Note: This is a simplified version. Full editing capabilities for medications, activities, allergies, and emergency contacts will be added in future updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

