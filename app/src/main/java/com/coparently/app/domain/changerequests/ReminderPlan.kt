package com.coparently.app.domain.changerequests

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the answering parent's phone reminds them of a request that waits on their answer (phase 3a,
 * spec §4.3): how many reminders, and at what times, with none at night.
 *
 * **How many** follows the length of the window, from the moment the request was sent to its
 * deadline ([countFor]). Three are spread over it, at a third and two thirds of the way and two
 * hours before the deadline; two sit at the midpoint and an hour before; one sits at the midpoint.
 *
 * **Night is the answering parent's own.** A reminder between [QUIET_FROM] and [QUIET_UNTIL] on
 * their clock is moved out of it. That is why their phone makes the plan, in its own time zone, when
 * it first receives the request's clock: no profile stores a zone, and the proposer may be in
 * another one. What the proposer's phone can say before then is only how many ([countFor]).
 *
 * Nothing here reads the time or the zone itself; both are passed in, as in [AnswerBy], so a test can
 * stand in any zone and at any moment. The reminders are placed by arithmetic on the window, but
 * whether one is at night, and where it moves to, is read off the zone's calendar and converted back
 * to an instant, never found by adding hours, so a clock change cannot carry a reminder into the night.
 */
object ReminderPlan {

    /**
     * Where the night begins on the answering parent's clock: 21:00. A reminder at exactly this time is
     * already at night.
     */
    val QUIET_FROM: LocalTime = LocalTime.of(21, 0)

    /**
     * Where the night ends on the answering parent's clock: 08:00. A reminder at exactly this time is
     * not at night, and a reminder that is moved out of the night goes here.
     */
    val QUIET_UNTIL: LocalTime = LocalTime.of(8, 0)

    /**
     * Where a reminder goes when the next [QUIET_UNTIL] would be at or after the deadline: this time of
     * day, on the evening before that morning. 20:30.
     */
    val EVENING_BEFORE: LocalTime = LocalTime.of(20, 30)

    /** The most reminders a request gets. */
    private const val MAX_REMINDERS = 3

    /** The first two of the three reminders sit at a third and two thirds of the window. */
    private const val THIRDS = 3L

    /** A window of this length or more gets [MAX_REMINDERS]: 24 hours. */
    private const val FULL_PLAN_FROM_MILLIS = 86_400_000L

    /** A window of this length or more, and under [FULL_PLAN_FROM_MILLIS], gets two reminders: 6 hours. */
    private const val SHORT_PLAN_FROM_MILLIS = 21_600_000L

    private const val HOUR_MILLIS = 3_600_000L

    /**
     * How many reminders a window of [windowMillis], from the request's sending to its deadline, gets:
     * three for 24 hours or more, two for 6 hours or more, one for any shorter window that is not
     * empty, and none for an empty or a negative one.
     */
    fun countFor(windowMillis: Long): Int = when {
        windowMillis >= FULL_PLAN_FROM_MILLIS -> MAX_REMINDERS
        windowMillis >= SHORT_PLAN_FROM_MILLIS -> 2
        windowMillis > 0 -> 1
        else -> 0
    }

    /**
     * The times at which to remind the answering parent, in epoch millis, earliest first. Empty when
     * the window is empty and when every reminder was dropped. The steps run in this order:
     * 1. place the [countFor] reminders over the window from [sentAtMillis] to [answerByMillis];
     * 2. move each one that falls in the quiet hours of [zone]'s clock to the next [QUIET_UNTIL] (one
     *    outside them stays where it is);
     * 3. send one that moved to or past the deadline to [EVENING_BEFORE] on the evening before that
     *    morning instead, so it still comes before the deadline;
     * 4. drop one at or before [sentAtMillis]: moving back can pass the request's sending, and a
     *    reminder cannot come before the thing it reminds of;
     * 5. drop one before [notBeforeMillis]: it is already past (ruling 6);
     * 6. merge those that landed on the same time.
     *
     * @param sentAtMillis When the request was sent: the start of the window.
     * @param answerByMillis The deadline: the end of the window.
     * @param notBeforeMillis The moment the answering phone received the clock. A reminder before it is
     *   not planned, since the phone must not remind late (ruling 6).
     * @param zone The answering parent's own time zone, which decides what is night.
     */
    fun plan(sentAtMillis: Long, answerByMillis: Long, notBeforeMillis: Long, zone: ZoneId): List<Long> =
        placed(sentAtMillis, answerByMillis)
            .map { outOfTheNight(it, answerByMillis, zone) }
            .filter { it > sentAtMillis }
            .filter { it >= notBeforeMillis }
            .distinct()
            .sorted()

    /** The reminders [countFor] gives for the window from [sentAtMillis] to [answerByMillis], where they fall. */
    private fun placed(sentAtMillis: Long, answerByMillis: Long): List<Long> {
        val window = answerByMillis - sentAtMillis
        val midpoint = sentAtMillis + window / 2
        return when (countFor(window)) {
            MAX_REMINDERS -> listOf(
                sentAtMillis + window / THIRDS,
                sentAtMillis + window * 2 / THIRDS,
                answerByMillis - 2 * HOUR_MILLIS
            )
            2 -> listOf(midpoint, answerByMillis - HOUR_MILLIS)
            1 -> listOf(midpoint)
            else -> emptyList()
        }
    }

    /**
     * [millis] where it is when it is outside the quiet hours of [zone]; otherwise the next
     * [QUIET_UNTIL], or, if that is at or after [answerByMillis], the evening before it.
     */
    private fun outOfTheNight(millis: Long, answerByMillis: Long, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        if (!isNight(local.toLocalTime())) return millis
        val morning = nextMorning(local)
        val beforeDeadline = morning.toInstant().toEpochMilli() < answerByMillis
        return (if (beforeDeadline) morning else theEveningBefore(morning)).toInstant().toEpochMilli()
    }

    /** Whether [time] is in the quiet hours: from [QUIET_FROM] through midnight up to [QUIET_UNTIL]. */
    private fun isNight(time: LocalTime): Boolean = time >= QUIET_FROM || time < QUIET_UNTIL

    /** The next [QUIET_UNTIL] on the calendar of [from]'s zone: its own day's if still ahead, else the next day's. */
    private fun nextMorning(from: ZonedDateTime): ZonedDateTime {
        val today = from.toLocalDate()
        val day = if (from.toLocalTime() < QUIET_UNTIL) today else today.plusDays(1)
        return day.atTime(QUIET_UNTIL).atZone(from.zone)
    }

    /** [EVENING_BEFORE] on the calendar day before [morning]. */
    private fun theEveningBefore(morning: ZonedDateTime): ZonedDateTime =
        morning.toLocalDate().minusDays(1).atTime(EVENING_BEFORE).atZone(morning.zone)
}
