package com.coparently.app.presentation.changerequests

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.localizedDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * An offer of several days answered day by day (21 October 2026, Zuzana's test): each day still
 * waiting on this parent, the parent it would go to, and its own Accept and Decline; then one
 * button that sends what was picked. A day left unpicked stays waiting. Drawn beside the offer's
 * Accept and Decline, which still answer every day at once, and only while two days or more wait.
 *
 * @param group The offer.
 * @param uid This parent, the one being asked.
 * @param onAnswer Called with the picked days, ISO date to the answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwapDayAnswers(
    group: DaySwapGroup,
    uid: String,
    parentNames: ParentNames,
    onAnswer: (Map<String, Boolean>) -> Unit
) {
    val waiting = group.swaps.filter { DaySwapInbox.awaitsAnswerFrom(it, uid) }
    if (waiting.size < 2) return
    var picks by remember(group.revision) { mutableStateOf(emptyMap<String, Boolean>()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            text = stringResource(R.string.day_swap_by_day_heading),
            style = MaterialTheme.typography.labelLarge
        )
        waiting.forEach { swap ->
            val iso = swap.date.toString()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
            ) {
                Text(
                    text = stringResource(
                        R.string.day_swap_day_to,
                        swap.date.format(localizedDate("EEEMMMd")),
                        parentNames.labelFor(swap.override.toParent)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                ANSWERS.forEach { (answer, label) ->
                    FilterChip(
                        selected = picks[iso] == answer,
                        onClick = {
                            picks = if (picks[iso] == answer) picks - iso else picks + (iso to answer)
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
        }
        OutlinedButton(enabled = picks.isNotEmpty(), onClick = { onAnswer(picks) }) {
            Text(stringResource(R.string.day_swap_send_answers))
        }
    }
}

/** The months an offer's days fall in, as [com.coparently.app.presentation.calendar.swapMiniMonths] built them. */
@Composable
internal fun SwapMonths(months: List<MiniMonthModel>, parentNames: ParentNames) {
    months.forEach { MiniMonth(model = it, parentNames = parentNames) }
}

/** Each day's two chips: the answer and its label. */
private val ANSWERS = listOf(true to R.string.day_swap_accept, false to R.string.day_swap_decline)

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
