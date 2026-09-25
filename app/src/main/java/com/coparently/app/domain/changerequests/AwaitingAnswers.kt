package com.coparently.app.domain.changerequests

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.DaySwapInbox
import com.coparently.app.domain.model.Event
import java.time.LocalDate

/**
 * What waits in the change-request inbox for one parent's answer, counted the way the inbox
 * shows it.
 *
 * Home's "Awaiting your answer" row and the calendar's change-request banner both open that
 * inbox, and both print a number. They used to count it differently — the banner left out the
 * events the co-parent added that still need an answer, and counted a swap offered for a week as
 * seven — so the UI tour showed 6 on Home and 2 on the calendar for the same inbox. Both read
 * this object now.
 *
 * A custody-pattern proposal is not counted here: the calendar raises its own banner for it, and
 * Home adds it to its row itself.
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
    fun eventCount(events: List<Event>, uid: String): Int =
        if (uid.isEmpty()) 0 else events.count { it.acceptance.isPending && it.createdByFirebaseUid != uid }

    /**
     * Everything the inbox asks [uid] to answer: incoming change requests, day-swap offers and
     * pending events.
     *
     * @param pendingChangeRequests Incoming change requests still pending, as the repository
     *   counts them.
     */
    fun count(
        pendingChangeRequests: Int,
        overrides: Map<String, DayOverride>,
        events: List<Event>,
        uid: String,
        today: LocalDate
    ): Int = pendingChangeRequests + swapOffers(overrides, uid, today).size + eventCount(events, uid)
}
