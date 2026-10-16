package com.coparently.app.domain.notification

/**
 * Schedules the steps of a request's clock to run when their time comes (phase 3a, spec §5.3):
 * a reminder to the answering parent, the deadline and the end on the proposer's phone.
 *
 * Implemented in the data layer (WorkManager); the domain layer only depends on this abstraction,
 * as it does for [ReminderScheduler]. A step is a job that carries the clock's id, never the
 * request: whoever runs it reads the clock and the request again, so a step whose request was
 * answered, withdrawn or replaced meanwhile finds nothing to do.
 *
 * Scheduling the same step for the same clock at the same time twice leaves one job while the first
 * is still pending or running; once it has finished, the same step can be scheduled again. So a
 * step cannot schedule its own job again from inside itself: that request is dropped while it runs.
 * A time that has already passed runs the step as soon as the system allows.
 */
interface RequestClockScheduler {

    /** Schedules a reminder planned for [atMillis]. One job per planned time. */
    fun scheduleReminder(clockId: String, atMillis: Long)

    /** Schedules the step that runs when the deadline [atMillis] passes. One job per clock. */
    fun scheduleDeadline(clockId: String, atMillis: Long)

    /**
     * Schedules the step that closes the request at [atMillis]. One job per time: the end moves
     * when a last chance is given, and the new end is another job beside the old one.
     */
    fun scheduleEnd(clockId: String, atMillis: Long)

    /**
     * Cancels every pending step on this device — for when the account's local data is wiped or its
     * session ends, so a previous account's clocks never act for the next one.
     */
    fun cancelAll()
}
