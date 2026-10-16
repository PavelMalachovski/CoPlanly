package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A family's request clocks, seen at a moment that keeps moving (phase 3a, spec §4.1): what the
 * inbox, Home and the calendar read, so that every card on every screen answers for the same
 * instant.
 *
 * A clock stores no state but its end. Whether a request is open, past its deadline, in its last
 * chance or over follows from the clock and the time, so a screen needs the clocks again whenever
 * either changes — a snapshot from the listener, or the minute turning over.
 *
 * While the app runs, this listener is also how the answering phone first sees a request's clock
 * (spec §5.3): each snapshot hands the clocks it has not yet received to the [keeper], which
 * delivers them at once rather than at the next sync.
 */
@Singleton
class RequestClocks @Inject constructor(
    private val repository: RequestDeadlineRepository,
    private val time: RequestTime,
    private val keeper: RequestClockKeeper
) {

    /**
     * The clocks of [familyId] that [myUid] is a party to, each time they change and each minute,
     * stamped with the moment they were read.
     *
     * Nothing is emitted until the listener has answered once. A screen shows the default [ClocksAt],
     * which holds no clock, until then — which is how a request without a clock looks.
     *
     * Each snapshot hands the keeper the clocks addressed to [myUid] that are neither delivered nor
     * closed; the keeper starts one delivery per clock however many screens collect this.
     */
    fun observeFamily(myUid: String, familyId: String): Flow<ClocksAt> {
        val snapshots = repository.observeFamily(myUid, familyId).onEach { clocks ->
            val undelivered = clocks.filter {
                it.responderUid == myUid && it.deliveredAtMillis == null && it.outcome == null
            }
            if (undelivered.isNotEmpty()) keeper.deliverSoon(myUid, undelivered)
        }
        return combine(snapshots, time.ticks) { clocks, _ -> ClocksAt.of(clocks, time.nowMillis()) }
    }
}
