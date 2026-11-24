package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.SwapDaysButton
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.common.optionMenuItems
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_add_event
import com.coparently.app.resources.calendar_today_button
import com.coparently.app.resources.calendar_viewmode_day
import com.coparently.app.resources.calendar_viewmode_label
import com.coparently.app.resources.calendar_viewmode_month
import com.coparently.app.resources.calendar_viewmode_week
import org.jetbrains.compose.resources.stringResource

/**
 * The grid card's head row (calendar K1, November 2026): the view's name with its menu ("Month ▾") on
 * the left, then Today, "⇄" and "+".
 *
 * The month title left this row for the screen's header ([CalendarHeader]), where the wordmark was,
 * and the arrows went with it: months page by swipe, and Week and Day view page by swipe too. Today
 * is a word, never an icon, because an icon for it was not understood.
 *
 * @param viewMode The view the grid shows
 * @param onViewModeChange Called with the view picked, or null to draw no control (while days are
 *   chosen for a swap, which only Month view shows)
 * @param onToday Brings the calendar back to today
 * @param onSwapDays Opens the day swap's selection, or null to draw no "⇄" (outside Month view,
 *   while days are chosen, without a family schedule)
 * @param onAddEvent Opens the new-event form, or null to draw no "+" (while days are chosen for a
 *   swap). It closes the row since the tester redesign of 12 November 2026: a floating "+" sat on
 *   the grid's last row once the six rows reached the card's bottom.
 *
 * The iPhone draws the same row (MON-24 phase 3, part C), the control as `UISegmentedControl`
 * there ([AdaptiveSegmented]), with no navigation bar over the Calendar. The control is as wide as
 * its letters, so the row is one line at the default font size in every language; it wraps rather
 * than cuts at a large font: the actions go under the control when both do not fit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarMonthBar(
    viewMode: CalendarViewMode,
    onViewModeChange: ((CalendarViewMode) -> Unit)?,
    onToday: () -> Unit,
    onSwapDays: (() -> Unit)? = null,
    onAddEvent: (() -> Unit)? = null
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        if (onViewModeChange != null) {
            // At least Spacing.S before the actions: the gap counts in the row's measure, so they go
            // under the control rather than touching it when both only just fit (German at 200 %).
            Box(modifier = Modifier.padding(end = Spacing.S)) {
                ViewModeControl(viewMode = viewMode, onViewModeChange = onViewModeChange)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthBarActions(onToday, onSwapDays, onAddEvent)
        }
    }
}

/** Today, "⇄" and "+", in that order; see [CalendarMonthBar]. */
@Composable
private fun MonthBarActions(
    onToday: () -> Unit,
    onSwapDays: (() -> Unit)?,
    onAddEvent: (() -> Unit)?
) {
    OutlinedButton(
        onClick = onToday,
        contentPadding = PaddingValues(horizontal = Spacing.M)
    ) {
        Text(
            text = stringResource(Res.string.calendar_today_button),
            style = MaterialTheme.typography.labelLarge
        )
    }
    onSwapDays?.let { onClick -> SwapDaysButton(onClick = onClick) }
    onAddEvent?.let { onClick ->
        FilledTonalIconButton(onClick = onClick) {
            AppIcon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.calendar_add_event)
            )
        }
    }
}

/**
 * The view the grid shows, written out in full ("Month ▾"), which opens a menu of Month, Week and
 * Day with the current one checked (owner, 24 November 2026; it replaced the "M | W | D" letters,
 * which read as initials). Choosing a view, or a tap outside, closes the menu.
 *
 * On the iPhone it is the system's pop-up button ([NativeControls.MenuButton]): the view's name and
 * the system's up-and-down chevron, its `UIMenu` the three views; on Android a tonal button with a
 * downward chevron over Material's `DropdownMenu`. Both are named "View: Month" for TalkBack and
 * VoiceOver, and each menu entry by its view.
 */
@Composable
private fun ViewModeControl(viewMode: CalendarViewMode, onViewModeChange: (CalendarViewMode) -> Unit) {
    val modes = CalendarViewMode.entries
    val names = modes.map { viewModeName(it) }
    val current = names[modes.indexOf(viewMode)]
    val description = "${stringResource(Res.string.calendar_viewmode_label)}: $current"
    val native = nativeControls()
    if (native != null) {
        native.MenuButton(
            items = optionMenuItems(modes.zip(names), viewMode, onViewModeChange),
            title = current,
            sfSymbol = null,
            accessibilityLabel = description,
            tint = MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
        )
        return
    }
    ViewModeMenu(modes, names, viewMode, description, onViewModeChange)
}

/** Android's view button and its menu; see [ViewModeControl]. */
@Composable
private fun ViewModeMenu(
    modes: List<CalendarViewMode>,
    names: List<String>,
    viewMode: CalendarViewMode,
    description: String,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    val current = names[modes.indexOf(viewMode)]
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(start = Spacing.M, end = Spacing.S),
            modifier = Modifier.semantics {
                contentDescription = description
                role = Role.DropdownList
            }
        ) {
            Text(text = current, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            AppIcon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(IconSizes.Small)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEachIndexed { index, mode ->
                val chosen = mode == viewMode
                DropdownMenuItem(
                    text = { Text(names[index]) },
                    onClick = {
                        expanded = false
                        onViewModeChange(mode)
                    },
                    leadingIcon = {
                        if (chosen) {
                            AppIcon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(IconSizes.Standard))
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = names[index]
                        selected = chosen
                    }
                )
            }
        }
    }
}

/** A view's name: on the button, in its menu and for TalkBack. */
@Composable
private fun viewModeName(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.MONTH -> stringResource(Res.string.calendar_viewmode_month)
    CalendarViewMode.WEEK -> stringResource(Res.string.calendar_viewmode_week)
    CalendarViewMode.DAY -> stringResource(Res.string.calendar_viewmode_day)
}

/**
 * The white card the grid sits in, with its head row (redesign, November 2026; the legend left it
 * for the header's "?" sheet in calendar K1):
 * `surfaceContainer` on the lavender ground, `shapes.large` and a 1 dp `outlineVariant` edge, as
 * `SectionGroup` draws every card.
 *
 * @param modifier Modifier for the card; the screen gives it the space left under the banners
 * @param content The month bar and the grid, top to bottom
 */
@Composable
fun CalendarGridCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        content = content
    )
}
