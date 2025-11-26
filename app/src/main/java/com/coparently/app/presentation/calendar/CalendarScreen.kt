package com.coparently.app.presentation.calendar

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import android.os.Build
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.presentation.calendar.components.CalendarHeader
import com.coparently.app.presentation.calendar.components.ViewModeSelector
import com.coparently.app.presentation.calendar.components.CustodyIndicatorToday
import com.coparently.app.presentation.calendar.navigation.CalendarNavigation
import com.coparently.app.presentation.common.QuickActionsBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.coparently.app.presentation.event.EventViewModel
import com.coparently.app.presentation.event.EventUiState
import com.coparently.app.presentation.theme.CoParentlyColors
import com.coparently.app.presentation.theme.dimensions
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Main calendar screen showing calendar view with events.
 * Supports multiple view modes: Day, 3 Days, Week, Month.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    onEventClick: (String) -> Unit = {},
    onAddEventClick: (LocalDate?, Int?) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    eventViewModel: EventViewModel = hiltViewModel(),
    calendarViewModel: CalendarViewModel = hiltViewModel()
) {
    // Get responsive dimensions and haptic feedback
    val dims = dimensions()
    val haptic = LocalHapticFeedback.current
    val events by eventViewModel.events.collectAsState()
    val custodySchedules by calendarViewModel.custodySchedules.collectAsState()
    val viewMode by calendarViewModel.viewMode.collectAsState()
    val selectedDate by calendarViewModel.selectedDate.collectAsState()

    // Animation optimization: Check device capabilities (3.2)
    // Reduce animation duration on older devices for better performance
    val animationDuration = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            150 // Faster animations on older devices (API < 28)
        } else {
            200 // Normal animations on modern devices
        }
    }

    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val now = remember { YearMonth.now() }
    val startMonth = remember { now.minusMonths(12) }
    val endMonth = remember { now.plusMonths(12) }

    val calendarState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = YearMonth.from(selectedDate),
        firstDayOfWeek = firstDayOfWeek
    )

    var showDatePicker by remember { mutableStateOf(false) }
    val currentYearMonth = calendarState.firstVisibleMonth.yearMonth
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentYearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli(),
        yearRange = IntRange(now.year - 5, now.year + 5)
    )
    val scope = rememberCoroutineScope()

    // Pull-to-Refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Quick Actions Bottom Sheet state
    var showQuickActions by remember { mutableStateOf(false) }
    val quickActionsSheetState = rememberModalBottomSheetState()

    // Snackbar state for undo functionality
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by eventViewModel.uiState.collectAsState()

    // Delete button state - show red cross when long pressing event
    var showDeleteButton by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<String?>(null) }
    var isDragOverDeleteButton by remember { mutableStateOf(false) }

    // Load events based on view mode
    LaunchedEffect(viewMode, selectedDate) {
        val start = when (viewMode) {
            CalendarViewMode.DAY -> selectedDate.atStartOfDay()
            CalendarViewMode.THREE_DAYS -> selectedDate.atStartOfDay()
            CalendarViewMode.WEEK -> {
                // Calculate first day of week (Monday = 1, Sunday = 7)
                val dayOfWeek = selectedDate.dayOfWeek.value
                val daysToSubtract = (dayOfWeek - 1).toLong()
                val firstDay = selectedDate.minusDays(daysToSubtract)
                firstDay.atStartOfDay()
            }
            CalendarViewMode.MONTH -> {
                // Load events for extended range to support MonthView buffer (6 weeks before/after)
                val visibleMonth = YearMonth.from(selectedDate)
                val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
                val firstDayOfWeek = weekFields.firstDayOfWeek

                // Calculate the start of the week 6 weeks before the first day of month
                var startDate = visibleMonth.atDay(1)
                while (startDate.dayOfWeek != firstDayOfWeek) {
                    startDate = startDate.minusDays(1)
                }
                startDate = startDate.minusWeeks(6)

                startDate.atStartOfDay()
            }
        }

        val end = when (viewMode) {
            CalendarViewMode.DAY -> selectedDate.atTime(23, 59, 59)
            CalendarViewMode.THREE_DAYS -> selectedDate.plusDays(2).atTime(23, 59, 59)
            CalendarViewMode.WEEK -> {
                val dayOfWeek = selectedDate.dayOfWeek.value
                val daysToAdd = (7 - dayOfWeek).toLong()
                val lastDay = selectedDate.plusDays(daysToAdd)
                lastDay.atTime(23, 59, 59)
            }
                        CalendarViewMode.MONTH -> {
                            // Load events for extended range to support MonthView buffer (6 weeks before/after)
                            val visibleMonth = YearMonth.from(selectedDate)
                            val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
                            val firstDayOfWeek = weekFields.firstDayOfWeek

                            // Calculate the end of the week 6 weeks after the last day of month
                            var endDate = visibleMonth.atEndOfMonth()
                            while (endDate.dayOfWeek != firstDayOfWeek.plus(6)) { // Last day of week
                                endDate = endDate.plusDays(1)
                            }
                            endDate = endDate.plusWeeks(6)

                            endDate.atTime(23, 59, 59)
                        }
        }

        eventViewModel.loadEventsForDateRange(start, end)
    }

    // Update calendar state when selected date changes (for month view)
    LaunchedEffect(selectedDate, viewMode) {
        if (viewMode == CalendarViewMode.MONTH) {
            val newMonth = YearMonth.from(selectedDate)
            val currentMonth = calendarState.firstVisibleMonth.yearMonth
            if (newMonth != currentMonth) {
                scope.launch {
                    try {
                        calendarState.animateScrollToMonth(newMonth)
                    } catch (e: Exception) {
                        // Handle scroll error gracefully
                    }
                }
            }
        }
    }

    // Show snackbar with undo when event is moved
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is EventUiState.OperationSuccess -> {
                if (state.message == "Event rescheduled" && eventViewModel.hasUndoAction()) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Event moved",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        eventViewModel.undoLastMove()
                    }
                }
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            CalendarNavigation(
                currentDate = selectedDate,
                viewMode = viewMode,
                onDateChange = { newDate ->
                    calendarViewModel.setSelectedDate(newDate)
                }
            ) { date ->
                CalendarHeader(
                    selectedDate = date,
                    onNavigateToToday = { calendarViewModel.setSelectedDate(LocalDate.now()) },
                    onSettingsClick = onSettingsClick
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            Box {
                // Red delete button - appears above the "+" button when long pressing event or dragging
                if ((showDeleteButton && eventToDelete != null) || isDragOverDeleteButton) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            eventToDelete?.let { eventId ->
                                eventViewModel.deleteEventById(eventId)
                            }
                            showDeleteButton = false
                            eventToDelete = null
                        },
                        containerColor = if (isDragOverDeleteButton) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        contentColor = MaterialTheme.colorScheme.onError,
                        shape = RoundedCornerShape(dims.cornerRadius),
                        modifier = Modifier
                            .offset(y = (-64).dp) // Position above the "+" button
                            .graphicsLayer {
                                // Scale up when dragging over it
                                scaleX = if (isDragOverDeleteButton) 1.2f else 1f
                                scaleY = if (isDragOverDeleteButton) 1.2f else 1f
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete event",
                            modifier = Modifier.size(dims.iconSize)
                        )
                    }
                }

                // Regular "+" button
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddEventClick(null, null)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(dims.cornerRadius)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.calendar_add_event),
                        modifier = Modifier.size(dims.iconSize)
                    )
                }
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    // Refresh events for current view
                    val start = when (viewMode) {
                        CalendarViewMode.DAY -> selectedDate.atStartOfDay()
                        CalendarViewMode.THREE_DAYS -> selectedDate.atStartOfDay()
                        CalendarViewMode.WEEK -> {
                            val dayOfWeek = selectedDate.dayOfWeek.value
                            val daysToSubtract = (dayOfWeek - 1).toLong()
                            val firstDay = selectedDate.minusDays(daysToSubtract)
                            firstDay.atStartOfDay()
                        }
                        CalendarViewMode.MONTH -> {
                            // Load events for extended range to support MonthView buffer (6 weeks before/after)
                            val visibleMonth = YearMonth.from(selectedDate)
                            val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
                            val firstDayOfWeek = weekFields.firstDayOfWeek

                            // Calculate the start of the week 6 weeks before the first day of month
                            var startDate = visibleMonth.atDay(1)
                            while (startDate.dayOfWeek != firstDayOfWeek) {
                                startDate = startDate.minusDays(1)
                            }
                            startDate = startDate.minusWeeks(6)

                            startDate.atStartOfDay()
                        }
                    }

                    val end = when (viewMode) {
                        CalendarViewMode.DAY -> selectedDate.atTime(23, 59, 59)
                        CalendarViewMode.THREE_DAYS -> selectedDate.plusDays(2).atTime(23, 59, 59)
                        CalendarViewMode.WEEK -> {
                            val dayOfWeek = selectedDate.dayOfWeek.value
                            val daysToAdd = (7 - dayOfWeek).toLong()
                            val lastDay = selectedDate.plusDays(daysToAdd)
                            lastDay.atTime(23, 59, 59)
                        }
                        CalendarViewMode.MONTH -> {
                            // Load events for extended range to support MonthView buffer (6 weeks before/after)
                            val visibleMonth = YearMonth.from(selectedDate)
                            val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
                            val firstDayOfWeek = weekFields.firstDayOfWeek

                            // Calculate the end of the week 6 weeks after the last day of month
                            var endDate = visibleMonth.atEndOfMonth()
                            while (endDate.dayOfWeek != firstDayOfWeek.plus(6)) { // Last day of week
                                endDate = endDate.plusDays(1)
                            }
                            endDate = endDate.plusWeeks(6)

                            endDate.atTime(23, 59, 59)
                        }
                    }

                    eventViewModel.loadEventsForDateRange(start, end)
                    calendarViewModel.loadCustodySchedules()
                    kotlinx.coroutines.delay(500) // Small delay for better UX
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Animated view mode selector with sliding indicator
                ViewModeSelector(
                    selectedMode = viewMode,
                    onModeSelected = { mode ->
                        calendarViewModel.setViewMode(mode)
                    }
                )

            // Today's custody indicator (only for month view)
            // Optimization 3.1: Use key() to prevent unnecessary recompositions
            if (viewMode == CalendarViewMode.MONTH && custodySchedules.isNotEmpty()) {
                val today = LocalDate.now()
                val todayCustody = CustodyHelper.getCustodyForDate(today, custodySchedules)
                if (todayCustody != null) {
                    key(todayCustody) {
                        AnimatedContent(
                            targetState = todayCustody,
                            transitionSpec = {
                                slideInVertically(
                                    animationSpec = tween(animationDuration),
                                    initialOffsetY = { -it }
                                ) + fadeIn() togetherWith slideOutVertically(
                                    animationSpec = tween(animationDuration),
                                    targetOffsetY = { it }
                                ) + fadeOut()
                            },
                            modifier = Modifier.padding(horizontal = dims.paddingMedium, vertical = dims.paddingSmall)
                        ) { custody ->
                            CustodyIndicatorToday(custody = custody)
                        }
                    }
                }
            }

            // Calendar content based on view mode - optimized with Crossfade
            // Optimization 3.1: Use key() to prevent unnecessary recompositions
            Crossfade(
                targetState = viewMode,
                animationSpec = tween(
                    durationMillis = animationDuration,
                    easing = FastOutSlowInEasing
                ),
                modifier = Modifier.weight(1f)
            ) { mode ->
                key(mode) {
                    when (mode) {
                        CalendarViewMode.DAY -> {
                            DayWeekView(
                                selectedDate = selectedDate,
                                daysCount = 1,
                                events = events,
                                custodySchedules = custodySchedules,
                                onDateChange = { calendarViewModel.setSelectedDate(it) },
                                onEventClick = onEventClick,
                                onAddEventClick = { date, hour ->
                                    onAddEventClick(date, hour)
                                },
                                onEventDragDrop = { eventId, targetDate, targetHour ->
                                    eventViewModel.moveEvent(eventId, targetDate, targetHour)
                                },
                                onEventResize = { eventId: String, newStartTime: java.time.LocalDateTime?, newEndTime: java.time.LocalDateTime? ->
                                    eventViewModel.resizeEvent(eventId, newStartTime, newEndTime)
                                },
                                onEventDelete = { eventId ->
                                    eventViewModel.deleteEventById(eventId)
                                },
                                onEventLongPressStart = { eventId ->
                                    showDeleteButton = true
                                    eventToDelete = eventId
                                },
                                onEventLongPressEnd = {
                                    showDeleteButton = false
                                    eventToDelete = null
                                },
                                onDragOverDeleteButton = { isOver ->
                                    isDragOverDeleteButton = isOver
                                }
                            )
                        }
                        CalendarViewMode.THREE_DAYS -> {
                            DayWeekView(
                                selectedDate = selectedDate,
                                daysCount = 3,
                                events = events,
                                custodySchedules = custodySchedules,
                                onDateChange = { calendarViewModel.setSelectedDate(it) },
                                onEventClick = onEventClick,
                                onAddEventClick = { date, hour ->
                                    onAddEventClick(date, hour)
                                },
                                onEventDragDrop = { eventId, targetDate, targetHour ->
                                    eventViewModel.moveEvent(eventId, targetDate, targetHour)
                                },
                                onEventResize = { eventId: String, newStartTime: java.time.LocalDateTime?, newEndTime: java.time.LocalDateTime? ->
                                    eventViewModel.resizeEvent(eventId, newStartTime, newEndTime)
                                },
                                onEventDelete = { eventId ->
                                    eventViewModel.deleteEventById(eventId)
                                },
                                onEventLongPressStart = { eventId ->
                                    showDeleteButton = true
                                    eventToDelete = eventId
                                },
                                onEventLongPressEnd = {
                                    showDeleteButton = false
                                    eventToDelete = null
                                },
                                onDragOverDeleteButton = { isOver ->
                                    isDragOverDeleteButton = isOver
                                }
                            )
                        }
                        CalendarViewMode.WEEK -> {
                            DayWeekView(
                                selectedDate = selectedDate,
                                daysCount = 7,
                                events = events,
                                custodySchedules = custodySchedules,
                                onDateChange = { calendarViewModel.setSelectedDate(it) },
                                onEventClick = onEventClick,
                                onAddEventClick = { date, hour ->
                                    onAddEventClick(date, hour)
                                },
                                onEventDragDrop = { eventId, targetDate, targetHour ->
                                    eventViewModel.moveEvent(eventId, targetDate, targetHour)
                                },
                                onEventResize = { eventId: String, newStartTime: java.time.LocalDateTime?, newEndTime: java.time.LocalDateTime? ->
                                    eventViewModel.resizeEvent(eventId, newStartTime, newEndTime)
                                },
                                onEventDelete = { eventId ->
                                    eventViewModel.deleteEventById(eventId)
                                },
                                onEventLongPressStart = { eventId ->
                                    showDeleteButton = true
                                    eventToDelete = eventId
                                },
                                onEventLongPressEnd = {
                                    showDeleteButton = false
                                    eventToDelete = null
                                },
                                onDragOverDeleteButton = { isOver ->
                                    isDragOverDeleteButton = isOver
                                }
                            )
                        }
                        CalendarViewMode.MONTH -> {
                            val visibleMonthYear = YearMonth.from(selectedDate)

                            MonthView(
                                selectedMonth = visibleMonthYear,
                                selectedDate = selectedDate,
                                events = events,
                                custodySchedules = custodySchedules,
                                onDayClick = { clickedDate ->
                                    calendarViewModel.setSelectedDate(clickedDate)
                                    calendarViewModel.setViewMode(CalendarViewMode.DAY)
                                },
                                onMonthChange = { newMonth ->
                                    calendarViewModel.setSelectedDate(newMonth.atDay(1))
                                },
                                onDateChange = { newDate ->
                                    calendarViewModel.setSelectedDate(newDate)
                                },
                                onEventDragDrop = { eventId, targetDate ->
                                    eventViewModel.moveEvent(eventId, targetDate)
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // Date picker dialog for selecting month and year
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedDate = LocalDate.ofInstant(
                                java.time.Instant.ofEpochMilli(millis),
                                ZoneId.systemDefault()
                            )
                            val selectedYearMonth = YearMonth.from(pickedDate)

                            calendarViewModel.setSelectedDate(pickedDate)
                            if (viewMode != CalendarViewMode.MONTH) {
                                calendarViewModel.setViewMode(CalendarViewMode.MONTH)
                            } else {
                                scope.launch {
                                    calendarState.animateScrollToMonth(selectedYearMonth)
                                }
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
            colors = DatePickerDefaults.colors()
        ) {
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                title = null,
                headline = null,
                showModeToggle = true
            )
        }
    }

    // Quick Actions Bottom Sheet
    if (showQuickActions) {
        QuickActionsBottomSheet(
            onEventCreate = { onAddEventClick(null, null) },
            onNavigateToToday = { calendarViewModel.setSelectedDate(LocalDate.now()) },
            onShowSettings = { onSettingsClick?.invoke() },
            onDismiss = { showQuickActions = false },
            sheetState = quickActionsSheetState
        )
    }
}


@Composable
private fun CalendarDayContent(
    day: CalendarDay,
    events: List<com.coparently.app.domain.model.Event>,
    custodySchedules: List<CustodyScheduleEntity>,
    onClick: (CalendarDay) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback = LocalHapticFeedback.current
) {
    val isToday = CustodyHelper.isToday(day.date)
    val custody = CustodyHelper.getCustodyForDate(day.date, custodySchedules)

    // Animate scale for today - optimized with graphicsLayer
    val scale by animateFloatAsState(
        targetValue = if (isToday) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dayScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick(day)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            // Day number
            AnimatedContent(
                targetState = day.date.dayOfMonth,
                transitionSpec = {
                    scaleIn(tween(200)) + fadeIn() togetherWith scaleOut(tween(200)) + fadeOut()
                }
            ) { dayNumber ->
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape
                        )
                        .size(if (isToday) 32.dp else 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            day.position == DayPosition.MonthDate -> {
                                if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            }
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        }
                    )
                }
            }

            // Custody indicator for the day
            if (custody != null && day.position == DayPosition.MonthDate) {
                val custodyColor = when (custody) {
                    "mom" -> CoParentlyColors.MomPink
                    "dad" -> CoParentlyColors.DadBlue
                    else -> Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .size(if (isToday) 8.dp else 6.dp)
                        .background(color = custodyColor, shape = CircleShape)
                        .padding(top = 2.dp)
                )
            }

            // Event indicators
            if (events.isNotEmpty()) {
                val momEvents = events.filter { it.parentOwner == "mom" }
                val dadEvents = events.filter { it.parentOwner == "dad" }

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (momEvents.isNotEmpty()) {
                        EventIndicatorDot(
                            color = CoParentlyColors.MomPink,
                            isToday = isToday
                        )
                    }
                    if (dadEvents.isNotEmpty()) {
                        EventIndicatorDot(
                            color = CoParentlyColors.DadBlue,
                            isToday = isToday
                        )
                    }
                    if (momEvents.isEmpty() && dadEvents.isEmpty() && events.isNotEmpty()) {
                        EventIndicatorDot(
                            color = MaterialTheme.colorScheme.tertiary,
                            isToday = isToday
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventIndicatorDot(
    color: Color,
    isToday: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isToday) 1.33f else 1f, // 8dp / 6dp = 1.33
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dotScale"
    )

    Box(
        modifier = Modifier
            .size(6.dp) // Base size
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(color = color, shape = CircleShape)
    )
}

