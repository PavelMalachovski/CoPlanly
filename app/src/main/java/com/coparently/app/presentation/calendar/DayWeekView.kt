package com.coparently.app.presentation.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.theme.CoPlanlyColors
import com.coparently.app.presentation.theme.Dimensions
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.dimensions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/** Virtual center page of the day/week pager (allows ~3 years of swiping each way). */
private const val PAGER_BASE_PAGE = 1200

/** Height of the custody band drawn above the week's day headers. */
private val CUSTODY_BAND_HEIGHT = 16.dp

/** Full-hue bar on an event block's start edge, carrying parent identity. */
private val EVENT_ACCENT_BAR_WIDTH = 3.dp

/** Block height from which a week-view title is allowed a second line. */
private val TWO_LINE_MIN_HEIGHT = 44.dp

/** Opacity of the hour-grid outline. Enough to read on DarkSurface without becoming a cage. */
private const val GRIDLINE_ALPHA = 0.55f

/** Today's tint strength in the week grid, drawn over the cell's base fill. */
private const val TODAY_TINT_ALPHA = 0.05f

/** Full-hue edge on a contact-window band: the marker that carries whose afternoon it is. */
private val CONTACT_WINDOW_EDGE_WIDTH = 3.dp

/** Corner radius of a contact-window band, matching the hour cells it lies over. */
private val CONTACT_WINDOW_CORNER = 4.dp

/** Timestamp format of a contact window's label in Day view. */
private val CONTACT_WINDOW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Width of the hour-label gutter.
 *
 * The day headers, the custody band, the hour grid and the absolutely-positioned events
 * overlay all measure from this. They have to agree: if one drifts, blocks stop lining up
 * with the day they belong to. It used to be written out as `iconSize * 2.17f` in four
 * separate places.
 *
 * 1.25x (~30dp on a compact phone) rather than the old 2.17x (~52dp). The gutter shows a
 * bare hour number ("13"), so it no longer has to fit "13:00"; the 22dp reclaimed goes to the
 * seven day columns, which are the scarce resource here.
 */
private val Dimensions.hourGutterWidth: Dp get() = iconSize * 1.25f

/**
 * Hourly view for day/week calendar views.
 * Horizontal swiping between days/weeks uses a [HorizontalPager], so the content
 * follows the finger with fling physics instead of a fixed swipe threshold.
 */
@Suppress("LongParameterList", "LongMethod") // screen-level composable: callbacks are its API surface
@Composable
fun DayWeekView(
    selectedDate: LocalDate,
    daysCount: Int,
    events: List<Event>,
    getCustody: (LocalDate) -> String?,
    getProposedCustody: (LocalDate) -> String? = { null },
    getContactWindows: (LocalDate) -> List<ContactWindow> = { emptyList() },
    parentNames: ParentNames,
    onDateChange: (LocalDate) -> Unit,
    onEventClick: (String) -> Unit,
    onAddEventClick: (LocalDate, Int) -> Unit = { _, _ -> },
    onEventDragDrop: ((String, LocalDate, Int) -> Unit)? = null,
    onEventResize: ((String, LocalDateTime?, LocalDateTime?) -> Unit)? = null,
    onEventDelete: ((String) -> Unit)? = null,
    onEventLongPressStart: ((String) -> Unit)? = null,
    onEventLongPressEnd: (() -> Unit)? = null,
    onDragOverDeleteButton: ((Boolean) -> Unit)? = null,
    deleteTargetBounds: () -> Rect? = { null },
    onOfferDay: ((LocalDate) -> Unit)? = null,
    holidays: Map<LocalDate, com.coparently.app.domain.holidays.Holiday> = emptyMap()
) {
    // The pager is anchored at a fixed date; each page offsets it by daysCount.
    // External date changes (Today button, month picker) re-anchor the pager.
    var anchorDate by remember { mutableStateOf(selectedDate) }
    var lastPagerDate by remember { mutableStateOf(selectedDate) }
    val pagerState = rememberPagerState(initialPage = PAGER_BASE_PAGE) { PAGER_BASE_PAGE * 2 + 1 }

    LaunchedEffect(selectedDate, daysCount) {
        if (selectedDate != lastPagerDate) {
            anchorDate = selectedDate
            lastPagerDate = selectedDate
            pagerState.scrollToPage(PAGER_BASE_PAGE)
        }
    }

    LaunchedEffect(pagerState, daysCount) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newDate = anchorDate.plusDays((page - PAGER_BASE_PAGE).toLong() * daysCount)
            if (newDate != lastPagerDate) {
                lastPagerDate = newDate
                onDateChange(newDate)
            }
        }
    }

    // Hour scroll position shared between pages so swiping keeps the time window
    var savedHourIndex by remember {
        mutableIntStateOf((java.time.LocalTime.now().hour - 1).coerceIn(0, 23))
    }
    var savedHourOffset by remember { mutableIntStateOf(0) }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        val pageDate = remember(page, anchorDate, daysCount) {
            anchorDate.plusDays((page - PAGER_BASE_PAGE).toLong() * daysCount)
        }
        val scrollState = rememberLazyListState(savedHourIndex, savedHourOffset)
        if (page == pagerState.settledPage) {
            LaunchedEffect(scrollState) {
                snapshotFlow {
                    scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    savedHourIndex = index
                    savedHourOffset = offset
                }
            }
        }
        CompositionLocalProvider(LocalDeleteTargetBounds provides deleteTargetBounds) {
            DayWeekPage(
                selectedDate = pageDate,
                daysCount = daysCount,
                events = events,
                getCustody = getCustody,
                getProposedCustody = getProposedCustody,
                getContactWindows = getContactWindows,
                parentNames = parentNames,
                scrollState = scrollState,
                onEventClick = onEventClick,
                onAddEventClick = onAddEventClick,
                onEventDragDrop = onEventDragDrop,
                onEventResize = onEventResize,
                onEventDelete = onEventDelete,
                onEventLongPressStart = onEventLongPressStart,
                onEventLongPressEnd = onEventLongPressEnd,
                onDragOverDeleteButton = onDragOverDeleteButton,
                onOfferDay = onOfferDay,
                holidays = holidays
            )
        }
    }
}

/**
 * One pager page: the fixed hour grid plus day columns and event overlay for
 * [selectedDate] (day view) or the week containing it (week view).
 */
// Pre-existing hour-grid body moved out of DayWeekView unchanged; splitting it
// further is tracked separately (was baselined under the old function name).
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun DayWeekPage(
    selectedDate: LocalDate,
    daysCount: Int,
    events: List<Event>,
    getCustody: (LocalDate) -> String?,
    getProposedCustody: (LocalDate) -> String?,
    getContactWindows: (LocalDate) -> List<ContactWindow>,
    parentNames: ParentNames,
    scrollState: LazyListState,
    onEventClick: (String) -> Unit,
    onAddEventClick: (LocalDate, Int) -> Unit = { _, _ -> },
    onEventDragDrop: ((String, LocalDate, Int) -> Unit)? = null,
    onEventResize: ((String, LocalDateTime?, LocalDateTime?) -> Unit)? = null,
    onEventDelete: ((String) -> Unit)? = null,
    onEventLongPressStart: ((String) -> Unit)? = null,
    onEventLongPressEnd: (() -> Unit)? = null,
    onDragOverDeleteButton: ((Boolean) -> Unit)? = null,
    onOfferDay: ((LocalDate) -> Unit)? = null,
    holidays: Map<LocalDate, com.coparently.app.domain.holidays.Holiday> = emptyMap()
) {
    val dims = dimensions()
    val today by rememberToday()
    val hours = (0..23).toList()
    val density = LocalDensity.current
    // Match the actually-rendered theme, not the system one (the app can force light while
    // the system is dark). Hoisted here because both the day headers and the hour-grid
    // columns below need it to pick the readable member of a colour pair.
    val isDarkTheme =
        MaterialTheme.colorScheme.surface.luminance() < CoPlanlyColors.DARK_LUMINANCE_THRESHOLD
    val hourCellHeight = dims.buttonHeight * 1.07f
    val hourCellHeightPx = remember(hourCellHeight, density) {
        with(density) { hourCellHeight.toPx() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fixed header row with modern design - 1.5x larger
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                // Pager supplies the horizontal motion; a date change inside one page only fades.
                fadeIn(tween(Motion.SHORT_MS, easing = LinearEasing)) togetherWith
                    fadeOut(tween(Motion.SHORT_MS, easing = LinearEasing))
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
                // Custody band across the visible week. The column tint alone answers "whose day
                // is this?" only once the eye has settled on a column; the band answers it for
                // the whole week at a glance, and shows where the handover falls.
                if (daysCount > 1) {
                    CustodyWeekBand(
                        dates = currentDates,
                        getCustody = getCustody,
                        parentNames = parentNames,
                        gutterWidth = dims.hourGutterWidth,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Week number for 3 days and week views (absolutely positioned, doesn't affect layout)
                if (daysCount >= 3) {
                    val weekFields = WeekFields.ISO // Always use Monday-first week
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
                            .width(dims.hourGutterWidth)
                            .fillMaxHeight()
                    )

                    currentDates.forEach { date ->
                        val isToday = date == today
                        val holiday = holidays[date]
                        val isPublicHoliday = holiday != null && !holiday.isSchoolVacation

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
                            // Day view's visible way to offer the day (D-10): a long-press on a
                            // month cell used to be the only one. Today onwards, and only where
                            // the day has a parent to hand it over from.
                            val offerThisDay = onOfferDay?.takeIf {
                                daysCount == 1 && !date.isBefore(today) && getCustody(date) != null
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp,
                                    Alignment.CenterHorizontally
                                )
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f, fill = offerThisDay == null),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = date.format(DateTimeFormatter.ofPattern("EEE")),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Normal,
                                        color = if (isToday) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isToday -> MaterialTheme.colorScheme.primary
                                            isPublicHoliday -> if (isDarkTheme) {
                                                CoPlanlyColors.HolidayRedDark
                                            } else {
                                                CoPlanlyColors.HolidayRed
                                            }
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    // Holiday name shown in single-day view where there is room
                                    if (holiday != null && daysCount == 1) {
                                        val holidayName = if (
                                            Locale.getDefault().language == holiday.localLanguage
                                        ) {
                                            holiday.nameLocal
                                        } else {
                                            holiday.nameEn
                                        }
                                        Text(
                                            text = holidayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            // Holiday/vacation names are text, so each needs the
                                            // member of its pair that clears AA on this theme.
                                            color = when {
                                                isPublicHoliday && isDarkTheme -> CoPlanlyColors.HolidayRedDark
                                                isPublicHoliday -> CoPlanlyColors.HolidayRed
                                                isDarkTheme -> CoPlanlyColors.VacationTint
                                                else -> CoPlanlyColors.VacationTintLight
                                            }
                                        )
                                    }
                                }
                                if (offerThisDay != null) {
                                    OfferDayButton(onClick = { offerThisDay(date) })
                                }
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
                                .width(dims.hourGutterWidth)
                                .height(hourCellHeight) // ~60dp for compact
                                .padding(top = dims.paddingSmall / 2),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                // Hour number only: on an hour gridline the ":00" is constant,
                                // so it costs gutter width without telling the user anything.
                                // The accessible time-slot description below still spells it out.
                                text = String.format(Locale.getDefault(), "%02d", hour),
                                // labelSmall (11sp) rather than bodyMedium: the hour gutter is
                                // narrow, and this keeps the rendered size while still scaling
                                // with the user's font-size setting.
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // Day columns (fade on date change; the pager does the sliding)
                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = {
                                // Pager supplies the horizontal motion; a date change inside one page only fades.
                                fadeIn(tween(Motion.SHORT_MS, easing = LinearEasing)) togetherWith
                                    fadeOut(tween(Motion.SHORT_MS, easing = LinearEasing))
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
                                    val isToday = date == today
                                    val custody = getCustody(date)
                                    val isWeekend = CustodyHelper.isWeekend(date)
                                    // Custody wins over the today tint, matching MonthView:
                                    // it is the product's core signal, and today already
                                    // reads as today from its coloured header above. The old
                                    // order hid custody on the one column parents check first.
                                    // The weekend is no longer part of that race — it is the
                                    // base both are drawn over. See DayCellFills.
                                    val fill = DayCellFills.weekHourCell(
                                        isWeekend = isWeekend,
                                        isToday = isToday,
                                        custody = custody,
                                        proposedCustody = getProposedCustody(date)
                                    )
                                    val baseColor = when (fill.base) {
                                        DayCellBase.WEEKEND ->
                                            if (isDarkTheme) {
                                                CoPlanlyColors.WeekendBackgroundDark
                                            } else {
                                                CoPlanlyColors.WeekendBackgroundLight
                                            }
                                        DayCellBase.SURFACE -> MaterialTheme.colorScheme.surface
                                    }
                                    val overlayColor = when (fill.overlay) {
                                        DayCellOverlay.CUSTODY_MOM -> ParentColors.container("mom")
                                        DayCellOverlay.CUSTODY_DAD -> ParentColors.container("dad")
                                        DayCellOverlay.TODAY ->
                                            MaterialTheme.colorScheme.primaryContainer
                                                .copy(alpha = TODAY_TINT_ALPHA)
                                        DayCellOverlay.PUBLIC_HOLIDAY,
                                        DayCellOverlay.NONE -> Color.Transparent
                                    }
                                    // A pending proposal's preview, over the agreed tint rather
                                    // than instead of it — same hue, less of it. The week gets
                                    // it as well as the month: unlike a handover, which the week
                                    // already shows as the boundary between two runs of its
                                    // custody band, a proposal has no other form in this layout,
                                    // and leaving it out would make the week the one view that
                                    // shows a schedule as settled while the month says it is not.
                                    val proposalColor = when (fill.pendingProposalFor) {
                                        DayCellOverlay.CUSTODY_MOM ->
                                            ParentColors.container(
                                                "mom",
                                                alpha = CoPlanlyColors.PROPOSAL_TINT_ALPHA
                                            )
                                        DayCellOverlay.CUSTODY_DAD ->
                                            ParentColors.container(
                                                "dad",
                                                alpha = CoPlanlyColors.PROPOSAL_TINT_ALPHA
                                            )
                                        else -> Color.Transparent
                                    }

                                    // Resolved here: the semantics lambda is not a composable context.
                                    val slotDescription = stringResource(
                                        R.string.calendar_time_slot_description,
                                        String.format(Locale.getDefault(), "%02d:00", hour),
                                        date.format(DateTimeFormatter.ofPattern("MMM dd"))
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(hourCellHeight)
                                            .background(
                                                color = baseColor,
                                                shape = RoundedCornerShape(dims.paddingSmall)
                                            )
                                            .background(
                                                color = overlayColor,
                                                shape = RoundedCornerShape(dims.paddingSmall)
                                            )
                                            .background(
                                                color = proposalColor,
                                                shape = RoundedCornerShape(dims.paddingSmall)
                                            )
                                            // Hour cells had no outline at all, so on a dark
                                            // surface the grid read as one flat block and the
                                            // hour boundaries were invisible.
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                                    .copy(alpha = GRIDLINE_ALPHA),
                                                shape = RoundedCornerShape(dims.paddingSmall)
                                            )
                                            .clickable {
                                                onAddEventClick(date, hour)
                                            }
                                            .semantics {
                                                contentDescription = slotDescription
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
            val hourLabelWidth = dims.hourGutterWidth
            val horizontalPadding = 8.dp
            val daySpacing = 4.dp

            Box(
                modifier = Modifier
                    .matchParentSize() // Match parent Box size (same as LazyColumn)
                    .padding(
                        start = horizontalPadding + hourLabelWidth,
                        end = horizontalPadding
                        // No top padding: offsetting by the header height shifted every event.
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
                        val spacingPx = with(density) { daySpacing.toPx() }
                        val totalSpacingPx = spacingPx * (currentDates.size - 1)
                        val columnWidth = (containerWidthPx - totalSpacingPx) / currentDates.size
                        val totalScroll = firstVisibleHour * hourCellHeightPx + scrollOffset
                        val firstHour = hours.first()

                        // Vertical pixel offset (from the top of the visible grid) for a time
                        fun yOffsetFor(time: LocalDateTime): Float {
                            val minutesFromTop = (time.hour - firstHour) * 60f + time.minute + time.second / 60f
                            return minutesFromTop / 60f * hourCellHeightPx - totalScroll
                        }

                        currentDates.forEachIndexed { dayIndex, date ->
                            val dayColumnX = dayIndex * (columnWidth + spacingPx)

                            // Contact windows (MON-6b) first, so events sit on top of them: an
                            // hour band in the window parent's tint over the day's own, on the
                            // same base (weekend grey or surface) the hour cells use, so the
                            // weekend layer survives inside the band too (DayCellFills).
                            val bandBase = if (CustodyHelper.isWeekend(date)) {
                                if (isDarkTheme) {
                                    CoPlanlyColors.WeekendBackgroundDark
                                } else {
                                    CoPlanlyColors.WeekendBackgroundLight
                                }
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            getContactWindows(date).forEach { window ->
                                ContactWindowBand(
                                    window = window,
                                    bounds = Rect(
                                        left = dayColumnX,
                                        top = yOffsetFor(date.atTime(window.start)),
                                        right = dayColumnX + columnWidth,
                                        bottom = yOffsetFor(date.atTime(window.end))
                                    ),
                                    baseColor = bandBase,
                                    parentName = parentNames.labelFor(window.parent),
                                    showLabel = daysCount == 1
                                )
                            }

                            // Multi-day/overnight events are clamped to this day and laid out
                            // in side-by-side lanes when they overlap in time.
                            layoutDayEvents(events, date).forEach { seg ->
                                val laneWidth = columnWidth / seg.laneCount
                                val x = dayColumnX + seg.lane * laneWidth
                                val y = yOffsetFor(seg.segStart)

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { x.toDp() },
                                            y = with(density) { y.toDp() }
                                        )
                                        .width(with(density) { laneWidth.toDp() })
                                        .padding(horizontal = 1.dp)
                                ) {
                                    EventChip(
                                        event = seg.event,
                                        onClick = { onEventClick(seg.event.id) },
                                        columnWidthPx = laneWidth,
                                        hourHeightPx = hourCellHeightPx,
                                        baseDate = date,
                                        baseHour = seg.segStart.hour,
                                        parentNames = parentNames,
                                        onDragDrop = onEventDragDrop,
                                        onResize = onEventResize,
                                        onDelete = onEventDelete,
                                        onLongPressStart = onEventLongPressStart,
                                        onLongPressEnd = onEventLongPressEnd,
                                        onDragOverDeleteButton = onDragOverDeleteButton,
                                        displayStart = seg.segStart,
                                        displayEnd = seg.segEnd,
                                        resizable = !seg.clamped,
                                        draggable = !seg.clamped,
                                        showTime = daysCount == 1
                                    )
                                }
                            }

                            // Current-time indicator (red line + dot) on today's column
                            if (date == today) {
                                val nowY = yOffsetFor(LocalDateTime.now())
                                Row(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { dayColumnX.toDp() },
                                            y = with(density) { nowY.toDp() } - 4.dp
                                        )
                                        .width(with(density) { columnWidth.toDp() }),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NowIndicatorColor, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(2.dp)
                                            .background(NowIndicatorColor)
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

/**
 * One contact window (MON-6b) as an hour band over its day column: the window parent's custody
 * tint over the cell's own base, and a full-hue edge — the saturation rule's two strengths of one
 * hue, the tint for the stretch of time and the edge as its marker. Drawn on its own base first
 * so the two parents' 14% tints are not stacked into a third colour, the reasoning `MonthView`'s
 * handover diagonal already follows.
 *
 * Not clickable: an empty hour inside the band still creates an event through the cell beneath,
 * which is the only route to one in this view. It carries a description instead, so a screen
 * reader hears the window the eye sees.
 *
 * @param window The window.
 * @param bounds Where it sits, in the overlay's pixels.
 * @param baseColor The hour cell's base under the band — weekend grey or surface.
 * @param parentName The window parent's name, for the label and the description.
 * @param showLabel Day view has room to write the description in the band; a week column does not.
 */
@Composable
private fun ContactWindowBand(
    window: ContactWindow,
    bounds: Rect,
    baseColor: Color,
    parentName: String,
    showLabel: Boolean
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(CONTACT_WINDOW_CORNER)
    val from = window.start.format(CONTACT_WINDOW_TIME)
    val to = window.end.format(CONTACT_WINDOW_TIME)
    val description = stringResource(R.string.calendar_contact_window_desc, parentName, from, to)
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { bounds.left.toDp() },
                y = with(density) { bounds.top.toDp() }
            )
            .width(with(density) { bounds.width.toDp() })
            .height(with(density) { bounds.height.toDp() })
            .clip(shape)
            .background(baseColor)
            .background(ParentColors.container(window.parent))
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(CONTACT_WINDOW_EDGE_WIDTH)
                .background(ParentColors.fill(window.parent))
        )
        if (showLabel) {
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = ParentColors.text(window.parent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = CONTACT_WINDOW_EDGE_WIDTH + 4.dp, top = 2.dp)
            )
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
    parentNames: ParentNames,
    onDragDrop: ((String, LocalDate, Int) -> Unit)?,
    onResize: ((String, LocalDateTime?, LocalDateTime?) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    onLongPressStart: ((String) -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    onDragOverDeleteButton: ((Boolean) -> Unit)? = null,
    // Visible segment of the event within the current day (differs from the real event
    // times for multi-day / overnight events, which are clamped to each day).
    displayStart: LocalDateTime = event.startDateTime,
    displayEnd: LocalDateTime = event.endDateTime ?: event.startDateTime.plusHours(1),
    // Continuation segments of multi-day events are not resizable/movable (ambiguous).
    resizable: Boolean = true,
    draggable: Boolean = true,
    // Day view spells the time out under the title; week view leaves it to the block's
    // vertical position and spends the row on the title instead.
    showTime: Boolean = true
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    // Read through the latest value rather than keyed: the lambda must not restart the drag
    // gesture, which a new key would do mid-drag.
    val currentDeleteTarget by rememberUpdatedState(LocalDeleteTargetBounds.current)

    // Sizing/temp times follow the visible segment; edits still act on the real event.
    val eventStart = displayStart
    val eventEnd = displayEnd

    // Calculate total event duration in minutes
    val totalDuration = java.time.Duration.between(eventStart, eventEnd)
    val totalMinutes = totalDuration.toMinutes().coerceAtLeast(15) // Minimum 15 minutes
    val eventHeightDp = with(density) { (hourHeightPx * totalMinutes / 60f).toDp() }

    // Transparent background colors (more transparent)
    val backgroundColor = when (event.parentOwner) {
        "mom", "dad" -> ParentColors.container(event.parentOwner, alpha = 0.3f)
        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    }

    val borderColor = when (event.parentOwner) {
        "mom", "dad" -> ParentColors.container(event.parentOwner, alpha = 0.8f)
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
    }

    // Full-hue bar on the start edge. This is what carries parent identity in week view, where
    // the block has no room for a label at all.
    val accentColor = when (event.parentOwner) {
        "mom", "dad" -> ParentColors.fill(event.parentOwner)
        else -> MaterialTheme.colorScheme.tertiary
    }

    // The title is NOT tinted with the parent colour: pink text on a pink fill (and blue on
    // blue) was the same hue at three alphas and unreadable. Identity is carried by the
    // border above; the label just has to be legible.
    val textColor = MaterialTheme.colorScheme.onSurface

    // Drag states
    var isDraggingEvent by remember { mutableStateOf(false) }
    var isResizingStart by remember { mutableStateOf(false) }
    var isResizingEnd by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    var resizeDragStart by remember { mutableStateOf(Offset.Zero) }
    var resizeDragAmountStart by remember { mutableStateOf(0f) }
    var resizeDragAmountEnd by remember { mutableStateOf(0f) }

    // Track if event is over delete button
    var isOverDeleteButton by remember { mutableStateOf(false) }

    // Long press state - track when user is holding the event
    var isLongPressing by remember { mutableStateOf(false) }
    // The times a resize would set, shown live in the badge while the handle is held. They are
    // the same values the drop writes (UX-16: a corner moves by the minute, never shorter than
    // MIN_EVENT_MINUTES), so the badge cannot promise a time the drop then rounds away.
    val tempStartTime = remember(isResizingStart, resizeDragAmountStart, eventStart, eventEnd) {
        if (isResizingStart) {
            resizedStart(eventStart, eventEnd, resizeDragAmountStart, hourHeightPx)
        } else {
            eventStart
        }
    }

    val tempEndTime = remember(isResizingEnd, resizeDragAmountEnd, eventStart, eventEnd) {
        if (isResizingEnd) {
            resizedEnd(eventStart, eventEnd, resizeDragAmountEnd, hourHeightPx)
        } else {
            eventEnd
        }
    }

    // Calculate dynamic height based on resize state
    val dynamicHeightDp = if (isResizingStart || isResizingEnd) {
        val heightAdjustment = with(density) {
            when {
                isResizingStart -> -resizeDragAmountStart.toDp()
                isResizingEnd -> resizeDragAmountEnd.toDp()
                else -> 0.dp
            }
        }
        (eventHeightDp + heightAdjustment).coerceAtLeast(24.dp)
    } else {
        eventHeightDp.coerceAtLeast(24.dp)
    }

    // Calculate vertical offset to keep bottom edge fixed when resizing from top
    val verticalOffsetDp = if (isResizingStart) {
        with(density) { resizeDragAmountStart.toDp() }
    } else {
        0.dp
    }

    // Track global position for delete button detection
    var eventGlobalPosition by remember { mutableStateOf(Offset.Zero) }

    // Accessibility strings resolved in composable scope: the semantics lambdas below are
    // not composable contexts, so stringResource cannot be called inside them.
    val a11yTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val chipStateDescription = when {
        isDraggingEvent -> stringResource(R.string.calendar_event_dragging)
        isResizingStart -> stringResource(
            R.string.calendar_event_resizing_start,
            tempStartTime.format(a11yTimeFormatter)
        )
        isResizingEnd -> stringResource(
            R.string.calendar_event_resizing_end,
            tempEndTime.format(a11yTimeFormatter)
        )
        isLongPressing -> stringResource(R.string.calendar_event_long_pressed)
        else -> stringResource(R.string.calendar_event_chip_hint)
    }
    val deleteActionLabel = stringResource(R.string.event_preview_delete)
    val openChip = onClick
    val chipDescription = stringResource(
        R.string.calendar_event_chip_description,
        event.title,
        chipStateDescription
    )
    val resizeStartDescription = stringResource(R.string.calendar_resize_start_handle)
    val resizeEndDescription = stringResource(R.string.calendar_resize_end_handle)
    val endLaterLabel = stringResource(R.string.calendar_action_end_later)
    val endEarlierLabel = stringResource(R.string.calendar_action_end_earlier)
    // As tall as fits: 48 dp at each edge would leave a one-hour block (about 51 dp) no middle to
    // tap or move it by, so a handle takes up to a third of the block. The actions below are the
    // route that does not depend on hitting it (docs/AUDIT-2026-10-design.md D-10).
    val handleTouchHeight = minOf(RESIZE_HANDLE_TOUCH_HEIGHT, eventHeightDp / 3)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeightDp)
            .offset(y = verticalOffsetDp)
            .onGloballyPositioned { coordinates ->
                // Store global position of event for delete button detection
                // localToWindow converts local coordinates to window coordinates
                eventGlobalPosition = coordinates.localToWindow(Offset.Zero)
            }
            .clip(RoundedCornerShape(6.dp))
            .background(
                color = if (isDraggingEvent && isOverDeleteButton) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                } else {
                    backgroundColor
                }
            )
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = Size(EVENT_ACCENT_BAR_WIDTH.toPx(), size.height)
                )
            }
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(event.id, onDelete, onLongPressStart, onLongPressEnd) {
                if (onDelete != null && onLongPressStart != null && onLongPressEnd != null) {
                    detectTapGestures(
                        onTap = {
                            if (!isLongPressing) {
                                // Normal click - open event
                                onClick()
                            }
                        },
                        onLongPress = {
                            isLongPressing = true
                            onLongPressStart(event.id)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                } else {
                    // If no delete handler, use normal clickable
                    detectTapGestures(
                        onTap = { onClick() }
                    )
                }
            }
            // Track pointer release when long pressing - restart when isLongPressing changes
            .pointerInput(event.id, isLongPressing) {
                if (isLongPressing && onLongPressEnd != null) {
                    awaitPointerEventScope {
                        while (isLongPressing) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val isPressed = event.changes.firstOrNull()?.pressed == true
                            if (!isPressed) {
                                // Released - hide delete button
                                isLongPressing = false
                                onLongPressEnd()
                                break
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                if (isDraggingEvent) {
                    shadowElevation = 8.dp.toPx()
                    translationX = totalDrag.x
                    translationY = totalDrag.y
                    // Make event more transparent and red-tinted when over delete button
                    alpha = if (isOverDeleteButton) 0.5f else 0.8f
                    if (isOverDeleteButton) {
                        // Add red tint when over delete button
                        // This is handled by changing the background color in the Box
                    }
                }
            }
            .semantics {
                contentDescription = chipDescription
                // Taps arrive through pointerInput, which TalkBack cannot activate, and the
                // drag-to-delete gesture has no accessible form at all. Both as actions.
                role = Role.Button
                // `this.` because the chip's own `onClick` parameter shadows the semantics one.
                this.onClick(label = null) {
                    openChip()
                    true
                }
                val actions = buildList {
                    if (onDelete != null) {
                        add(
                            CustomAccessibilityAction(deleteActionLabel) {
                                onDelete(event.id)
                                true
                            }
                        )
                    }
                    // The resize handle as actions, a quarter hour at a time. Without them,
                    // changing a duration needed a drag on a small target. Like the drag, they
                    // keep the end on its day and the event at least MIN_EVENT_MINUTES long.
                    if (onResize != null && resizable) {
                        val laterEnd = eventEnd.plusMinutes(RESIZE_ACTION_MINUTES)
                        if (laterEnd.toLocalDate() == eventEnd.toLocalDate()) {
                            add(
                                CustomAccessibilityAction(endLaterLabel) {
                                    onResize(event.id, null, laterEnd)
                                    true
                                }
                            )
                        }
                        val earlierEnd = eventEnd.minusMinutes(RESIZE_ACTION_MINUTES)
                        if (!earlierEnd.isBefore(eventStart.plusMinutes(MIN_EVENT_MINUTES))) {
                            add(
                                CustomAccessibilityAction(endEarlierLabel) {
                                    onResize(event.id, null, earlierEnd)
                                    true
                                }
                            )
                        }
                    }
                }
                if (actions.isNotEmpty()) customActions = actions
            }
    ) {
        // Event content - center area for drag & drop
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Asymmetric: the start edge clears the 3dp parent accent bar drawn behind.
                //
                // There used to be a further `.padding(start = 12.dp, end = 12.dp)` here,
                // commented as avoiding the resize handles. It cannot: the handles are pinned
                // to TopCenter and BottomCenter, so only vertical space moves content clear of
                // them. What it actually did was eat 24dp of a ~53dp week column, leaving
                // roughly one and a half characters — which is why week blocks rendered as
                // nothing but an ellipsis.
                .padding(start = 5.dp, end = 3.dp, top = 4.dp, bottom = 4.dp)
                .pointerInput(
                    columnWidthPx,
                    hourHeightPx,
                    onDragDrop,
                    onDelete,
                    onDragOverDeleteButton,
                    eventGlobalPosition,
                    draggable
                ) {
                    // Center drag for moving event
                    if (onDragDrop != null && draggable && columnWidthPx > 0f && hourHeightPx > 0f) {
                        val deleteSlopPx = with(density) { DELETE_TARGET_SLOP.toPx() }

                        // Store initial touch position in window coordinates
                        var startPositionInWindow = Offset.Zero

                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                isDraggingEvent = true
                                totalDrag = Offset.Zero
                                // Calculate initial position in window: event position + offset from event top-left
                                startPositionInWindow = eventGlobalPosition + startOffset
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = {
                                isDraggingEvent = false
                                totalDrag = Offset.Zero
                                isOverDeleteButton = false
                                onDragOverDeleteButton?.invoke(false)
                            },
                            onDragEnd = {
                                if (isDraggingEvent) {
                                    // Check if dropped over delete button area using the last known position
                                    // The position is already tracked in the drag handler
                                    if (isOverDeleteButton && onDelete != null) {
                                        // Delete event
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDelete(event.id)
                                        isOverDeleteButton = false
                                        onDragOverDeleteButton?.invoke(false)
                                    } else {
                                        // Normal drag & drop: snap the vertical move to a 15-minute grid
                                        val dayOffset = (totalDrag.x / columnWidthPx).roundToInt()
                                        val rawMinuteShift = (totalDrag.y / hourHeightPx * 60f).roundToInt()
                                        val snapSteps = (rawMinuteShift / MOVE_SNAP_MINUTES.toFloat()).roundToInt()
                                        val minuteShift = snapSteps * MOVE_SNAP_MINUTES
                                        if (dayOffset != 0 || minuteShift != 0) {
                                            val newStart = resizedTime(
                                                event.startDateTime.plusDays(dayOffset.toLong()),
                                                minuteShift.toFloat() / 60f * hourHeightPx,
                                                hourHeightPx,
                                                MOVE_SNAP_MINUTES
                                            )
                                            val targetMinuteOfDay = newStart.hour * 60 + newStart.minute
                                            onDragDrop(event.id, newStart.toLocalDate(), targetMinuteOfDay)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                                isDraggingEvent = false
                                totalDrag = Offset.Zero
                                isOverDeleteButton = false
                                onDragOverDeleteButton?.invoke(false)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount

                            // Calculate absolute position of pointer in window
                            // startPositionInWindow is the initial touch position in window coordinates
                            // totalDrag is the accumulated drag offset
                            // So current position = startPositionInWindow + totalDrag
                            val currentPositionInWindow = startPositionInWindow + totalDrag

                            // Over the red delete button itself, where it is drawn, with a little
                            // slop. This used to be the screen's bottom-right quadrant, which is
                            // not where the button sits (it floats 64 dp above the "+" button), so
                            // a move that ended low on the right deleted the event
                            // (docs/AUDIT-2026-10-design.md D-10).
                            val target = currentDeleteTarget()
                            val isInDeleteArea = target != null &&
                                target.inflate(deleteSlopPx).contains(currentPositionInWindow)

                            if (isInDeleteArea != isOverDeleteButton) {
                                isOverDeleteButton = isInDeleteArea
                                onDragOverDeleteButton?.invoke(isInDeleteArea)
                                if (isInDeleteArea) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (event.isPrivate) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.calendar_event_private),
                        tint = textColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
                if (event.pickupConfirmedBy != null) {
                    // pickupConfirmedBy is a slot; it is shown as that parent's name.
                    val confirmedByName = parentNames.labelFor(event.pickupConfirmedBy)
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(
                            R.string.calendar_event_pickup_confirmed,
                            confirmedByName
                        ),
                        tint = textColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    // Wrap onto a second line when the block is tall enough to show one.
                    // A ~53dp column fits roughly seven characters per line, so a second line
                    // is the difference between "Dentist" and "Dentist appt".
                    maxLines = if (dynamicHeightDp >= TWO_LINE_MIN_HEIGHT) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    // Medium rather than SemiBold: at this size the heavier weight is no more
                    // legible on a tinted fill, and it costs about half a character per line —
                    // which in a ~54dp column is the difference between fitting a word and not.
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // The time is spelled out only in day view. In week view the block's vertical
            // position already encodes it, and the row it would occupy is worth more to the
            // title. It still appears mid-resize in either mode, where the user is actively
            // setting a time and needs the feedback.
            if (showTime && totalMinutes >= 45 || isResizingStart || isResizingEnd) {
                Text(
                    text = "${tempStartTime.format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    )} - ${tempEndTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
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
                    text = "${tempStartTime.format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    )} - ${tempEndTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Top resize handle (start time) - centered pill, drag up to start earlier, down to start later
        if (onResize != null && resizable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(RESIZE_HANDLE_TOUCH_WIDTH)
                    .height(handleTouchHeight)
                    .drawBehind { drawResizePill(borderColor, atTop = true) }
                    .semantics { contentDescription = resizeStartDescription }
                    .pointerInput(hourHeightPx, onResize) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isResizingStart = true
                                resizeDragStart = startOffset
                                resizeDragAmountStart = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                if (isResizingStart) {
                                    val newStartTime =
                                        resizedStart(eventStart, eventEnd, resizeDragAmountStart, hourHeightPx)
                                    if (newStartTime != eventStart) {
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

        // Bottom resize handle (end time) - centered pill, drag down to end later, up to end earlier
        if (onResize != null && resizable) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(RESIZE_HANDLE_TOUCH_WIDTH)
                    .height(handleTouchHeight)
                    .drawBehind { drawResizePill(borderColor, atTop = false) }
                    .semantics { contentDescription = resizeEndDescription }
                    .pointerInput(hourHeightPx, onResize) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isResizingEnd = true
                                resizeDragStart = startOffset
                                resizeDragAmountEnd = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                if (isResizingEnd) {
                                    val newEndTime =
                                        resizedEnd(eventStart, eventEnd, resizeDragAmountEnd, hourHeightPx)
                                    if (newEndTime != eventEnd) {
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

/**
 * Day view's "Swap this day". It opens the sheet a long-press on a month cell opens, for the day
 * on screen (docs/AUDIT-2026-10-design.md D-10). The label is the sheet's own title, so the
 * button says where it leads.
 */
@Composable
private fun OfferDayButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.day_swap_sheet_title),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Width of a resize handle's touch strip; the pill drawn in it is [RESIZE_PILL_SIZE] wide. */
private val RESIZE_HANDLE_TOUCH_WIDTH = 48.dp

/** Tallest a resize handle's touch strip gets, on a block tall enough to spare it. */
private val RESIZE_HANDLE_TOUCH_HEIGHT = 24.dp

/** The visible pill of a resize handle, unchanged from before its touch strip grew. */
private val RESIZE_PILL_SIZE = DpSize(40.dp, 14.dp)

/** Minutes the accessibility actions move an event's end by, the same step as a move. */
private const val RESIZE_ACTION_MINUTES = 15L

/**
 * Draws a resize handle's pill against the strip's outer edge: the top of the strip for the
 * start handle, the bottom for the end handle, centred across it, where it always sat.
 */
private fun DrawScope.drawResizePill(color: Color, atTop: Boolean) {
    val pill = RESIZE_PILL_SIZE.toSize()
    val left = (size.width - pill.width) / 2f
    val top = if (atTop) 0f else size.height - pill.height
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = pill,
        cornerRadius = CornerRadius(pill.height / 2f)
    )
}

/**
 * Where the red delete button is, in window coordinates, while it is on screen, or null.
 * [DayWeekView] provides it and each event's drag reads it (docs/AUDIT-2026-10-design.md D-10).
 * A local rather than a parameter, so it is not threaded through [DayWeekPage] into every chip.
 */
private val LocalDeleteTargetBounds = compositionLocalOf<() -> Rect?> { { null } }

/**
 * How far outside the red delete button a dragged event still counts as over it. The button is
 * 56 dp; a finger carrying an event covers part of it, so an exact hit test would feel stingy.
 */
private val DELETE_TARGET_SLOP = 16.dp

/**
 * Minutes a whole event moves by when it is dragged (UX-16): a quarter hour, because a move is a
 * coarse gesture across a grid drawn in hours and nobody reschedules a dentist to 10:07.
 */
private const val MOVE_SNAP_MINUTES = 15

/**
 * Minutes a resize handle moves by (UX-16): one. A corner is the precise gesture — the one a
 * parent reaches for when pickup is at 15:40 — and the live time badge over the chip is what
 * makes single minutes usable under a thumb.
 */
private const val RESIZE_STEP_MINUTES = 1

/**
 * The shortest an event can be made by dragging a corner. The move grid's quarter hour, so a
 * block stays tall enough to grab again, and so a corner dragged past the other one leaves an
 * event rather than refusing the drop.
 */
private const val MIN_EVENT_MINUTES = 15L

/** Google-Calendar-style red "now" indicator. */
private val NowIndicatorColor = Color(0xFFEA4335)

/**
 * A single event's visible slice within one day, with its lane assignment for
 * side-by-side layout of overlapping events.
 *
 * @property segStart start of the visible slice (clamped to the day)
 * @property segEnd end of the visible slice (clamped to the day)
 * @property clamped true when the event extends beyond this day (multi-day/overnight)
 * @property lane 0-based column index within its overlap cluster
 * @property laneCount number of columns in that cluster
 */
private data class EventSegment(
    val event: Event,
    val segStart: LocalDateTime,
    val segEnd: LocalDateTime,
    val clamped: Boolean,
    val lane: Int,
    val laneCount: Int
)

/**
 * Computes the drawable event slices for [date]: events that overlap the day are
 * clamped to it (so multi-day/overnight events appear on every day they cover), then
 * grouped into overlap clusters and assigned side-by-side lanes (greedy first-fit,
 * optimal for intervals) so concurrent events don't stack on top of each other.
 */
private fun layoutDayEvents(events: List<Event>, date: LocalDate): List<EventSegment> {
    val dayStart = date.atStartOfDay()
    val dayEnd = date.plusDays(1).atStartOfDay()

    data class Slice(val event: Event, val start: LocalDateTime, val end: LocalDateTime, val clamped: Boolean)

    val slices = events.mapNotNull { e ->
        val s = e.startDateTime
        val en = e.endDateTime ?: e.startDateTime.plusHours(1)
        if (s.isBefore(dayEnd) && en.isAfter(dayStart)) {
            val cs = if (s.isBefore(dayStart)) dayStart else s
            val ce = if (en.isAfter(dayEnd)) dayEnd else en
            Slice(e, cs, ce, s.isBefore(dayStart) || en.isAfter(dayEnd))
        } else {
            null
        }
    }.sortedWith(compareBy({ it.start }, { it.end }))

    val result = mutableListOf<EventSegment>()
    val cluster = mutableListOf<Slice>()
    var clusterEnd: LocalDateTime? = null

    fun flushCluster() {
        if (cluster.isEmpty()) return
        val laneEnds = mutableListOf<LocalDateTime>()
        val lanes = IntArray(cluster.size)
        cluster.forEachIndexed { i, slice ->
            var lane = laneEnds.indexOfFirst { !slice.start.isBefore(it) }
            if (lane == -1) {
                laneEnds.add(slice.end)
                lane = laneEnds.size - 1
            } else {
                laneEnds[lane] = slice.end
            }
            lanes[i] = lane
        }
        val laneCount = laneEnds.size
        cluster.forEachIndexed { i, slice ->
            result.add(EventSegment(slice.event, slice.start, slice.end, slice.clamped, lanes[i], laneCount))
        }
        cluster.clear()
        clusterEnd = null
    }

    for (slice in slices) {
        val currentEnd = clusterEnd
        if (currentEnd == null || slice.start.isBefore(currentEnd)) {
            cluster.add(slice)
            clusterEnd = if (currentEnd == null || slice.end.isAfter(currentEnd)) slice.end else currentEnd
        } else {
            flushCluster()
            cluster.add(slice)
            clusterEnd = slice.end
        }
    }
    flushCluster()
    return result
}

/**
 * Applies a vertical drag (in pixels) to a base time and snaps the result to the nearest
 * multiple of [stepMinutes] within the same day — [MOVE_SNAP_MINUTES] for a move,
 * [RESIZE_STEP_MINUTES] for a corner.
 */
private fun resizedTime(
    base: LocalDateTime,
    dragPx: Float,
    hourHeightPx: Float,
    stepMinutes: Int
): LocalDateTime {
    val deltaMinutes = (dragPx / hourHeightPx * 60f).roundToInt()
    val moved = base.plusMinutes(deltaMinutes.toLong()).withSecond(0).withNano(0)
    val minutesOfDay = moved.hour * 60 + moved.minute
    val snapped = ((minutesOfDay + stepMinutes / 2) / stepMinutes) * stepMinutes
    val clamped = snapped.coerceIn(0, 24 * 60 - stepMinutes)
    return moved.toLocalDate().atStartOfDay().plusMinutes(clamped.toLong())
}

/**
 * Where the top handle puts the start: [start] moved by the minute, and never later than
 * [MIN_EVENT_MINUTES] before [end].
 */
private fun resizedStart(
    start: LocalDateTime,
    end: LocalDateTime,
    dragPx: Float,
    hourHeightPx: Float
): LocalDateTime {
    val moved = resizedTime(start, dragPx, hourHeightPx, RESIZE_STEP_MINUTES)
    // An event already shorter than the minimum is not stretched by a drag that did not ask to:
    // the bound is where it starts, not a quarter hour before its end.
    val latest = end.minusMinutes(MIN_EVENT_MINUTES).let { if (it.isBefore(start)) start else it }
    return if (moved.isAfter(latest)) latest else moved
}

/**
 * Where the bottom handle puts the end: [end] moved by the minute, and never earlier than
 * [MIN_EVENT_MINUTES] after [start].
 */
private fun resizedEnd(
    start: LocalDateTime,
    end: LocalDateTime,
    dragPx: Float,
    hourHeightPx: Float
): LocalDateTime {
    val moved = resizedTime(end, dragPx, hourHeightPx, RESIZE_STEP_MINUTES)
    val earliest = start.plusMinutes(MIN_EVENT_MINUTES).let { if (it.isAfter(end)) end else it }
    return if (moved.isBefore(earliest)) earliest else moved
}

/**
 * Solid band above the week's day headers, split into runs of consecutive same-custody days.
 *
 * Each run is drawn in the parent's solid chip tone ([ParentColors.chipFill], so its label
 * clears AA) and labelled when it is wide enough to hold a word; a one-day
 * run gets no label, because a clipped "M" is worse than a plain coloured block. Column geometry
 * (gutter width, weights, 4dp gaps) mirrors the header and content rows so the band lines up
 * with the days it describes.
 *
 * @param dates Visible dates, in order
 * @param getCustody Unified custody lookup (model first, legacy schedules as fallback)
 * @param gutterWidth Width of the hour-label gutter the band must skip
 * @param modifier Modifier for the band row
 */
@Composable
private fun CustodyWeekBand(
    dates: List<LocalDate>,
    getCustody: (LocalDate) -> String?,
    parentNames: ParentNames,
    gutterWidth: Dp,
    modifier: Modifier = Modifier
) {
    // Collapse the week into runs so a Mon–Wed stretch is one block, not three.
    val runs: List<Pair<String?, Int>> = remember(dates, getCustody) {
        buildList {
            dates.forEach { date ->
                val custody = getCustody(date)
                val last = lastOrNull()
                if (last != null && last.first == custody) {
                    set(lastIndex, custody to last.second + 1)
                } else {
                    add(custody to 1)
                }
            }
        }
    }
    if (runs.all { it.first == null }) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CUSTODY_BAND_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.width(gutterWidth))

        runs.forEach { (custody, days) ->
            // The solid chip-grade tone, not the full-strength fill: the band carries a name in
            // white-or-black, and white on the full pink is 4.35:1, under AA. The label colour is
            // then picked by contrast against whatever the family chose.
            val color = when (custody) {
                "mom" -> ParentColors.chipFill("mom")
                "dad" -> ParentColors.chipFill("dad")
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .weight(days.toFloat())
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (custody != null && days >= 2) {
                    Text(
                        text = parentNames.labelFor(custody),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ParentColors.onFill(color),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
