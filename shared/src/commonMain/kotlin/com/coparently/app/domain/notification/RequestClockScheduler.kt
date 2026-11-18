package com.coparently.app.domain.notification

/**
 * The jobs a request's clock left on a phone (phase 3a, spec §5.3), which only an older build
 * schedules now: since MON-25 the deadline notice and the end run on the server, and since iPhone
 * phase 4 part C so do the reminders to the answering parent (`functions/request-clock-steps.js`,
 * the `request_reminder` push), so no phone schedules a step of its own.
 *
 * What is left is clearing what an older build queued: a job carries the clock's id, never the
 * request, and finishes without acting on this build, but it would still sit in the platform's
 * queue, and run, after the account it was queued for has signed out.
 *
 * Implemented in the data layer (WorkManager on Android); the iPhone never had such jobs.
 */
interface RequestClockScheduler {

    /**
     * Cancels every pending step on this device — for when the account's local data is wiped or its
     * session ends, so a previous account's clocks never act for the next one.
     */
    fun cancelAll()
}
