package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.custody.receiverOf
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.changerequests.SwapMonths
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the review sheet offers: the chosen days split by way, whom they are offered to, and the
 * months they fall in, each day in the wash of whom it would go to.
 *
 * @property sides The chosen days, from this parent's side.
 * @property coParentName The co-parent's name, which the title, the note and the button use.
 * @property months [com.coparently.app.presentation.calendar.swapMiniMonths]'s months, or empty to
 *   draw none.
 */
internal data class SwapSheetOffer(
    val sides: SwapSides,
    val coParentName: String,
    val months: List<MiniMonthModel> = emptyList()
)

/**
 * What the review sheet's buttons do.
 *
 * @property onOffer Sends the offerable days, the note and the deadline.
 * @property onCancel Closes the sheet without sending.
 * @property onChangeDays Back to choosing, keeping the days; null where there is no grid to go
 *   back to (the inbox's "Propose a change"), and the button then reads "Not now".
 */
internal class SwapSheetActions(
    val onOffer: (dates: List<LocalDate>, note: String?, answerBy: AnswerByPick) -> Unit,
    val onCancel: () -> Unit,
    val onChangeDays: (() -> Unit)? = null
)

/**
 * Offers one day, or several, to the other parent: the review step of a day swap (day swap design,
 * 28 October 2026, board SW-3).
 *
 * "You give Lana" and "You take", each with its days; the months as they will look if Lana accepts;
 * an optional message, the deadline, and "Send to Lana". It replaced a sheet that counted the days
 * and said "Mom gives 2 days to Dad" in grey under it, which the owner could not read a direction
 * from.
 *
 * **This sheet never applies anything by itself.** It offers; the co-parent answers in the inbox.
 * A swap that applied itself would just be an edit, which the custody editor already does, and
 * the whole point of the feature is that a day changes hands only when both parents have said so.
 *
 * **Each day goes to whoever does not have it** ([SwapSides]). Days the app has no custody answer
 * for are left out of the offer and counted out loud, rather than guessed at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySwapSheet(offer: SwapSheetOffer, parentNames: ParentNames, actions: SwapSheetActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        DaySwapSheetContent(offer, parentNames, actions)
    }
}

/**
 * The sheet's body, split out of the `ModalBottomSheet` so a screenshot can capture it (a sheet
 * opens its own window, which a node capture does not see).
 */
@Composable
internal fun DaySwapSheetContent(
    offer: SwapSheetOffer,
    parentNames: ParentNames,
    actions: SwapSheetActions,
    modifier: Modifier = Modifier
) {
    var note by rememberSaveable { mutableStateOf("") }
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }
    val sides = offer.sides
    val name = offer.coParentName
    val me = parentNames.parents.me?.slot
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL)
            .padding(bottom = Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text(
            text = stringResource(R.string.day_swap_sheet_title_with, name),
            style = MaterialTheme.typography.titleLargeEmphasized
        )
        if (sides.isEmpty) {
            Text(
                text = stringResource(R.string.day_swap_needs_coparent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            receiverOf(me)?.let { other ->
                SwapSideGroup(stringResource(R.string.day_swap_you_give, name), sides.toOther, other)
            }
            me?.let { SwapSideGroup(stringResource(R.string.day_swap_you_take), sides.toOfferer, it) }
            if (sides.unknown.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.day_swap_skipped_days,
                        sides.unknown.size,
                        sides.unknown.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (offer.months.isNotEmpty()) {
                SwapMonths(offer.months, parentNames)
                Text(
                    text = stringResource(R.string.day_swap_sheet_caption, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.day_swap_note_to, name)) },
                modifier = Modifier.fillMaxWidth()
            )
            SwapDeadline(sides.offerable, name, answerBy) { answerBy = it }
        }
        SwapSheetButtons(sides, name, actions) { actions.onOffer(sides.offerable, note.ifBlank { null }, answerBy) }
    }
}

/** "Change days" (or "Not now") and "Send to {name}", wrapping under each other at a large font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwapSheetButtons(sides: SwapSides, name: String, actions: SwapSheetActions, onSend: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        val back = actions.onChangeDays
        TextButton(onClick = back ?: actions.onCancel) {
            Text(stringResource(if (back != null) R.string.day_swap_change_days else R.string.day_swap_cancel))
        }
        if (!sides.isEmpty) {
            Button(onClick = onSend) { Text(stringResource(R.string.day_swap_send_to, name)) }
        }
    }
}

/**
 * When the offer should be answered by (phase 3a, spec §6.1). The cap is the offer's earliest day, so
 * an offer that starts today or tomorrow gets a shorter choice, or none.
 */
@Composable
private fun SwapDeadline(
    offerable: List<LocalDate>,
    coParentName: String,
    selected: AnswerByPick,
    onSelect: (AnswerByPick) -> Unit
) {
    val cap = remember(offerable) { RequestCap.forSwap(offerable, ZoneId.systemDefault()) }
    AnswerByField(
        offer = rememberAnswerByOffer(cap),
        selected = selected,
        onSelect = onSelect,
        coParentName = coParentName
    )
}

/** Whether [dates], soonest first, are one run of consecutive days. */
internal fun isConsecutive(dates: List<LocalDate>): Boolean =
    dates.zipWithNext().all { (a, b) -> b == a.plusDays(1) }
