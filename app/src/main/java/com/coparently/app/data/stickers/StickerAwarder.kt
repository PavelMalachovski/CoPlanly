package com.coparently.app.data.stickers

import com.coparently.app.data.requests.RequestTime
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.stickers.Sticker
import com.coparently.app.domain.stickers.StickerAwards
import com.coparently.app.domain.stickers.StickerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives the app's own stickers when this parent answers a request (October 2026, owner
 * decisions): "on time" once an ISO week for an answer before the request's deadline, and
 * "regularity" for four weeks in a row of those with nothing left unanswered ([StickerAwards]).
 *
 * Called by the requests inbox right after an answer lands, with the clock the request named
 * before it was answered. A request with no clock has no deadline to beat and gives nothing. The
 * app's stickers push nobody: the receiver finds them on Home.
 */
@Singleton
class StickerAwarder @Inject constructor(
    private val clocks: RequestDeadlineRepository,
    private val stickers: StickerStore,
    private val time: RequestTime
) {

    /**
     * Gives what an answer to the request timed by [clockId] earns, now. Best-effort: a sticker
     * that cannot be stored is simply not given, and the answer it rode on stands.
     */
    suspend fun onAnswered(clockId: String?) {
        val me = stickers.signedInUid()
        val clock = clockId?.let { clocks.get(it) }
        val now = time.nowMillis()
        if (me == null || clock == null || !StickerAwards.answeredOnTime(clock, me, now)) return
        award(me, clock, Instant.ofEpochMilli(now).atZone(time.zone()).toLocalDate())
    }

    /** This week's on-time sticker, unless it is given already, then regularity if the run is complete. */
    private suspend fun award(me: String, clock: RequestDeadline, today: LocalDate) {
        val week = StickerAwards.isoWeek(today)
        val received = stickers.receivedOnce()
        val onTimeId = StickerAwards.appStickerId(me, StickerKind.ON_TIME, week)
        val onTime = received.firstOrNull { it.id == onTimeId }
            ?: Sticker(onTimeId, me, null, clock.familyId, StickerKind.ON_TIME)
                .takeIf { stickers.award(onTimeId, StickerKind.ON_TIME, clock.familyId) }
            ?: return
        val familyClocks = withTimeoutOrNull(CLOCKS_WAIT_MS) { clocks.observeFamily(me, clock.familyId).first() }
            ?: return
        val history = (received + onTime).distinctBy { it.id }
        if (StickerAwards.regular(history, familyClocks, me, today, time.zone())) {
            val regularId = StickerAwards.appStickerId(me, StickerKind.REGULAR, week)
            stickers.award(regularId, StickerKind.REGULAR, clock.familyId)
        }
    }

    private companion object {
        /** How long the family's clocks may take to answer before regularity is left for next time. */
        const val CLOCKS_WAIT_MS = 10_000L
    }
}
