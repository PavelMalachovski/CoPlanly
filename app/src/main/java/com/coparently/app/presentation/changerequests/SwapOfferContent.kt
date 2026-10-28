package com.coparently.app.presentation.changerequests

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.custody.receiverOf
import com.coparently.app.presentation.calendar.components.SwapSideGroup
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing

/**
 * An offer the co-parent made, split by way from the side of the parent being asked (day swap
 * design, board SW-6): the days that would come to "you", and those that would go to them.
 *
 * Null while the co-parent's slot is not known, when nothing can say which way a day goes.
 */
internal fun DaySwapGroup.sidesAsked(parentNames: ParentNames): SwapSides? =
    parentNames.parents.coParent?.slot?.let { SwapSides.ofOffer(this, offererSlot = it) }

/**
 * The heading of an offer as the parent being asked reads it — the push's own words (board SW-5):
 * "{name} offers you 2 days", "{name} asks for 1 of your days", or "{name} proposes a day swap".
 */
@Composable
internal fun swapOfferHeading(sides: SwapSides?, offererName: String): String = when {
    sides == null || (sides.toOther.isNotEmpty() && sides.toOfferer.isNotEmpty()) ->
        stringResource(R.string.day_swap_heading_both, offererName)
    sides.toOfferer.isEmpty() ->
        pluralStringResource(R.plurals.day_swap_heading_gives, sides.toOther.size, offererName, sides.toOther.size)
    else ->
        pluralStringResource(R.plurals.day_swap_heading_asks, sides.toOfferer.size, offererName, sides.toOfferer.size)
}

/**
 * The offer's two sides under "You" and the co-parent's name, each day a chip in the wash of whom
 * it would go to; the inbox card and Home's dialog draw it, as the review sheet draws the sender's.
 */
@Composable
internal fun SwapOfferSides(sides: SwapSides, offererName: String, parentNames: ParentNames) {
    val offererSlot = parentNames.parents.coParent?.slot ?: return
    val readerSlot = receiverOf(offererSlot) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        SwapSideGroup(stringResource(R.string.day_swap_side_you), sides.toOther, readerSlot)
        SwapSideGroup(offererName, sides.toOfferer, offererSlot)
    }
}

/** The offer's note as the co-parent wrote it, quoted and attributed: half of it is their words. */
@Composable
internal fun SwapNoteQuote(note: String?, offererName: String) {
    if (note.isNullOrBlank()) return
    Column(
        modifier = Modifier
            .border(QUOTE_EDGE, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
    ) {
        Text(
            text = stringResource(R.string.day_swap_note_from, offererName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = note, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
    }
}

/** "Accept swap" while two days or more wait, "Accept" for one (board SW-6). */
@StringRes
internal fun swapAcceptLabel(waitingDays: Int): Int =
    if (waitingDays >= 2) R.string.day_swap_accept_swap else R.string.day_swap_accept

/** The quote's edge. */
private val QUOTE_EDGE = 1.dp
