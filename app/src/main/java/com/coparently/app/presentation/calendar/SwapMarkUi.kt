package com.coparently.app.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing

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
 * "→ Lana" or "→ you" at the foot of a month cell (`SwapMark`): filled in the receiver's deep tone
 * while the day is being chosen, outlined in it on the cell's surface while the swap waits — the
 * same two forms on both phones. One line, ellipsised: it names a person, never an amount.
 *
 * @param name The receiver's name; only its first word is drawn, for room.
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
    val text = if (toReader) {
        stringResource(R.string.day_swap_mark_to_you)
    } else {
        stringResource(R.string.day_swap_mark_to, name.substringBefore(' '))
    }
    val surface = MaterialTheme.colorScheme.surface
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (pending) MaterialTheme.colorScheme.onSurface else ParentColors.onFill(edge),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
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
            .padding(horizontal = Spacing.XS)
    )
}

/** A swap mark's stripe, and the distance from one stripe to the next. */
private val SWAP_STRIPE_WIDTH = 3.dp
private val SWAP_STRIPE_PERIOD = 8.dp

/** A swap mark's edge, its corner (the cell's `extraSmall` shape) and its dashes while waiting. */
private val SWAP_EDGE_WIDTH = 2.dp
private val SWAP_EDGE_CORNER = 4.dp
private val SWAP_DASH = 4.dp
private val SWAP_DASH_GAP = 3.dp
