package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SwapDirection
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.isConsecutive
import com.coparently.app.presentation.common.AnswerButtons
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One day swap in the inbox.
 *
 * Three states, and the difference between them is who is being asked. A swap the co-parent
 * offered carries Accept and Decline; one this parent offered carries Withdraw instead (October
 * 2026), never an answer, because `DayOverrideTransition` and `firestore.rules` both refuse a parent
 * deciding their own offer — rendering those buttons would promise an action the server rejects.
 * An offer answered in part shows the answer so far; once every day is answered or withdrawn it
 * leaves for the history.
 *
 * @param group The offer and where each of its days stands.
 * @param currentUserId This device's own uid, for telling "offered to you" from "offered by you".
 * @param parentNames Resolves a uid or a slot to that person's name — never "Mom" or "Dad".
 * @param months The months its days fall in, each day in the colour of whom it goes to (21 October
 *   2026), or empty while they are computed.
 * @param answers Its Accept and Decline, or its Withdraw.
 */
@Composable
internal fun DaySwapCard(
    group: DaySwapGroup,
    currentUserId: String,
    parentNames: ParentNames,
    months: List<MiniMonthModel>,
    answers: InboxAnswers
) {
    val first = group.swaps.first()
    val offeredByMe = first.override.requestedBy == currentUserId

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            if (offeredByMe) {
                OwnSwapHeader(group, parentNames)
            } else {
                // The co-parent's offer says who offers which way (board SW-6), over the days still
                // waiting on this parent; answered days are listed under the status line.
                val shown = group.waitingOn(currentUserId) ?: group
                val offerer = parentNames.labelForUid(first.override.requestedBy)
                val sides = shown.sidesAsked(parentNames)
                TitleWithClockChip(swapOfferHeading(sides, offerer))
                sides?.let { SwapOfferSides(it, offerer, parentNames) }
                SwapNoteQuote(first.override.note, offerer)
            }
            // An offer answered in part (21 October 2026) still waits, and says how each answered day went.
            val waiting = group.swaps.any { it.override.isPending }
            Text(
                text = stringResource(
                    daySwapStatusLabel(if (waiting) DayOverrideStatus.PENDING else first.override.status)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (waiting) AnsweredDays(group)
            SwapMonths(months, parentNames)
            CardClockDetails()
            SwapAnswerArea(group, currentUserId, parentNames, answers)
        }
    }
}

/** This parent's own offer: its days as a title, and the sentence saying which way they go. */
@Composable
private fun OwnSwapHeader(group: DaySwapGroup, parentNames: ParentNames) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    TitleWithClockChip(
        if (group.dayCount == 1) {
            group.firstDate.format(dateFormatter)
        } else if (!isConsecutive(group.swaps.map { it.date })) {
            group.swaps.joinToString(", ") { it.date.format(dateFormatter) }
        } else {
            stringResource(
                R.string.day_swap_range,
                group.firstDate.format(dateFormatter),
                group.lastDate.format(dateFormatter)
            )
        }
    )
    Text(
        text = daySwapSentence(group, offeredByMe = true, parentNames = parentNames),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    group.swaps.first().override.note?.let { note ->
        Text(text = note, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The card's answer (board SW-6): one main pair of buttons, and "Answer day by day" at two
 * waiting days or more, which switches the card into [SwapDayAnswers] in their place.
 */
@Composable
private fun SwapAnswerArea(group: DaySwapGroup, uid: String, parentNames: ParentNames, answers: InboxAnswers) {
    var byDay by rememberSaveable(group.revision) { mutableStateOf(false) }
    val waitingDays = group.waitingOn(uid)?.dayCount ?: 0
    val hidesAnswers = LocalCardClock.current?.hidesAnswers == true
    if (byDay && waitingDays > 0 && !hidesAnswers) {
        SwapDayAnswers(
            group = group,
            uid = uid,
            parentNames = parentNames,
            onAnswer = {
                byDay = false
                answers.answerSwapDays(group, it)
            },
            onBack = { byDay = false }
        )
    } else {
        DaySwapButtons(group, uid, answers)
        if (waitingDays >= 2 && !hidesAnswers) {
            TextButton(onClick = { byDay = true }) { Text(stringResource(R.string.day_swap_answer_by_day)) }
        }
    }
}

/**
 * Who offered the days to whom, with the count in the sentence, so one card answers "how many days"
 * without the reader counting rows — the thing five separate cards made impossible.
 */
@Composable
private fun daySwapSentence(group: DaySwapGroup, offeredByMe: Boolean, parentNames: ParentNames): String {
    val first = group.swaps.first()
    val offerer = first.override.requestedBy
    // Which way the days go, from the offer itself (second audit: "Bob is offering you a day" over
    // a day Bob was asking for).
    val direction = group.direction(
        offeredByReader = offeredByMe,
        readerSlot = parentNames.parents.me?.slot,
        coParentSlot = parentNames.parents.coParent?.slot
    )
    val (mine, theirs) = when (direction) {
        SwapDirection.GIVES ->
            R.plurals.day_swap_inbox_offered_by_you_days to R.plurals.day_swap_inbox_offered_to_you_days
        SwapDirection.ASKS -> R.plurals.day_swap_inbox_asked_by_you_days to R.plurals.day_swap_inbox_asked_of_you_days
        // "Proposes swapping" claims no direction, which is all an unknown slot allows.
        SwapDirection.BOTH, SwapDirection.UNKNOWN ->
            R.plurals.day_swap_inbox_swap_by_you_days to R.plurals.day_swap_inbox_swap_with_you_days
    }
    val resource = if (offeredByMe) mine else theirs
    // The other parent: whom the offerer's days go to, or who offered them.
    val other = if (offeredByMe) {
        parentNames.parents.coParent?.uid?.let { parentNames.labelForUid(it) }
            ?: parentNames.labelFor(first.override.toParent)
    } else {
        parentNames.labelForUid(offerer)
    }
    return pluralStringResource(resource, group.dayCount, group.dayCount, other)
}

/**
 * The swap card's buttons: Accept and Decline while any day waits on this parent — hidden while the
 * card's clock says the next move is the offering parent's (phase 3a) — or Withdraw while any day of
 * their own offer is still unanswered.
 */
@Composable
private fun DaySwapButtons(group: DaySwapGroup, currentUserId: String, answers: InboxAnswers) {
    val hidesAnswers = LocalCardClock.current?.hidesAnswers == true
    when {
        group.awaitsAnswerFrom(currentUserId) -> if (!hidesAnswers) {
            AnswerButtons(
                acceptLabel = stringResource(swapAcceptLabel(group.waitingOn(currentUserId)?.dayCount ?: 0)),
                declineLabel = stringResource(R.string.day_swap_decline),
                onAccept = { answers.answerSwap(group, true) },
                onDecline = { answers.answerSwap(group, false) }
            )
        }
        group.withdrawableBy(currentUserId) -> TextButton(onClick = { answers.withdrawSwap(group) }) {
            Text(stringResource(R.string.change_request_withdraw))
        }
    }
}
