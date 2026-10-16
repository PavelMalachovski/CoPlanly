package com.coparently.app.e2e

import com.coparently.app.data.notification.RequestClockWork
import com.coparently.app.domain.notification.RequestClockScheduler

/**
 * A phone's [RequestClockScheduler] that schedules nothing and remembers every step it was asked
 * for, in order (phase 3a).
 *
 * The production scheduler hands each step to WorkManager to run at its time; a two-phone test
 * cannot wait hours for a deadline, so it reads what was scheduled here and runs a step itself
 * when it wants one. Both parents run in one process, so each [EmulatorParent] has its own.
 */
class RecordingClockScheduler : RequestClockScheduler {

    /**
     * One step a clock asked for.
     *
     * @property step Which step: [RequestClockWork.STEP_REMINDER], [RequestClockWork.STEP_DEADLINE]
     *   or [RequestClockWork.STEP_END].
     * @property clockId The clock the step is for.
     * @property atMillis When the step was to run, in epoch millis.
     */
    data class Scheduled(val step: String, val clockId: String, val atMillis: Long)

    private val calls = mutableListOf<Scheduled>()

    /** Every step asked for since the last [cancelAll], oldest first. */
    val scheduled: List<Scheduled>
        get() = synchronized(calls) { calls.toList() }

    override fun scheduleReminder(clockId: String, atMillis: Long) =
        record(RequestClockWork.STEP_REMINDER, clockId, atMillis)

    override fun scheduleDeadline(clockId: String, atMillis: Long) =
        record(RequestClockWork.STEP_DEADLINE, clockId, atMillis)

    override fun scheduleEnd(clockId: String, atMillis: Long) =
        record(RequestClockWork.STEP_END, clockId, atMillis)

    /** Forgets every step, as the production scheduler cancels them at sign-out. */
    override fun cancelAll() {
        synchronized(calls) { calls.clear() }
    }

    private fun record(step: String, clockId: String, atMillis: Long) {
        synchronized(calls) { calls += Scheduled(step, clockId, atMillis) }
    }
}
