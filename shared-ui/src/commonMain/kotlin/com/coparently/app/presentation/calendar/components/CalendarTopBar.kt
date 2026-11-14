package com.coparently.app.presentation.calendar.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.SwapSelectionHeader
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.addBarAction
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_add_event
import com.coparently.app.resources.calendar_filters_button
import com.coparently.app.resources.calendar_settings
import com.coparently.app.resources.calendar_today_button
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * What the calendar's top bar shows.
 *
 * @property children The family on screen's children, in display order
 * @property viewMode The view the grid shows
 * @property titleDate The day whose month titles the grid card ([CalendarMonthBar]'s `selectedDate`)
 * @property filtersActive Whether any filter is narrowing the calendar
 * @property swapMonth The month on screen while days are chosen for a swap, or null while they are not
 */
data class CalendarTopBarState(
    val children: List<ChildInfo>,
    val viewMode: CalendarViewMode,
    val titleDate: LocalDate,
    val filtersActive: Boolean,
    val swapMonth: YearMonth? = null
)

/**
 * What the calendar's top bar does.
 *
 * @property onViewModeChange Called with the mode picked in the segmented control
 * @property onFilters Opens the filter sheet
 * @property onSettings Opens Settings, or null to offer no gear
 * @property onToday Brings the calendar back to today
 * @property onAddEvent Opens the new-event form
 */
data class CalendarTopBarActions(
    val onViewModeChange: (CalendarViewMode) -> Unit,
    val onFilters: () -> Unit,
    val onSettings: (() -> Unit)?,
    val onToday: () -> Unit,
    val onAddEvent: () -> Unit
)

/**
 * What the bar does while days are chosen for a swap.
 *
 * @property onPreviousMonth Pages the grid a month back
 * @property onNextMonth Pages the grid a month on
 * @property onClose Ends the selection
 */
data class SwapSelectionBarActions(
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onClose: () -> Unit
)

/**
 * The calendar's top bar slot: [SwapSelectionHeader] while days are chosen for a swap, else
 * [CalendarHeader]. Android draws both as it always did, every row.
 *
 * Where a shell draws the bars (the iPhone, MON-24 phase 3, part B, spec §5.3) the navigation bar is
 * titled with the month the grid card shows, inline, and carries Today (`calendar`), Filters
 * (`line.3.horizontal.decrease.circle`, filled while a filter narrows the calendar), the gear
 * (`gearshape`) and "+" (`plus`), last, at the bar's edge; the header keeps only its Month/Week/Day
 * control, under the bar. While days are chosen the bar is the selection header's content. Public
 * for the app's `CalendarBarsTest`.
 *
 * @param state What the bar shows
 * @param actions What it does
 * @param swapActions What it does while days are chosen ([CalendarTopBarState.swapMonth] not null)
 */
@Composable
fun CalendarTopBar(
    state: CalendarTopBarState,
    actions: CalendarTopBarActions,
    swapActions: SwapSelectionBarActions
) {
    val swapMonth = state.swapMonth
    if (swapMonth != null) {
        SwapSelectionHeader(
            month = swapMonth,
            onPreviousMonth = swapActions.onPreviousMonth,
            onNextMonth = swapActions.onNextMonth,
            onClose = swapActions.onClose
        )
        return
    }
    if (shellDrawsBars()) CalendarShellBar(state, actions)
    CalendarHeader(
        children = state.children,
        viewMode = state.viewMode,
        onViewModeChange = actions.onViewModeChange,
        onFiltersClick = actions.onFilters,
        filtersActive = state.filtersActive,
        onSettingsClick = actions.onSettings
    )
}

/** The calendar's bar as the iPhone's navigation bar draws it; see [CalendarTopBar]. */
@Composable
private fun CalendarShellBar(state: CalendarTopBarState, actions: CalendarTopBarActions) {
    val today = BarAction(
        label = stringResource(Res.string.calendar_today_button),
        icon = Icons.Default.Today,
        sfSymbol = "calendar",
        onClick = actions.onToday
    )
    val filters = BarAction(
        label = stringResource(Res.string.calendar_filters_button),
        icon = Icons.Default.FilterList,
        // Filled while a filter narrows the calendar, as Android fills its Filters button.
        sfSymbol = if (state.filtersActive) FILTERS_ACTIVE_SYMBOL else FILTERS_SYMBOL,
        onClick = actions.onFilters
    )
    val settings = actions.onSettings?.let { onClick ->
        BarAction(
            label = stringResource(Res.string.calendar_settings),
            icon = Icons.Default.Settings,
            sfSymbol = "gearshape",
            onClick = onClick
        )
    }
    val add = addBarAction(stringResource(Res.string.calendar_add_event), actions.onAddEvent)
    AppTopBar(
        ScreenBar(
            title = calendarMonthTitle(state.titleDate),
            largeTitle = false,
            actions = listOfNotNull(today, filters, settings, add)
        )
    )
}

/** The Filters action's symbol. */
private const val FILTERS_SYMBOL = "line.3.horizontal.decrease.circle"

/** The Filters action's symbol while a filter narrows the calendar. */
private const val FILTERS_ACTIVE_SYMBOL = "line.3.horizontal.decrease.circle.fill"
