package com.coparently.app.presentation.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.R
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.utils.localizedDate
import java.time.LocalDate
import java.time.YearMonth

/** How [CalendarScreen] saves its swap selection with the screen; see [LocalDateSetSaver]. */
internal val SwapSelectionSaver: Saver<Set<LocalDate>, ArrayList<String>> = Saver(
    save = { LocalDateSetSaver.save(it) },
    restore = { LocalDateSetSaver.restore(it) }
)

/**
 * The visible way into a day swap (day swap design, board SW-1): an icon-only "⇄" at the end of
 * the legend row, named "Swap days" for TalkBack and on a long press. Icon only by owner decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapDaysButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.day_swap_start)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        OutlinedIconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = label,
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    }
}

/**
 * The calendar's top bar while days are being chosen (board SW-2): close, "Choose days to swap",
 * and the month with arrows either side, so paging while choosing needs no swipe. Back closes it
 * too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwapSelectionHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.day_swap_close_selection))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.day_swap_choose_title),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
        },
        actions = {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.day_swap_previous_month)
                )
            }
            Text(
                text = month.atDay(1).format(localizedDate("MMMyyyy")),
                style = MaterialTheme.typography.labelLarge
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.day_swap_next_month)
                )
            }
        }
    )
}
