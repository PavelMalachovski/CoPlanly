package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ClockState
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How a step of a request's clock went. */
enum class StepResult {
    /** Done, or nothing to do: the job is finished. */
    DONE,

    /** Something could not be read or written; the job is tried again shortly, a few times. */
    RETRY
}

/**
 * The three steps of a request's clock (phase 3a, spec §4.3–§4.4), run by `RequestClockWorker`
 * when their jobs come due and by [RequestClockKeeper] for an end already due.
 *
 * - **A reminder**, on the answering parent's phone: posted and counted only while the request is
 *   open and its record still waits, and not when the job runs more than half an hour after the
 *   time it was planned for — a phone that was off must not remind at night (ruling 6).
 * - **The deadline**, on the proposer's phone: while a last chance can still be given and the
 *   record still waits, the proposer is told and the end is scheduled; when none can be given, the
 *   end runs at once.
 * - **The end**, on the proposer's phone: the clock is closed as "No answer", the proposer is told,
 *   the "No answer" card goes to the thread and a proposal is withdrawn so it stops blocking the
 *   other parent. An answered request is never closed (Review Focus 5).
 *
 * Every step reads the clock again and acts for the family it names, never the family on screen
 * (spec §5.6); `RequestRecords` reads a family this parent has left as gone. **The deadline and the
 * end decide on the server's copy of the record** (`RequestRecords.lookOnServer`): what they write
 * cannot be taken back, and this phone's copy may not yet hold the co-parent's answer. A server out
 * of reach is [StepResult.RETRY]; their jobs wait for a network before they start
 * (`RequestClockWork.needsNetwork`), so a phone offline at the deadline is told once it is back
 * online rather than spending its retries. A reminder reads this phone's copy: a wrong one costs a
 * notice. **A clock or a record that cannot be read is [StepResult.RETRY]**, never "nothing to do":
 * the repository reads any failure as no clock, so a step that gave up on it would be lost. A step
 * whose retries all fail online is given up: the keeper's sweep on the next sync arms the end again
 * (or runs it, once due) and the reminders still ahead, but not a deadline already passed — that
 * notice is then not shown, and the end still comes.
 *
 * **A step cannot schedule its own job again.** Jobs are kept by name, and a name that is running
 * is not enqueued twice, so a step that fired early answers [StepResult.RETRY] when its time is
 * moments away and [StepResult.DONE] otherwise, and leaves the rest to the keeper's sweep.
 */
@Singleton
class RequestClockSteps @Inject constructor(
    private val repository: RequestDeadlineRepository,
    private val records: RequestRecords,
    private val notices: RequestNotices,
    private val telling: RequestTelling,
    private val scheduler: RequestClockScheduler,
    private val time: RequestTime
) {

    /** Reminds the answering parent of clock [clockId], planned for [plannedAtMillis]. */
    suspend fun reminder(clockId: String, plannedAtMillis: Long): StepResult {
        val clock = repository.get(clockId) ?: return StepResult.RETRY
        val now = time.nowMillis()
        val due = clock.responderUid == repository.signedInUid() &&
            now - plannedAtMillis <= LATE_REMINDER_MILLIS &&
            RequestClock.stateOf(clock, now) == ClockState.OPEN
        if (!due) return StepResult.DONE
        return when (records.look(clock).status) {
            RecordStatus.UNKNOWN -> StepResult.RETRY
            RecordStatus.PENDING -> {
                notices.remind(clock)
                repository.reportReminderShown(clock)
                StepResult.DONE
            }
            RecordStatus.ANSWERED, RecordStatus.GONE -> StepResult.DONE
        }
    }

    /** Runs when clock [clockId]'s deadline passes, on the proposer's phone. */
    suspend fun deadline(clockId: String): StepResult {
        val clock = repository.get(clockId) ?: return StepResult.RETRY
        if (clock.proposerUid != repository.signedInUid()) return StepResult.DONE
        val now = time.nowMillis()
        return when (RequestClock.stateOf(clock, now)) {
            ClockState.OPEN -> earlyBy(clock.answerByMillis - now)
            ClockState.NO_ANSWER_LAST_CHANCE_POSSIBLE -> tellDeadlinePassed(clock)
            ClockState.NO_ANSWER -> endOf(clock)
            // A last chance was given; its own end job is scheduled with it.
            ClockState.LAST_CHANCE -> StepResult.DONE
        }
    }

    /** Ends clock [clockId]'s request with no answer, on the proposer's phone, when it is due. */
    suspend fun end(clockId: String): StepResult {
        val clock = repository.get(clockId) ?: return StepResult.RETRY
        if (clock.proposerUid != repository.signedInUid()) return StepResult.DONE
        return endOf(clock)
    }

    private suspend fun tellDeadlinePassed(clock: RequestDeadline): StepResult =
        when (records.lookOnServer(clock).status) {
            RecordStatus.UNKNOWN -> StepResult.RETRY
            RecordStatus.PENDING -> {
                notices.deadlinePassed(clock)
                scheduler.scheduleEnd(clock.id, RequestClock.endMillis(clock))
                StepResult.DONE
            }
            RecordStatus.ANSWERED, RecordStatus.GONE -> StepResult.DONE
        }

    private suspend fun endOf(clock: RequestDeadline): StepResult {
        val now = time.nowMillis()
        return when {
            clock.outcome != null -> withdrawLeftover(clock)
            RequestClock.stateOf(clock, now) != ClockState.NO_ANSWER -> notOverYet(clock, now)
            else -> closeUnanswered(clock)
        }
    }

    /**
     * A clock already closed: the end ran before and only its last part, a proposal's withdrawal,
     * may have failed. It is tried again while the proposal still waits; nothing is told twice.
     */
    private suspend fun withdrawLeftover(clock: RequestDeadline): StepResult {
        if (clock.kind != RequestKind.PROPOSAL) return StepResult.DONE
        return when (records.lookOnServer(clock).status) {
            RecordStatus.UNKNOWN -> StepResult.RETRY
            RecordStatus.PENDING ->
                if (records.withdrawUnansweredProposal(clock)) StepResult.DONE else StepResult.RETRY
            RecordStatus.ANSWERED, RecordStatus.GONE -> StepResult.DONE
        }
    }

    /**
     * The end fired before the request is over: a last chance was given since, moving the end.
     * The moved end is a job of another name, so it can be scheduled from here.
     */
    private fun notOverYet(clock: RequestDeadline, now: Long): StepResult {
        val endAt = RequestClock.endMillis(clock)
        scheduler.scheduleEnd(clock.id, endAt)
        return earlyBy(endAt - now)
    }

    /**
     * Closes the request in the spec's order (§4.4): the clock, the notice, the card, then a
     * proposal's withdrawal. Once the closure is written the rest runs to its end even when the job
     * is stopped, so a retry never finds the proposal gone and the card unsent.
     */
    private suspend fun closeUnanswered(clock: RequestDeadline): StepResult {
        val look = records.lookOnServer(clock)
        val card = look.card
        return when {
            look.status == RecordStatus.UNKNOWN -> StepResult.RETRY
            look.status != RecordStatus.PENDING || card == null -> StepResult.DONE
            // The rules judge "after the deadline" by the server's clock: a refusal moments after it
            // is "try again shortly", not final.
            !repository.close(clock) -> StepResult.RETRY
            else -> withContext(NonCancellable) {
                notices.noAnswer(clock)
                telling.unanswered(clock, card)
                val withdrawn = clock.kind != RequestKind.PROPOSAL || records.withdrawUnansweredProposal(clock)
                if (withdrawn) StepResult.DONE else StepResult.RETRY
            }
        }
    }

    /** A step that ran [millisEarly] before its time: tried again shortly when that is soon. */
    private fun earlyBy(millisEarly: Long): StepResult =
        if (millisEarly <= SHORT_RETRY_MILLIS) StepResult.RETRY else StepResult.DONE

    private companion object {
        /** A reminder whose job runs later than this after its planned time is not shown (ruling 6). */
        const val LATE_REMINDER_MILLIS = 30 * 60_000L

        /**
         * A step that fires at most this early is retried rather than left to the next sweep: about
         * what the worker's retries span.
         */
        const val SHORT_RETRY_MILLIS = 60_000L
    }
}
