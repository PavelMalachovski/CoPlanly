package com.coparently.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.home.WeekDay
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_timeline_meta
import com.coparently.app.resources.home_timeline_meta_you
import com.coparently.app.resources.home_week_tile_with_events
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** The ring around today's tile. */
private val TODAY_RING = 2.dp

/** The event dot under a tile's day number. */
private val EVENT_DOT = 6.dp

/**
 * Home's "This week" (redesign, November 2026, variant A): seven day tiles from today, each in the
 * day's custody wash — the calendar's own, [ParentColors.container] at the custody alpha — with
 * today ringed in the primary colour and a dot under a day that has events. A tap opens the
 * calendar's Day view on that day.
 *
 * The wash is the only colour a tile carries for its parent, so each tile's description says in
 * words whose day it is ("Mon 16 Nov · your day"), and that the day has events.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param days The seven days, from [com.coparently.app.domain.home.HomeWeek.daysOf]
 * @param parentNames Resolves a slot to that parent's name, or "you"
 * @param onOpenDay Opens the calendar's Day view on a day
 */
@Composable
fun WeekTiles(days: List<WeekDay>, parentNames: ParentNames, onOpenDay: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        days.forEachIndexed { index, day ->
            WeekTile(
                day = day,
                isToday = index == 0,
                parentNames = parentNames,
                onClick = { onOpenDay(day.date) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeekTile(
    day: WeekDay,
    isToday: Boolean,
    parentNames: ParentNames,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    // The calendar grid's custody wash (`DayCellColors`): the parent's wash at the custody alpha.
    val wash = day.dayParent?.let { ParentColors.container(it, alpha = ParentColors.custodyTintAlpha) }
        ?: Color.Transparent
    val slot = day.dayParent
    val dotColor = when {
        !day.hasEvents -> Color.Transparent
        slot != null -> ParentColors.fill(slot)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = tileDescription(day, parentNames)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .background(wash)
            .border(
                if (isToday) {
                    BorderStroke(TODAY_RING, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                shape
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isToday
            }
            .padding(vertical = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
    ) {
        Text(
            text = ReaderDateText.date(day.date, "EEE"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = day.date.day.toString(),
            style = MaterialTheme.typography.titleMediumEmphasized
        )
        Box(
            modifier = Modifier
                .size(EVENT_DOT)
                .background(dotColor, CircleShape)
        )
    }
}

/** "Mon 16 Nov · your day", then that the day has events; the date alone when nobody holds it. */
@Composable
private fun tileDescription(day: WeekDay, parentNames: ParentNames): String {
    val date = ReaderDateText.date(day.date, "EEEdMMM")
    val whose = day.dayParent?.let { slot ->
        if (parentNames.isReader(slot)) {
            stringResource(Res.string.home_timeline_meta_you, date)
        } else {
            stringResource(Res.string.home_timeline_meta, date, parentNames.labelFor(slot))
        }
    } ?: date
    return if (day.hasEvents) stringResource(Res.string.home_week_tile_with_events, whose) else whose
}
