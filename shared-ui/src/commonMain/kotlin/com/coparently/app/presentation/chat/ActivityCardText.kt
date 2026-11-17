package com.coparently.app.presentation.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.pushwording.CardSubject
import com.coparently.app.pushwording.CardWording
import com.coparently.app.resources.Res
import com.coparently.app.resources.allStringResources
import org.jetbrains.compose.resources.StringResource

/**
 * What an announcement's sentence is about — the `%1$s` of its string — before the reading device
 * words it. A tagged value rather than a `String`, because not everything a card is about is the
 * sender's text: a day is written the way the reader's language writes it, and a schedule is a
 * noun this app words itself.
 */
sealed interface CardSubjectText {

    /** A day, as ISO text on the wire. The reader's language says it, with its weekday. */
    data class IsoDate(val iso: String) : CardSubjectText

    /** A noun the app words itself, in the reader's language. */
    data class Res(val id: StringResource) : CardSubjectText

    /** What the sender wrote, shown as it is: an event's title, an expense's description. */
    data class Raw(val text: String) : CardSubjectText
}

/**
 * Turns an announcement's [ActivityKind] into the sentence *this* device speaks.
 *
 * The whole point of the payload is that the sentence is composed by the reader, not the sender:
 * the two parents may not read the same language, and a card written in the sender's locale is a
 * card the recipient may not be able to read.
 *
 * **The decisions are `:push-wording`'s [CardWording]** (iPhone phase 4 part C, design §11 call 4):
 * which string each kind takes, what its sentence is about, who a settle-up's sentence is for. The
 * notification extension words a card's push from the same decisions, without Compose, so a new
 * [ActivityKind] reaches the thread and the push at once ([CardWording.keyFor] is the `when` with no
 * `else`). This maps each decided key to its Compose Multiplatform resource; a key with no resource
 * fails at once (`ActivityCardTextTest` resolves every one).
 */
object ActivityCardText {

    /**
     * The string resource for [kind]. Each takes the entity's own name as `%1$s`; it is passed as
     * a format argument and never concatenated, because word order differs by language. The two
     * settle-up kinds are the exception: they take the month and the amount, and a reader words
     * them through [settleUpResource].
     */
    fun resourceFor(kind: ActivityKind): StringResource = resourceOf(CardWording.keyFor(kind))

    /**
     * Whether [kind]'s sentence takes the card's money as two arguments rather than one subject: a
     * payment (`%1$s` the month, `%2$s` the amount) or an alimony agreement (`%1$s` the amount,
     * `%2$s` the day of the month).
     */
    fun takesMoney(kind: ActivityKind): Boolean = CardWording.takesMoney(kind)

    /**
     * A settle-up card's sentence on this phone: who owes is a matter of who reads it, so the
     * sentence is chosen by [ActivityKind.readerPays]. Takes the month as `%1$s` and the amount
     * as `%2$s`.
     *
     * @param readerIsSender Whether this phone sent the card.
     */
    fun settleUpResource(kind: ActivityKind, readerIsSender: Boolean): StringResource =
        resourceOf(CardWording.settleUpKey(kind, readerIsSender))

    /** What [card]'s sentence is about: [CardWording.subjectOf]'s decision, its noun as a resource. */
    fun subjectOf(card: ActivityAnnouncement): CardSubjectText = when (val about = CardWording.subjectOf(card)) {
        is CardSubject.IsoDate -> CardSubjectText.IsoDate(about.iso)
        is CardSubject.Key -> CardSubjectText.Res(resourceOf(about.key))
        is CardSubject.Raw -> CardSubjectText.Raw(about.text)
    }

    /** The Compose Multiplatform resource named [key]: the same files the app's `R.string` is generated from. */
    private fun resourceOf(key: String): StringResource = Res.allStringResources.getValue(key)
}
