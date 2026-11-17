package com.coparently.app.e2e

import com.coparently.app.domain.notification.RequestClockScheduler

/**
 * A phone's [RequestClockScheduler] that cancels nothing and counts how often it was asked to
 * (phase 3a). No phone schedules a step of a request's clock since iPhone phase 4 part C — the
 * server sends the reminders — so all that is left to a phone is cancelling an older build's jobs
 * at sign-out. Both parents run in one process, so each [EmulatorParent] has its own.
 */
class RecordingClockScheduler : RequestClockScheduler {

    @Volatile
    private var cancellations = 0

    /** How many times every job was cancelled, as the production scheduler does at sign-out. */
    val cancelled: Int
        get() = cancellations

    override fun cancelAll() {
        cancellations++
    }
}
