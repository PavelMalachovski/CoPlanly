package com.coparently.app.presentation.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale as JavaLocale
import com.coparently.app.data.local.entity.CustodyScheduleEntity
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.theme.CoParentlyColors
import com.coparently.app.presentation.theme.dimensions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Enhanced month view with week numbers and modern UX.
 * Shows calendar in month grid with week numbers on the left.
 * Supports vertical swipe gestures to navigate between weeks.
 */
@Composable
fun MonthView(
    selectedMonth: YearMonth,
    selectedDate: LocalDate? = null,
    events: List<Event>,
    custodySchedules: List<CustodyScheduleEntity>,
    onDayClick: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onDateChange: ((LocalDate) -> Unit)? = null,
    onEventDragDrop: ((String, LocalDate) -> Unit)? = null
) {
    val weekFields = remember { WeekFields.of(Locale.getDefault()) }
    val firstDayOfWeek = remember { weekFields.firstDayOfWeek }
    val density = LocalDensity.current

    val referenceDate = selectedDate ?: selectedMonth.atDay(1)
    val currentWeekStart = remember(referenceDate, firstDayOfWeek) {
        alignToWeekStart(referenceDate, firstDayOfWeek)
    }

    val totalDragState = remember { mutableFloatStateOf(0f) }
    var totalDrag by totalDragState
    var isSwipeInProgress by remember { mutableStateOf(false) }

    val weeksBefore = 6
    val weeksAfter = 6
    val visibleWeeks = 6
    val weekSpacing = 4.dp

    val weeks = remember(currentWeekStart, selectedMonth, firstDayOfWeek) {
        (-weeksBefore..weeksAfter).map { offset ->
            val weekStart = currentWeekStart.plusWeeks(offset.toLong())
            generateWeeksFromWeekStart(
                weekStart = weekStart,
                firstDayOfWeek = firstDayOfWeek,
                weeksToShow = 1
            ).first()
        }
    }

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = weeksBefore
    )

    LaunchedEffect(currentWeekStart) {
        totalDrag = 0f
        isSwipeInProgress = false
        lazyListState.scrollToItem(weeksBefore)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        WeekdayHeader(firstDayOfWeek)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            val totalSpacing = weekSpacing * (visibleWeeks - 1)
            val weekRowHeight = remember(maxHeight) {
                val calculated = (maxHeight - totalSpacing) / visibleWeeks
                if (calculated < 48.dp) 48.dp else calculated
            }
            val weekRowHeightPx = remember(weekRowHeight, density) {
                with(density) { weekRowHeight.toPx() }
            }

            // Track week numbers to avoid duplicates
            val weeksWithVisibility = remember(weeks, weekFields) {
                val seen = mutableSetOf<Int>()
                weeks.mapIndexed { index, week ->
                    val weekNumber = week.firstOrNull()?.get(weekFields.weekOfWeekBasedYear()) ?: 0
                    val isDuplicate = seen.contains(weekNumber)
                    if (!isDuplicate) {
                        seen.add(weekNumber)
                    }
                    Triple(week, index, !isDuplicate)
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentWeekStart) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val dragValue = totalDrag
                                if (abs(dragValue) > 200f) {
                                    val weeksToAdd = if (dragValue > 0) -1 else 1
                                    val newWeekStart = currentWeekStart.plusWeeks(weeksToAdd.toLong())
                                    if (onDateChange != null) {
                                        onDateChange(newWeekStart)
                                    } else {
                                        val newMonth = YearMonth.from(newWeekStart)
                                        if (newMonth != selectedMonth) {
                                            onMonthChange(newMonth)
                                        }
                                    }
                                }
                                totalDrag = 0f
                                isSwipeInProgress = false
                            },
                            onDragCancel = {
                                totalDrag = 0f
                                isSwipeInProgress = false
                            }
                        ) { _, dragAmount ->
                            totalDrag += dragAmount
                            if (!isSwipeInProgress && abs(totalDrag) > 5f) {
                                isSwipeInProgress = true
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(weekSpacing),
                userScrollEnabled = false
            ) {
                items(
                    items = weeksWithVisibility,
                    key = { (week, _, _) -> week.firstOrNull() ?: currentWeekStart }
                ) { (week, _, showWeekNumber) ->
                    WeekRow(
                        week = week,
                        selectedMonth = selectedMonth,
                        events = events,
                        custodySchedules = custodySchedules,
                        weekFields = weekFields,
                        weekHeight = weekRowHeight,
                        onDayClick = onDayClick,
                        isSwipeInProgress = isSwipeInProgress,
                        showWeekNumber = showWeekNumber,
                        onEventDragDrop = onEventDragDrop,
                        weekRowHeightPx = weekRowHeightPx
                    )
                }
            }
        }
    }
}

/**
 * Weekday header row (Mon, Tue, Wed, etc.) - reduced height by 2x
 */
@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek) {
    val dims = dimensions()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dims.buttonHeight * 0.8f) // ~45dp for compact
            .background(MaterialTheme.colorScheme.surface)
            .semantics {
                contentDescription = "Calendar weekday header"
            },
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Week number column header
        Box(
            modifier = Modifier
                .width(dims.iconSize * 1.33f) // ~32dp for compact
                .height(dims.buttonHeight * 0.8f) // ~45dp for compact
                .padding(end = dims.paddingSmall / 2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // Weekday names
        val weekdays = remember(firstDayOfWeek) {
            val days = mutableListOf<DayOfWeek>()
            var current = firstDayOfWeek
            repeat(7) {
                days.add(current)
                current = current.plus(1)
            }
            days
        }

        weekdays.forEach { dayOfWeek ->
            val isToday = dayOfWeek == LocalDate.now().dayOfWeek

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(dims.paddingSmall / 8) // ~1dp for compact
                    .background(
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(dims.cornerRadius / 2)
                    )
                    .padding(dims.paddingSmall / 2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        Locale.getDefault()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    fontSize = 8.sp,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Single week row with week number and day cells
 */
@Composable
private fun WeekRow(
    week: List<LocalDate>,
    selectedMonth: YearMonth,
    events: List<Event>,
    custodySchedules: List<CustodyScheduleEntity>,
    weekFields: WeekFields,
    weekHeight: Dp,
    onDayClick: (LocalDate) -> Unit,
    isSwipeInProgress: Boolean = false,
    showWeekNumber: Boolean = true,
    onEventDragDrop: ((String, LocalDate) -> Unit)? = null,
    weekRowHeightPx: Float
) {
    val dims = dimensions()
    val weekNumber = remember(week) {
        week.firstOrNull()?.get(weekFields.weekOfWeekBasedYear()) ?: 0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(weekHeight),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Week number - only show if not duplicate
        Box(
            modifier = Modifier
                .width(dims.iconSize * 1.33f) // ~32dp for compact
                .fillMaxHeight()
                .padding(end = dims.paddingSmall / 2),
            contentAlignment = Alignment.Center
        ) {
            if (showWeekNumber) {
                Text(
                    text = weekNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Day cells
        week.forEach { date ->
            DayCell(
                date = date,
                isCurrentMonth = YearMonth.from(date) == selectedMonth,
                events = events.filter { it.startDateTime.toLocalDate() == date },
                custodySchedules = custodySchedules,
                onDayClick = onDayClick,
                isSwipeInProgress = isSwipeInProgress,
                onEventDragDrop = onEventDragDrop,
                weekRowHeightPx = weekRowHeightPx
            )
        }
    }
}

/**
 * Individual day cell with events preview
 */
@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    events: List<Event>,
    custodySchedules: List<CustodyScheduleEntity>,
    onDayClick: (LocalDate) -> Unit,
    isSwipeInProgress: Boolean = false,
    onEventDragDrop: ((String, LocalDate) -> Unit)? = null,
    weekRowHeightPx: Float
) {
    val dims = dimensions()
    val isToday = CustodyHelper.isToday(date)
    val custody = CustodyHelper.getCustodyForDate(date, custodySchedules)

    val backgroundColor = when {
        !isCurrentMonth -> MaterialTheme.colorScheme.surface
        custody == "mom" -> CoParentlyColors.MomPink.copy(alpha = 0.08f)
        custody == "dad" -> CoParentlyColors.DadBlue.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }

    // Build semantic content description for accessibility
    val semanticDescription = buildString {
        // Date information
        append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", JavaLocale.getDefault())))

        // Today indicator
        if (isToday) {
            append(", Today")
        }

        // Month context
        if (!isCurrentMonth) {
            append(", Outside current month")
        }

        // Custody information
        if (custody != null) {
            append(", With ")
            append(if (custody == "mom") "Mom" else "Dad")
        }

        // Events information
        if (events.isNotEmpty()) {
            append(", ")
            append(events.size)
            append(" event")
            if (events.size > 1) append("s")
            // List first event title
            events.firstOrNull()?.let { event ->
                append(": ")
                append(event.title)
            }
        }
    }

    val clickLabel = "View events for ${date.format(DateTimeFormatter.ofPattern("MMMM d", JavaLocale.getDefault()))}"

    var cellWidthPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp) // Ensure 48dp minimum touch target
            .semantics {
                contentDescription = semanticDescription
                role = Role.Button
            }
            .padding(dims.paddingSmall / 8) // ~1dp for compact
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(dims.cornerRadius / 2)
            )
            .onGloballyPositioned { coordinates ->
                cellWidthPx = coordinates.size.width.toFloat()
            }
            .clickable(
                enabled = !isSwipeInProgress,
                onClick = {
                    if (!isSwipeInProgress) {
                        onDayClick(date)
                    }
                },
                onClickLabel = clickLabel
                // Material3 automatically applies ripple effect
            )
            .padding(dims.paddingSmall / 2),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Day number with minimum touch target size (48dp for accessibility)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 32.dp, minHeight = 32.dp) // Visual size
                    .background(
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (events.isNotEmpty()) {
                val eventToShow = events.firstOrNull()
                eventToShow?.let { event ->
                    val eventColor = when (event.parentOwner) {
                        "mom" -> CoParentlyColors.MomPink
                        "dad" -> CoParentlyColors.DadBlue
                        else -> MaterialTheme.colorScheme.tertiary
                    }

                    // Animated visibility for events with spring animation
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(event.id) {
                        isVisible = true
                    }

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        // Pulse animation for today's events
                        val pulseModifier = if (isToday && event.startDateTime.toLocalDate() == LocalDate.now()) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulse by infiniteTransition.animateFloat(
                                initialValue = 0.95f,
                                targetValue = 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAnimation"
                            )
                            Modifier.graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                            }
                        } else {
                            Modifier
                        }

                        // Drag and drop state
                        var isDragging by remember { mutableStateOf(false) }
                        var totalDrag by remember { mutableStateOf(Offset.Zero) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(pulseModifier)
                                .background(
                                    color = eventColor.copy(alpha = if (isDragging) 0.7f else 0.9f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .pointerInput(cellWidthPx, weekRowHeightPx, onEventDragDrop) {
                                    if (onEventDragDrop != null && cellWidthPx > 0f && weekRowHeightPx > 0f) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                isDragging = true
                                                totalDrag = Offset.Zero
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                totalDrag = Offset.Zero
                                            },
                                            onDragEnd = {
                                                if (isDragging) {
                                                    val horizontalDays = (totalDrag.x / cellWidthPx).roundToInt()
                                                    val verticalWeeks = (totalDrag.y / weekRowHeightPx).roundToInt()
                                                    val dayOffset = horizontalDays + verticalWeeks * 7
                                                    if (dayOffset != 0) {
                                                        val targetDate = date.plusDays(dayOffset.toLong())
                                                        onEventDragDrop(event.id, targetDate)
                                                    }
                                                }
                                                isDragging = false
                                                totalDrag = Offset.Zero
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                        }
                                    }
                                }
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (events.size > 1) {
                    Text(
                        text = "+${events.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = if (isCurrentMonth) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Generate weeks starting from a specific week start date
 */
private fun generateWeeksFromWeekStart(
    weekStart: LocalDate,
    firstDayOfWeek: DayOfWeek,
    weeksToShow: Int = 6
): List<List<LocalDate>> {
    var currentDate = weekStart
    while (currentDate.dayOfWeek != firstDayOfWeek) {
        currentDate = currentDate.minusDays(1)
    }

    val weeks = mutableListOf<List<LocalDate>>()

    repeat(weeksToShow) {
        val week = mutableListOf<LocalDate>()
        repeat(7) {
            week.add(currentDate)
            currentDate = currentDate.plusDays(1)
        }
        weeks.add(week)
    }

    return weeks
}

private fun alignToWeekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
    var current = date
    while (current.dayOfWeek != firstDayOfWeek) {
        current = current.minusDays(1)
    }
    return current
}
