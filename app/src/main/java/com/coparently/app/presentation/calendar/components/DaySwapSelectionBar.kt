package com.coparently.app.presentation.calendar.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.custody.receiverOf
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleSmallEmphasized
import com.coparently.app.utils.swapDateList
import java.time.LocalDate

/**
 * The bar under the grid while days are being chosen for a swap (day swap design, board SW-2).
 *
 * One line per way the days go — "Lana — 2 days: Fri, Nov 6, Sat, Nov 7", "You — 1 day: …" — each
 * after a dot in the receiver's colour, so the direction is said in words and colour before
 * anything is sent. It replaced a bare count ("3 days"), which said nothing about which way. The
 * date lines wrap rather than clip; the buttons keep one line each, the reason they sit under the
 * text (a device test found "Отменить выбор" and "Предложить обмен" leave a shared row no room).
 *
 * @param sides The chosen days, from this parent's side.
 * @param parentNames Names the receivers; the reader is "You".
 * @param onNext Opens the review sheet; disabled while no day can be offered.
 * @param onCancel Ends the choosing; Back does the same.
 */
@Composable
internal fun DaySwapSelectionBar(
    sides: SwapSides,
    parentNames: ParentNames,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCancel)
    val me = parentNames.parents.me?.slot
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            if (sides.isEmpty) {
                Text(
                    text = stringResource(R.string.day_swap_bar_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            receiverOf(me)?.let { other -> SideLine(other, sides.toOther, parentNames) }
            me?.let { SideLine(it, sides.toOfferer, parentNames) }
            if (sides.unknown.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.day_swap_skipped_days,
                        sides.unknown.size,
                        sides.unknown.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.day_swap_selection_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = onNext, enabled = !sides.isEmpty) {
                    Text(text = stringResource(R.string.day_swap_next), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One way the chosen days go: [toSlot]'s dot, their name ("You" for the reader) and the days. */
@Composable
private fun SideLine(toSlot: String, days: List<LocalDate>, parentNames: ParentNames) {
    if (days.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val dates = swapDateList(days, locale)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = Spacing.XS)
                .size(SIDE_DOT)
                .background(ParentColors.fill(toSlot), CircleShape)
        )
        Text(
            text = if (parentNames.isReader(toSlot)) {
                pluralStringResource(R.plurals.day_swap_bar_to_you, days.size, days.size, dates)
            } else {
                pluralStringResource(
                    R.plurals.day_swap_bar_to,
                    days.size,
                    parentNames.labelFor(toSlot),
                    days.size,
                    dates
                )
            },
            style = MaterialTheme.typography.titleSmallEmphasized
        )
    }
}

/** The receiver's colour dot before a line. */
private val SIDE_DOT = 10.dp
