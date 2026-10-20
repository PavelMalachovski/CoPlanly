package com.coparently.app.presentation.chat

import androidx.annotation.StringRes
import com.coparently.app.R
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind

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
    data class Res(@StringRes val id: Int) : CardSubjectText

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
 * `when` with **no `else`**, deliberately. Adding a member to [ActivityKind] then fails to
 * compile until it has been given a sentence — and, because every key must exist in all five
 * locale files, until it has been translated. A silent fallback is how an announcement ends up
 * saying nothing.
 */
object ActivityCardText {

    /**
     * The string resource for [kind]. Each takes the entity's own name as `%1$s`; it is passed as
     * a format argument and never concatenated, because word order differs by language. The two
     * settle-up kinds are the exception: they take the month and the amount, and a reader words
     * them through [settleUpResource].
     */
    @StringRes
    fun resourceFor(kind: ActivityKind): Int = when (kind) {
        ActivityKind.EVENT_CREATED -> R.string.activity_event_created
        ActivityKind.EVENT_UPDATED -> R.string.activity_event_updated
        ActivityKind.EVENT_DELETED -> R.string.activity_event_deleted
        ActivityKind.EVENT_ACCEPTED -> R.string.activity_event_accepted
        ActivityKind.EVENT_DECLINED -> R.string.activity_event_declined
        ActivityKind.CHANGE_REQUESTED -> R.string.activity_change_requested
        ActivityKind.CHANGE_ACCEPTED -> R.string.activity_change_accepted
        ActivityKind.CHANGE_DECLINED -> R.string.activity_change_declined
        ActivityKind.EXPENSE_ADDED -> R.string.activity_expense_added
        ActivityKind.EXPENSE_UPDATED -> R.string.activity_expense_updated
        ActivityKind.EXPENSE_DELETED -> R.string.activity_expense_deleted
        ActivityKind.DAY_SWAP_OFFERED -> R.string.activity_day_swap_offered
        ActivityKind.DAY_SWAP_ACCEPTED -> R.string.activity_day_swap_accepted
        ActivityKind.DAY_SWAP_DECLINED -> R.string.activity_day_swap_declined
        ActivityKind.CUSTODY_PROPOSED -> R.string.activity_custody_proposed
        ActivityKind.CUSTODY_ACCEPTED -> R.string.activity_custody_accepted
        ActivityKind.CUSTODY_DECLINED -> R.string.activity_custody_declined
        ActivityKind.REQUEST_LAST_CHANCE -> R.string.activity_request_last_chance
        ActivityKind.REQUEST_UNANSWERED -> R.string.activity_request_unanswered
        // As the sender's own phone says it; [settleUpResource] picks by who reads it, and both
        // take the month and the amount rather than one subject.
        ActivityKind.SETTLE_UP_OWED -> R.string.activity_settle_up_owed_to_you
        ActivityKind.SETTLE_UP_OWING -> R.string.activity_settle_up_you_owe
    }

    /**
     * A settle-up card's sentence on this phone: who owes is a matter of who reads it, so the
     * sentence is chosen by [ActivityKind.readerPays]. Takes the month as `%1$s` and the amount
     * as `%2$s`.
     *
     * @param readerIsSender Whether this phone sent the card.
     */
    @StringRes
    fun settleUpResource(kind: ActivityKind, readerIsSender: Boolean): Int = if (kind.readerPays(readerIsSender)) {
        R.string.activity_settle_up_you_owe
    } else {
        R.string.activity_settle_up_owed_to_you
    }

    /**
     * What [card]'s sentence is about, decided by the entity the card names:
     *
     * - a day swap is its date, which the reader's language writes (`title` is the ISO date; an
     *   older build prints it as it is);
     * - a custody proposal is worded — it has no name of its own, and a request's card carries a
     *   blank title. The cards that existed before carry the family id there, and their sentences
     *   take no argument, so the worded subject changes nothing they show;
     * - anything else is its `title`, as the sender wrote it.
     *
     * `when` with no `else`, like [resourceFor]: a new [ActivityEntityType] has to be given a
     * subject before this builds.
     */
    fun subjectOf(card: ActivityAnnouncement): CardSubjectText = when (card.entityType) {
        ActivityEntityType.DAY_SWAP -> CardSubjectText.IsoDate(card.title)
        ActivityEntityType.CUSTODY_PROPOSAL -> CardSubjectText.Res(R.string.activity_subject_custody_schedule)
        ActivityEntityType.EVENT,
        ActivityEntityType.CHANGE_REQUEST,
        ActivityEntityType.EXPENSE -> CardSubjectText.Raw(card.title)
    }
}
