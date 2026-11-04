package com.coparently.app.presentation.chat

import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType

/**
 * Where a tap on a chat bubble leads, decided from the message alone.
 *
 * Kept out of `MessageItem` so the rule is written down once and tested on the JVM. A card about an
 * event used to share the change-request route: "New event: …" opened the inbox highlighted on
 * that event, the inbox found no request for it and said "That request is no longer open", and the
 * event itself — its time, its description — was never shown. [Event] is that card's own target
 * now; a change-request card still opens the inbox exactly as before.
 */
sealed interface ChatCardTarget {

    /**
     * The change-request inbox with the request about [eventId] highlighted. The card names the
     * event rather than the request, and the inbox resolves it to the newest request the reader
     * can still act on (`ChangeRequestHighlight.forEvent`).
     */
    data class ChangeRequest(val eventId: String) : ChatCardTarget

    /**
     * The change-request inbox with nothing highlighted: a day-swap card, whose entity is a date
     * that the inbox's event-id argument would misread as a request that no longer exists, and an
     * expense split card, whose proposal the inbox lists with its answers (October 2026).
     */
    data object Inbox : ChatCardTarget

    /**
     * The event [eventId] itself: its preview over the thread, or the inbox while it still waits
     * on the reader's answer (`EventCardOutcome`).
     */
    data class Event(val eventId: String) : ChatCardTarget

    /** The family's Alimony screen: an alimony card, a proposal or a month paid (25 October 2026). */
    data object Support : ChatCardTarget

    companion object {

        /**
         * Where a tap on [message] leads, or null for a bubble that opens nothing: an ordinary
         * message, an "event removed" card (there is no event left to open), and an expense or
         * custody-proposal card, which have no destination reachable from chat. A bubble that
         * looks tappable and does nothing is worse than one that plainly is not a button.
         *
         * `when` over the entity type with no `else`, so a new [ActivityEntityType] does not
         * compile until its cards have been given a destination, or deliberately none.
         */
        fun of(message: Message): ChatCardTarget? {
            if (message.messageType == MessageType.EVENT_LINK) {
                // A change-request card from before announcements: it carries the event id in
                // `attachments`, and the inbox resolves that to the request.
                return message.attachments.firstOrNull()?.let { ChangeRequest(it) }
            }
            val card = message.activity ?: return null
            return when (card.entityType) {
                ActivityEntityType.EVENT ->
                    if (card.kind == ActivityKind.EVENT_DELETED) null else Event(card.entityId)
                ActivityEntityType.CHANGE_REQUEST -> ChangeRequest(card.entityId)
                ActivityEntityType.DAY_SWAP, ActivityEntityType.SPLIT_PROPOSAL -> Inbox
                ActivityEntityType.EXPENSE, ActivityEntityType.CUSTODY_PROPOSAL -> null
                // A sticker is the whole message; a settle-up payment has no screen of its own.
                ActivityEntityType.STICKER -> null
                ActivityEntityType.PAYMENT -> if (card.kind.isSupport) Support else null
                ActivityEntityType.SUPPORT_AGREEMENT -> Support
            }
        }
    }
}
