package com.coparently.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.data.family.RecordPlace
import com.coparently.app.data.family.placementFamilyId
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.placeOf
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.Spacing

/** Width of the parent-coloured mark at a pill's start. */
internal val EVENT_PILL_MARK_WIDTH = 3.dp

/** Height of the bar a cell draws instead of a pill when the title has no room (an off-grid literal). */
private val EVENT_BAR_HEIGHT = 4.dp

/**
 * The pill's text: `labelSmall` on a tighter line, centred in it. `labelSmall`'s own 16 sp line did
 * not fit under the day number in a phone's 48 dp cell, and the UI tour showed the titles cut in half.
 */
@Composable
private fun pillTextStyle(): TextStyle = MaterialTheme.typography.labelSmall.copy(
    lineHeight = 13.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

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
 * A pill is drawn only where its line fits under the day number; a cell too short for it (a large
 * font, a short window) draws a bar in the mark's colour instead, never a title cut in half.
 *
 * No content description: the cell's own names the events.
 */
@Composable
internal fun DayCellPills(events: List<Event>, isCurrentMonth: Boolean, modifier: Modifier = Modifier) {
    val pills = pillsFor(events)
    if (pills.shown.isEmpty()) return
    val style = pillTextStyle()
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCurrentMonth) 1f else OUTSIDE_MONTH_DOT_ALPHA)
    ) {
        if (maxHeight >= lineHeight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pills.shown.forEach { event ->
                    EventPill(event = event, style = style, modifier = Modifier.weight(1f))
                }
                if (pills.more > 0) {
                    Text(
                        text = "+${pills.more}",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            val mark = pillMarkColor(pills.shown.first())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EVENT_BAR_HEIGHT)
                    .clip(CoPlanlyCorners.Mark)
                    .background(mark)
            )
        }
    }
}

/** The colour of an event's mark: its owner's hue, a friend's teal, or neutral for an earlier family. */
@Composable
private fun pillMarkColor(event: Event): Color = eventDotColor(
    // An earlier family's event is drawn neutral: its slot colour would now name the wrong person.
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

/** One event's pill: the surface, a leading mark in the event's colour, the title on one line. */
@Composable
private fun EventPill(event: Event, style: TextStyle, modifier: Modifier = Modifier) {
    val mark = pillMarkColor(event)
    Text(
        text = event.title,
        style = style,
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
