package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.RequestDeadline

/**
 * The local notice a request's clock posts on this phone (phase 3a, spec §4.3, §5.3; MON-24 phase 2,
 * Task 8h): a reminder to the answering parent. What the proposer is told — the deadline passed, the
 * request ended with no answer — is pushed by the server since MON-25 (`request_deadline_passed`,
 * `request_no_answer`), worded by `PushNotifier`.
 *
 * [RequestClockSteps] decides *whether* a notice is due; the platform words and posts it. On
 * Android that is the app's `RequestNotices`: the notification manager, the schedule channel, a
 * `BigTextStyle` beside the content text (CLAUDE.md item 15), the strings in this phone's language,
 * and the other parent named by name, never by role. Posting is quiet when the platform does not
 * allow notifications. A reminder reports whether it was shown, because it is counted (the
 * proposer's card says how many the other parent saw; notification audit, 9 November 2026).
 */
interface RequestClockNotices {

    /**
     * Reminds the answering parent that the proposer waits for an answer until the deadline.
     *
     * @return Whether the reminder was shown: false while notifications are not allowed or the
     *   channel is muted, when the proposer's card must not count it.
     */
    suspend fun remind(clock: RequestDeadline): Boolean
}
