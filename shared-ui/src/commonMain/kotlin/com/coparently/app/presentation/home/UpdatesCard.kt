package com.coparently.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.presentation.changerequests.swapDatesText
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_activity_accepted_event
import com.coparently.app.resources.home_activity_accepted_new_time
import com.coparently.app.resources.home_activity_accepted_swap
import com.coparently.app.resources.home_activity_change_requested
import com.coparently.app.resources.home_activity_declined_event
import com.coparently.app.resources.home_activity_declined_new_time
import com.coparently.app.resources.home_activity_declined_swap
import com.coparently.app.resources.home_activity_event_created
import com.coparently.app.resources.home_activity_event_updated
import com.coparently.app.resources.home_activity_pickup_confirmed
import com.coparently.app.resources.home_updates_empty
import com.coparently.app.resources.home_updates_see_all
import com.coparently.app.resources.request_history_custody
import com.coparently.app.resources.request_history_split
import com.coparently.app.utils.ReaderDateText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home's Updates card (redesign, November 2026, variant A): the co-parent's feed from
 * [HomeFeed.items], newest first, each row behind a status disc, and "See all" to the Requests
 * inbox's History. With nothing to show it says so inside the card rather than as an
 * `EmptyState`, because the card is the page's first thing and must keep its place.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param items The feed, at most five rows
 * @param coParentName The co-parent of the family on screen, by name, for the empty line
 * @param onOpenChangeRequests Opens the requests inbox
 * @param onOpenRequestHistory Opens the requests inbox at its History
 * @param onOpenEvent Opens an event by id
 */
@Composable
fun UpdatesCard(
    items: List<ActivityItem>,
    coParentName: String,
    onOpenChangeRequests: () -> Unit,
    onOpenRequestHistory: () -> Unit,
    onOpenEvent: (String) -> Unit
) {
    SectionGroup {
        if (items.isEmpty()) {
            Text(
                text = stringResource(Res.string.home_updates_empty, coParentName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M)
            )
            Divider()
        }
        items.forEach { item ->
            UpdateRow(
                item = item,
                onClick = {
                    when {
                        item.answer != null -> onOpenRequestHistory()
                        item.isChangeRequest -> onOpenChangeRequests()
                        else -> onOpenEvent(item.eventId)
                    }
                }
            )
            Divider()
        }
        SectionRow(
            title = stringResource(Res.string.home_updates_see_all),
            titleColor = MaterialTheme.colorScheme.primary,
            onClick = onOpenRequestHistory,
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

@Composable
private fun UpdateRow(item: ActivityItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDisc(updateMark(item))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = updateLabel(item),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ReaderDateText.dateWithTime(item.timestamp, "dMMM"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The row's status on a disc: accepted in the theme's good-status tertiary, declined in the error
 * colour, and added, changed or asked on the neutral primary container. The row's sentence says
 * the same in words, so the disc carries no description of its own.
 */
@Composable
private fun StatusDisc(mark: UpdateMark) {
    val colors = MaterialTheme.colorScheme
    val (container, tint, icon) = when (mark) {
        UpdateMark.ACCEPTED -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Default.Check)
        UpdateMark.DECLINED -> Triple(colors.errorContainer, colors.error, Icons.Default.Close)
        UpdateMark.ADDED -> Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Default.Add)
        UpdateMark.CHANGED, UpdateMark.ASKED ->
            Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Default.Edit)
    }
    DiscIcon(container = container, tint = tint, icon = icon)
}

@Composable
private fun DiscIcon(container: Color, tint: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(IconSizes.Large)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSizes.Small)
        )
    }
}

/** What a feed row says: the kind's sentence, or for an answer, what was answered and how. */
@Composable
private fun updateLabel(item: ActivityItem): String {
    val answer = item.answer
    return if (answer == null) stringResource(item.kind.labelRes(), item.title) else answerLabel(answer)
}

@Composable
private fun answerLabel(answer: HistoryEntry): String {
    val accepted = answer.outcome == RequestOutcome.ACCEPTED
    return when (val subject = answer.subject) {
        is RequestSubject.NewTime -> stringResource(
            if (accepted) Res.string.home_activity_accepted_new_time else Res.string.home_activity_declined_new_time,
            subject.eventTitle
        )
        is RequestSubject.DaySwap -> stringResource(
            if (accepted) Res.string.home_activity_accepted_swap else Res.string.home_activity_declined_swap,
            swapDatesText(subject.firstDate, subject.dayCount)
        )
        is RequestSubject.NewEvent -> stringResource(
            if (accepted) Res.string.home_activity_accepted_event else Res.string.home_activity_declined_event,
            subject.eventTitle
        )
        // Never in the feed: a custody or split decision has its own card and banner (HomeFeed).
        RequestSubject.CustodySchedule -> stringResource(Res.string.request_history_custody)
        RequestSubject.SplitRatio -> stringResource(Res.string.request_history_split)
    }
}

private fun ActivityKind.labelRes(): StringResource = when (this) {
    ActivityKind.EVENT_CREATED -> Res.string.home_activity_event_created
    ActivityKind.EVENT_UPDATED -> Res.string.home_activity_event_updated
    ActivityKind.PICKUP_CONFIRMED -> Res.string.home_activity_pickup_confirmed
    ActivityKind.CHANGE_REQUESTED -> Res.string.home_activity_change_requested
    // An answer is worded from its subject (answerLabel); this is only the kind's own sentence.
    ActivityKind.ANSWER -> Res.string.home_activity_accepted_event
}
