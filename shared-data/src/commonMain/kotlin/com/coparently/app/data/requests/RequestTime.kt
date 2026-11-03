package com.coparently.app.data.requests

import com.coparently.app.domain.InternalSharedApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

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
 * The zone is kotlinx-datetime's since MON-24 phase 2 (Task 8h): the system's zone, which on Android
 * is `java.time`'s `ZoneId.systemDefault()` read through `TimeZone.currentSystemDefault()` on every
 * call, the zone the reminders were planned and checked in when this held a `ZoneId`.
 * `RequestClocksTest` holds the zone to `ZoneId.systemDefault()` on every call.
 *
 * @property ticks Emits once at once, then at each minute boundary, for as long as it is collected.
 */
class RequestTime internal constructor(
    private val now: () -> Long,
    private val zoneOf: () -> TimeZone,
    val ticks: Flow<Unit>
) {

    /** The system's time, the device's zone as it is now, and a tick on every minute. */
    constructor() : this(::systemNowMillis, { TimeZone.currentSystemDefault() }, minuteTicks())

    /** The moment, in epoch millis. */
    fun nowMillis(): Long = now()

    /** The device's time zone. Read each time: the parent may travel, or change it, while the app runs. */
    fun zone(): TimeZone = zoneOf()

    /** For the app's tests: a [RequestTime] that stands where a test puts it. */
    companion object {

        /**
         * A clock reading [now] and [zoneOf], ticking with [ticks]. The primary constructor stays
         * `internal` so the graph check (`KoinGraphTest`) reads only the no-argument one; the app's
         * tests, in another module, build their clocks here.
         */
        @InternalSharedApi
        fun of(now: () -> Long, zoneOf: () -> TimeZone, ticks: Flow<Unit>): RequestTime =
            RequestTime(now, zoneOf, ticks)
    }
}

/**
 * Emits at once, then at each minute boundary of [now]'s clock, for as long as it is collected.
 *
 * Boundaries rather than a minute after the last tick, so the ticks of different screens agree and
 * a state that changes on the minute is on screen within a second of it. A tick exactly on a
 * boundary waits a whole minute for the next, never none.
 *
 * Public because the form's "Answer by" chips in the app tick on the same minutes
 * (`rememberAnswerByOffer`).
 *
 * @param now The clock whose minutes are counted; the system's unless a test stands in for it.
 */
fun minuteTicks(now: () -> Long = ::systemNowMillis): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(MINUTE_MILLIS - now() % MINUTE_MILLIS)
    }
}

/** The system's time in epoch millis: what `System.currentTimeMillis()` answers on the JVM. */
private fun systemNowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private const val MINUTE_MILLIS = 60_000L
