package com.coparently.app.presentation.changerequests

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.dateWithTime
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime

/** The creator's initial sits on a disc this wide, beside the bubble holding their words. */
private val CREATOR_DISC_SIZE = 28.dp

/** How much of the creator's words the card shows before an ellipsis. */
private const val CREATOR_WORDS_MAX_LINES = 6

/**
 * An event created for the other parent's days, waiting on their answer (phase 2, spec §5.1).
 *
 * The parent asked sees Accept and Decline; the creator sees the same card under "Waiting for
 * <name>", so they see what they sent, with Withdraw (October 2026, owner decision: it deletes the
 * event, as the calendar would, and the other parent's thread says "Event removed"). Both read the
 * creator's own words, when they wrote any, and the event's month with its day ringed, so "Tuesday"
 * means something before the parent answers.
 *
 * It says plainly that the event is in nobody's calendar yet. Without that line the card reads as a
 * notification about something that already happened, and a parent would reasonably assume the
 * event is on their day whether they answer or not.
 *
 * @param event The event awaiting a decision.
 * @param miniMonth Its month, or null while it is still being computed.
 * @param parentNames Resolves a uid or a slot to that person's name — never "Mom" or "Dad".
 * @param actions Accept and Decline for the parent asked ([AwaitingEventActions]), hidden while the
 *   card's clock ([LocalCardClock]) says the deadline passed and the next move is the creator's;
 *   Withdraw for the creator ([AwaitingEventWithdrawal]); null for a creator's card with no button.
 */
@Composable
internal fun AwaitingEventCard(
    event: Event,
    miniMonth: MiniMonthModel?,
    parentNames: ParentNames,
    actions: AwaitingEventButtons?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = event.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                CardClockChip()
            }
            Text(
                text = eventWhenText(event),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (actions is AwaitingEventActions) {
                    stringResource(
                        R.string.event_acceptance_offered_to_you,
                        parentNames.labelForUid(event.createdByFirebaseUid)
                    )
                } else {
                    stringResource(R.string.event_acceptance_offered_by_you, parentNames.labelFor(event.parentOwner))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.description?.takeIf { it.isNotBlank() }?.let { words ->
                CreatorWords(words = words, authorUid = event.createdByFirebaseUid, parentNames = parentNames)
            }
            miniMonth?.let { FoldedMonth(model = it, parentNames = parentNames, key = event.id) }
            Text(
                text = stringResource(R.string.event_acceptance_hidden_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CardClockDetails()
            AnswersOrWaiting(actions)
        }
    }
}

/**
 * Accept and Decline for the parent asked, or "waiting" for the creator, with their Withdraw when
 * the card has one. The parent asked gets neither answer while the card's clock says the deadline
 * passed and the next move is the creator's: no answer is asked for until a last chance (phase 3a).
 */
@Composable
private fun AnswersOrWaiting(actions: AwaitingEventButtons?) {
    when (actions) {
        is AwaitingEventActions -> if (LocalCardClock.current?.hidesAnswers != true) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(onClick = actions.onAccept) {
                    Text(stringResource(R.string.event_acceptance_accept))
                }
                TextButton(onClick = actions.onDecline) {
                    Text(stringResource(R.string.event_acceptance_decline))
                }
            }
        }
        is AwaitingEventWithdrawal -> {
            WaitingLine()
            TextButton(onClick = actions.onWithdraw) {
                Text(stringResource(R.string.change_request_withdraw))
            }
        }
        null -> WaitingLine()
    }
}

/** The creator's line: the event waits for the other parent's answer. */
@Composable
private fun WaitingLine() {
    Text(
        text = stringResource(R.string.event_acceptance_waiting),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * When [event] happens, on the reader's clock: one day with both times, a start alone when it has
 * no end, or both sides in full when it ends on another day.
 */
@Composable
private fun eventWhenText(event: Event): String {
    val start = event.startDateTime
    val end = event.endDateTime
    return when {
        end == null -> stringResource(
            R.string.event_acceptance_when_start,
            start.format(localizedDate("MMMEd")),
            start.format(shortTime())
        )
        end.toLocalDate() == start.toLocalDate() -> stringResource(
            R.string.event_acceptance_when,
            start.format(localizedDate("MMMEd")),
            start.format(shortTime()),
            end.format(shortTime())
        )
        else -> stringResource(
            R.string.event_acceptance_when_span,
            start.format(dateWithTime("MMMEd")),
            end.format(dateWithTime("MMMEd"))
        )
    }
}

/**
 * The creator's own words, in a bubble headed by their name, beside a disc with their initial in
 * their colour. A creator this phone cannot place in a slot is drawn in the neutral tones.
 */
@Composable
private fun CreatorWords(words: String, authorUid: String?, parentNames: ParentNames) {
    val name = parentNames.labelForUid(authorUid)
    val slot = parentNames.slotForUid(authorUid)
    val discColor = slot?.let { ParentColors.container(it, alpha = ParentColors.custodyTintAlpha) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    val nameColor = slot?.let { ParentColors.text(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(
            modifier = Modifier
                .size(CREATOR_DISC_SIZE)
                .background(discColor, CircleShape)
                // Decorative: the bubble beside it says whose words these are.
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = nameColor
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
                verticalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                Text(text = name, style = MaterialTheme.typography.labelMedium, color = nameColor)
                Text(
                    text = words,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = CREATOR_WORDS_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
