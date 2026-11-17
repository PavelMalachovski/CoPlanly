package com.coparently.app.pushwording

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind

/**
 * What an announcement's sentence is about — the `%1$s` of its string — before the reading device
 * words it. A tagged value rather than a `String`, because not everything a card is about is the
 * sender's text: a day is written the way the reader's language writes it, and a schedule is a noun
 * this app words itself.
 */
sealed interface CardSubject {

    /** A day, as ISO text on the wire. The reader's language says it, with its weekday. */
    data class IsoDate(val iso: String) : CardSubject

    /** A noun the app words itself, by its string [key], in the reader's language. */
    data class Key(val key: String) : CardSubject

    /** What the sender wrote, shown as it is: an event's title, an expense's description. */
    data class Raw(val text: String) : CardSubject
}

/**
 * A chat card's wording decisions, without Compose (iPhone phase 4 part C, design §11 call 4): which
 * string each [ActivityKind] takes, what its sentence is about, and the sentence a card's push
 * carries ([text]). The thread reads the same decisions through `ActivityCardText` in `:shared-ui`,
 * which maps each key to its Compose Multiplatform resource, so a new [ActivityKind] reaches the
 * thread and the push at once.
 *
 * The whole point of the payload is that the sentence is composed by the reader, not the sender:
 * the two parents may not read the same language.
 */
object CardWording {

    /**
     * The string key for [kind]. Each takes the entity's own name as `%1$s`; it is passed as a
     * format argument and never concatenated, because word order differs by language. The settle-up
     * kinds are worded through [settleUpKey], the money kinds take two arguments ([takesMoney]).
     *
     * `when` with **no `else`**, deliberately. Adding a member to [ActivityKind] then fails to
     * compile until it has been given a sentence — and, because every key must exist in all five
     * locale files, until it has been translated. A silent fallback is how an announcement ends up
     * saying nothing.
     */
    // One branch per kind, with no `else` (above): a `when` over the whole enum is as complex as
    // the enum is long, and that is the point.
    @Suppress("CyclomaticComplexMethod")
    fun keyFor(kind: ActivityKind): String = when (kind) {
        ActivityKind.EVENT_CREATED -> "activity_event_created"
        ActivityKind.EVENT_UPDATED -> "activity_event_updated"
        ActivityKind.EVENT_DELETED -> "activity_event_deleted"
        ActivityKind.EVENT_ACCEPTED -> "activity_event_accepted"
        ActivityKind.EVENT_DECLINED -> "activity_event_declined"
        ActivityKind.CHANGE_REQUESTED -> "activity_change_requested"
        ActivityKind.CHANGE_ACCEPTED -> "activity_change_accepted"
        ActivityKind.CHANGE_DECLINED -> "activity_change_declined"
        ActivityKind.EXPENSE_ADDED -> "activity_expense_added"
        ActivityKind.EXPENSE_UPDATED -> "activity_expense_updated"
        ActivityKind.EXPENSE_DELETED -> "activity_expense_deleted"
        ActivityKind.EXPENSE_QUESTIONED -> "activity_expense_questioned"
        ActivityKind.DAY_SWAP_OFFERED -> "activity_day_swap_offered"
        ActivityKind.DAY_SWAP_ACCEPTED -> "activity_day_swap_accepted"
        ActivityKind.DAY_SWAP_DECLINED -> "activity_day_swap_declined"
        ActivityKind.CUSTODY_PROPOSED -> "activity_custody_proposed"
        ActivityKind.CUSTODY_ACCEPTED -> "activity_custody_accepted"
        ActivityKind.CUSTODY_DECLINED -> "activity_custody_declined"
        ActivityKind.REQUEST_LAST_CHANCE -> "activity_request_last_chance"
        ActivityKind.REQUEST_UNANSWERED -> "activity_request_unanswered"
        // As the sender's own phone says it; [settleUpKey] picks by who reads it, and both take the
        // month and the amount rather than one subject.
        ActivityKind.SETTLE_UP_OWED -> SETTLE_UP_OWED_TO_YOU
        ActivityKind.SETTLE_UP_OWING -> SETTLE_UP_YOU_OWE
        // Plain sentences, like a custody proposal's: the card cannot reach the reader's parent
        // names, and a share without a name would not say whose it is. The fallback text, which the
        // export prints, carries the shares in the proposer's first person.
        ActivityKind.SPLIT_PROPOSED -> "activity_split_proposed"
        ActivityKind.SPLIT_ACCEPTED -> "activity_split_accepted"
        ActivityKind.SPLIT_DECLINED -> "activity_split_declined"
        ActivityKind.SPLIT_WITHDRAWN -> "activity_split_withdrawn"
        ActivityKind.DAY_SWAP_WITHDRAWN -> "activity_day_swap_withdrawn"
        ActivityKind.CUSTODY_WITHDRAWN -> "activity_custody_withdrawn"
        ActivityKind.STICKER_SENT -> "activity_sticker_sent"
        // The payment and alimony cards take two arguments ([takesMoney]); these are their
        // sentences, so the mapping stays total.
        ActivityKind.SETTLE_UP_PAID -> "activity_settle_up_paid"
        ActivityKind.SETTLE_UP_CONFIRMED -> "activity_settle_up_confirmed"
        ActivityKind.SUPPORT_PROPOSED -> "activity_support_proposed"
        ActivityKind.SUPPORT_ACCEPTED -> "activity_support_accepted"
        ActivityKind.SUPPORT_DECLINED -> "activity_support_declined"
        ActivityKind.SUPPORT_WITHDRAWN -> "activity_support_withdrawn"
        ActivityKind.SUPPORT_PAID -> "activity_support_paid"
        ActivityKind.SUPPORT_CONFIRMED -> "activity_support_confirmed"
    }

    /**
     * Whether [kind]'s sentence takes the card's money as two arguments rather than one subject: a
     * payment (`%1$s` the month, `%2$s` the amount) or an alimony agreement (`%1$s` the amount,
     * `%2$s` the day of the month).
     */
    fun takesMoney(kind: ActivityKind): Boolean =
        kind.isPayment || kind == ActivityKind.SUPPORT_PROPOSED || kind == ActivityKind.SUPPORT_ACCEPTED

    /**
     * A settle-up card's sentence on this phone: who owes is a matter of who reads it, so the
     * sentence is chosen by [ActivityKind.readerPays]. Takes the month as `%1$s` and the amount as
     * `%2$s`.
     *
     * @param readerIsSender Whether this phone sent the card.
     */
    fun settleUpKey(kind: ActivityKind, readerIsSender: Boolean): String =
        if (kind.readerPays(readerIsSender)) SETTLE_UP_YOU_OWE else SETTLE_UP_OWED_TO_YOU

    /**
     * What [card]'s sentence is about, decided by the entity the card names:
     *
     * - a day swap is its date, which the reader's language writes (`title` is the ISO date; an
     *   older build prints it as it is);
     * - a custody proposal is worded — it has no name of its own, and a request's card carries a
     *   blank title. The cards that existed before carry the family id there, and their sentences
     *   take no argument, so the worded subject changes nothing they show;
     * - an expense split proposal is worded the same way, for the same reason;
     * - a sticker is its sender's neutral phrase on both phones ([stickerPhraseKey]): the card is the
     *   thread's record of what was sent, and the Family tab words it for its receiver;
     * - anything else is its `title`, as the sender wrote it.
     *
     * `when` with no `else`, like [keyFor]: a new [ActivityEntityType] has to be given a subject
     * before this builds.
     */
    fun subjectOf(card: ActivityAnnouncement): CardSubject = when (card.entityType) {
        ActivityEntityType.DAY_SWAP -> CardSubject.IsoDate(card.title)
        ActivityEntityType.CUSTODY_PROPOSAL -> CardSubject.Key("activity_subject_custody_schedule")
        ActivityEntityType.SPLIT_PROPOSAL -> CardSubject.Key("activity_subject_expense_split")
        ActivityEntityType.STICKER -> CardSubject.Key(stickerPhraseKey(card.title))
        ActivityEntityType.PAYMENT,
        ActivityEntityType.SUPPORT_AGREEMENT,
        ActivityEntityType.EVENT,
        ActivityEntityType.CHANGE_REQUEST,
        ActivityEntityType.EXPENSE -> CardSubject.Raw(card.title)
    }

    /**
     * The neutral phrase of the sticker whose wire value is [wire] — what `StickerText.phrase` (in
     * `:shared-ui`) answers for its kind, the sentence a receiver whose role is "parent" reads — or
     * the generic "a sticker" for a kind this build does not know. `StickerKind` lives in `:shared`,
     * which this module cannot read, so its wire values are spelled here; `CardWordingStickerTest`
     * (the app's) holds the two to every kind.
     */
    fun stickerPhraseKey(wire: String?): String = when (wire) {
        "great_parent" -> "sticker_great_parent_parent"
        "on_time" -> "sticker_on_time_parent"
        "regular" -> "sticker_regular_parent"
        // Each key spelled out, not built from the wire value: `push-keys.txt` is checked against the
        // keys this file names (`tools/test/push-keys.test.js`).
        "thank_you" -> "sticker_thank_you"
        "kids_happy" -> "sticker_kids_happy"
        "good_team" -> "sticker_good_team"
        "well_done" -> "sticker_well_done"
        "patience" -> "sticker_patience"
        "flexible" -> "sticker_flexible"
        else -> "activity_subject_sticker"
    }

    /**
     * A chat card's sentence for its push, worded on the receiving phone in [strings]' language
     * (notification audit, 9 November 2026), or null when this build cannot word it — the push then
     * shows the card's preview, as a push from an older sender does.
     *
     * The thread's sentence — `MessageItem` with `settleUpCardText` and `moneyCardText` — composed
     * without Compose. A push is always the co-parent's card, so its reader is never the sender.
     */
    fun text(card: ActivityAnnouncement, strings: WordingStrings, clock: ReaderClock): String? {
        if (card.kind.isSettleUp) {
            val month = card.whenIso?.let(clock::monthOfDay) ?: card.title
            val key = settleUpKey(card.kind, readerIsSender = false)
            return strings.orNull(key, month, clock.amount(card.amount, card.currency))
        }
        val key = keyFor(card.kind)
        if (takesMoney(card.kind)) {
            val amount = clock.amount(card.amount, card.currency)
            return when (card.kind) {
                // `title` is the day of the month the agreement names.
                ActivityKind.SUPPORT_PROPOSED, ActivityKind.SUPPORT_ACCEPTED -> strings.orNull(key, amount, card.title)
                // `title` is the month, `2026-10`.
                else -> strings.orNull(key, clock.monthOfMonth(card.title) ?: card.title, amount)
            }
        }
        return subjectText(card, strings, clock)?.let { strings.orNull(key, it) }
    }

    /** What [card]'s sentence is about, worded; null when the app has no such noun. */
    private fun subjectText(card: ActivityAnnouncement, strings: WordingStrings, clock: ReaderClock): String? =
        when (val about = subjectOf(card)) {
            is CardSubject.IsoDate -> clock.dayWithWeekday(about.iso)
            is CardSubject.Key -> strings.orNull(about.key)
            is CardSubject.Raw -> about.text
        }

    /**
     * [key] worded with [args], or null when the strings hold no such key — a key the release
     * build's resource shrinker removed would read as unknown, and its push shows the preview rather
     * than crash.
     */
    private fun WordingStrings.orNull(key: String, vararg args: Any): String? =
        try {
            string(key, *args)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            null
        }

    private const val SETTLE_UP_OWED_TO_YOU = "activity_settle_up_owed_to_you"
    private const val SETTLE_UP_YOU_OWE = "activity_settle_up_you_owe"
}
