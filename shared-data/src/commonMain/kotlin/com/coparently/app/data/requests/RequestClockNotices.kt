package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.RequestDeadline

/**
 * The local notices a request's clock posts on this phone (phase 3a, spec §4.3–§4.4, §5.3; MON-24
 * phase 2, Task 8h): a reminder to the answering parent, and to the proposer that the deadline
 * passed and that the request ended with no answer.
 *
 * [RequestClockSteps] decides *whether* a notice is due; the platform words and posts it. On
 * Android that is the app's `RequestNotices`: the notification manager, the schedule channel, a
 * `BigTextStyle` beside the content text (CLAUDE.md item 15), the strings in this phone's language,
 * and the other parent named by name, never by role. Posting is quiet when the platform does not
 * allow notifications. Only a reminder reports whether it was shown, because only a reminder is
 * counted (the proposer's card says how many the other parent saw; notification audit, 9 November
 * 2026).
 */
interface RequestClockNotices {

    /**
     * Reminds the answering parent that the proposer waits for an answer until the deadline.
     *
     * @return Whether the reminder was shown: false while notifications are not allowed or the
     *   channel is muted, when the proposer's card must not count it.
     */
    suspend fun remind(clock: RequestDeadline): Boolean

    /** Tells the proposer that the answering parent did not answer by the deadline. */
    suspend fun deadlinePassed(clock: RequestDeadline)

    /** Tells the proposer that the request is closed with no answer. */
    suspend fun noAnswer(clock: RequestDeadline)
}
