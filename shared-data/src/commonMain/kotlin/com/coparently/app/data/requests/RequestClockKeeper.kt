@file:OptIn(ExperimentalAtomicApi::class)

package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Keeps this phone's part of every open request clock in hand (phase 3a, spec §4.3, §5.3): the
 * answering parent's.
 *
 * **The answering phone delivers a clock once.** When it first sees one, it plans the reminders in
 * its own zone — only it knows the zone, and only reminders still ahead (ruling 6) — records the
 * delivery together with the plan, and schedules them. The delivery time is never before the clock
 * was created, so a slow phone never says "delivered" before "sent".
 *
 * **The proposer's phone keeps nothing** since MON-25: the deadline notice and the end are the
 * server's (`functions/request-clock-steps.js`), which schedules them from the clock itself.
 *
 * [sweep] runs on every full sync, which is how a phone that was not running learns of a clock, and
 * how the reminders still ahead are armed again after a job was lost. [deliverSoon] runs while the
 * app is open, for each snapshot of the family's clocks the screens are listening to.
 *
 * Only a clock this phone can trust is acted on: its id must spell its own fields, as
 * [RequestDeadlineId] derives it, so the other parent cannot set or close this parent's request
 * under an id of their own; and the sweep keeps only clocks whose other parent is still one of this
 * parent's co-parents (CLAUDE.md items 38, 41).
 *
 * The sync runs [sweep] as its [RequestClockSweep]. Both are in `:shared-data` since MON-24 phase 2
 * (the sync since Task 8g, this since Task 8h); the jobs it schedules are the platform's
 * ([RequestClockScheduler]: WorkManager on Android).
 */
class RequestClockKeeper internal constructor(
    private val repository: RequestDeadlineRepository,
    private val scheduler: RequestClockScheduler,
    private val time: RequestTime,
    private val scope: CoroutineScope
) : RequestClockSweep {

    /** The production keeper: its deliveries run for as long as the process does. */
    constructor(
        repository: RequestDeadlineRepository,
        scheduler: RequestClockScheduler,
        time: RequestTime
    ) : this(repository, scheduler, time, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    /** The ids of the clocks a delivery is running for, so one listener seen by three screens writes once. */
    private val inFlight = AtomicReference(emptySet<String>())

    /**
     * Keeps every open clock that [myUid] must answer and whose proposer is in [coParents]: delivers
     * it, or arms its reminders still ahead again. A clock this parent proposed is left alone: what
     * remains of it is the server's.
     */
    override suspend fun sweep(myUid: String, coParents: Set<String>) {
        val now = time.nowMillis()
        repository.openFor(myUid, now - LOOKBACK_MILLIS)
            .filter { it.responderUid == myUid && it.proposerUid in coParents && isGenuine(it) }
            .forEach { keepAsResponder(it, now) }
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

    /**
     * Adds [id] to the deliveries running, atomically: true for the one caller that added it, false
     * while it is there already. A compare-and-set loop over an immutable set rather than a lock,
     * which common code has none of; the set it replaces is the one it read, compared by identity.
     */
    private fun claim(id: String): Boolean {
        while (true) {
            val running = inFlight.load()
            if (id in running) return false
            if (inFlight.compareAndSet(running, running + id)) return true
        }
    }

    /** Takes [id] out of the deliveries running, atomically. */
    private fun release(id: String) {
        while (true) {
            val running = inFlight.load()
            if (inFlight.compareAndSet(running, running - id)) return
        }
    }

    private companion object {
        /**
         * How far back a sweep reads deadlines: a request stays open at most two days past its
         * deadline, so four days catch every clock this phone may still have to deliver — the
         * delivery is recorded for the proposer's card even when no reminder is left to plan.
         */
        const val LOOKBACK_MILLIS = 4 * 24 * 3_600_000L
    }
}
