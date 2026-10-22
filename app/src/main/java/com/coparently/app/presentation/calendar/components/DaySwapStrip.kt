package com.coparently.app.presentation.calendar.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ParentNames
import java.time.LocalDate

/**
 * Where a day's swap stands, as Day view's strip says it (21 October 2026, Zuzana's test: "show the
 * swap in the day, and let me act on it there").
 */
internal sealed interface DaySwapStripState {

    /** This parent offered the day to [toParent]'s slot; it waits, and may be withdrawn. */
    data class OfferedByMe(val toParent: String) : DaySwapStripState

    /** The co-parent [byUid] asks for the day to go to [toParent]'s slot; it waits on this parent. */
    data class OfferedToMe(val byUid: String, val toParent: String) : DaySwapStripState

    /** An agreed swap: the day is [withParent]'s, and a change may be proposed while it is ahead. */
    data class Agreed(val withParent: String, val canChange: Boolean) : DaySwapStripState

    companion object {
        /**
         * The strip for [date], or null when the day has no swap worth a line: none, or one
         * declined or withdrawn with no agreement behind it.
         */
        fun of(override: DayOverride?, myUid: String?, date: LocalDate, today: LocalDate): DaySwapStripState? {
            if (override == null || myUid.isNullOrBlank()) return null
            if (override.isPending) {
                return if (override.requestedBy == myUid) {
                    OfferedByMe(override.toParent)
                } else {
                    OfferedToMe(override.requestedBy, override.toParent)
                }
            }
            return override.agreedToParent?.let { Agreed(it, canChange = !date.isBefore(today)) }
        }
    }
}

/**
 * What Day view's strip does: answer, withdraw, or propose a change to an agreed day.
 *
 * @property onAccept Takes the day the co-parent offered.
 * @property onDecline Turns it down.
 * @property onWithdraw Takes back this parent's own offer.
 * @property onProposeChange Opens the swap sheet for the day, as "Swap this day" does.
 */
internal data class DaySwapStripActions(
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onWithdraw: () -> Unit,
    val onProposeChange: () -> Unit
)

/** The strip over Day view's grid for a day with a swap; see [DaySwapStripState]. */
@Composable
internal fun DaySwapStrip(
    state: DaySwapStripState,
    parentNames: ParentNames,
    actions: DaySwapStripActions,
    modifier: Modifier = Modifier
) {
    val text = when (state) {
        is DaySwapStripState.OfferedByMe ->
            stringResource(R.string.day_swap_strip_offered_by_you, parentNames.labelFor(state.toParent))
        is DaySwapStripState.OfferedToMe -> stringResource(
            R.string.day_swap_strip_offered_to_you,
            parentNames.labelForUid(state.byUid),
            parentNames.labelFor(state.toParent)
        )
        is DaySwapStripState.Agreed ->
            stringResource(R.string.day_swap_strip_agreed, parentNames.labelFor(state.withParent))
    }
    InlineBanner(
        text = text,
        modifier = modifier,
        icon = Icons.Default.SwapHoriz,
        tone = if (state is DaySwapStripState.OfferedToMe) BannerTone.ATTENTION else BannerTone.INFO,
        // Nothing to do on an agreed day already lived; everything else carries its buttons.
        actions = if (state !is DaySwapStripState.Agreed || state.canChange) {
            { DaySwapStripButtons(state, actions) }
        } else {
            null
        }
    )
}

/** The strip's buttons, in the banner's own row; see [DaySwapStrip]. */
@Composable
private fun DaySwapStripButtons(state: DaySwapStripState, actions: DaySwapStripActions) {
    when (state) {
        is DaySwapStripState.OfferedByMe ->
            TextButton(onClick = actions.onWithdraw) { Text(stringResource(R.string.change_request_withdraw)) }
        is DaySwapStripState.OfferedToMe -> {
            Button(onClick = actions.onAccept) { Text(stringResource(R.string.day_swap_accept)) }
            TextButton(onClick = actions.onDecline) { Text(stringResource(R.string.day_swap_decline)) }
        }
        is DaySwapStripState.Agreed -> TextButton(onClick = actions.onProposeChange) {
            Text(stringResource(R.string.request_history_propose_change))
        }
    }
}
