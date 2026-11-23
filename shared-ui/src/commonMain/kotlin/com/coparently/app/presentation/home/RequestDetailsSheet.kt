package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.changerequests.InboxItem
import com.coparently.app.presentation.changerequests.LocalCardClock
import com.coparently.app.presentation.changerequests.SchedulePreviewWithChanges
import com.coparently.app.presentation.changerequests.SwapMonths
import com.coparently.app.presentation.common.AdaptiveSheet
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.SheetHeight
import com.coparently.app.presentation.parentingplan.planCitationLine
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.form_close
import org.jetbrains.compose.resources.stringResource

/**
 * A request waiting on this parent, over Home, for answering later (owner request, November 2026,
 * board HomeSwapDetails): its status and deadline, its title, and the request on a mini month of the
 * reader's own calendar — a swap's days in the colour of whom they go to, a schedule proposal as its
 * first month with Proposed | Now and each changed day ringed, a new time or a waiting event on the
 * month of its day — then the proposer's note and Decline and Accept again. An answer closes it.
 *
 * Material's bottom sheet on Android, the phone's own page sheet on the iPhone ([AdaptiveSheet]).
 *
 * @param item The request, as Home's Requests block has it now.
 * @param ctx What Home's request cards are drawn from.
 * @param onDismiss Closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestDetailsSheet(item: InboxItem, ctx: HomeRequestsContext, onDismiss: () -> Unit) {
    val body: @Composable () -> Unit = { RequestDetailsBody(item, ctx, onDismiss) }
    AdaptiveSheet(
        onDismiss = onDismiss,
        height = SheetHeight.Large,
        material = {
            ModalBottomSheet(onDismissRequest = onDismiss) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { body() }
            }
        },
        content = body,
        scrollable = true
    )
}

/**
 * The sheet's body, split out of the sheet so the app's screenshot tests can render it: a
 * `ModalBottomSheet` opens a window of its own, which a node capture does not see.
 *
 * Public, not internal, for those tests; Home is its only caller.
 */
@Composable
fun RequestDetailsBody(item: InboxItem, ctx: HomeRequestsContext, onDismiss: () -> Unit) {
    CompositionLocalProvider(LocalCardClock provides ctx.clockFor(item)) {
        RequestDetailsContent(item, ctx, onDismiss)
    }
}

@Composable
private fun RequestDetailsContent(item: InboxItem, ctx: HomeRequestsContext, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.XL, end = Spacing.XL, bottom = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                StatusLine(waitingForYou = true, coParentName = ctx.coParentName)
            }
            IconButton(onClick = onDismiss) {
                AppIcon(imageVector = Icons.Default.Close, contentDescription = stringResource(Res.string.form_close))
            }
        }
        Text(text = requestTitle(item, ctx), style = MaterialTheme.typography.titleLargeEmphasized)
        RequestSummary(item, ctx)
        RequestOnTheCalendar(item, ctx)
        RequestNote(item, ctx)
        RequestAnswerButtons(item, ctx, after = onDismiss)
    }
}

/** The request on a mini month of the reader's own calendar, as its kind is drawn. */
@Composable
private fun RequestOnTheCalendar(item: InboxItem, ctx: HomeRequestsContext) {
    val names = ctx.parentNames
    when (item) {
        is InboxItem.Swap -> SwapMonths(ctx.swapMonths[item.group.key].orEmpty(), names)
        is InboxItem.Proposal -> {
            SchedulePreviewWithChanges(
                proposal = item.proposal,
                current = ctx.custody?.model,
                overrides = ctx.custody?.dayOverrides.orEmpty(),
                parentNames = names,
                background = MaterialTheme.colorScheme.surfaceContainerLow
            )
            planCitationLine(ctx.proposalCitation)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        is InboxItem.Request -> {
            val custody = ctx.custody
            val month = remember(item.request, custody) {
                changeRequestMonth(
                    item.request,
                    CustodyResolver.resolver(custody?.model, custody?.dayOverrides.orEmpty(), legacy = { null })
                )
            }
            MiniMonth(model = month, parentNames = names)
        }
        is InboxItem.AwaitingEvent -> ctx.miniMonths[item.event.id]?.let { MiniMonth(model = it, parentNames = names) }
        else -> Unit
    }
}
