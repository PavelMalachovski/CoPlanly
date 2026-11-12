package com.coparently.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.data.family.RecordPlace
import com.coparently.app.data.family.placementFamilyId
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.placeOf
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.Spacing

/** Width of the parent-coloured mark at a pill's start. */
internal val EVENT_PILL_MARK_WIDTH = 3.dp

/**
 * The pills of one month cell and its "+n" (November 2026 redesign; they replaced the dots).
 *
 * A pill is the theme's `surface` with the title in `onSurface` — opaque, so the title reads on the
 * pill and not on the custody wash under it — and a 3 dp mark at its start in the event's colour:
 * its owner's full hue, the friend's teal when a calendar friend takes part, neutral for an earlier
 * family's event (the saturation rule's "marker" strength, as the dot was). No member initial: a
 * month chip carries none (FAM-5). On a day borrowed from a neighbouring month the whole pill is
 * dimmed as the dot was, because the grid must still say which month it shows.
 *
 * No content description: the cell's own names the events.
 */
@Composable
internal fun DayCellPills(events: List<Event>, isCurrentMonth: Boolean, modifier: Modifier = Modifier) {
    val pills = pillsFor(events)
    if (pills.shown.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCurrentMonth) 1f else OUTSIDE_MONTH_DOT_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pills.shown.forEach { event ->
            EventPill(event = event, modifier = Modifier.weight(1f))
        }
        if (pills.more > 0) {
            Text(
                text = "+${pills.more}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** One event's pill: the surface, a leading mark in the event's colour, the title on one line. */
@Composable
private fun EventPill(event: Event, modifier: Modifier = Modifier) {
    val mark = eventDotColor(
        // An earlier family's event is drawn neutral: its slot colour would now name the wrong
        // person.
        parentOwner = if (
            placeOf(event.placementFamilyId, event.createdByFirebaseUid) == RecordPlace.EARLIER_FAMILY
        ) {
            ""
        } else {
            event.parentOwner
        },
        // The pill as a whole is dimmed on a borrowed day; the mark must not be dimmed twice.
        isCurrentMonth = true,
        friendParticipates = !event.friendParticipates.isNullOrBlank()
    )
    Text(
        text = event.title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CoPlanlyCorners.Tag)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawRect(color = mark, size = Size(EVENT_PILL_MARK_WIDTH.toPx(), size.height))
            }
            .padding(start = EVENT_PILL_MARK_WIDTH + Spacing.XXS, end = Spacing.XXS)
    )
}
