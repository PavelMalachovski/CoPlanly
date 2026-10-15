package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * The rules for what waits in the change-request inbox for one parent's answer.
 *
 * Home's Requests row and the calendar's change-request banner used to count that inbox
 * differently — the banner left out the events the co-parent added that still need an answer, and
 * counted a swap offered for a week as seven — so the UI tour showed 6 on Home and 2 on the
 * calendar for the same inbox. Both print [RequestInbox]'s count now, which is built from these
 * rules; a chat card about an event reads [eventAwaitsAnswerFrom] to decide where it opens.
 */
object AwaitingAnswers {

    /**
     * Day-swap offers waiting for [uid]'s answer, one per offer rather than per day, soonest
     * first — the inbox's own cards ([DaySwapInbox.groups]).
     */
    fun swapOffers(
        overrides: Map<String, DayOverride>,
        uid: String,
        today: LocalDate
    ): List<DaySwapGroup> =
        if (uid.isEmpty()) {
            emptyList()
        } else {
            DaySwapInbox.groups(overrides, today).filter { it.awaitsAnswerFrom(uid) }
        }

    /**
     * Events still waiting for [uid] to accept them. Only events somebody else created: the
     * creator cannot decide their own (`EventAcceptanceTransition`), so the inbox never lists
     * them for the creator.
     */
    fun eventCount(events: List<Event>, uid: String): Int = events.count { eventAwaitsAnswerFrom(it, uid) }

    /**
     * Whether [event] waits for [uid]'s answer: still pending, and created by somebody else. The
     * rule [eventCount] counts by, and the one a chat card about an event reads to decide whether
     * the inbox is where it opens — so a card never sends a parent to an inbox that does not list
     * the event. False while [uid] is not known yet (empty), as everything in this object is.
     */
    fun eventAwaitsAnswerFrom(event: Event, uid: String): Boolean =
        uid.isNotEmpty() && event.acceptance.isPending && event.createdByFirebaseUid != uid
}
