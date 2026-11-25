package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SwapSentence
import com.coparently.app.domain.custody.SwapSentenceShape
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.domain.custody.receiverOf
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.SwapSideGroup
import com.coparently.app.presentation.calendar.components.childrenList
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.day_swap_accept
import com.coparently.app.resources.day_swap_accept_swap
import com.coparently.app.resources.day_swap_heading_asks
import com.coparently.app.resources.day_swap_heading_both
import com.coparently.app.resources.day_swap_heading_child
import com.coparently.app.resources.day_swap_heading_child_asks
import com.coparently.app.resources.day_swap_heading_gives
import com.coparently.app.resources.day_swap_note_from
import com.coparently.app.resources.day_swap_proposed_by
import com.coparently.app.resources.day_swap_proposed_by_answer_by
import com.coparently.app.resources.day_swap_sentence_asks
import com.coparently.app.resources.day_swap_sentence_both
import com.coparently.app.resources.day_swap_sentence_days_asks
import com.coparently.app.resources.day_swap_sentence_days_both
import com.coparently.app.resources.day_swap_sentence_days_gives
import com.coparently.app.resources.day_swap_sentence_gives
import com.coparently.app.resources.day_swap_side_you
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.swapDateList
import com.coparently.app.utils.unbreakableDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An offer the co-parent made, split by way from the side of the parent being asked (day swap
 * design, board SW-6): the days that would come to "you", and those that would go to them.
 *
 * Null while the co-parent's slot is not known, when nothing can say which way a day goes.
 */
fun DaySwapGroup.sidesAsked(parentNames: ParentNames): SwapSides? =
    parentNames.parents.coParent?.slot?.let { SwapSides.ofOffer(this, offererSlot = it) }

/**
 * The heading of an offer as the parent being asked reads it.
 *
 * Phrased by who has the children (meeting of 25 November 2026, item 3) when [children] names
 * some: "Lenička with you on Fri, Nov 13?" while any day comes to the reader, "Lenička with Pavel on
 * Fri, Nov 20?" when every day goes to the sender. Otherwise — the children not known yet, the
 * family with none the swap moves, or the co-parent's slot unknown ([sides] null) — the push's own
 * words as before (board SW-5): "{name} offers you 2 days", "{name} asks for 1 of your days", or
 * "{name} proposes a day swap".
 *
 * @param children The children the swap moves, by first name
 *   ([com.coparently.app.domain.custody.swapChildren]), or null while they are not known.
 */
@Composable
fun swapOfferHeading(sides: SwapSides?, offererName: String, children: List<String>? = null): String {
    val names = children?.takeIf { it.isNotEmpty() }?.let { childNamesText(it) }
    val sentence = sides?.let { SwapSentence.of(it, children.orEmpty()) }
    if (names != null && sentence != null) {
        return when (sentence.shape) {
            SwapSentenceShape.ASKS ->
                stringResource(
                    Res.string.day_swap_heading_child_asks,
                    names,
                    offererName,
                    swapDateList(sentence.toSender)
                )
            SwapSentenceShape.GIVES, SwapSentenceShape.BOTH ->
                stringResource(Res.string.day_swap_heading_child, names, swapDateList(sentence.toYou))
        }
    }
    return plainSwapOfferHeading(sides, offererName)
}

/** The heading that names no child: the push's own words (board SW-5). */
@Composable
private fun plainSwapOfferHeading(sides: SwapSides?, offererName: String): String = when {
    sides == null || (sides.toOther.isNotEmpty() && sides.toOfferer.isNotEmpty()) ->
        stringResource(Res.string.day_swap_heading_both, offererName)
    sides.toOfferer.isEmpty() ->
        pluralStringResource(Res.plurals.day_swap_heading_gives, sides.toOther.size, offererName, sides.toOther.size)
    else ->
        pluralStringResource(Res.plurals.day_swap_heading_asks, sides.toOfferer.size, offererName, sides.toOfferer.size)
}

/**
 * What an offer's card or pop-up shows beside its own days, as one value so their signatures stay
 * small.
 *
 * @property months The months its days fall in, each day in the colour of whom it would go to.
 * @property children The children a swap of the family moves, by first name, or null while not known.
 * @property answerByMillis By when the answer is due, for a surface that says it under the sentence;
 *   null when it has no clock or the surface states it elsewhere (the inbox card's clock rows).
 */
data class SwapOfferDetails(
    val months: List<MiniMonthModel>,
    val children: List<String>?,
    val answerByMillis: Long? = null
)

/**
 * The offer phrased by who has the children on which days (meeting of 25 November 2026, item 3):
 * "Lenička will be with you on Fri, Nov 13 and with Pavel on Fri, Nov 20", or the days alone when
 * the swap moves no child ("Fri, Nov 13 will be with you"). Names are never declined; the verb
 * follows how many children there are. Nothing while the children or the co-parent's slot are not
 * known ([children] null, [sides] null), and the card then reads as it did before.
 */
@Composable
fun SwapOfferSentence(sides: SwapSides?, offererName: String, children: List<String>?) {
    if (sides == null || children == null) return
    val sentence = SwapSentence.of(sides, children) ?: return
    Text(
        text = swapSentenceText(sentence, offererName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** [sentence] in the reader's words, as [SwapOfferSentence] draws it. */
@Composable
internal fun swapSentenceText(sentence: SwapSentence, offererName: String): String {
    val toYou = swapDateList(sentence.toYou)
    val toSender = swapDateList(sentence.toSender)
    val count = sentence.children.size
    val names = childNamesText(sentence.children)
        ?: return when (sentence.shape) {
            SwapSentenceShape.BOTH ->
                stringResource(Res.string.day_swap_sentence_days_both, toYou, offererName, toSender)
            SwapSentenceShape.GIVES -> stringResource(Res.string.day_swap_sentence_days_gives, toYou)
            SwapSentenceShape.ASKS -> stringResource(Res.string.day_swap_sentence_days_asks, offererName, toSender)
        }
    return when (sentence.shape) {
        SwapSentenceShape.BOTH ->
            pluralStringResource(Res.plurals.day_swap_sentence_both, count, names, toYou, offererName, toSender)
        SwapSentenceShape.GIVES -> pluralStringResource(Res.plurals.day_swap_sentence_gives, count, names, toYou)
        SwapSentenceShape.ASKS ->
            pluralStringResource(Res.plurals.day_swap_sentence_asks, count, names, offererName, toSender)
    }
}

/**
 * Whether [swapOfferHeading] names the children rather than the sender: then a card adds
 * [SwapProposedByLine] under the sentence, so the sender is still named.
 */
fun swapHeadingNamesChildren(sides: SwapSides?, children: List<String>?): Boolean =
    sides != null && !sides.isEmpty && !children.isNullOrEmpty()

/** The children's names as one phrase in the reader's words ("Ema, Leo and Max"), or null for none. */
@Composable
private fun childNamesText(names: List<String>): String? =
    childrenList(names, stringResource(Res.string.calendar_names_and))

/**
 * Who proposed the swap, under its sentence: "Pavel proposes this swap", and " · answer by …" when
 * [answerByMillis] says when the answer is due (meeting of 25 November 2026, item 3). A card that
 * states its deadline elsewhere passes null.
 */
@Composable
fun SwapProposedByLine(offererName: String, answerByMillis: Long?) {
    Text(
        text = answerByMillis?.let { millis ->
            stringResource(
                Res.string.day_swap_proposed_by_answer_by,
                offererName,
                unbreakableDate(ReaderDateText.answerBy(millis))
            )
        } ?: stringResource(Res.string.day_swap_proposed_by, offererName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The offer's two sides under "You" and the co-parent's name, each day a chip in the wash of whom
 * it would go to; the inbox card and Home's dialog draw it, as the review sheet draws the sender's.
 */
@Composable
fun SwapOfferSides(sides: SwapSides, offererName: String, parentNames: ParentNames) {
    val offererSlot = parentNames.parents.coParent?.slot ?: return
    val readerSlot = receiverOf(offererSlot) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        SwapSideGroup(stringResource(Res.string.day_swap_side_you), sides.toOther, readerSlot)
        SwapSideGroup(offererName, sides.toOfferer, offererSlot)
    }
}

/** The offer's note as the co-parent wrote it, quoted and attributed: half of it is their words. */
@Composable
fun SwapNoteQuote(note: String?, offererName: String) {
    if (note.isNullOrBlank()) return
    Column(
        modifier = Modifier
            .border(QUOTE_EDGE, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
    ) {
        Text(
            text = stringResource(Res.string.day_swap_note_from, offererName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = note, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
    }
}

/** "Accept swap" while two days or more wait, "Accept" for one (board SW-6). */
fun swapAcceptLabel(waitingDays: Int): StringResource =
    if (waitingDays >= 2) Res.string.day_swap_accept_swap else Res.string.day_swap_accept

/** The quote's edge. */
private val QUOTE_EDGE = 1.dp
