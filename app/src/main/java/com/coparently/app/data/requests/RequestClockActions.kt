package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.RequestClock
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a parent does to a request's clock from its card (phase 3a, spec §4.4, §4.5): the answering
 * parent opening the request, and the proposer giving the one last chance.
 *
 * **Both read the clock again** by its id, rather than trusting the card that was drawn: the card is
 * a moment old, and the other phone may have written since. The callers pass ids, never a clock a
 * screen held (CLAUDE.md item 17).
 *
 * - **Seen** is written once, by the answering parent only, and never before the clock was created:
 *   the rules allow a few minutes of clock skew, and a slow phone must not report "opened" before
 *   "sent".
 * - **A last chance** is given only by the proposer, only while the button is offered
 *   ([RequestClock.lastChanceWindowMillis]), and only while the server says the request still waits
 *   — this phone's copy may not yet hold the co-parent's answer. A server out of reach gives
 *   nothing, and nothing is written. Once the clock has it, the push, the card and the end follow
 *   even when the screen that asked has gone.
 */
@Singleton
class RequestClockActions @Inject constructor(
    private val repository: RequestDeadlineRepository,
    private val records: RequestRecords,
    private val telling: RequestTelling,
    private val scheduler: RequestClockScheduler,
    private val time: RequestTime
) {

    /**
     * Records that the answering parent opened the request clock [clockId] times.
     *
     * @return Whether it was written: false for the proposer, for a request already seen, for a
     *   clock that cannot be read, and for a write that was refused.
     */
    suspend fun markSeen(clockId: String): Boolean {
        val clock = repository.get(clockId)
            ?.takeIf { it.responderUid == repository.signedInUid() && it.seenAtMillis == null }
            ?: return false
        val now = time.nowMillis()
        return repository.markSeen(clock, clock.createdAtMillis?.let { maxOf(now, it) } ?: now)
    }

    /**
     * Gives the answering parent one last chance on the request clock [clockId] times: the clock,
     * then the push and the "Last chance" card, then the job that ends it, in that order.
     *
     * @return Whether it was given: false when the signed-in parent is not the proposer, the button
     *   is not offered now, the request no longer waits or the server cannot say, and when the
     *   clock's write was refused — then nobody is told and nothing is scheduled.
     */
    suspend fun giveLastChance(clockId: String): Boolean {
        val grant = grantFor(clockId) ?: return false
        if (!repository.giveLastChance(grant.clock, grant.untilMillis)) return false
        withContext(NonCancellable) {
            telling.lastChance(grant.clock, grant.card, grant.untilMillis)
            scheduler.scheduleEnd(grant.clock.id, grant.untilMillis)
        }
        return true
    }

    /** The last chance the proposer may give now, or null when they may not. */
    private suspend fun grantFor(clockId: String): Grant? {
        val clock = repository.get(clockId)?.takeIf { it.proposerUid == repository.signedInUid() } ?: return null
        val now = time.nowMillis()
        val window = RequestClock.lastChanceWindowMillis(clock, now) ?: return null
        val look = records.lookOnServer(clock)
        return look.card?.takeIf { look.status == RecordStatus.PENDING }?.let { Grant(clock, it, now + window) }
    }

    /** A last chance about to be given: the clock, what its card names, and when it ends. */
    private data class Grant(val clock: RequestDeadline, val card: CardSubject, val untilMillis: Long)
}
