package com.coparently.app.domain.changerequests

import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus

/**
 * Resolves the chat card's link target.
 *
 * The card posted into a thread carries the **event** id, not the request id
 * (`RequestChangeViewModel.postChatMessage` puts `event.id` in `attachments`), and one event can
 * collect several requests over its life. Tapping the card therefore has to pick one, and the
 * only useful pick is the one the reader can still act on.
 */
object ChangeRequestHighlight {

    /**
     * @param requests Every request the inbox knows about, in any order.
     * @param eventId Event the tapped card refers to.
     * @return The newest pending request for that event; failing that the newest request of any
     *   status, so a card for an already-answered proposal still explains itself; null when the
     *   event has no requests at all.
     */
    fun forEvent(requests: List<ChangeRequest>, eventId: String): ChangeRequest? {
        val forEvent = requests.filter { it.eventId == eventId }
        if (forEvent.isEmpty()) return null

        return forEvent
            .filter { it.status == ChangeRequestStatus.PENDING }
            .maxByOrNull { it.createdAt }
            ?: forEvent.maxByOrNull { it.createdAt }
    }
}
