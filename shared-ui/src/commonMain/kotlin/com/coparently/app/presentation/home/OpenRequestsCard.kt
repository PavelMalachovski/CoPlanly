package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_activity_review
import com.coparently.app.resources.home_open_requests_title
import com.coparently.app.resources.home_requests_waiting_on_them
import com.coparently.app.resources.home_requests_waiting_on_you
import com.coparently.app.resources.home_waiting_for_coparent
import com.coparently.app.resources.home_waiting_for_you
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home's "Open requests" card (redesign, November 2026, variant A): "Waiting for you" with Review,
 * and "Waiting for {co-parent}", each only while something waits there. The counts are the
 * Requests row's ([RequestsRowState], design item 40), so the card, the inbox and the calendar's
 * banner count from one inbox. Home leaves the card out when nothing waits on either parent.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param state What waits on each parent
 * @param coParentName The co-parent of the family on screen, by name
 * @param onOpenChangeRequests Opens the requests inbox
 */
@Composable
fun OpenRequestsCard(state: RequestsRowState, coParentName: String, onOpenChangeRequests: () -> Unit) {
    SectionGroup {
        Text(
            text = stringResource(Res.string.home_open_requests_title),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = Spacing.L, end = Spacing.L, top = Spacing.M)
                .semantics { heading() }
        )
        if (state.waitingOnMe > 0) {
            SectionRow(
                title = stringResource(Res.string.home_waiting_for_you),
                supporting = pluralStringResource(
                    Res.plurals.home_requests_waiting_on_you,
                    state.waitingOnMe,
                    state.waitingOnMe
                ),
                onClick = onOpenChangeRequests,
                stackTrailingAtLargeFont = true,
                trailing = {
                    Button(onClick = onOpenChangeRequests) {
                        Text(stringResource(Res.string.home_activity_review))
                    }
                }
            )
        }
        if (state.waitingOnMe > 0 && state.waitingOnThem > 0) Divider()
        if (state.waitingOnThem > 0) {
            SectionRow(
                title = stringResource(Res.string.home_waiting_for_coparent, coParentName),
                supporting = pluralStringResource(
                    Res.plurals.home_requests_waiting_on_them,
                    state.waitingOnThem,
                    state.waitingOnThem,
                    coParentName
                ),
                onClick = onOpenChangeRequests,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}
