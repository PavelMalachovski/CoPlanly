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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.SwapDaysButton
import com.coparently.app.presentation.common.AdaptiveSegmented
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_add_event
import com.coparently.app.resources.calendar_today_button
import com.coparently.app.resources.calendar_viewmode_day
import com.coparently.app.resources.calendar_viewmode_day_short
import com.coparently.app.resources.calendar_viewmode_month
import com.coparently.app.resources.calendar_viewmode_month_short
import com.coparently.app.resources.calendar_viewmode_week
import com.coparently.app.resources.calendar_viewmode_week_short
import org.jetbrains.compose.resources.stringResource

/**
 * The grid card's head row (calendar K1, November 2026): the Month | Week | Day control on the
 * left, as letters spoken as the views' names, then Today, "⇄" and "+".
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
 * Month, Week and Day as one segmented control of letters, "M | W | D", each segment named in full
 * for TalkBack: the view is a choice always in sight, and the letters leave the row its actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeControl(viewMode: CalendarViewMode, onViewModeChange: (CalendarViewMode) -> Unit) {
    val modes = CalendarViewMode.entries
    val letters = modes.map { viewModeLetter(it) }
    val names = modes.map { viewModeName(it) }
    AdaptiveSegmented(
        labels = letters,
        selectedIndex = modes.indexOf(viewMode),
        onSelect = { onViewModeChange(modes[it]) },
        // VoiceOver reads the segments' letters; the control as a whole is named in full.
        accessibilityLabel = names.joinToString(", "),
        // A segment a letter wide: the native control fills its width, which in this row would push
        // Today, "⇄" and "+" onto a line of their own.
        modifier = Modifier.width(NATIVE_SEGMENT_WIDTH * modes.size)
    ) {
        // Sized to its letters: Material's segment is at least 58 dp wide with 12 dp of padding on
        // each side, which made the control 190 dp and pushed Today, "⇄" and "+" under it.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(SEGMENT_WIDTH * modes.size)) {
            modes.forEachIndexed { index, mode ->
                val name = names[index]
                SegmentedButton(
                    selected = mode == viewMode,
                    onClick = { onViewModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    contentPadding = PaddingValues(horizontal = Spacing.XS),
                    modifier = Modifier.semantics { contentDescription = name },
                    // No check mark: a letter needs the segment's width, not a tick beside it.
                    icon = {},
                    label = {
                        Text(
                            text = letters[index],
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                )
            }
        }
    }
}

/** A view's letter on the control: "M", "W", "D" in English. */
@Composable
private fun viewModeLetter(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.MONTH -> stringResource(Res.string.calendar_viewmode_month_short)
    CalendarViewMode.WEEK -> stringResource(Res.string.calendar_viewmode_week_short)
    CalendarViewMode.DAY -> stringResource(Res.string.calendar_viewmode_day_short)
}

/** A view's name, which TalkBack reads for its letter. */
@Composable
private fun viewModeName(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.MONTH -> stringResource(Res.string.calendar_viewmode_month)
    CalendarViewMode.WEEK -> stringResource(Res.string.calendar_viewmode_week)
    CalendarViewMode.DAY -> stringResource(Res.string.calendar_viewmode_day)
}

/** A segment of the iPhone's native view control, wide enough for its letter. */
private val NATIVE_SEGMENT_WIDTH = 40.dp

/** A segment of the Material view control: a letter and a touch target's width, no more. */
private val SEGMENT_WIDTH = 48.dp

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
