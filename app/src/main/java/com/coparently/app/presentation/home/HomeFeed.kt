package com.coparently.app.presentation.home

import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.model.Event
import java.time.Duration
import java.time.LocalDateTime

/**
 * Kind of change surfaced on the home dashboard.
 */
enum class ActivityKind {
    EVENT_CREATED,
    EVENT_UPDATED,
    PICKUP_CONFIRMED,
    CHANGE_REQUESTED,

    /** The co-parent's answer to one of this parent's requests (phase 2). */
    ANSWER
}

/**
 * One entry in the "recent changes the co-parent made" feed.
 *
 * @property answer The answer an [ActivityKind.ANSWER] row is about; null for every other kind.
 * @property requestStatus Where a change request stands, for a [ActivityKind.CHANGE_REQUESTED] row;
 *   null for every other kind. Only a pending one still asks the reader to review it (UX audit M2).
 */
data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val timestamp: LocalDateTime,
    val eventId: String,
    val isChangeRequest: Boolean,
    val answer: HistoryEntry? = null,
    val requestStatus: ChangeRequestStatus? = null
)

/**
 * The co-parent's feed on Home: the events they created or changed, the changes they asked for,
 * and — since phase 2 (spec §6.1) — their answers to this parent's requests, newest first.
 *
 * Pure, so the rules are tested without a repository: an answer is one row, and the event write
 * that answering made (accepting an event stamps it; accepting a new time moves it) is not a
 * second one.
 */
object HomeFeed {

    /** How close two timestamps must be to read as one write. */
    internal val NEAR_THRESHOLD: Duration = Duration.ofSeconds(2)

    /**
     * How close an event's last write may be to an answer about it and still be that answer's own
     * write: accepting a change request moves the event a moment before it marks the request.
     */
    private val ANSWER_EDIT_WINDOW: Duration = Duration.ofMinutes(1)

    /**
     * Up to [maxItems] of what [partnerId] did, newest first.
     *
     * @param events Every event on this phone; only the co-parent's non-private writes count.
     * @param changeRequests The family on screen's change requests; the co-parent's own count.
     * @param history The family on screen's history; the co-parent's answers to this parent count.
     */
    fun items(
        events: List<Event>,
        changeRequests: List<ChangeRequest>,
        history: List<HistoryEntry>,
        partnerId: String,
        maxItems: Int
    ): List<ActivityItem> {
        val answers = history
            .filter { entry ->
                // Their answers only: a withdrawal is not an answer, and "No answer" is nothing they did.
                // A custody or split decision is told by its own card, banner and pop-up.
                entry.decidedBy == partnerId &&
                    entry.askedBy != partnerId &&
                    entry.outcome.isAnswer &&
                    entry.subject != RequestSubject.CustodySchedule &&
                    entry.subject != RequestSubject.SplitRatio
            }
            .map { it.toActivityItem() }

        val eventItems = events
            .filter { !it.isPrivate && it.lastModifiedBy == partnerId }
            .filterNot { event -> answers.any { it.answered(event) } }
            .map { it.toActivityItem() }

        val requestItems = changeRequests
            .filter { it.requestedBy == partnerId }
            .map { request ->
                ActivityItem(
                    id = "cr_${request.id}",
                    kind = ActivityKind.CHANGE_REQUESTED,
                    title = request.eventTitle,
                    timestamp = request.createdAt,
                    eventId = request.eventId,
                    isChangeRequest = true,
                    requestStatus = request.status
                )
            }

        return (eventItems + requestItems + answers)
            .sortedByDescending { it.timestamp }
            .take(maxItems)
    }

    /** Whether [event]'s last write is the one this answer made. */
    private fun ActivityItem.answered(event: Event): Boolean =
        eventId == event.id && Duration.between(timestamp, event.updatedAt).abs() <= ANSWER_EDIT_WINDOW

    private fun HistoryEntry.toActivityItem(): ActivityItem {
        val event = when (val about = subject) {
            is RequestSubject.NewTime -> about.eventId to about.eventTitle
            is RequestSubject.NewEvent -> about.eventId to about.eventTitle
            is RequestSubject.DaySwap, RequestSubject.CustodySchedule, RequestSubject.SplitRatio -> "" to ""
        }
        return ActivityItem(
            id = "ans_$key",
            kind = ActivityKind.ANSWER,
            title = event.second,
            timestamp = decidedAt,
            eventId = event.first,
            isChangeRequest = false,
            answer = this
        )
    }

    private fun Event.toActivityItem(): ActivityItem {
        val kind = when {
            pickupConfirmedBy != null && pickupConfirmedAt != null &&
                Duration.between(pickupConfirmedAt, updatedAt).abs() <= NEAR_THRESHOLD ->
                ActivityKind.PICKUP_CONFIRMED
            Duration.between(createdAt, updatedAt).abs() <= NEAR_THRESHOLD ->
                ActivityKind.EVENT_CREATED
            else -> ActivityKind.EVENT_UPDATED
        }
        return ActivityItem(
            id = "ev_${id}_$updatedAt",
            kind = kind,
            title = title,
            timestamp = updatedAt,
            eventId = id,
            isChangeRequest = false
        )
    }
}
