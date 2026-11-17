package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.repository.RequestDeadlineRepository

/** How a step of a request's clock went. */
enum class StepResult {
    /** Done, or nothing to do: the job is finished. */
    DONE,

    /** Something could not be read or written; the job is tried again shortly, a few times. */
    RETRY
}

/**
 * The step of a request's clock that runs on a phone (phase 3a, spec §4.3), run by
 * `RequestClockWorker` when its job comes due: **a reminder**, on the answering parent's phone.
 * It is posted and counted only while the request is open and its record still waits, and not
 * when the job runs more than half an hour after the time it was planned for — a phone that was
 * off must not remind at night (ruling 6) — nor when it runs in the quiet hours of the phone's
 * zone now ([ReminderPlan.isQuietAt]): Doze can hold a reminder planned for 20:50 past 21:00, and
 * the parent may have travelled since the plan.
 *
 * **The deadline notice and the end are the server's since MON-25** (`functions/request-clock-steps.js`):
 * it tells the proposer the deadline passed, closes a request nobody answered as "No answer", posts
 * its card and withdraws a proposal, so no phone runs those steps any more. A job an older build
 * left queued for either finishes without acting (`RequestClockWorker`).
 *
 * The step reads the clock again and acts for the family it names, never the family on screen
 * (spec §5.6); `RequestRecords` reads a family this parent has left as gone. It reads this phone's
 * copy of the record: a wrong one costs a notice. **A clock or a record that cannot be read is
 * [StepResult.RETRY]**, never "nothing to do": the repository reads any failure as no clock, so a
 * step that gave up on it would be lost. A reminder whose retries all fail is given up; the
 * keeper's sweep on the next sync arms the reminders still ahead again.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8h). What only the platform can do stays behind two
 * seams it binds: posting a notice ([RequestClockNotices]; the app's `RequestNotices`) and keeping
 * the jobs (`RequestClockScheduler`; the app's `WorkRequestClockScheduler`, whose worker runs this).
 */
class RequestClockSteps(
    private val repository: RequestDeadlineRepository,
    private val records: RequestRecords,
    private val notices: RequestClockNotices,
    private val time: RequestTime
) {

    /** Reminds the answering parent of clock [clockId], planned for [plannedAtMillis]. */
    suspend fun reminder(clockId: String, plannedAtMillis: Long): StepResult {
        val clock = repository.get(clockId) ?: return StepResult.RETRY
        val now = time.nowMillis()
        val due = clock.responderUid == repository.signedInUid() &&
            now - plannedAtMillis <= LATE_REMINDER_MILLIS &&
            !ReminderPlan.isQuietAt(now, time.zone()) &&
            RequestClock.stateOf(clock, now) == ClockState.OPEN
        if (!due) return StepResult.DONE
        return when (records.look(clock).status) {
            RecordStatus.UNKNOWN -> StepResult.RETRY
            RecordStatus.PENDING -> {
                // Counted only when shown: the proposer's card says how many reminders the other
                // parent saw, and a phone with notifications off saw none.
                if (notices.remind(clock)) repository.reportReminderShown(clock)
                StepResult.DONE
            }
            RecordStatus.ANSWERED, RecordStatus.GONE -> StepResult.DONE
        }
    }

    private companion object {
        /** A reminder whose job runs later than this after its planned time is not shown (ruling 6). */
        const val LATE_REMINDER_MILLIS = 30 * 60_000L
    }
}
