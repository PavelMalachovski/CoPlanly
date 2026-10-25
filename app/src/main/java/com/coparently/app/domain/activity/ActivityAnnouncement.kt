package com.coparently.app.domain.activity

/**
 * What happened, as a fact rather than a sentence.
 *
 * **The two parents may not read the same language.** A card composed in the sender's locale is a
 * card the recipient may not be able to read, and the sender's language is not a property of the
 * message. So an announcement carries the facts and the *reader's* device turns them into a
 * sentence, through `stringResource`, in its own language.
 *
 * This replaces `RequestChangeViewModel.postChatMessage`, which built its card by concatenating
 * hardcoded English inside a ViewModel — the class of defect CLAUDE.md tracks, and one that would
 * have filled a Russian phone's chat with English the moment it was generalised to every change.
 *
 * @property kind What happened. Each member renders as one string resource; the mapping is a
 *   `when` with no `else`, so adding a member is a compile error until it is translated.
 * @property entityType What it happened to, so a tap can route without parsing [kind].
 * @property entityId The row to open on tap.
 * @property title The entity's own name — an event's title, an expense's description. Passed as a
 *   format argument, never concatenated into the sentence, because word order differs by language.
 * @property whenIso ISO date-time the change is about, where one applies. Formatted by the reader
 *   in their own locale and zone.
 * @property amount Formatted amount for an expense, or null. Already carries its currency symbol.
 *   On a `SPLIT_PROPOSED` card it is the proposed share of slot 1 in basis points, as decimal text
 *   (`"3500"` for 35 %), with no currency.
 * @property currency ISO currency code, or null. Kept beside [amount] so a reader can group by it
 *   without parsing the formatted string — the app never converts between currencies.
 * @property planCitation On a `CUSTODY_PROPOSED` card only: the parenting-plan answer the proposal
 *   was built from (MON-21), as the same `PlanCitationCodec` string the custody document carries
 *   under `proposalPlanCitation`, or null. Copied here because the custody document forgets it the
 *   moment the proposal is answered, while a message is immutable — so this is what lets the
 *   export (MON-3) say which answer a proposal cited, months later. Kept verbatim, never decoded
 *   on read: a string this build cannot parse still survives a round trip through Room.
 */
data class ActivityAnnouncement(
    val kind: ActivityKind,
    val entityType: ActivityEntityType,
    val entityId: String,
    val title: String,
    val whenIso: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val planCitation: String? = null
) {
    /**
     * The payload as a Firestore sub-map, keys stable from the start.
     *
     * A co-parent on an older build must keep reading the `messages` collection, so renaming a
     * key later is not available. Absent optionals are omitted rather than written as empty
     * strings, matching every other sub-map in this schema.
     */
    fun toMap(): Map<String, Any> = buildMap {
        put("kind", kind.name)
        put("entityType", entityType.name)
        put("entityId", entityId)
        put("title", title)
        whenIso?.let { put("whenIso", it) }
        amount?.let { put("amount", it) }
        currency?.let { put("currency", it) }
        planCitation?.let { put("planCitation", it) }
    }

    companion object {
        /**
         * The sub-map as a payload, or null when it cannot describe one.
         *
         * A payload whose [kind] or [entityType] this build does not recognise is **dropped**,
         * not defaulted. The message still renders: `Message.content` carries a plain-text
         * fallback for exactly this case, so an older build shows something rather than an empty
         * bubble or, worse, a confident sentence about the wrong thing.
         */
        fun fromMap(map: Map<*, *>?): ActivityAnnouncement? {
            if (map == null) return null
            val kind = (map["kind"] as? String)
                ?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } }
                ?: return null
            val entityType = (map["entityType"] as? String)
                ?.let { name -> ActivityEntityType.entries.firstOrNull { it.name == name } }
                ?: return null
            val entityId = (map["entityId"] as? String)?.takeIf { it.isNotBlank() } ?: return null

            return ActivityAnnouncement(
                kind = kind,
                entityType = entityType,
                entityId = entityId,
                title = (map["title"] as? String).orEmpty(),
                whenIso = (map["whenIso"] as? String)?.takeIf { it.isNotBlank() },
                amount = (map["amount"] as? String)?.takeIf { it.isNotBlank() },
                currency = (map["currency"] as? String)?.takeIf { it.isNotBlank() },
                planCitation = (map["planCitation"] as? String)?.takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * What an announcement is about, so a tap can route without inspecting [ActivityKind].
 *
 * A budget is deliberately absent: it is a private planning tool, not a shared fact, and a card
 * per budget edit would be noise in the one place both parents look.
 */
enum class ActivityEntityType {
    EVENT,
    EXPENSE,
    CHANGE_REQUEST,
    DAY_SWAP,
    CUSTODY_PROPOSAL,

    /**
     * An expense split proposal (October 2026): `entityId` is the family's id — its
     * `family_settings` document — and `title` is blank, since a split has no name of its own.
     */
    SPLIT_PROPOSAL,

    /**
     * A sticker one parent sent the other (owner decision, 25 October 2026): `entityId` is the
     * `stickers/{id}` document, `title` the kind's wire value. A tap leads nowhere.
     */
    STICKER,

    /**
     * A payment between the two parents (25 October 2026): `entityId` is the `payments/{id}`
     * document, `title` the month (`2026-10`), `amount` and `currency` what was paid.
     */
    PAYMENT,

    /**
     * The family's alimony agreement (25 October 2026): `entityId` is the family's id — its
     * `support_agreements` document.
     */
    SUPPORT_AGREEMENT
}

/**
 * Everything worth telling the co-parent about.
 *
 * Each member renders as exactly one string resource, and the mapping is a `when` with no `else` —
 * so adding a member here does not compile until it has been given a sentence in all five
 * locales. That is deliberate: a silent fallback is how an announcement ends up saying nothing.
 */
enum class ActivityKind {
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_DELETED,
    EVENT_ACCEPTED,
    EVENT_DECLINED,
    CHANGE_REQUESTED,
    CHANGE_ACCEPTED,
    CHANGE_DECLINED,
    EXPENSE_ADDED,
    EXPENSE_UPDATED,
    EXPENSE_DELETED,

    /**
     * The reader questions an expense the co-parent recorded (second audit). The payload is
     * [EXPENSE_ADDED]'s. A tap leads nowhere: the expense is the co-parent's to change or delete,
     * and the reason is said in the thread beside the card.
     */
    EXPENSE_QUESTIONED,
    DAY_SWAP_OFFERED,
    DAY_SWAP_ACCEPTED,
    DAY_SWAP_DECLINED,
    CUSTODY_PROPOSED,
    CUSTODY_ACCEPTED,
    CUSTODY_DECLINED,

    /**
     * The proposer gave a request a last chance (phase 3a): it stays open, with a new end.
     *
     * Announced about any of the four kinds of request that wait for an answer, and carries the
     * request's **own** [ActivityEntityType], so a tap goes where the request's other cards go
     * (`ChatCardTarget`) and no rule of its own is needed:
     *
     * - a day swap: `DAY_SWAP`, the first ISO date as both `entityId` and `title`;
     * - a change request: `CHANGE_REQUEST`, the event's id and its title;
     * - an event waiting for acceptance: `EVENT`, the event's id and its title;
     * - a custody proposal: `CUSTODY_PROPOSAL`, the family's id and a blank `title` — a proposal
     *   has no name of its own, so the reader words it;
     * - an expense split proposal: `SPLIT_PROPOSAL`, the family's id and a blank `title`, likewise.
     *
     * A record in the thread, never a push of its own: the last chance has its own push type, and
     * `notifyOfChatMessage` skips this kind.
     */
    REQUEST_LAST_CHANCE,

    /**
     * A request ended with no answer (phase 3a). The payload is [REQUEST_LAST_CHANCE]'s.
     *
     * A record in the thread that pushes nobody — `notifyOfChatMessage` skips this kind: nothing
     * more is sent to the parent who did not answer once their last chance has run.
     */
    REQUEST_UNANSWERED,

    /**
     * Settle up, sent by the parent the month's balance is owed **to** (tester feedback, October
     * 2026). Posted by `SettleUpMessages` instead of a sentence in the composer, so the amount
     * cannot be edited. `entityType` is `EXPENSE` (a tap leads nowhere), `title` and `whenIso`
     * name the month (`2026-10`, `2026-10-01`), `amount` is a plain decimal with two places
     * (`450.00`, unlike an expense card's) and `currency` its ISO code: the payer's phone builds a
     * payment code from the two.
     */
    SETTLE_UP_OWED,

    /** Settle up, sent by the parent who **owes** the balance. The payload is [SETTLE_UP_OWED]'s. */
    SETTLE_UP_OWING,

    /**
     * An expense split was proposed (October 2026). `entityType` is `SPLIT_PROPOSAL`, `entityId` the
     * family's id, `title` blank, and `amount` the proposed share of slot 1 in basis points as
     * decimal text (`"3500"`), which the fallback text words in the proposer's first person.
     */
    SPLIT_PROPOSED,

    /** The co-parent accepted an expense split proposal. The payload is [SPLIT_PROPOSED]'s, without `amount`. */
    SPLIT_ACCEPTED,

    /** The co-parent declined an expense split proposal. The payload is [SPLIT_ACCEPTED]'s. */
    SPLIT_DECLINED,

    /**
     * The proposer took their expense split proposal back. The payload is [SPLIT_ACCEPTED]'s. Only
     * a withdrawal the parent made: one the clock makes at "No answer" posts [REQUEST_UNANSWERED].
     */
    SPLIT_WITHDRAWN,

    /**
     * The parent who offered a day swap took it back before it was answered (October 2026). The
     * payload is [DAY_SWAP_OFFERED]'s: `DAY_SWAP`, the first ISO date as `entityId` and `title`. A
     * card in the thread, pushed like any message; it has no push type of its own.
     */
    DAY_SWAP_WITHDRAWN,

    /**
     * The proposer took their custody proposal back before it was answered (October 2026). The
     * payload is [CUSTODY_PROPOSED]'s. Only a withdrawal the parent made: one the clock makes at "No
     * answer" posts [REQUEST_UNANSWERED]. Pushed like any message; no push type of its own.
     */
    CUSTODY_WITHDRAWN,

    /**
     * A sticker was sent (owner decision, 25 October 2026: stickers show in the chat). `STICKER`,
     * the sticker's id, and its kind's wire value as `title`; the reader's phone words it
     * (`StickerText`). Never pushed — the sticker has its own push, `sticker_received` — and never
     * in the export, which is the parents' communication record, not their courtesies.
     */
    STICKER_SENT,

    /**
     * The payer says they paid the month's settle-up (25 October 2026). `PAYMENT`, the payment's
     * id, the month as `title`, and the `amount` and `currency` paid (two decimals, like
     * [SETTLE_UP_OWED]). Sent by the payer.
     */
    SETTLE_UP_PAID,

    /** The parent paid says the money arrived. The payload is [SETTLE_UP_PAID]'s; sent by them. */
    SETTLE_UP_CONFIRMED,

    /**
     * An alimony agreement or change was proposed (25 October 2026). `SUPPORT_AGREEMENT`, the
     * family's id, the day of the month as `title` (`"5"`), and the monthly `amount` and `currency`.
     * The sender is the proposer; [readerPays] does not apply — the agreement names its payer.
     */
    SUPPORT_PROPOSED,

    /** The co-parent accepted the alimony proposal. The payload is [SUPPORT_PROPOSED]'s. */
    SUPPORT_ACCEPTED,

    /** The co-parent declined the alimony proposal. The payload is [SUPPORT_PROPOSED]'s. */
    SUPPORT_DECLINED,

    /** The proposer took the alimony proposal back. The payload is [SUPPORT_PROPOSED]'s. */
    SUPPORT_WITHDRAWN,

    /** The payer says this month's alimony is paid. The payload is [SETTLE_UP_PAID]'s. */
    SUPPORT_PAID,

    /** The parent paid says this month's alimony arrived. The payload is [SETTLE_UP_PAID]'s. */
    SUPPORT_CONFIRMED;

    /** Whether this is one of the two settle-up cards. */
    val isSettleUp: Boolean get() = this == SETTLE_UP_OWED || this == SETTLE_UP_OWING

    /** Whether this card says a payment was made or arrived: a settle-up's or a month of alimony's. */
    val isPayment: Boolean get() = this in PAYMENT_KINDS

    /** Whether this card is about alimony: the agreement, or a month of it paid. */
    val isSupport: Boolean get() = this in SUPPORT_KINDS

    /**
     * On a settle-up card, whether the parent reading it is the one who pays; false for every other
     * card. The sender of [SETTLE_UP_OWED] is paid, the sender of [SETTLE_UP_OWING] pays.
     *
     * @param readerIsSender Whether the reading phone sent the card.
     */
    fun readerPays(readerIsSender: Boolean): Boolean = when (this) {
        SETTLE_UP_OWED -> !readerIsSender
        SETTLE_UP_OWING -> readerIsSender
        else -> false
    }

    private companion object {
        val PAYMENT_KINDS = setOf(SETTLE_UP_PAID, SETTLE_UP_CONFIRMED, SUPPORT_PAID, SUPPORT_CONFIRMED)
        val SUPPORT_KINDS = setOf(
            SUPPORT_PROPOSED, SUPPORT_ACCEPTED, SUPPORT_DECLINED, SUPPORT_WITHDRAWN, SUPPORT_PAID, SUPPORT_CONFIRMED
        )
    }
}
