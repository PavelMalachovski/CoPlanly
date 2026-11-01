package com.coparently.app.domain.changerequests

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

/**
 * The request-clock arithmetic as it was on `java.time`, kept verbatim as the judge of the
 * kotlinx-datetime code that replaced it (MON-24, Task 7 slice 3): `AnswerBy`'s offer and
 * resolution, `ReminderPlan`'s plan and quiet hours, `RequestCap`'s local-time caps, the history's
 * reading of an instant on the device's clock and the month review's month. Only the types the
 * answers come back in are the oracle's own; the arithmetic is the deleted code's.
 */
@Suppress("MagicNumber") // the deleted code's constants, as they were
internal object RequestClockOracle {

    /** A chip as the old offer gave it: which preset, its time, whether the cap shortened it. */
    data class OldChip(val preset: AnswerByPreset, val answerByMillis: Long, val compressed: Boolean)

    /** An offer as the old code gave it. */
    data class OldOffer(
        val band: AnswerByBand,
        val nowMillis: Long,
        val chips: List<OldChip> = emptyList(),
        val fixedMillis: Long? = null,
        val latestMillis: Long? = null
    )

    /** A pick as the old code held it: a preset, or a `java.time` date. */
    sealed interface OldPick {
        data class Preset(val preset: AnswerByPreset) : OldPick
        data class OnDate(val date: LocalDate) : OldPick
    }

    // ---- AnswerBy, as it was --------------------------------------------------------------------

    private const val PICKED_DATE_HOUR = 20
    private const val QUARTER_HOUR_MILLIS = 900_000L
    private const val NO_DEADLINE_UNDER_MILLIS = 7_200_000L
    private const val FULL_CHOICE_FROM_MILLIS = 172_800_000L
    private const val LAST_CHANCE_RESERVE_MILLIS = 86_400_000L
    private const val MAX_AHEAD_MILLIS = 2_592_000_000L
    private const val SHORT_PRESET_DAYS = 3L
    private const val LONG_PRESET_DAYS = 7L

    fun offer(nowMillis: Long, capMillis: Long?, zone: ZoneId): OldOffer = when {
        capMillis == null -> choices(nowMillis, null, zone)
        capMillis - nowMillis < NO_DEADLINE_UNDER_MILLIS -> OldOffer(AnswerByBand.TOO_SOON, nowMillis)
        capMillis - nowMillis < FULL_CHOICE_FROM_MILLIS -> OldOffer(
            band = AnswerByBand.FIXED,
            nowMillis = nowMillis,
            fixedMillis = quarterDown(nowMillis + (capMillis - nowMillis) / 2, zone)
        )
        else -> choices(nowMillis, capMillis, zone)
    }

    fun resolve(pick: OldPick, nowMillis: Long, capMillis: Long?, zone: ZoneId): Long? =
        resolve(pick, offer(nowMillis, capMillis, zone), zone)

    private fun resolve(pick: OldPick, offer: OldOffer, zone: ZoneId): Long? {
        val nowMillis = offer.nowMillis
        return when (offer.band) {
            AnswerByBand.TOO_SOON -> null
            AnswerByBand.FIXED -> offer.fixedMillis
            AnswerByBand.CHOICES -> offer.latestMillis?.let { pickMillis(pick, nowMillis, it, zone) }
        }
    }

    private fun choices(nowMillis: Long, capMillis: Long?, zone: ZoneId): OldOffer {
        val latest = latestMillis(nowMillis, capMillis, zone)
        val chips = AnswerByPreset.entries
            .map { preset ->
                val wanted = presetMillis(preset, nowMillis, zone)
                OldChip(preset, minOf(wanted, latest), compressed = wanted > latest)
            }
            .distinctBy { it.answerByMillis }
        return OldOffer(AnswerByBand.CHOICES, nowMillis, chips = chips, latestMillis = latest)
    }

    private fun latestMillis(nowMillis: Long, capMillis: Long?, zone: ZoneId): Long {
        val farthest = quarterDown(nowMillis + MAX_AHEAD_MILLIS, zone)
        if (capMillis == null) return farthest
        return minOf(quarterDown(capMillis - LAST_CHANCE_RESERVE_MILLIS, zone), farthest)
    }

    private fun pickMillis(pick: OldPick, nowMillis: Long, latest: Long, zone: ZoneId): Long = when (pick) {
        is OldPick.Preset -> minOf(presetMillis(pick.preset, nowMillis, zone), latest)
        is OldPick.OnDate -> {
            val evening = pick.date.atTime(PICKED_DATE_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            val chosen = if (evening - nowMillis < NO_DEADLINE_UNDER_MILLIS) {
                presetMillis(AnswerByPreset.TOMORROW, nowMillis, zone)
            } else {
                evening
            }
            minOf(chosen, latest)
        }
    }

    private fun presetMillis(preset: AnswerByPreset, nowMillis: Long, zone: ZoneId): Long {
        val days = when (preset) {
            AnswerByPreset.TOMORROW -> 1L
            AnswerByPreset.THREE_DAYS -> SHORT_PRESET_DAYS
            AnswerByPreset.WEEK -> LONG_PRESET_DAYS
        }
        val later = Instant.ofEpochMilli(nowMillis).atZone(zone).plusDays(days).toInstant().toEpochMilli()
        return quarterUp(later, zone)
    }

    private fun quarterUp(millis: Long, zone: ZoneId): Long {
        val offset = offsetMillis(millis, zone)
        return (millis + offset + QUARTER_HOUR_MILLIS - 1).floorDiv(QUARTER_HOUR_MILLIS) * QUARTER_HOUR_MILLIS - offset
    }

    private fun quarterDown(millis: Long, zone: ZoneId): Long {
        val offset = offsetMillis(millis, zone)
        return (millis + offset).floorDiv(QUARTER_HOUR_MILLIS) * QUARTER_HOUR_MILLIS - offset
    }

    private fun offsetMillis(millis: Long, zone: ZoneId): Long =
        zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds.seconds.inWholeMilliseconds

    // ---- ReminderPlan, as it was ----------------------------------------------------------------

    private val QUIET_FROM: LocalTime = LocalTime.of(21, 0)
    private val QUIET_UNTIL: LocalTime = LocalTime.of(8, 0)
    private val EVENING_BEFORE: LocalTime = LocalTime.of(20, 30)
    private const val MAX_REMINDERS = 3
    private const val THIRDS = 3L
    private const val HOUR_MILLIS = 3_600_000L

    fun plan(sentAtMillis: Long, answerByMillis: Long, notBeforeMillis: Long, zone: ZoneId): List<Long> =
        placed(sentAtMillis, answerByMillis)
            .map { outOfTheNight(it, answerByMillis, zone) }
            .filter { it > sentAtMillis }
            .filter { it >= notBeforeMillis }
            .distinct()
            .sorted()

    private fun placed(sentAtMillis: Long, answerByMillis: Long): List<Long> {
        val window = answerByMillis - sentAtMillis
        val midpoint = sentAtMillis + window / 2
        return when (ReminderPlan.countFor(window)) {
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

    private fun outOfTheNight(millis: Long, answerByMillis: Long, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        if (!isNight(local.toLocalTime())) return millis
        val morning = nextMorning(local)
        val beforeDeadline = morning.toInstant().toEpochMilli() < answerByMillis
        return (if (beforeDeadline) morning else theEveningBefore(morning)).toInstant().toEpochMilli()
    }

    fun isQuietAt(millis: Long, zone: ZoneId): Boolean =
        isNight(Instant.ofEpochMilli(millis).atZone(zone).toLocalTime())

    private fun isNight(time: LocalTime): Boolean = time >= QUIET_FROM || time < QUIET_UNTIL

    private fun nextMorning(from: ZonedDateTime): ZonedDateTime {
        val today = from.toLocalDate()
        val day = if (from.toLocalTime() < QUIET_UNTIL) today else today.plusDays(1)
        return day.atTime(QUIET_UNTIL).atZone(from.zone)
    }

    private fun theEveningBefore(morning: ZonedDateTime): ZonedDateTime =
        morning.toLocalDate().minusDays(1).atTime(EVENING_BEFORE).atZone(morning.zone)

    // ---- RequestCap's local times, as they were -------------------------------------------------

    fun forSwap(dates: Collection<LocalDate>, zone: ZoneId): Long? =
        dates.minOrNull()?.let { startOfDay(it, zone) }

    fun forChange(currentStart: LocalDateTime, proposedStart: LocalDateTime, nowMillis: Long, zone: ZoneId): Long? =
        listOf(currentStart, proposedStart).map { millisOf(it, zone) }.filter { it > nowMillis }.minOrNull()

    fun forEvent(start: LocalDateTime, nowMillis: Long, zone: ZoneId): Long? =
        millisOf(start, zone).takeIf { it > nowMillis }

    fun startOfDay(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun millisOf(local: LocalDateTime, zone: ZoneId): Long = local.atZone(zone).toInstant().toEpochMilli()

    // ---- the history's clock and the month review's month, as they were -------------------------

    /** A clock's or a decision's instant on [zone]'s wall clock, as `RequestHistory` read it. */
    fun localOf(millis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** The days of [month], as `MonthReviewCalculator` listed them. */
    fun daysOf(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map { month.atDay(it) }

    /** Whether [date] falls in [month], as the month review and the expenses' filter asked it. */
    fun inMonth(date: LocalDate, month: YearMonth): Boolean = YearMonth.from(date) == month
}
