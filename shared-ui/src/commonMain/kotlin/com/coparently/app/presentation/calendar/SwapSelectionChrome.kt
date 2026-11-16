package com.coparently.app.presentation.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.LocalBarHost
import com.coparently.app.presentation.common.ScreenBackStep
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.resources.Res
import com.coparently.app.resources.day_swap_choose_title
import com.coparently.app.resources.day_swap_close_selection
import com.coparently.app.resources.day_swap_next_month
import com.coparently.app.resources.day_swap_previous_month
import com.coparently.app.resources.day_swap_start
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource

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
fun SwapDaysButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.day_swap_start)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        OutlinedIconButton(onClick = onClick) {
            AppIcon(
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
 *
 * The iPhone draws it in content too (MON-24 phase 3, part C): its shell hides the navigation bar
 * over the Calendar's root, so the header's close button and arrows are Compose's there as well,
 * drawn here with no bar host however the screen is hosted.
 */
@Composable
internal fun SwapSelectionHeader(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClose: () -> Unit
) {
    ScreenBackStep(onBack = onClose)
    // Drawn here on every platform: a host would take the bar out of the content (see the KDoc).
    CompositionLocalProvider(LocalBarHost provides null) {
        SwapSelectionTopBar(month, onPreviousMonth, onNextMonth, onClose)
    }
}

/** [SwapSelectionHeader]'s Material top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapSelectionTopBar(
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClose: () -> Unit
) {
    val title = stringResource(Res.string.day_swap_choose_title)
    AppTopBar(
        ScreenBar(
            title = title,
            leading = BarLeading.Close(label = stringResource(Res.string.day_swap_close_selection), onClick = onClose),
            titleContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
            },
            // The month between its arrows, which no bar action can hold.
            trailingContent = {
                IconButton(onClick = onPreviousMonth) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(Res.string.day_swap_previous_month)
                    )
                }
                Text(
                    text = ReaderDateText.month(month, "MMMyyyy"),
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onNextMonth) {
                    AppIcon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.day_swap_next_month)
                    )
                }
            }
        )
    )
}
