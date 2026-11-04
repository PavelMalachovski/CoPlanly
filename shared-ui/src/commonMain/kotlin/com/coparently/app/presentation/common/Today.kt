package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Today's date, as a value that actually changes when today does.
 *
 * `remember { LocalDate.now() }` is the obvious thing to write and it is wrong here: it captures
 * the date at first composition and never revisits it, so an app left open overnight goes on
 * highlighting yesterday. For a product whose entire question is *whose day is it today*, that is
 * an answer that quietly becomes wrong while the screen still looks right.
 *
 * Reading `LocalDate.now()` inline is no better on its own. It is correct whenever it runs, but
 * nothing makes it run: midnight is not a recomposition trigger. This is — the state changes, and
 * everything reading it recomposes.
 *
 * The wait is recomputed each round from the real clock rather than assuming 24 hours, so a
 * daylight-saving change, a manual clock adjustment or a delay that fires late all land on the
 * right date: whatever wakes the loop, the new value comes from the clock, not from counting.
 *
 * Known limitation: while the process is in Doze the timer is throttled, so the change can arrive
 * late — but it arrives with the correct date, and any screen the user actually returns to
 * recomposes with it.
 */
@Composable
fun rememberToday(): State<LocalDate> {
    LocalFixedToday.current?.let { fixed -> return remember(fixed) { mutableStateOf(fixed) } }
    val today = remember { mutableStateOf(systemToday()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMidnight(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())))
            today.value = systemToday()
        }
    }

    return today
}

/**
 * A fixed date in place of the clock, for rendering that must not depend on the day it runs: the
 * screenshot tests provide their pinned fixture date, so the weekday header's "today" does not
 * move a baseline every day. `null`, the default and the app's value, follows the clock.
 */
val LocalFixedToday = staticCompositionLocalOf<LocalDate?> { null }

/**
 * How long from [now] until the start of the next day, in milliseconds.
 *
 * Never zero or negative. A timer that fires marginally early would otherwise be asked to wait no
 * time at all, and the loop would spin against the clock instead of waiting for it — the one way
 * a date that updates itself can cost more than a date that never does.
 */
internal fun millisUntilNextMidnight(now: LocalDateTime): Long {
    // A wall-clock difference, as `java.time.Duration.between` of two `LocalDateTime`s was: both
    // ends are read in UTC, so no zone's daylight-saving change stretches or shortens it.
    val nextMidnight = now.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC)
    return (nextMidnight - now.toInstant(TimeZone.UTC)).inWholeMilliseconds.coerceAtLeast(1L)
}

/** Today in the phone's zone, what `LocalDate.now()` read. */
private fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
