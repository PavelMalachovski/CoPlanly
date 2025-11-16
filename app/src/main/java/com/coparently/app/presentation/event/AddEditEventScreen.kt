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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Screen for adding or editing an event.
 * Modernized with Material 3 design, date/time pickers, and validation.
 * Based on Design Roadmap Day 3: Forms & Input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventScreen(
    eventId: String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: EventViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var parentOwner by remember { mutableStateOf("mom") }
    var eventType by remember { mutableStateOf("general") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.now()) }
    var endTime by remember { mutableStateOf(LocalTime.now().plusHours(1)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val dims = dimensions()

    // Validation
    val isTitleValid = title.isNotBlank()
    val isFormValid = isTitleValid

    // Load event if editing
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
                    IconButton(onClick = onCancel) {
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
                            if (isFormValid) {
                                scope.launch {
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
                                    onSave()
                                }
                            }
                        },
                        enabled = isFormValid
                    ) {
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
            )
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
                onValueChange = { title = it },
                label = { Text("Event Title") },
                placeholder = { Text("e.g., Soccer Practice") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = null
                    )
                },
                isError = !isTitleValid && title.isNotEmpty(),
                supportingText = {
                    if (!isTitleValid && title.isNotEmpty()) {
                        Text("Title is required")
                    }
                },
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
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Add details about the event...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null
                    )
                },
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
                        onClick = { parentOwner = value },
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
                                contentDescription = null,
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
                            onClick = { eventType = value },
                            label = { Text(label) },
                            leadingIcon = {
                                if (eventType == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
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
                            onClick = { eventType = value },
                            label = { Text(label) },
                            leadingIcon = {
                                if (eventType == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
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
                onClick = { showDatePicker = true }
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
                            contentDescription = null,
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    onClick = { showStartTimePicker = true }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
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
                    onClick = { showEndTimePicker = true }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
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

