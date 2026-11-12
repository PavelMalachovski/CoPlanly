package com.coparently.app.presentation.home

import com.coparently.app.domain.changerequests.RequestOutcome

/** The status an Updates row leads with, on its disc. */
internal enum class UpdateMark {
    /** The co-parent accepted one of this parent's requests, or confirmed a pickup. */
    ACCEPTED,

    /** The co-parent declined one of this parent's requests. */
    DECLINED,

    /** The co-parent added an event. */
    ADDED,

    /** The co-parent changed an event. */
    CHANGED,

    /** The co-parent asked for a change. */
    ASKED
}

/**
 * Which mark an Updates row carries: an answer by its outcome, anything else by its kind. A
 * confirmed pickup is a good status, so it takes the accepted check.
 */
internal fun updateMark(item: ActivityItem): UpdateMark {
    val answer = item.answer
    return when {
        answer != null && answer.outcome == RequestOutcome.ACCEPTED -> UpdateMark.ACCEPTED
        answer != null -> UpdateMark.DECLINED
        else -> when (item.kind) {
            ActivityKind.EVENT_CREATED -> UpdateMark.ADDED
            ActivityKind.EVENT_UPDATED -> UpdateMark.CHANGED
            ActivityKind.CHANGE_REQUESTED -> UpdateMark.ASKED
            ActivityKind.PICKUP_CONFIRMED, ActivityKind.ANSWER -> UpdateMark.ACCEPTED
        }
    }
}
