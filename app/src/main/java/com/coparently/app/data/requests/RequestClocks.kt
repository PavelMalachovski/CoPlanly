package com.coparently.app.data.requests

import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 */
@Singleton
class RequestClocks @Inject constructor(
    private val repository: RequestDeadlineRepository,
    private val time: RequestTime
) {

    /**
     * The clocks of [familyId] that [myUid] is a party to, each time they change and each minute,
     * stamped with the moment they were read.
     *
     * Nothing is emitted until the listener has answered once. A screen shows the default [ClocksAt],
     * which holds no clock, until then — which is how a request without a clock looks.
     */
    fun observeFamily(myUid: String, familyId: String): Flow<ClocksAt> =
        combine(repository.observeFamily(myUid, familyId), time.ticks) { clocks, _ ->
            ClocksAt.of(clocks, time.nowMillis())
        }
}
