package com.coparently.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
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

/** The ellipsis a pill's title ends in when it is cut ([TextOverflow.Ellipsis]'s own). */
private const val ELLIPSIS = "\u2026"

/** The space between two stacked pills. */
private val PILL_GAP = Spacing.XXS

/**
 * The room after a pill's title, counted in the fit: at 2 dp a title that just fit ("Vet") ran
 * flush into the pill's end (text review, November 2026).
 */
internal val PILL_END_PADDING = Spacing.XS

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
 * Up to two pills since calendar K1 (November 2026), each title on up to two lines broken at its
 * spaces and never inside a word ([pillTitleLines]): a title with a word wider than the pill takes
 * one line and ends in an ellipsis instead. "+n" ends the last pill's line. A pill is drawn only
 * where its first line fits under the day number ([pillLines]); a cell too short for any (a large
 * font, a short window) draws a bar in the mark's colour instead, never a title cut in half, and so
 * does a pill too narrow for its title's first letter and "…" ([pillTitleFits]: beside "+n", or at
 * 200 %), never a pill that holds only "…".
 *
 * No content description: the cell's own names the events.
 */
@Composable
internal fun DayCellPills(events: List<Event>, isCurrentMonth: Boolean, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    val style = pillTextStyle()
    val density = LocalDensity.current
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCurrentMonth) 1f else OUTSIDE_MONTH_DOT_ALPHA)
    ) {
        val lines = pillLines(
            wanted = pillsFor(events).shown.size,
            height = maxHeight.value,
            lineHeight = lineHeight.value,
            gap = PILL_GAP.value
        )
        val pills = pillsFor(events, max = lines.size)
        if (pills.shown.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(PILL_GAP)) {
                pills.shown.forEachIndexed { index, event ->
                    val last = index == pills.shown.lastIndex
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XXS),
                        verticalAlignment = Alignment.Top
                    ) {
                        EventPill(
                            event = event,
                            style = style,
                            measurer = measurer,
                            maxLines = lines[index],
                            modifier = Modifier.weight(1f)
                        )
                        if (last && pills.more > 0) {
                            Text(
                                text = "+${pills.more}",
                                style = style,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        } else {
            EventBar(pillMarkColor(events.first()))
        }
    }
}

/**
 * The bar a cell draws in place of a pill whose title has no room — under the day number, or beside
 * "+n" in a narrow column: a [EVENT_BAR_HEIGHT] stroke in the mark's colour, never a pill that holds
 * only "…".
 */
@Composable
private fun EventBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EVENT_BAR_HEIGHT)
            .clip(CoPlanlyCorners.Mark)
            .background(color)
    )
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

/**
 * One event's pill: the surface, a leading mark in the event's colour, the title on up to
 * [maxLines] lines broken at its spaces, or on one line when a word of it is wider than the pill
 * ([pillTitleLines]), ellipsised where it still does not fit. No hyphenation: whether it works
 * depends on the locale and the platform, and the break must not.
 */
@Composable
private fun EventPill(
    event: Event,
    style: TextStyle,
    measurer: TextMeasurer,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val mark = pillMarkColor(event)
    val textStyle = style.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Paragraph)
    val widestWord = remember(event.title, textStyle, measurer) {
        titleWords(event.title).maxOfOrNull { word ->
            measurer.measure(text = word, style = textStyle, maxLines = 1, softWrap = false).size.width
        } ?: 0
    }
    // The least a title can show: its first letter and the ellipsis.
    val leastTitle = remember(event.title, textStyle, measurer) {
        measurer.measure(
            text = event.title.trim().take(1) + ELLIPSIS,
            style = textStyle,
            maxLines = 1,
            softWrap = false
        ).size.width
    }
    val lineHeight = with(LocalDensity.current) { textStyle.lineHeight.toDp() }
    BoxWithConstraints(modifier = modifier) {
        val padding = with(LocalDensity.current) {
            (EVENT_PILL_MARK_WIDTH + Spacing.XXS + PILL_END_PADDING).roundToPx()
        }
        val lineWidth = (constraints.maxWidth - padding).toFloat()
        if (pillTitleFits(leastTitle.toFloat(), lineWidth)) {
            Text(
                text = event.title,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = pillTitleLines(
                    widestWord = widestWord.toFloat(),
                    lineWidth = lineWidth,
                    maxLines = maxLines
                ),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CoPlanlyCorners.Tag)
                    .background(MaterialTheme.colorScheme.surface)
                    .drawBehind {
                        drawRect(color = mark, size = Size(EVENT_PILL_MARK_WIDTH.toPx(), size.height))
                    }
                    .padding(start = EVENT_PILL_MARK_WIDTH + Spacing.XXS, end = PILL_END_PADDING)
            )
        } else {
            // Not even one letter and "…" fit: the mark's bar, centred on the line the pill would
            // take, says there is an event, as a short cell's bar does.
            Box(
                modifier = Modifier.fillMaxWidth().height(lineHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                EventBar(mark)
            }
        }
    }
}
