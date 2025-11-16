package com.coparently.app.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.theme.CoParentlyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

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
    onAddEventClick: (LocalDate, Int) -> Unit = { _, _ -> }
) {
    val hours = (0..23).toList()

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
        // Animated header with same animation as hour columns
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                val direction = swipeDirection
                (slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { fullWidth -> fullWidth * direction }
                ) + fadeIn(animationSpec = tween(300))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { fullWidth -> -fullWidth * direction }
                ) + fadeOut(animationSpec = tween(300)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) { currentDate ->
            val currentDates = remember(currentDate, daysCount) {
                val startDate = if (daysCount == 7) {
                    // For week view, start from Monday of the week containing currentDate
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val dayOfWeek = currentDate.dayOfWeek
                    val daysFromMonday = (dayOfWeek.value - weekFields.firstDayOfWeek.value + 7) % 7
                    currentDate.minusDays(daysFromMonday.toLong())
                } else {
                    // For day and 3-day views, start from currentDate
                    currentDate
                }
                (0 until daysCount).map { startDate.plusDays(it.toLong()) }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp)
            ) {
                // Week number for 3 days and week views (absolutely positioned, doesn't affect layout)
                if (daysCount >= 3) {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    val firstDate = currentDates.firstOrNull() ?: currentDate
                    val weekNumber = firstDate.get(weekFields.weekOfWeekBasedYear())

                    Box(
                        modifier = Modifier
                            .width(32.dp)
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
                            .width(52.dp)
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

        // Scrollable content - time stays in place, only days animate
        // Use single scrollState shared across all dates to preserve scroll position
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f)
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
                            .width(52.dp)
                            .height(60.dp)
                            .padding(top = 4.dp),
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

                    // Day columns - animated, inside AnimatedContent
                    AnimatedContent(
                        targetState = selectedDate,
                        transitionSpec = {
                            val direction = swipeDirection
                            (slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { fullWidth -> fullWidth * direction }
                            ) + fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { fullWidth -> -fullWidth * direction }
                            ) + fadeOut(animationSpec = tween(300)))
                        },
                        modifier = Modifier.weight(1f)
                    ) { currentDate ->
                        // Day columns for this hour - use same logic as dates array
                        val currentDates = remember(currentDate, daysCount) {
                            val startDate = if (daysCount == 7) {
                                // For week view, start from Monday of the week containing currentDate
                                val weekFields = WeekFields.of(Locale.getDefault())
                                val dayOfWeek = currentDate.dayOfWeek
                                val daysFromMonday = (dayOfWeek.value - weekFields.firstDayOfWeek.value + 7) % 7
                                currentDate.minusDays(daysFromMonday.toLong())
                            } else {
                                // For day and 3-day views, start from currentDate
                                currentDate
                            }
                            (0 until daysCount).map { startDate.plusDays(it.toLong()) }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            currentDates.forEach { date ->
                            val dateEvents = events.filter {
                                it.startDateTime.toLocalDate() == date &&
                                it.startDateTime.hour == hour
                            }
                            val isToday = date == LocalDate.now()
                            val custody = CustodyHelper.getCustodyForDate(date, custodySchedules)
                            val backgroundColor = when {
                                dateEvents.isNotEmpty() -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                                custody == "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.03f)
                                custody == "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.03f)
                                else -> MaterialTheme.colorScheme.surface
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .background(
                                        color = backgroundColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (dateEvents.isNotEmpty()) {
                                            onEventClick(dateEvents.first().id)
                                        } else {
                                            onAddEventClick(date, hour)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (dateEvents.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            dateEvents.take(2).forEach { event ->
                                                EventChip(
                                                    event = event,
                                                    onClick = { onEventClick(event.id) }
                                                )
                                            }
                                            if (dateEvents.size > 2) {
                                                Text(
                                                    text = "+${dateEvents.size - 2}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center
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
            }
        }
    }
}


@Composable
private fun EventChip(
    event: Event,
    onClick: () -> Unit
) {
    val backgroundColor = when (event.parentOwner) {
        "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.9f)
        "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val textColor = when (event.parentOwner) {
        "mom" -> Color.White
        "dad" -> Color.White
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

