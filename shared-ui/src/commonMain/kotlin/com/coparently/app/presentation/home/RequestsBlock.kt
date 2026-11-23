package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.change_request_inbox_title
import com.coparently.app.resources.home_requests_for_you
import com.coparently.app.resources.home_requests_new
import com.coparently.app.resources.home_requests_none
import com.coparently.app.resources.home_updates_see_all
import com.coparently.app.resources.home_waiting_for_coparent
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home's one Requests block, as list items: the heading with "N for you" and "See all · N new",
 * an action card per request waiting on this parent, then "Waiting for {co-parent}" and a row per
 * request waiting on them. With nothing waiting it says so in one line. There is no "Recent"
 * section: the co-parent's updates live in the inbox "See all" opens, and "N new" keeps them seen.
 *
 * @param onSeeAll Opens the inbox and records the look ([RequestsSeenViewModel.markSeen]).
 */
internal fun LazyListScope.requestsBlock(
    state: RequestsBlockState,
    ctx: HomeRequestsContext?,
    onSeeAll: () -> Unit
) {
    item(key = "requests_heading") { RequestsHeading(state.forYou, state.newCount, onSeeAll) }
    if (ctx == null || state.items.isEmpty) {
        item(key = "requests_none") { NothingWaiting() }
        return
    }
    items(state.items.forMe, key = { "me_${it.key}" }) { item -> RequestActionCard(item, ctx) }
    if (state.items.forThem.isNotEmpty()) {
        item(key = "requests_them") {
            GroupLabel(text = stringResource(Res.string.home_waiting_for_coparent, ctx.coParentName))
        }
        items(state.items.forThem, key = { "them_${it.key}" }) { item -> WaitingForThemRow(item, ctx) }
    }
}

/**
 * The same block as one column, for the app's screenshot tests; Home draws it through
 * [requestsBlock], as list items.
 */
@Composable
fun RequestsBlock(state: RequestsBlockState, ctx: HomeRequestsContext, onSeeAll: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        RequestsHeading(state.forYou, state.newCount, onSeeAll)
        if (state.items.isEmpty) NothingWaiting()
        state.items.forMe.forEach { RequestActionCard(it, ctx) }
        if (state.items.forThem.isNotEmpty()) {
            GroupLabel(text = stringResource(Res.string.home_waiting_for_coparent, ctx.coParentName))
            state.items.forThem.forEach { WaitingForThemRow(it, ctx) }
        }
    }
}

/**
 * "Requests" with "N for you" in the filled primary chip while anything waits on this parent, and
 * at its end "See all" with "N new" in the secondary container while there is something unopened.
 * It wraps rather than squeezes at large text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestsHeading(forYou: Int, newCount: Int, onSeeAll: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.change_request_inbox_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        if (forYou > 0) {
            PillChip(
                label = pluralStringResource(Res.plurals.home_requests_for_you, forYou, forYou),
                container = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(Res.string.home_updates_see_all))
                if (newCount > 0) {
                    PillChip(
                        label = pluralStringResource(Res.plurals.home_requests_new, newCount, newCount),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Nothing waits on either parent: one quiet line, so the heading does not stand over nothing. */
@Composable
private fun NothingWaiting() {
    SectionGroup {
        Text(
            text = stringResource(Res.string.home_requests_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M)
        )
    }
}
