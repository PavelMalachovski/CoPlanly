package com.coparently.app.data.requests

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The time every part of the request clocks asks (phase 3a, spec §4.1): the moment, the device's
 * zone, and a tick on each minute.
 *
 * Nothing that decides a state reads the system clock itself. A card, a banner and a background
 * step take their moment from here — or are handed it — so they all answer for the same instant and
 * a test can stand at any moment it likes. The ticks are what move a request from "open" to "no
 * answer" on a screen where nothing was written and nothing arrived: they make the same clocks be
 * read again, a minute later.
 *
 * @property ticks Emits once at once, then at each minute boundary, for as long as it is collected.
 */
@Singleton
class RequestTime internal constructor(
    private val now: () -> Long,
    private val zoneOf: () -> ZoneId,
    val ticks: Flow<Unit>
) {

    /** The system's time, the device's zone as it is now, and a tick on every minute. */
    @Inject
    constructor() : this(System::currentTimeMillis, ZoneId::systemDefault, minuteTicks())

    /** The moment, in epoch millis. */
    fun nowMillis(): Long = now()

    /** The device's time zone. Read each time: the parent may travel, or change it, while the app runs. */
    fun zone(): ZoneId = zoneOf()
}

/**
 * Emits at once, then at each minute boundary of [now]'s clock, for as long as it is collected.
 *
 * Boundaries rather than a minute after the last tick, so the ticks of different screens agree and
 * a state that changes on the minute is on screen within a second of it. A tick exactly on a
 * boundary waits a whole minute for the next, never none.
 *
 * @param now The clock whose minutes are counted; the system's unless a test stands in for it.
 */
internal fun minuteTicks(now: () -> Long = System::currentTimeMillis): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(MINUTE_MILLIS - now() % MINUTE_MILLIS)
    }
}

private const val MINUTE_MILLIS = 60_000L
