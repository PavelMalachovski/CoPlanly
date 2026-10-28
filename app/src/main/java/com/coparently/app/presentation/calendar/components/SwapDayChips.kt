package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelLargeEmphasized
import com.coparently.app.utils.swapDateList
import java.time.LocalDate

/**
 * One side of a day swap under its own label — "You give Lana", "You take", "You", "Lana" — with a
 * chip per day in the wash of whom it goes to and an edge in their deep tone (day swap design,
 * boards SW-3 and SW-6). The review sheet, the inbox card and Home's dialog draw a swap's two sides
 * through this, so a day reads the same wherever it is answered. Nothing is drawn for no days.
 *
 * @param label The side's heading.
 * @param days The days on this side, soonest first.
 * @param toSlot The slot these days would go to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwapSideGroup(label: String, days: List<LocalDate>, toSlot: String, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val wash = ParentColors.container(toSlot)
    val edge = ParentColors.chipFill(toSlot)
    val shape = MaterialTheme.shapes.small
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLargeEmphasized,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            days.forEach { day ->
                Text(
                    text = swapDateList(listOf(day), locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .background(wash, shape)
                        .border(CHIP_EDGE, edge, shape)
                        .padding(horizontal = Spacing.S, vertical = Spacing.XS)
                )
            }
        }
    }
}

/** A day chip's edge in the receiver's deep tone. */
private val CHIP_EDGE = 1.dp
