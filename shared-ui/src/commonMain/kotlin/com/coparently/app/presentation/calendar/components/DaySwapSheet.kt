package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelLargeEmphasized
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.answer_by_compressed
import com.coparently.app.resources.answer_by_title
import com.coparently.app.resources.day_swap_add_note
import com.coparently.app.resources.day_swap_cancel
import com.coparently.app.resources.day_swap_change_days
import com.coparently.app.resources.day_swap_mark_to
import com.coparently.app.resources.day_swap_mark_to_you
import com.coparently.app.resources.day_swap_needs_coparent
import com.coparently.app.resources.day_swap_note_to
import com.coparently.app.resources.day_swap_send_to
import com.coparently.app.resources.day_swap_sheet_title_count
import com.coparently.app.resources.day_swap_sheet_title_with
import com.coparently.app.resources.day_swap_skipped_days
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.swapDateList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the review sheet offers: the chosen days split by way and whom they are offered to.
 *
 * @property sides The chosen days, from this parent's side.
 * @property coParentName The co-parent's name, which the title, the note and the button use.
 * @property months [com.coparently.app.presentation.calendar.swapMiniMonths]'s months. The compact
 *   sheet draws none, the grid behind it being in view; kept for callers that still pass them.
 */
data class SwapSheetOffer(
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
class SwapSheetActions(
    val onOffer: (dates: List<LocalDate>, note: String?, answerBy: AnswerByPick) -> Unit,
    val onCancel: () -> Unit,
    val onChangeDays: (() -> Unit)? = null
)

/**
 * Offers one day, or several, to the other parent: the review step of a day swap (day swap design,
 * 28 October 2026, board SW-3).
 *
 * A title counting the days, one row per way ("Fri 13, Sat 14 Nov → Lana", "Sat 21 Nov → you"), an
 * optional note and deadline behind two chips, and "Send to Lana". The grid stays in view above it
 * (it opens partly expanded), where it used to carry mini months of its own.
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
fun DaySwapSheet(offer: SwapSheetOffer, parentNames: ParentNames, actions: SwapSheetActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    // At a large font the half-height anchor cannot hold the rows and the pinned button; wait until
    // the sheet's own show() has settled, or this expand races it.
    val largeFont = stacksAtLargeFont()
    LaunchedEffect(largeFont) {
        if (largeFont) {
            snapshotFlow { sheetState.currentValue }.first { it != SheetValue.Hidden }
            sheetState.expand()
        }
    }
    ModalBottomSheet(onDismissRequest = actions.onCancel, sheetState = sheetState) {
        DaySwapSheetContent(offer, parentNames, actions, onNeedRoom = { scope.launch { sheetState.expand() } })
    }
}

/**
 * The sheet's body, split out of the `ModalBottomSheet` so a screenshot can capture it (a sheet
 * opens its own window, which a node capture does not see).
 *
 * **It is compact on purpose, so the grid stays in view** (Zuzana's redesign, November 2026): a
 * title that counts the days, one row per way ("Fri 13, Sat 14 Nov → Bob"), and two chips — "Add a
 * note" and "Answer by …" — each opening its field in place, with "Send to Bob" pinned under them.
 * The mini months the sheet used to draw are the inbox card's and Home's pop-up's now.
 *
 * **The send button is pinned under a part that scrolls.** A sheet does not scroll its content by
 * itself, and at a large font an offer once could not be sent at all (owner report, 9 November
 * 2026). Everything above the button scrolls, and a divider shows while there is more to scroll to.
 */
@Composable
fun DaySwapSheetContent(
    offer: SwapSheetOffer,
    parentNames: ParentNames,
    actions: SwapSheetActions,
    modifier: Modifier = Modifier,
    onNeedRoom: () -> Unit = {}
) {
    var note by rememberSaveable { mutableStateOf("") }
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }
    val sides = offer.sides
    val scroll = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                // Wraps a short offer; takes what is left above the button, and scrolls, for a long one.
                .weight(1f, fill = false)
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.XL)
                .padding(bottom = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            SwapSheetBody(offer, parentNames, note, { note = it }, answerBy, { answerBy = it }, onNeedRoom)
        }
        if (scroll.canScrollForward || scroll.canScrollBackward) HorizontalDivider()
        SwapSheetBar(sides, offer.coParentName, actions) {
            actions.onOffer(sides.offerable, note.ifBlank { null }, answerBy)
        }
    }
}

/** Everything above the button: the title, the rows, the note and the deadline. */
@Composable
@Suppress("LongParameterList") // the body's two fields are hoisted, each with its setter
private fun SwapSheetBody(
    offer: SwapSheetOffer,
    parentNames: ParentNames,
    note: String,
    onNoteChange: (String) -> Unit,
    answerBy: AnswerByPick,
    onAnswerByChange: (AnswerByPick) -> Unit,
    onNeedRoom: () -> Unit
) {
    val sides = offer.sides
    val name = offer.coParentName
    val me = parentNames.parents.me?.slot
    if (sides.isEmpty) {
        Text(
            text = stringResource(Res.string.day_swap_sheet_title_with, name),
            style = MaterialTheme.typography.titleLargeEmphasized
        )
        Text(
            text = stringResource(Res.string.day_swap_needs_coparent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val count = sides.offerable.size
    Text(
        text = pluralStringResource(Res.plurals.day_swap_sheet_title_count, count, count, name),
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = Modifier.semantics { heading() }
    )
    // An unknown own slot cannot be told which side is whose: the rows are left out, the offer stands.
    if (me != null) {
        SectionGroup {
            swapSheetRows(sides, me).forEachIndexed { index, row ->
                if (index > 0) Divider()
                SwapSheetRowLine(row, parentNames)
            }
        }
    }
    if (sides.unknown.isNotEmpty()) {
        Text(
            text = pluralStringResource(Res.plurals.day_swap_skipped_days, sides.unknown.size, sides.unknown.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SwapOptions(sides.offerable, name, note, onNoteChange, answerBy, onAnswerByChange, onNeedRoom)
}

/** One row: the receiver's dot, the days, and "→ name" or "→ you" in the receiver's text colour. */
@Composable
private fun SwapSheetRowLine(row: SwapSheetRow, parentNames: ParentNames) {
    val target = if (parentNames.isReader(row.receiverSlot)) {
        stringResource(Res.string.day_swap_mark_to_you)
    } else {
        stringResource(Res.string.day_swap_mark_to, parentNames.labelFor(row.receiverSlot))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(DOT_SIZE).background(ParentColors.fill(row.receiverSlot), CircleShape))
        Text(
            text = swapDateList(row.dates),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = target,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = ParentColors.text(row.receiverSlot)
        )
    }
}

/**
 * "Add a note" and "Answer by …": two chips, each opening its field in place. A note already typed
 * keeps its field open, and so does a rotation (both flags are saveable).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList") // the two fields are hoisted, each with its setter
private fun SwapOptions(
    offerable: List<LocalDate>,
    coParentName: String,
    note: String,
    onNoteChange: (String) -> Unit,
    answerBy: AnswerByPick,
    onAnswerByChange: (AnswerByPick) -> Unit,
    onNeedRoom: () -> Unit
) {
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    var answerByOpen by rememberSaveable { mutableStateOf(false) }
    val zone = TimeZone.currentSystemDefault()
    val cap = remember(offerable) { RequestCap.forSwap(offerable, zone) }
    val offer = rememberAnswerByOffer(cap)
    val answerByMillis = AnswerBy.resolve(answerBy, offer, zone)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        if (!noteOpen && note.isEmpty()) {
            AssistChip(
                onClick = {
                    noteOpen = true
                    onNeedRoom()
                },
                label = { Text(stringResource(Res.string.day_swap_add_note)) },
                leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(IconSizes.Small)) }
            )
        }
        AssistChip(
            onClick = {
                answerByOpen = !answerByOpen
                if (answerByOpen) onNeedRoom()
            },
            label = {
                Text(
                    if (answerByMillis == null) {
                        stringResource(Res.string.answer_by_title)
                    } else {
                        stringResource(Res.string.answer_by_compressed, ReaderDateText.answerBy(answerByMillis, zone))
                    }
                )
            },
            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(IconSizes.Small)) }
        )
    }
    if (noteOpen || note.isNotEmpty()) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(Res.string.day_swap_note_to, coParentName)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (answerByOpen) {
        AnswerByField(offer, answerBy, onAnswerByChange, coParentName)
    }
}

/** "Not now" (or "Change days") and the pinned "Send to {name}". */
@Composable
private fun SwapSheetBar(sides: SwapSides, name: String, actions: SwapSheetActions, onSend: () -> Unit) {
    val back = actions.onChangeDays
    val backButton = @Composable {
        TextButton(onClick = back ?: actions.onCancel) {
            Text(stringResource(if (back != null) Res.string.day_swap_change_days else Res.string.day_swap_cancel))
        }
    }
    if (sides.isEmpty) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.XL, vertical = Spacing.S)) { backButton() }
    } else {
        StickyActionBar(
            label = stringResource(Res.string.day_swap_send_to, name),
            onClick = onSend,
            notice = backButton
        )
    }
}

private val DOT_SIZE = 10.dp

/** Whether [dates], soonest first, are one run of consecutive days. */
fun isConsecutive(dates: List<LocalDate>): Boolean =
    dates.zipWithNext().all { (a, b) -> b == a.plus(1, DateTimeUnit.DAY) }
