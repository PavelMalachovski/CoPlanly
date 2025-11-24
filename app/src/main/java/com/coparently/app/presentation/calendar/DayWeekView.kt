package com.coparently.app.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.theme.CoParentlyColors
import com.coparently.app.presentation.theme.dimensions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Hourly view for day/week calendar views.
 * Shows activities scheduled by hour with the child.
 */
@Composable
fun DayWeekView(
    selectedDate: LocalDate,
    daysCount: Int,
    events: List<Event>,
    custodySchedules: List<CustodyScheduleEntity>,
    onDateChange: (LocalDate) -> Unit,
    onEventClick: (String) -> Unit,
    onAddEventClick: (LocalDate, Int) -> Unit = { _, _ -> },
    onEventDragDrop: ((String, LocalDate, Int) -> Unit)? = null,
    onEventResize: ((String, LocalDateTime?, LocalDateTime?) -> Unit)? = null
) {
    val dims = dimensions()
    val hours = (0..23).toList()
    val density = LocalDensity.current
    val hourCellHeight = dims.buttonHeight * 1.07f
    val hourCellHeightPx = remember(hourCellHeight, density) {
        with(density) { hourCellHeight.toPx() }
    }

    // Handle swipe to change dates
    val totalDragState = remember { mutableFloatStateOf(0f) }
    var totalDrag by totalDragState

    // Save scroll state to preserve position when dates change
    // Using single scrollState shared across all dates preserves scroll position
    // The key is that scrollState is created once and reused across AnimatedContent transitions
    val scrollState = rememberLazyListState(
        initialFirstVisibleItemIndex = 6 // Start at 6 AM for better UX
    )

    // State for swipe direction
    var swipeDirection by remember { mutableStateOf(0) } // -1 for left, 1 for right

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedDate) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dragValue = totalDrag
                        if (kotlin.math.abs(dragValue) > 200f) {
                            swipeDirection = if (dragValue > 0) -1 else 1
                            val daysToAdd = if (dragValue > 0) -daysCount else daysCount
                            onDateChange(selectedDate.plusDays(daysToAdd.toLong()))
                        }
                        totalDrag = 0f
                    }
                ) { _, dragAmount ->
                    totalDrag = totalDrag + dragAmount
                }
            }
    ) {
        // Fixed header row with modern design - 1.5x larger
        // Animated header with optimized animation (200ms slide + 150ms fade)
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                val direction = swipeDirection
                (slideInHorizontally(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    ),
                    initialOffsetX = { fullWidth -> fullWidth * direction }
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                )) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    ),
                    targetOffsetX = { fullWidth -> -fullWidth * direction }
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                ))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.buttonHeight * 1.6f) // ~90dp for compact
        ) { currentDate ->
            val currentDates = DateRangeHelper.rememberDateRange(currentDate, daysCount)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.buttonHeight * 1.6f) // ~90dp for compact
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = dims.paddingSmall)
            ) {
                // Week number for 3 days and week views (absolutely positioned, doesn't affect layout)
                if (daysCount >= 3) {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val firstDate = currentDates.firstOrNull() ?: currentDate
                    val weekNumber = firstDate.get(weekFields.weekOfWeekBasedYear())

                    Box(
                        modifier = Modifier
                            .width(dims.iconSize * 1.33f) // ~32dp for compact
                            .fillMaxHeight()
                            .align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weekNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // Main row with time column and dates - exactly matches content structure
                // This row is identical to content row, ensuring perfect alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Time column space - fixed width for consistency (matches content layout)
                    Box(
                        modifier = Modifier
                            .width(dims.iconSize * 2.17f) // ~52dp for compact
                            .fillMaxHeight()
                    )

                    currentDates.forEach { date ->
                        val isToday = date == LocalDate.now()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("EEE")),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 9.sp,
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main container: Box to overlay events on top of scrollable grid
        Box(
            modifier = Modifier.weight(1f)
        ) {
            // Scrollable content - time stays in place, only days animate
            // Use single scrollState shared across all dates to preserve scroll position
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(hours.size) { hourIndex ->
                    val hour = hours[hourIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Hour label - static, outside AnimatedContent
                        // Fixed width to ensure consistent layout and single-line time display
                        Box(
                            modifier = Modifier
                                .width(dims.iconSize * 2.17f) // ~52dp for compact
                                .height(hourCellHeight) // ~60dp for compact
                                .padding(top = dims.paddingSmall / 2),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = String.format("%02d:00", hour),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // Day columns - animated, optimized animation (200ms slide + 150ms fade)
                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = {
                                val direction = swipeDirection
                                (slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetX = { fullWidth -> fullWidth * direction }
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 150,
                                        easing = LinearEasing
                                    )
                                )) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetX = { fullWidth -> -fullWidth * direction }
                                ) + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 150,
                                        easing = LinearEasing
                                    )
                                ))
                            },
                            modifier = Modifier.weight(1f)
                        ) { currentDate ->
                            // Day columns for this hour - use optimized DateRangeHelper
                            val currentDates = DateRangeHelper.rememberDateRange(currentDate, daysCount)

                            // Background cells only (no events)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentDates.forEachIndexed { dayIndex, date ->
                                    val isToday = date == LocalDate.now()
                                    val custody = CustodyHelper.getCustodyForDate(date, custodySchedules)
                                    val backgroundColor = when {
                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                                        custody == "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.03f)
                                        custody == "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.03f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(hourCellHeight)
                                            .background(
                                                color = backgroundColor,
                                                shape = RoundedCornerShape(dims.paddingSmall)
                                            )
                                            .clickable {
                                                onAddEventClick(date, hour)
                                            }
                                            .semantics {
                                                contentDescription = "Time slot at ${String.format("%02d:00", hour)} on ${date.format(DateTimeFormatter.ofPattern("MMM dd"))}. Tap to add event."
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }


            // Events overlay layer - positioned absolutely above the grid
            val currentDates = DateRangeHelper.rememberDateRange(selectedDate, daysCount)
            val scrollOffset = scrollState.firstVisibleItemScrollOffset.toFloat()
            val firstVisibleHour = scrollState.firstVisibleItemIndex

            // Calculate layout dimensions
            val hourLabelWidth = dims.iconSize * 2.17f
            val horizontalPadding = 8.dp
            val daySpacing = 4.dp
            val headerHeight = dims.buttonHeight * 1.6f  // Match header height

            Box(
                modifier = Modifier
                    .matchParentSize()  // Match parent Box size (same as LazyColumn)
                    .padding(
                        start = horizontalPadding + hourLabelWidth,
                        end = horizontalPadding
                        // top = headerHeight  <- REMOVED: This was causing the time offset issue!
                    )
                    .clipToBounds() // Prevent events from drawing over the header
            ) {
                // Track container width for column calculations
                var containerWidthPx by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            containerWidthPx = coordinates.size.width.toFloat()
                        }
                ) {
                    if (containerWidthPx > 0f) {
                        // Render all events with absolute positioning
                        currentDates.forEachIndexed { dayIndex, date ->
                            events.filter { event ->
                                event.startDateTime.toLocalDate() == date
                            }.forEach { event ->
                                val eventStart = event.startDateTime
                                val eventEnd = event.endDateTime ?: event.startDateTime.plusHours(1)

                                // Calculate vertical position
                                val startHour = eventStart.hour
                                val startMinute = eventStart.minute
                                val startSecond = eventStart.second

                                // Offset from top = (hour - firstHour) * hourHeight + minute offset - total scroll
                                val hourOffset = (startHour - hours.first()) * hourCellHeightPx
                                val minuteOffset = (startMinute + startSecond / 60f) / 60f * hourCellHeightPx
                                val totalScroll = firstVisibleHour * hourCellHeightPx + scrollOffset
                                val totalVerticalOffset = hourOffset + minuteOffset - totalScroll

                                // Calculate horizontal position
                                val spacingPx = with(density) { daySpacing.toPx() }
                                val totalSpacingPx = spacingPx * (currentDates.size - 1)
                                val columnWidth = (containerWidthPx - totalSpacingPx) / currentDates.size
                                val horizontalOffset = dayIndex * (columnWidth + spacingPx)

                                // Only render if event is in visible area (with some buffer)
                                val eventDuration = java.time.Duration.between(eventStart, eventEnd)
                                val eventHeightPx = (eventDuration.toMinutes() / 60f) * hourCellHeightPx

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { horizontalOffset.toDp() },
                                            y = with(density) { totalVerticalOffset.toDp() }
                                        )
                                        .width(with(density) { columnWidth.toDp() })
                                        .padding(horizontal = 2.dp)
                                ) {
                                    EventChip(
                                        event = event,
                                        onClick = { onEventClick(event.id) },
                                        columnWidthPx = columnWidth,
                                        hourHeightPx = hourCellHeightPx,
                                        baseDate = date,
                                        baseHour = startHour,
                                        onDragDrop = onEventDragDrop,
                                        onResize = onEventResize
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EventChip(
    event: Event,
    onClick: () -> Unit,
    columnWidthPx: Float,
    hourHeightPx: Float,
    baseDate: LocalDate,
    baseHour: Int,
    onDragDrop: ((String, LocalDate, Int) -> Unit)?,
    onResize: ((String, LocalDateTime?, LocalDateTime?) -> Unit)? = null
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    // Calculate event position and height based on actual start/end times
    val eventStart = event.startDateTime
    val eventEnd = event.endDateTime ?: event.startDateTime.plusHours(1)

    // Calculate total event duration in minutes
    val totalDuration = java.time.Duration.between(eventStart, eventEnd)
    val totalMinutes = totalDuration.toMinutes().coerceAtLeast(15) // Minimum 15 minutes
    val eventHeightDp = with(density) { (hourHeightPx * totalMinutes / 60f).toDp() }

    // Transparent background colors (more transparent)
    val backgroundColor = when (event.parentOwner) {
        "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.3f)
        "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    }

    val borderColor = when (event.parentOwner) {
        "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.8f)
        "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
    }

    val textColor = when (event.parentOwner) {
        "mom" -> CoParentlyColors.MomPink
        "dad" -> CoParentlyColors.DadBlue
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    // Drag states
    var isDraggingEvent by remember { mutableStateOf(false) }
    var isResizingStart by remember { mutableStateOf(false) }
    var isResizingEnd by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    var resizeDragStart by remember { mutableStateOf(Offset.Zero) }
    var resizeDragAmountStart by remember { mutableStateOf(0f) }
    var resizeDragAmountEnd by remember { mutableStateOf(0f) }
    // Calculate temporary times for display during resize
    val tempStartTime = remember(isResizingStart, resizeDragAmountStart, eventStart) {
        if (isResizingStart) {
            val minutesChange = (resizeDragAmountStart / hourHeightPx * 60f).roundToInt()
            eventStart.plusMinutes(minutesChange.toLong())
        } else eventStart
    }

    val tempEndTime = remember(isResizingEnd, resizeDragAmountEnd, eventEnd) {
        if (isResizingEnd) {
            val minutesChange = (resizeDragAmountEnd / hourHeightPx * 60f).roundToInt()
            eventEnd.plusMinutes(minutesChange.toLong())
        } else eventEnd
    }

    // Calculate dynamic height based on resize state (no offset needed - handled by overlay)
    // Fix for top resize: clamp the drag amount so we don't shrink below minimum height
    // This prevents the bottom edge from moving when we hit the minimum height constraint
    val effectiveResizeDragStart = if (isResizingStart) {
        val maxDragDown = with(density) { (eventHeightDp - 24.dp).toPx() }
        resizeDragAmountStart.coerceAtMost(maxDragDown)
    } else 0f

    val dynamicHeightDp = if (isResizingStart || isResizingEnd) {
        val heightAdjustment = with(density) {
            when {
                isResizingStart -> -effectiveResizeDragStart.toDp()
                isResizingEnd -> resizeDragAmountEnd.toDp()
                else -> 0.dp
            }
        }
        (eventHeightDp + heightAdjustment).coerceAtLeast(24.dp)
    } else {
        eventHeightDp.coerceAtLeast(24.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeightDp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = !isDraggingEvent && !isResizingStart && !isResizingEnd, onClick = onClick)
            .graphicsLayer {
                if (isDraggingEvent) {
                    shadowElevation = 8.dp.toPx()
                    translationX = totalDrag.x
                    translationY = totalDrag.y
                    alpha = 0.8f
                } else if (isResizingStart) {
                    // Fix for top resize animation: move the box visually by the drag amount
                    // Use effectiveResizeDragStart to ensure visual top matches layout top + translation
                    translationY = effectiveResizeDragStart
                }
            }
            .semantics {
                contentDescription = "${event.title} event. ${
                    when {
                        isDraggingEvent -> "Dragging"
                        isResizingStart -> "Resizing start time to ${tempStartTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
                        isResizingEnd -> "Resizing end time to ${tempEndTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
                        else -> "Tap to view, long press center to move, drag corners to resize"
                    }
                }"
            }
    ) {
        // Event content - center area for drag & drop
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .padding(start = 12.dp, end = 12.dp) // Padding to avoid resize handles
                .pointerInput(columnWidthPx, hourHeightPx, onDragDrop) {
                    // Center drag for moving event
                    if (onDragDrop != null && columnWidthPx > 0f && hourHeightPx > 0f) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDraggingEvent = true
                                totalDrag = Offset.Zero
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = {
                                isDraggingEvent = false
                                totalDrag = Offset.Zero
                            },
                            onDragEnd = {
                                if (isDraggingEvent) {
                                    val dayOffset = (totalDrag.x / columnWidthPx).roundToInt()
                                    val hourOffset = (totalDrag.y / hourHeightPx).roundToInt()
                                    if (dayOffset != 0 || hourOffset != 0) {
                                        val targetDate = baseDate.plusDays(dayOffset.toLong())
                                        val targetHour = (baseHour + hourOffset).coerceIn(0, 23)
                                        onDragDrop(event.id, targetDate, targetHour)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                                isDraggingEvent = false
                                totalDrag = Offset.Zero
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    }
                },
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = if (totalMinutes >= 60) 2 else 1,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            // Always show time if enough space, or if resizing (to give feedback)
            if (totalMinutes >= 45 || isResizingStart || isResizingEnd) {
                Text(
                    text = "${tempStartTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} - ${tempEndTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Time Tracker Badge - Floating overlay for precise feedback during resize
        if (isResizingStart || isResizingEnd) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "${tempStartTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} - ${tempEndTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Top-left resize handle (for start time)
        if (onResize != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(20.dp)
                    .background(
                        color = borderColor,
                        shape = CircleShape
                    )
                    .pointerInput(hourHeightPx, onResize) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isResizingStart = true
                                resizeDragStart = startOffset
                                resizeDragAmountStart = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                if (isResizingStart && onResize != null) {
                                    // Calculate new start time directly from drag amount to ensure latest value
                                    // Apply the same clamping as the visual effect
                                    val maxDragDown = with(density) { (eventHeightDp - 24.dp).toPx() }
                                    val effectiveDrag = resizeDragAmountStart.coerceAtMost(maxDragDown)

                                    val minutesChange = (effectiveDrag / hourHeightPx * 60f).roundToInt()
                                    val newStartTime = eventStart.plusMinutes(minutesChange.toLong())

                                    // Ensure new start time is before end time and not negative
                                    if (newStartTime.isBefore(eventEnd) && newStartTime.isAfter(eventStart.minusDays(1))) {
                                        onResize(event.id, newStartTime, null)
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                isResizingStart = false
                                resizeDragAmountStart = 0f
                            },
                            onDragCancel = {
                                isResizingStart = false
                                resizeDragAmountStart = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeDragAmountStart += dragAmount.y
                        }
                    }
            )
        }

        // Bottom-right resize handle (for end time)
        if (onResize != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(
                        color = borderColor,
                        shape = CircleShape
                    )
                    .pointerInput(hourHeightPx, onResize) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isResizingEnd = true
                                resizeDragStart = startOffset
                                resizeDragAmountEnd = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                if (isResizingEnd && onResize != null) {
                                    // Calculate new end time directly from drag amount
                                    val minutesChange = (resizeDragAmountEnd / hourHeightPx * 60f).roundToInt()
                                    val newEndTime = eventEnd.plusMinutes(minutesChange.toLong())

                                    // Ensure new end time is after start time
                                    if (newEndTime.isAfter(eventStart) && newEndTime.isBefore(eventEnd.plusDays(1))) {
                                        onResize(event.id, null, newEndTime)
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                isResizingEnd = false
                                resizeDragAmountEnd = 0f
                            },
                            onDragCancel = {
                                isResizingEnd = false
                                resizeDragAmountEnd = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeDragAmountEnd += dragAmount.y
                        }
                    }
            )
        }
    }
}
