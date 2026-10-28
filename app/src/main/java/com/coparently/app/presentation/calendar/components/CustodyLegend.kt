package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing

/** The two parent slots, in the order a legend names them. */
internal val LEGEND_PARENT_SLOTS = listOf("mom", "dad")

/**
 * A legend swatch: a small day cell, so the mark in it reads as the grid's own (UX audit M9). At a
 * 12 dp circle the handover diagonal was a pink dot, the same as one parent's.
 */
internal val LEGEND_SWATCH = DpSize(20.dp, 14.dp)

/**
 * What the calendar's colours and marks mean, in one row over the grid (new-user review, October
 * 2026: a new parent could not tell whose pink it was, nor what a cell's triangle said).
 *
 * Each parent's name beside a swatch of their wash, the handover diagonal and, when the family has
 * any, the contact-time corner. It reads nothing about the month on screen, so it keeps one height
 * as the month pages — the calendar's banners must not move the grid (design refresh item 5). It
 * wraps rather than cuts at a large font.
 *
 * @param parentNames Resolves a slot to that parent's name; a slot nobody holds is left out
 * @param showContactWindows Whether the schedule has contact time, so its mark needs explaining
 * @param trailing Drawn at the row's end, centred on it — the "⇄" that starts a day swap (day swap
 *   design, board SW-1), or null
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustodyLegend(
    parentNames: ParentNames,
    showContactWindows: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val slots = LEGEND_PARENT_SLOTS.filter { parentNames.isKnown(it) }
    if (slots.isEmpty()) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LegendEntries(slots, parentNames, showContactWindows, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** The legend's entries, wrapping to more lines when they do not fit; see [CustodyLegend]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendEntries(
    slots: List<String>,
    parentNames: ParentNames,
    showContactWindows: Boolean,
    modifier: Modifier
) {
    val first = ParentColors.container(LEGEND_PARENT_SLOTS[0], alpha = ParentColors.custodyTintAlpha)
    val second = ParentColors.container(LEGEND_PARENT_SLOTS[1], alpha = ParentColors.custodyTintAlpha)
    val surface = MaterialTheme.colorScheme.surface
    val mark = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        slots.forEach { slot ->
            LegendEntry(label = parentNames.labelFor(slot)) { ParentSwatch(slot) }
        }
        if (slots.size == LEGEND_PARENT_SLOTS.size) {
            LegendEntry(label = stringResource(R.string.calendar_legend_handover)) {
                // A day split corner to corner, as the grid draws the day one parent hands over.
                Canvas(
                    modifier = Modifier
                        .size(LEGEND_SWATCH)
                        .clip(MaterialTheme.shapes.extraSmall)
                ) {
                    drawRect(surface)
                    drawRect(first)
                    val lower = Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(lower, surface)
                    drawPath(lower, second)
                }
            }
        }
        if (showContactWindows) {
            LegendEntry(label = stringResource(R.string.calendar_legend_contact)) {
                // The corner a month cell marks contact time with.
                Canvas(
                    modifier = Modifier
                        .size(LEGEND_SWATCH)
                        .clip(MaterialTheme.shapes.extraSmall)
                ) {
                    drawRect(surface)
                    val corner = Path().apply {
                        moveTo(size.width / 2, size.height)
                        lineTo(size.width, size.height)
                        lineTo(size.width, size.height / 2)
                        close()
                    }
                    drawPath(corner, mark)
                }
            }
        }
    }
}

/** A day cell washed in [slot]'s colour, over the surface it is drawn on in the grid. */
@Composable
internal fun ParentSwatch(slot: String) {
    Box(
        modifier = Modifier
            .size(LEGEND_SWATCH)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surface)
            .background(ParentColors.container(slot, alpha = ParentColors.custodyTintAlpha))
    )
}

/** A swatch and what it means, as every calendar legend lays one out. */
@Composable
internal fun LegendEntry(label: String, swatch: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clearAndSetSemantics { }
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
        ) { swatch() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
