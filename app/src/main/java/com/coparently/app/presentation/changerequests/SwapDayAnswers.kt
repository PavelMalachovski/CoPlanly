package com.coparently.app.presentation.changerequests

import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.swapDateList
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
internal fun SwapDayAnswers(
    group: DaySwapGroup,
    uid: String,
    parentNames: ParentNames,
    onAnswer: (Map<String, Boolean>) -> Unit,
    onBack: () -> Unit
) {
    val waiting = group.swaps.filter { DaySwapInbox.awaitsAnswerFrom(it, uid) }
    var picks by remember(group.revision) { mutableStateOf(emptyMap<String, Boolean>()) }
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            text = stringResource(R.string.day_swap_by_day_heading),
            style = MaterialTheme.typography.labelLarge
        )
        waiting.forEach { swap ->
            val iso = swap.date.toString()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                Text(
                    text = stringResource(
                        R.string.day_swap_day_to,
                        swapDateList(listOf(swap.date), locale),
                        parentNames.labelOrYou(swap.override.toParent)
                    ),
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
            TextButton(onClick = onBack) { Text(stringResource(R.string.day_swap_by_day_back)) }
            Button(enabled = picks.isNotEmpty(), onClick = { onAnswer(picks) }) {
                Text(stringResource(R.string.day_swap_send_answers))
            }
        }
    }
}

/** The months an offer's days fall in, as [com.coparently.app.presentation.calendar.swapMiniMonths] built them. */
@Composable
internal fun SwapMonths(months: List<MiniMonthModel>, parentNames: ParentNames) {
    months.forEach { MiniMonth(model = it, parentNames = parentNames) }
}

/** Each day's two chips: the answer and its label. */
private val ANSWERS = listOf(true to R.string.day_swap_yes, false to R.string.day_swap_no)

/** The days of an offer answered so far, each with its answer, while the rest still wait. */
@Composable
internal fun AnsweredDays(group: DaySwapGroup) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    group.swaps.filterNot { it.override.isPending }.forEach { answered ->
        Text(
            text = stringResource(
                R.string.day_swap_day_answered,
                answered.date.format(dateFormatter),
                stringResource(daySwapStatusLabel(answered.override.status))
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The line saying where an offer stands. */
@StringRes
internal fun daySwapStatusLabel(status: DayOverrideStatus): Int = when (status) {
    DayOverrideStatus.ACCEPTED -> R.string.day_swap_inbox_accepted
    DayOverrideStatus.DECLINED -> R.string.day_swap_inbox_declined
    DayOverrideStatus.PENDING -> R.string.day_swap_inbox_waiting
    DayOverrideStatus.CANCELLED -> R.string.change_request_status_cancelled
}
