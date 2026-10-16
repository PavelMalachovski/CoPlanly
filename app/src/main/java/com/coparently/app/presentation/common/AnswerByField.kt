package com.coparently.app.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.coparently.app.R
import com.coparently.app.data.requests.minuteTicks
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByBand
import com.coparently.app.domain.changerequests.AnswerByChip
import com.coparently.app.domain.changerequests.AnswerByOffer
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.utils.answerByText
import java.time.LocalDate
import java.time.ZoneId

/**
 * Saves an [AnswerByPick] through a rotation or a process death, as the text
 * [AnswerByPick.encode] writes and [AnswerByPick.decode] reads. Each form keeps its pick with
 * `rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }`.
 */
val AnswerByPickSaver: Saver<AnswerByPick, String> = Saver(
    save = { AnswerByPick.encode(it) },
    restore = { AnswerByPick.decode(it) }
)

/**
 * What the proposer is offered for a request with the cap [capMillis], kept current while the form is
 * open (phase 3a, spec §6.1): worked out again at once when the cap changes and on every minute
 * boundary, so a chip never reads a time more than a minute old. The repository works the pick out
 * again when it sends, against the server's cap; this is the preview of that.
 *
 * @param capMillis The first moment the request would change something (`RequestCap`), or null when
 *   nothing ahead would. Worked out by the caller once per change of the draft, never per
 *   recomposition.
 */
@Composable
fun rememberAnswerByOffer(capMillis: Long?): AnswerByOffer {
    val first = remember(capMillis) {
        AnswerBy.offer(System.currentTimeMillis(), capMillis, ZoneId.systemDefault())
    }
    val offer by produceState(first, capMillis) {
        minuteTicks().collect {
            value = AnswerBy.offer(System.currentTimeMillis(), capMillis, ZoneId.systemDefault())
        }
    }
    return offer
}

/**
 * The deadline block of a form that proposes something to the co-parent (phase 3a, spec §6.1): when
 * the proposer would like to be answered by, and what happens if nobody answers.
 *
 * What it shows is the offer's:
 * - a cap two days or more away, or none: a title and chips — the presets, a chip for a picked date,
 *   and "Pick a date…" — then how many reminders the co-parent gets and what happens without an
 *   answer. A preset the cap shortened reads "By <time>", because "A week" would be a promise the
 *   request cannot keep;
 * - a cap two to forty-eight hours away: the one deadline, with the same two lines under it;
 * - a cap under two hours away: one sentence saying the request goes out without a deadline.
 *
 * A chip is selected when its time is the time [selected] gives under [offer], so a preset merged
 * into another chip lights that chip.
 *
 * @param offer What is offered now; see [rememberAnswerByOffer].
 * @param selected The proposer's pick.
 * @param onSelect Called with a new pick.
 * @param coParentName The other parent's name, never a role.
 */
@Composable
fun AnswerByField(
    offer: AnswerByOffer,
    selected: AnswerByPick,
    onSelect: (AnswerByPick) -> Unit,
    coParentName: String,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val selectedMillis = AnswerBy.resolve(selected, offer, zone)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        if (selectedMillis == null) {
            // Too soon for a deadline: the offer gives none, whatever the pick.
            HelperLine(stringResource(R.string.answer_by_too_soon))
        } else if (offer.band == AnswerByBand.FIXED) {
            Text(
                text = stringResource(R.string.answer_by_fixed, answerByText(selectedMillis, zone)),
                style = MaterialTheme.typography.titleMediumEmphasized
            )
            Consequences(offer, selectedMillis, coParentName, outcome = R.string.answer_by_outcome_short)
        } else {
            Text(
                text = stringResource(R.string.answer_by_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() }
            )
            AnswerByChoices(offer, selected, selectedMillis, onSelect)
            Consequences(offer, selectedMillis, coParentName, outcome = R.string.answer_by_outcome)
        }
    }
}

/** The chips of a [AnswerByBand.CHOICES] offer, and the date picker "Pick a date…" opens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerByChoices(
    offer: AnswerByOffer,
    selected: AnswerByPick,
    selectedMillis: Long,
    onSelect: (AnswerByPick) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        offer.chips.forEach { chip ->
            FilterChip(
                selected = chip.answerByMillis == selectedMillis,
                onClick = { onSelect(chip.pick) },
                label = { Text(chipLabel(chip, zone)) }
            )
        }
        // A picked date no preset chip stands for is a chip of its own, selected; tapping it opens
        // the picker again.
        if (selected is AnswerByPick.OnDate && offer.chips.none { it.answerByMillis == selectedMillis }) {
            FilterChip(
                selected = true,
                onClick = { picking = true },
                label = { Text(stringResource(R.string.answer_by_compressed, answerByText(selectedMillis, zone))) }
            )
        }
        FilterChip(
            selected = false,
            onClick = { picking = true },
            label = { Text(stringResource(R.string.answer_by_pick_date)) }
        )
    }
    if (picking) {
        LocalDatePickerDialog(
            initialDate = (selected as? AnswerByPick.OnDate)?.date ?: LocalDate.now(zone).plusDays(PICKER_START_DAYS),
            confirmLabel = stringResource(R.string.answer_by_picker_ok),
            dismissLabel = stringResource(R.string.answer_by_picker_cancel),
            onConfirm = { onSelect(AnswerByPick.OnDate(it)) },
            onDismiss = { picking = false }
        )
    }
}

/** A preset's name, or "By <time>" when the cap shortened it. */
@Composable
private fun chipLabel(chip: AnswerByChip, zone: ZoneId): String {
    val preset = (chip.pick as? AnswerByPick.Preset)?.preset
    return if (chip.compressed || preset == null) {
        stringResource(R.string.answer_by_compressed, answerByText(chip.answerByMillis, zone))
    } else {
        stringResource(
            when (preset) {
                AnswerByPreset.TOMORROW -> R.string.answer_by_tomorrow
                AnswerByPreset.THREE_DAYS -> R.string.answer_by_three_days
                AnswerByPreset.WEEK -> R.string.answer_by_week
            }
        )
    }
}

/** The two lines under the choice: the reminders the co-parent gets, and what no answer leads to. */
@Composable
private fun Consequences(offer: AnswerByOffer, selectedMillis: Long, coParentName: String, @StringRes outcome: Int) {
    val reminders = ReminderPlan.countFor(selectedMillis - offer.nowMillis)
    HelperLine(
        LocalContext.current.resources.getQuantityString(
            R.plurals.answer_by_reminders,
            reminders,
            answerByText(selectedMillis, ZoneId.systemDefault()),
            coParentName,
            reminders
        )
    )
    HelperLine(stringResource(outcome))
}

@Composable
private fun HelperLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Days ahead of today the date picker opens on when no date was picked yet. */
private const val PICKER_START_DAYS = 3L
