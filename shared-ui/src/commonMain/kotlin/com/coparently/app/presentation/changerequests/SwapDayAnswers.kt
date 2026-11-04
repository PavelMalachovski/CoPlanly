package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.time.IsoText
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.change_request_status_cancelled
import com.coparently.app.resources.day_swap_by_day_back
import com.coparently.app.resources.day_swap_by_day_heading
import com.coparently.app.resources.day_swap_day_answered
import com.coparently.app.resources.day_swap_day_to
import com.coparently.app.resources.day_swap_day_to_you
import com.coparently.app.resources.day_swap_inbox_accepted
import com.coparently.app.resources.day_swap_inbox_declined
import com.coparently.app.resources.day_swap_inbox_waiting
import com.coparently.app.resources.day_swap_no
import com.coparently.app.resources.day_swap_send_answers
import com.coparently.app.resources.day_swap_yes
import com.coparently.app.utils.readerMediumDate
import com.coparently.app.utils.swapDateList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An offer answered day by day, as a mode of its own (day swap design, board SW-6; owner decision,
 * 28 October 2026): one row per day still waiting on this parent — "Fri, Nov 6 → you" — with Yes
 * and No, then "Send answer", enabled once one day is marked. A day left unmarked keeps waiting,
 * and the card then shows and decides only the days still waiting. Opened from "Answer day by
 * day" at two waiting days or more; while it is open the offer's main buttons are not drawn.
 *
 * @param group The offer.
 * @param uid This parent, the one being asked.
 * @param onAnswer Called with the marked days, ISO date to the answer.
 * @param onBack Back to the main answer.
 */
@Composable
fun SwapDayAnswers(
    group: DaySwapGroup,
    uid: String,
    parentNames: ParentNames,
    onAnswer: (Map<String, Boolean>) -> Unit,
    onBack: () -> Unit
) {
    val waiting = group.swaps.filter { DaySwapInbox.awaitsAnswerFrom(it, uid) }
    var picks by remember(group.revision) { mutableStateOf(emptyMap<String, Boolean>()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            text = stringResource(Res.string.day_swap_by_day_heading),
            style = MaterialTheme.typography.labelLarge
        )
        waiting.forEach { swap ->
            val iso = IsoText.date(swap.date)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                val day = swapDateList(listOf(swap.date))
                Text(
                    // "→ you" in the dative the grid's pill uses, not the capitalised "You" label.
                    text = if (parentNames.isReader(swap.override.toParent)) {
                        stringResource(Res.string.day_swap_day_to_you, day)
                    } else {
                        stringResource(Res.string.day_swap_day_to, day, parentNames.labelFor(swap.override.toParent))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ANSWERS.forEachIndexed { index, (answer, label) ->
                        SegmentedButton(
                            selected = picks[iso] == answer,
                            onClick = { picks = if (picks[iso] == answer) picks - iso else picks + (iso to answer) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ANSWERS.size),
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.day_swap_by_day_back)) }
            Button(enabled = picks.isNotEmpty(), onClick = { onAnswer(picks) }) {
                Text(stringResource(Res.string.day_swap_send_answers))
            }
        }
    }
}

/** Each day's two chips: the answer and its label. */
private val ANSWERS = listOf(true to Res.string.day_swap_yes, false to Res.string.day_swap_no)

/** The days of an offer answered so far, each with its answer, while the rest still wait. */
@Composable
internal fun AnsweredDays(group: DaySwapGroup) {
    group.swaps.filterNot { it.override.isPending }.forEach { answered ->
        Text(
            text = stringResource(
                Res.string.day_swap_day_answered,
                readerMediumDate(answered.date),
                stringResource(daySwapStatusLabel(answered.override.status))
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The line saying where an offer stands. */
internal fun daySwapStatusLabel(status: DayOverrideStatus): StringResource = when (status) {
    DayOverrideStatus.ACCEPTED -> Res.string.day_swap_inbox_accepted
    DayOverrideStatus.DECLINED -> Res.string.day_swap_inbox_declined
    DayOverrideStatus.PENDING -> Res.string.day_swap_inbox_waiting
    DayOverrideStatus.CANCELLED -> Res.string.change_request_status_cancelled
}
