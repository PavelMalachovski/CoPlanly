package com.coparently.app.presentation.calendar.components

import androidx.compose.runtime.Composable
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.SwapSelectionHeader
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.resources.Res
import com.coparently.app.resources.nav_calendar
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * What the calendar's top bar shows.
 *
 * @property children The family on screen's children, in display order
 * @property viewMode The view the grid shows
 * @property filtersActive Whether any filter is narrowing the calendar
 * @property swapMonth The month on screen while days are chosen for a swap, or null while they are not
 */
data class CalendarTopBarState(
    val children: List<ChildInfo>,
    val viewMode: CalendarViewMode,
    val filtersActive: Boolean,
    val swapMonth: YearMonth? = null
)

/**
 * What the calendar's top bar does.
 *
 * @property onViewModeChange Called with the mode picked in the segmented control
 * @property onFilters Opens the filter sheet
 * @property onSettings Opens Settings, or null to offer no gear
 */
data class CalendarTopBarActions(
    val onViewModeChange: (CalendarViewMode) -> Unit,
    val onFilters: () -> Unit,
    val onSettings: (() -> Unit)?
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
 * [CalendarHeader], every row of either, on both platforms.
 *
 * Where a shell draws the bars (the iPhone, MON-24 phase 3, part C) the calendar leads with the
 * redesign's header as Android does (the wordmark, the children's photos and the gear, "Your
 * family calendar", the Month/Week/Day control and Filters), and the grid card's own bar keeps
 * Today and "+" ([CalendarMonthBar]). So the shell is handed a hidden bar: no navigation bar over
 * the Calendar's root, while "Calendar" still names it to a screen pushed over it. The selection
 * header draws its close button and arrows in content too. Public for the app's `CalendarBarsTest`.
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
    if (shellDrawsBars()) {
        AppTopBar(ScreenBar(title = stringResource(Res.string.nav_calendar), hidden = true))
    }
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
    CalendarHeader(
        children = state.children,
        viewMode = state.viewMode,
        onViewModeChange = actions.onViewModeChange,
        onFiltersClick = actions.onFilters,
        filtersActive = state.filtersActive,
        onSettingsClick = actions.onSettings
    )
}
