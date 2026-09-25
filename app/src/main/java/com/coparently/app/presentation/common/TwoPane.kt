package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.Spacing

/**
 * The window width from which a tab lays its content out in two panes: Material's "expanded"
 * class (release audit R-8, week 8).
 *
 * The rail takes over from the bar at 600 dp, and between the two thresholds a single column
 * still reads well beside it. From 840 dp — a tablet held sideways, an unfolded book-style
 * foldable — one column of cards ran edge to edge, 1200 dp wide beside the rail.
 */
const val TWO_PANE_FROM_WIDTH_DP = 840

/** The widest the two panes are together; the window's background shows either side. */
internal val TWO_PANE_MAX_WIDTH = 1200.dp

/**
 * The widest a tab that stays one column is laid out on a wide window: a chat thread. Wider than
 * a detail screen's 640 dp, because a thread holds two columns of bubbles, one per parent.
 */
internal val WIDE_COLUMN_MAX_WIDTH = 840.dp

/** Whether this window is wide enough for [TwoPanes]. Read from the configuration, as the rail is. */
@Composable
fun rememberTwoPane(): Boolean = LocalConfiguration.current.screenWidthDp >= TWO_PANE_FROM_WIDTH_DP

/**
 * Two panes side by side, each half of the width and the whole height, [Spacing.L] apart, the
 * pair centred and at most [TWO_PANE_MAX_WIDTH] wide.
 *
 * Deliberately not `ListDetailPaneScaffold`: the three tabs that use this show two things at once
 * that are always both there (Home's two halves, the month's summary beside its expenses, a list
 * of threads beside the open one), with no navigation between panes to animate or to hand to
 * Back, so the adaptive library's pane state machine would be a dependency for nothing.
 *
 * @param start The first pane, the start side
 * @param end The second pane
 * @param modifier Applied to the outer box, which fills its parent
 */
@Composable
fun TwoPanes(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .widthIn(max = TWO_PANE_MAX_WIDTH)
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { start() }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { end() }
        }
    }
}

/**
 * [content] in one column at most [maxWidth] wide, centred, at the full height — for a tab whose
 * content is one reading column even on a wide window.
 *
 * @param maxWidth The column's cap
 * @param content The column's content, which fills it
 */
@Composable
internal fun WideColumn(maxWidth: Dp = WIDE_COLUMN_MAX_WIDTH, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            content()
        }
    }
}
