package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.calendar.CustodyHelper
import com.coparently.app.presentation.calendar.DayCellFills
import com.coparently.app.presentation.calendar.EVENT_DOT_HALO
import com.coparently.app.presentation.calendar.EVENT_DOT_SIZE
import com.coparently.app.presentation.calendar.MiniMonthDot
import com.coparently.app.presentation.calendar.MiniMonthGrid
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.dayCellBaseColor
import com.coparently.app.presentation.calendar.dayCellOverlayColor
import com.coparently.app.presentation.calendar.eventDotColor
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.localizedDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The one small calendar (phase 2, spec §5.3): a month of custody colours with the day it is
 * about marked, drawn by the month grid's own rules rather than a look of its own.
 *
 * Every layer comes from where `MonthView` takes it: the weekend base and the custody wash from
 * [DayCellFills] and `DayCellColors`, borrowed days from the neighbouring months at the borrowed-cell
 * scale, today as a filled disc, and a dot in the colour `MonthView` gives the day's first event.
 * What the full grid draws and this one does not — the handover diagonal, holidays, swap arrows —
 * is too small to read here. Phase 3 reuses it for day swaps, through [MiniMonthMark].
 *
 * It is not interactive. The grid is one node for TalkBack, whose description names the month,
 * each marked day, whose day it is and the mark; the cells are not focusable.
 *
 * @param model What to draw, computed off the main thread by the caller.
 * @param parentNames Names each parent in the legend and the description — never "Mom" or "Dad".
 * @param today The day drawn as today.
 */
@Composable
fun MiniMonth(
    model: MiniMonthModel,
    parentNames: ParentNames,
    modifier: Modifier = Modifier,
    today: LocalDate = rememberToday().value
) {
    val weeks = remember(model.month) { MiniMonthGrid.weeks(model.month) }
    val description = miniMonthDescription(model, parentNames)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clearAndSetSemantics { contentDescription = description }
        ) {
            WeekdayHeader()
            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        MiniMonthCell(
                            date = date,
                            model = model,
                            isToday = date == today,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        MiniMonthLegend(model = model, parentNames = parentNames)
    }
}

/** The weekday initials over the grid, Monday first, as the month grid orders its columns. */
@Composable
private fun WeekdayHeader() {
    val locale = Locale.getDefault()
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.XXS)
            )
        }
    }
}

/** One day: the base and the custody wash, its number, its mark and its dot. */
@Composable
private fun MiniMonthCell(date: LocalDate, model: MiniMonthModel, isToday: Boolean, modifier: Modifier) {
    val inMonth = YearMonth.from(date) == model.month
    // The month grid's two layers. No previous day and no holiday, so no handover diagonal and no
    // holiday tint: neither reads at this size, and the spec leaves both to the full grid.
    val fill = DayCellFills.monthCell(
        isWeekend = CustodyHelper.isWeekend(date),
        isCurrentMonth = inMonth,
        custody = model.custody[date],
        previousCustody = null,
        isPublicHoliday = false
    )
    val base = dayCellBaseColor(fill.base)
    Box(
        modifier = modifier
            .heightIn(min = CELL_MIN_HEIGHT)
            .background(base)
            .background(dayCellOverlayColor(fill)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.XXS),
            modifier = Modifier.padding(vertical = Spacing.XXS)
        ) {
            DayNumber(date = date, isToday = isToday, inMonth = inMonth, marked = date in model.marks)
            DayDot(dot = model.dots[date], inMonth = inMonth, base = base)
        }
    }
}

/** The day number: a filled disc for today, a ring for a marked day, as `MonthView` marks today. */
@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean, inMonth: Boolean, marked: Boolean) {
    val ring = if (marked) {
        Modifier.border(MARK_RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = NUMBER_DISC, minHeight = NUMBER_DISC)
            .background(
                color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .then(ring),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                isToday -> MaterialTheme.colorScheme.onPrimary
                !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = OUTSIDE_MONTH_NUMBER_ALPHA)
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = Spacing.XS)
        )
    }
}

/**
 * The day's dot, in a ring of the cell's base so it keeps 3:1 on its own parent's wash, as the
 * month grid draws it — or an empty space of the same size, so every row is the same height.
 */
@Composable
private fun DayDot(dot: MiniMonthDot?, inMonth: Boolean, base: Color) {
    val size = EVENT_DOT_SIZE + EVENT_DOT_HALO * 2
    if (dot == null) {
        Spacer(modifier = Modifier.size(size))
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(color = base, shape = CircleShape)
                .padding(EVENT_DOT_HALO)
                .background(
                    color = eventDotColor(dot.parentOwner, inMonth, dot.friendParticipates),
                    shape = CircleShape
                )
        )
    }
}

/** Each parent's name beside a swatch of their wash, and what the mark means. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiniMonthLegend(model: MiniMonthModel, parentNames: ParentNames) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        LEGEND_PARENT_SLOTS.filter { parentNames.isKnown(it) }.forEach { slot ->
            LegendEntry(label = parentNames.labelFor(slot)) { ParentSwatch(slot) }
        }
        model.marks.values.distinct().sorted().forEach { mark ->
            LegendEntry(
                label = stringResource(
                    when (mark) {
                        MiniMonthMark.NEW_EVENT -> R.string.mini_month_legend_new_event
                        MiniMonthMark.SWAP_DAY -> R.string.mini_month_legend_swap_day
                    }
                )
            ) {
                // The ring the grid draws round a marked day, in a day-cell swatch like the rest.
                Box(modifier = Modifier.size(LEGEND_SWATCH), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(LEGEND_SWATCH.height)
                            .border(MARK_RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * What TalkBack reads for the grid: "October 2026. Wednesday 21 October, Zuzana's day: new event."
 */
@Composable
private fun miniMonthDescription(model: MiniMonthModel, parentNames: ParentNames): String {
    val month = model.month.atDay(1).format(localizedDate("yMMMM"))
    val markedDays = model.marks.entries.sortedBy { it.key }.map { (date, mark) ->
        val day = date.format(localizedDate("EEEEMMMMd"))
        val meaning = when (mark) {
            MiniMonthMark.NEW_EVENT -> stringResource(R.string.mini_month_mark_new_event)
            MiniMonthMark.SWAP_DAY -> stringResource(R.string.mini_month_mark_swap_day)
        }
        val slot = model.custody[date]
        if (slot != null) {
            stringResource(
                R.string.mini_month_marked_day,
                day,
                if (parentNames.isReader(slot)) {
                    stringResource(R.string.calendar_agenda_custody_day_you)
                } else {
                    stringResource(R.string.calendar_agenda_custody_day, parentNames.labelFor(slot))
                },
                meaning
            )
        } else {
            stringResource(R.string.mini_month_marked_day_no_custody, day, meaning)
        }
    }
    return (listOf("$month.") + markedDays).joinToString(" ")
}

/** A cell's least height; it grows with the text rather than clipping it at 200 %. */
private val CELL_MIN_HEIGHT = 34.dp

/** The day number's disc: smaller than the month grid's 28 dp, for a cell of about 34 dp. */
private val NUMBER_DISC = 24.dp

/** The ring around a marked day, and around its swatch in the legend. */
private val MARK_RING_WIDTH = 2.dp

/** A borrowed day's number, as faint as the month grid draws it. */
private const val OUTSIDE_MONTH_NUMBER_ALPHA = 0.3f
