package com.coparently.app.presentation.event

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.components.TimePickerDialog
import com.coparently.app.presentation.theme.CoParentlyColors
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.utils.ValidationResult
import com.coparently.app.utils.ValidationUtils
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * Screen for adding or editing an event.
 * Modernized with Material 3 design, date/time pickers, and validation.
 * Based on Design Roadmap Day 3: Forms & Input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventScreen(
    eventId: String?,
    initialDate: LocalDate? = null,
    initialHour: Int? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EventViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var parentOwner by remember { mutableStateOf("mom") }
    var eventType by remember { mutableStateOf("general") }
    var startDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    var startTime by remember {
        mutableStateOf(
            if (initialHour != null) LocalTime.of(initialHour, 0)
            else LocalTime.now()
        )
    }
    var endTime by remember {
        mutableStateOf(
            if (initialHour != null) LocalTime.of(initialHour, 0).plusHours(1)
            else LocalTime.now().plusHours(1)
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val dims = dimensions()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSaving by remember { mutableStateOf(false) }

    // Validation states
    var titleError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var showTimeValidationError by remember { mutableStateOf(false) }
    var timeValidationMessage by remember { mutableStateOf("") }

    // Validate title
    fun validateTitle(): Boolean {
        val result = ValidationUtils.validateEventTitle(title)
        titleError = if (result is ValidationResult.Error) result.message else null
        return result is ValidationResult.Success
    }

    // Validate description
    fun validateDescription(): Boolean {
        val result = ValidationUtils.validateDescription(description)
        descriptionError = if (result is ValidationResult.Error) result.message else null
        return result is ValidationResult.Success
    }

    // Time validation effect
    LaunchedEffect(startTime, endTime, startDate) {
        val startDateTime = LocalDateTime.of(startDate, startTime)
        val endDateTime = LocalDateTime.of(startDate, endTime)

        if (endDateTime.isBefore(startDateTime) || endDateTime.isEqual(startDateTime)) {
            showTimeValidationError = true
            timeValidationMessage = "End time must be after start time"
        } else {
            showTimeValidationError = false
            timeValidationMessage = ""
        }
    }

    // Validation
    val isTitleValid = title.isNotBlank() && titleError == null
    val isFormValid = isTitleValid && descriptionError == null && !showTimeValidationError

    // Load event if editing, or load draft if creating new event
    // Issue 1.3: Draft saving functionality
    LaunchedEffect(eventId) {
        if (eventId != null) {
            scope.launch {
                val event = viewModel.getEventById(eventId)
                event?.let {
                    title = it.title
                    description = it.description ?: ""
                    parentOwner = it.parentOwner
                    eventType = it.eventType
                    startDate = it.startDateTime.toLocalDate()
                    startTime = it.startDateTime.toLocalTime()
                    endTime = it.endDateTime?.toLocalTime() ?: startTime.plusHours(1)
                }
            }
        } else {
            // Load draft for new event
            scope.launch {
                val draft = viewModel.loadEventDraft()
                draft?.let {
                    title = it.title
                    description = it.description
                    parentOwner = it.parentOwner
                    eventType = it.eventType
                    startDate = LocalDate.parse(it.startDate)
                    startTime = LocalTime.parse(it.startTime)
                    endTime = LocalTime.parse(it.endTime)
                }
            }
        }
    }

    // Auto-save draft when fields change (debounced)
    // Issue 1.3: Draft saving functionality
    LaunchedEffect(title, description, parentOwner, eventType, startDate, startTime, endTime) {
        if (eventId == null) { // Only save draft for new events
            kotlinx.coroutines.delay(1000) // Debounce 1 second
            viewModel.saveEventDraft(
                title = title,
                description = description,
                parentOwner = parentOwner,
                eventType = eventType,
                startDate = startDate,
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (eventId == null) "New Event" else "Edit Event",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCancel()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel"
                        )
                    }
                },
                actions = {
                    // Save button in app bar
                    IconButton(
                        onClick = {
                            // Выполняем валидацию
                            val isTitleValidated = validateTitle()
                            val isDescriptionValidated = validateDescription()

                            if (isFormValid && isTitleValidated && isDescriptionValidated && !isSaving) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSaving = true
                                scope.launch {
                                    try {
                                        val event = Event(
                                            id = eventId ?: UUID.randomUUID().toString(),
                                            title = title,
                                            description = description.ifEmpty { null },
                                            startDateTime = LocalDateTime.of(startDate, startTime),
                                            endDateTime = LocalDateTime.of(startDate, endTime),
                                            eventType = eventType,
                                            parentOwner = parentOwner,
                                            isRecurring = false,
                                            recurrencePattern = null,
                                            createdAt = LocalDateTime.now(),
                                            updatedAt = LocalDateTime.now()
                                        )

                                        if (eventId == null) {
                                            viewModel.createEvent(event)
                                        } else {
                                            viewModel.updateEvent(event)
                                        }

                                        // Clear draft after successful save
                                        // Issue 1.3: Draft saving functionality
                                        if (eventId == null) {
                                            viewModel.clearEventDraft()
                                        }

                                        // Show success snackbar
                                        snackbarHostState.showSnackbar(
                                            message = if (eventId == null) "Event created successfully" else "Event updated successfully",
                                            duration = SnackbarDuration.Short
                                        )

                                        // Navigate after a short delay
                                        kotlinx.coroutines.delay(500)
                                        onSave()
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(
                                            message = "Failed to save event: ${e.message}",
                                            duration = SnackbarDuration.Long
                                        )
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = isFormValid && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                tint = if (isFormValid) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(dims.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dims.paddingMedium + dims.paddingSmall / 2)
        ) {
            // Title Section
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotEmpty()) validateTitle()
                    else titleError = null
                },
                label = { Text("Event Title") },
                placeholder = { Text("e.g., Soccer Practice") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = "Event title icon"
                    )
                },
                isError = titleError != null,
                supportingText = if (titleError != null) {
                    { Text(
                        text = titleError ?: "",
                        color = MaterialTheme.colorScheme.error
                    ) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            // Description Section
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    validateDescription()
                },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Add details about the event...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Event description icon"
                    )
                },
                isError = descriptionError != null,
                supportingText = if (descriptionError != null) {
                    { Text(
                        text = descriptionError ?: "",
                        color = MaterialTheme.colorScheme.error
                    ) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.buttonHeight * 2.14f), // ~120dp for compact
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus() // Clear focus when Done is pressed
                    }
                )
            )

            // Parent Owner Selection with animated cards
            Text(
                text = "Assigned To",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("mom" to "Mom", "dad" to "Dad").forEach { (value, label) ->
                    val isSelected = parentOwner == value
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        ),
                        label = "parentOwnerScale"
                    )

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(dims.buttonHeight * 1.43f) // ~80dp for compact
                            .semantics {
                                role = Role.RadioButton
                                selected = isSelected
                                contentDescription = "Assign to $label, ${if (isSelected) "selected" else "not selected"}"
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            parentOwner = value
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                when (value) {
                                    "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.2f)
                                    "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = if (isSelected) {
                            BorderStroke(
                                2.dp,
                                when (value) {
                                    "mom" -> CoParentlyColors.MomPink
                                    "dad" -> CoParentlyColors.DadBlue
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        } else null,
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 4.dp else 0.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (value) {
                                    "mom" -> Icons.Default.Face
                                    "dad" -> Icons.Default.Person
                                    else -> Icons.Default.Person
                                },
                                contentDescription = "$label icon",
                                tint = when (value) {
                                    "mom" -> CoParentlyColors.MomPink
                                    "dad" -> CoParentlyColors.DadBlue
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(dims.iconSize * 1.17f) // ~28dp for compact
                            )
                            Spacer(modifier = Modifier.height(dims.paddingSmall / 2))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Event Type Selection
            Text(
                text = "Event Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Using Column and Row for chips layout instead of FlowRow
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "general" to "General",
                        "medical" to "Medical",
                        "school" to "School"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = eventType == value,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                eventType = value
                            },
                            label = { Text(label) },
                            leadingIcon = {
                                if (eventType == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(dims.iconSize * 0.75f) // ~18dp for compact
                                    )
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "$label event type, ${if (eventType == value) "selected" else "not selected"}"
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "sports" to "Sports",
                        "birthday" to "Birthday"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = eventType == value,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                eventType = value
                            },
                            label = { Text(label) },
                            leadingIcon = {
                                if (eventType == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(dims.iconSize * 0.75f) // ~18dp for compact
                                    )
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "$label event type, ${if (eventType == value) "selected" else "not selected"}"
                            }
                        )
                    }
                }
            }

            // Date & Time Section
            Text(
                text = "Date & Time",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Date Picker Button
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
                    .semantics {
                        role = Role.Button
                        contentDescription = "Select date, currently ${startDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"))}"
                    },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDatePicker = true
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dims.paddingMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendar icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startDate.format(
                                    DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")
                                ),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate to date picker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Time validation error display
            if (showTimeValidationError) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = timeValidationMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Time Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start Time
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
                        .semantics {
                            role = Role.Button
                            contentDescription = "Select start time, currently ${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                        },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showStartTimePicker = true
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Time picker icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dims.iconSize)
                        )
                        Spacer(modifier = Modifier.height(dims.paddingSmall))
                        Text(
                            text = "Start Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                // End Time
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
                        .semantics {
                            role = Role.Button
                            contentDescription = "Select end time, currently ${endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                        },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showEndTimePicker = true
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Time picker icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dims.iconSize)
                        )
                        Spacer(modifier = Modifier.height(dims.paddingSmall))
                        Text(
                            text = "End Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = LocalDate.ofInstant(
                                Instant.ofEpochMilli(millis),
                                ZoneId.systemDefault()
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Start Time Picker
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onTimeSelected = { selectedTime ->
                startTime = selectedTime
                // Auto-adjust end time if it's before start time
                if (endTime.isBefore(startTime)) {
                    endTime = startTime.plusHours(1)
                }
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    // End Time Picker
    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onTimeSelected = { selectedTime ->
                endTime = selectedTime
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

