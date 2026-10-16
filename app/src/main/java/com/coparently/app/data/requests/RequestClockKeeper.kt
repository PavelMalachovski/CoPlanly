package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this phone's part of every open request clock in hand (phase 3a, spec §4.3, §4.5, §5.3).
 *
 * - **The answering phone delivers a clock once.** When it first sees one, it plans the reminders
 *   in its own zone — only it knows the zone, and only reminders still ahead (ruling 6) — records
 *   the delivery together with the plan, and schedules them. The delivery time is never before the
 *   clock was created, so a slow phone never says "delivered" before "sent".
 * - **The proposer's phone keeps the deadline and the end scheduled**, and runs an end that is
 *   already due; a closed proposal goes back to [RequestClockSteps.end] so a failed withdrawal is
 *   tried again.
 *
 * [sweep] runs on every full sync, which is how a phone that was not running learns of a clock, and
 * how a step a job lost is armed again. [deliverSoon] runs while the app is open, for each snapshot
 * of the family's clocks the screens are listening to.
 *
 * Only a clock this phone can trust is acted on: its id must spell its own fields, as
 * [RequestDeadlineId] derives it, so the other parent cannot set or close this parent's request
 * under an id of their own; and the sweep keeps only clocks whose other parent is still one of this
 * parent's co-parents (CLAUDE.md items 38, 41).
 */
@Singleton
class RequestClockKeeper internal constructor(
    private val repository: RequestDeadlineRepository,
    private val steps: RequestClockSteps,
    private val scheduler: RequestClockScheduler,
    private val time: RequestTime,
    private val scope: CoroutineScope
) {

    /** The production keeper: its deliveries run for as long as the process does. */
    @Inject
    constructor(
        repository: RequestDeadlineRepository,
        steps: RequestClockSteps,
        scheduler: RequestClockScheduler,
        time: RequestTime
    ) : this(repository, steps, scheduler, time, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    /** The ids of the clocks a delivery is running for, so one listener seen by three screens writes once. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Keeps every open clock of [myUid] whose other parent is in [coParents]: delivers what this
     * parent must answer, and schedules or runs what they asked.
     */
    suspend fun sweep(myUid: String, coParents: Set<String>) {
        val now = time.nowMillis()
        repository.openFor(myUid, now - LOOKBACK_MILLIS)
            .filter { isGenuine(it) }
            .forEach { clock ->
                when (myUid) {
                    clock.responderUid -> if (clock.proposerUid in coParents) keepAsResponder(clock, now)
                    clock.proposerUid -> if (clock.responderUid in coParents) keepAsProposer(clock, now)
                }
            }
    }

    /**
     * Delivers, on the keeper's scope, each of [clocks] that [myUid] must answer and has not
     * received yet. A clock whose delivery is already running is skipped; one whose delivery failed
     * is tried again the next time it is handed over. It does not check the co-parent set, which it
     * is not given: [sweep] is the co-parent gate, and the listener feeding this reads the family on
     * screen only.
     */
    fun deliverSoon(myUid: String, clocks: Collection<RequestDeadline>) {
        clocks.filter { it.responderUid == myUid && isUndelivered(it) && isGenuine(it) }
            .filter { claim(it.id) }
            .forEach { clock ->
                scope.launch {
                    try {
                        deliver(clock, time.nowMillis())
                    } finally {
                        release(clock.id)
                    }
                }
            }
    }

    private suspend fun keepAsResponder(clock: RequestDeadline, now: Long) {
        when {
            isUndelivered(clock) -> if (claim(clock.id)) {
                try {
                    deliver(clock, now)
                } finally {
                    release(clock.id)
                }
            }
            clock.outcome == null ->
                clock.reminderTimesMillis.filter { it > now }.forEach { scheduler.scheduleReminder(clock.id, it) }
        }
    }

    private suspend fun keepAsProposer(clock: RequestDeadline, now: Long) {
        if (clock.outcome != null) {
            if (clock.kind == RequestKind.PROPOSAL) steps.end(clock.id)
            return
        }
        if (now < clock.answerByMillis) scheduler.scheduleDeadline(clock.id, clock.answerByMillis)
        val endAt = RequestClock.endMillis(clock)
        if (endAt <= now) steps.end(clock.id) else scheduler.scheduleEnd(clock.id, endAt)
    }

    /**
     * Plans [clock]'s reminders from its creation (or [now], before the server stamped it) in this
     * phone's zone, records the delivery and the plan, and schedules the plan once it is recorded.
     */
    private suspend fun deliver(clock: RequestDeadline, now: Long) {
        val sentAt = clock.createdAtMillis
        val times = ReminderPlan.plan(sentAt ?: now, clock.answerByMillis, now, time.zone())
        val deliveredAt = sentAt?.let { maxOf(now, it) } ?: now
        if (repository.markDelivered(clock, deliveredAt, times)) {
            times.forEach { scheduler.scheduleReminder(clock.id, it) }
        }
    }

    private fun isUndelivered(clock: RequestDeadline): Boolean =
        clock.deliveredAtMillis == null && clock.outcome == null

    private fun isGenuine(clock: RequestDeadline): Boolean =
        clock.id == RequestDeadlineId.of(clock.kind, clock.proposerUid, clock.ref, clock.familyId)

    private fun claim(id: String): Boolean = synchronized(inFlight) { inFlight.add(id) }

    private fun release(id: String) {
        synchronized(inFlight) { inFlight.remove(id) }
    }

    private companion object {
        /**
         * How far back a sweep reads deadlines: an end comes at most two days after its deadline, so
         * four days catch every clock that can still need a step.
         */
        const val LOOKBACK_MILLIS = 4 * 24 * 3_600_000L
    }
}
