package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.calendar.SwapDaysButton
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.LayoutConstants
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_add_event
import com.coparently.app.resources.calendar_jump_to_date
import com.coparently.app.resources.calendar_next_period
import com.coparently.app.resources.calendar_previous_period
import com.coparently.app.resources.calendar_today_button
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * The grid card's head row: the month on screen, Today, and the arrows either side of it.
 *
 * The month title was the calendar header's until the November 2026 redesign, where it was also
 * the view picker; the picker is now the header's segmented control, so a tap on the title does
 * the one thing left for it — "Jump to date", the picker that opens on the selected day.
 *
 * @param selectedDate The day whose month titles the card: the first of the month in Month view,
 *   the selected day in Week and Day view, as the header's title always read it
 * @param onToday Brings the calendar back to today
 * @param onPrevious One period back: a month, a week or a day, as the view shows; null draws no
 *   arrows (while days are chosen for a swap, the selection header pages the month)
 * @param onNext One period on; null as [onPrevious]
 * @param onJumpToDate Opens the date picker
 * @param onSwapDays Opens the day swap's selection, or null to draw no "⇄" (outside Month view,
 *   while days are chosen, without a family schedule)
 * @param onAddEvent Opens the new-event form, or null to draw no "+" (while days are chosen for a
 *   swap). It closes the row since the tester redesign of 12 November 2026: a floating "+" sat on
 *   the grid's last row once the six rows reached the card's bottom. "⇄" sits before it, in one row
 *   of actions, where it used to end the legend's row and push the legend onto two lines.
 */
@Composable
@Suppress("LongParameterList") // the bar's actions are this component's API surface
fun CalendarMonthBar(
    selectedDate: LocalDate,
    onToday: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onJumpToDate: () -> Unit,
    onSwapDays: (() -> Unit)? = null,
    onAddEvent: (() -> Unit)? = null
) {
    // From font scale 1.3 the actions alone fill the row (German's "Heute" chip at 200 % left the
    // month no room at all), so the title takes a line of its own above them.
    if (stacksAtLargeFont()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MonthTitle(selectedDate, onJumpToDate, Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonthBarActions(onToday, onPrevious, onNext, onSwapDays, onAddEvent)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthTitle(selectedDate, onJumpToDate, Modifier.weight(1f))
            MonthBarActions(onToday, onPrevious, onNext, onSwapDays, onAddEvent)
        }
    }
}

/** Today, the arrows, "⇄" and "+", in that order; see [CalendarMonthBar]. */
@Composable
private fun MonthBarActions(
    onToday: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSwapDays: (() -> Unit)?,
    onAddEvent: (() -> Unit)?
) {
    AssistChip(
        onClick = onToday,
        label = {
            Text(
                text = stringResource(Res.string.calendar_today_button),
                style = MaterialTheme.typography.labelMedium
            )
        }
    )
    onPrevious?.let { onClick ->
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.calendar_previous_period),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    onNext?.let { onClick ->
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.calendar_next_period),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    onSwapDays?.let { onClick -> SwapDaysButton(onClick = onClick) }
    onAddEvent?.let { onClick ->
        FilledTonalIconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.calendar_add_event)
            )
        }
    }
}

/**
 * "November", or "November 2027" outside this year: the year only when it is not this one, as
 * calendar apps do (docs/AUDIT-2026-10-design.md D-3). A button for TalkBack, labelled with
 * where it leads.
 */
@Composable
private fun MonthTitle(selectedDate: LocalDate, onJumpToDate: () -> Unit, modifier: Modifier) {
    val yearMonth = selectedDate.yearMonth
    // The month's stand-alone name, "LLLL": `java.time`'s `Month.getDisplayName(FULL_STANDALONE)`.
    val monthName = ReaderDateText.pattern(yearMonth, "LLLL").replaceFirstChar { it.uppercase() }
    val today by rememberToday()
    val label = if (yearMonth.year == today.year) monthName else "$monthName ${yearMonth.year}"
    val style = MaterialTheme.typography.titleMedium
    // The row holds up to six actions now, so a long month name ("Сентябрь", "September 2027")
    // steps its size down before it ends in an ellipsis.
    BasicText(
        text = label,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_TITLE_SIZE, maxFontSize = style.fontSize),
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.calendar_jump_to_date),
                onClick = onJumpToDate
            )
            .defaultMinSize(minHeight = LayoutConstants.MIN_TOUCH_TARGET)
            .padding(horizontal = Spacing.XS)
            .wrapContentHeight(Alignment.CenterVertically)
            .semantics { heading() }
    )
}

/**
 * The white card the grid sits in, with its head row and legend (redesign, November 2026):
 * `surfaceContainer` on the lavender ground, `shapes.large` and a 1 dp `outlineVariant` edge, as
 * `SectionGroup` draws every card.
 *
 * @param modifier Modifier for the card; the screen gives it the space left under the banners
 * @param content The month bar, the legend and the grid, top to bottom
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

/**
 * The day the arrows move to in Week or Day view: one week or one day from [anchor]. Month view
 * pages by month, through `CalendarViewModel.showMonth`, and is not asked.
 *
 * @param step 1 for on, -1 for back
 */
internal fun steppedDay(viewMode: CalendarViewMode, anchor: LocalDate, step: Int): LocalDate = when (viewMode) {
    CalendarViewMode.WEEK -> anchor.plus(step, DateTimeUnit.WEEK)
    CalendarViewMode.DAY, CalendarViewMode.MONTH -> anchor.plus(step, DateTimeUnit.DAY)
}

/** The smallest the month title steps down to before it ends in an ellipsis. */
private val MIN_TITLE_SIZE = 12.sp
