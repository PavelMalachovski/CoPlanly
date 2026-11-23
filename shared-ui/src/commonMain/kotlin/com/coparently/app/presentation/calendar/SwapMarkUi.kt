package com.coparently.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.resources.Res
import com.coparently.app.resources.day_swap_mark_to
import com.coparently.app.resources.day_swap_mark_to_you
import org.jetbrains.compose.resources.stringResource

/**
 * A [SwapMark]'s stripes and edge over a month cell: diagonal stripes in [hatch] (the receiver's
 * wash), and an edge in [edge] (their deep tone), dashed while the swap waits for an answer.
 * Drawn inside the cell's clip, after every `DayCellFills` layer.
 */
internal fun DrawScope.drawSwapMark(hatch: Color, edge: Color, dashed: Boolean) {
    val stripe = SWAP_STRIPE_WIDTH.toPx()
    val gap = SWAP_STRIPE_PERIOD.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stripe)
        x += gap
    }
    val width = SWAP_EDGE_WIDTH.toPx()
    drawRoundRect(
        color = edge,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(SWAP_EDGE_CORNER.toPx()),
        style = Stroke(
            width = width,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(SWAP_DASH.toPx(), SWAP_DASH_GAP.toPx()))
            } else {
                null
            }
        )
    )
}

/**
 * "→ L" or "→ you" at the foot of a month cell (`SwapMark`): filled in the receiver's deep tone
 * while the day is being chosen, outlined in it on the cell's surface while the swap waits — the
 * same two forms on both phones. The co-parent is their initial, as a member's mark is (FAM-5): a
 * cell is about 47 dp wide, and the rendered grid cut a whole name to "→ P…". The cell's description
 * names them in full, and the bar and the sheet beside it do too.
 *
 * Never an ellipsis: the mark is measured against the cell ([swapMarkText]). "→ you" that has no room
 * becomes the reader's initial ("→ A"), and a mark with no room even for an initial is the arrow
 * alone — the colour and the description still say whose — where it used to read "→ y…" or "→ …".
 *
 * @param name The receiver's name — the reader's own when [toReader]; only its initial is drawn.
 * @param toReader Whether the receiver is the reader, who reads "→ you".
 * @param edge The receiver's deep tone ([ParentColors.chipFill]).
 */
@Composable
internal fun SwapMarkPill(
    name: String,
    toReader: Boolean,
    edge: Color,
    pending: Boolean,
    modifier: Modifier = Modifier
) {
    val full = if (toReader) {
        stringResource(Res.string.day_swap_mark_to_you)
    } else {
        stringResource(Res.string.day_swap_mark_to, initialOf(name))
    }
    val initial = stringResource(Res.string.day_swap_mark_to, initialOf(name))
    // No tracking: the mark is a few letters in a narrow cell, and labelSmall's 0.5 sp cost "→ you" its room.
    val style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    val measurer = rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val density = LocalDensity.current
        val padding = with(density) { (Spacing.XXS * 2).roundToPx() }
        // Into the cell's own padding, which a month cell pads its content with (`DayCell`).
        val overhangPx = with(density) { swapMarkOverhang(dimensions().paddingSmall / 2).roundToPx() }
        val room = constraints.maxWidth + overhangPx * 2 - padding
        val text = swapMarkText(listOf(full, initial, SWAP_ARROW)) { candidate ->
            measurer.measure(candidate, style = style, maxLines = 1, softWrap = false).size.width <= room
        }
        Text(
            text = text,
            style = style,
            color = if (pending) MaterialTheme.colorScheme.onSurface else ParentColors.onFill(edge),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .overhanging(overhangPx)
                .padding(bottom = Spacing.XXS)
                .then(
                    if (pending) {
                        Modifier
                            .background(surface, CoPlanlyCorners.Pill)
                            .border(1.dp, edge, CoPlanlyCorners.Pill)
                    } else {
                        Modifier.background(edge, CoPlanlyCorners.Pill)
                    }
                )
                .padding(horizontal = Spacing.XXS)
        )
    }
}

/**
 * Measures the content up to [by] wider on each side than its slot and centres it there, so it may
 * reach into the padding around the slot — never past it, since [by] is at most that padding.
 */
private fun Modifier.overhanging(by: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + by * 2))
    val width = placeable.width.coerceAtMost(constraints.maxWidth)
    layout(width, placeable.height) { placeable.place((width - placeable.width) / 2, 0) }
}

/**
 * How far a swap mark may reach into a cell's horizontal [cellPadding]: up to the mark's edge
 * ([drawSwapMark]), which it must not cover.
 */
internal fun swapMarkOverhang(cellPadding: Dp): Dp = (cellPadding - SWAP_EDGE_WIDTH).coerceAtLeast(0.dp)

/** The arrow alone: a swap mark too narrow for any receiver. */
internal const val SWAP_ARROW = "→"

/** A name's first letter, upper-cased: how a cell names a receiver. */
private fun initialOf(name: String): String = name.trim().take(1).uppercase()

/**
 * The first of [candidates] — the full mark, the initial, then the arrow — that [fits]; the last
 * when none does, which the cell then clips rather than ellipsises. Pure, so the order is tested
 * without a composition.
 */
internal fun swapMarkText(candidates: List<String>, fits: (String) -> Boolean): String =
    candidates.distinct().firstOrNull(fits) ?: candidates.last()

/** A swap mark's stripe, and the distance from one stripe to the next. */
private val SWAP_STRIPE_WIDTH = 3.dp
private val SWAP_STRIPE_PERIOD = 8.dp

/** A swap mark's edge, its corner (the cell's `extraSmall` shape) and its dashes while waiting. */
private val SWAP_EDGE_WIDTH = 2.dp
private val SWAP_EDGE_CORNER = 4.dp
private val SWAP_DASH = 4.dp
private val SWAP_DASH_GAP = 3.dp
