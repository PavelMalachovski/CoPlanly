package com.coparently.app.domain.changerequests

import com.coparently.app.domain.changerequests.RequestClockOracle.OldPick
import kotlinx.datetime.onDay
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toKotlinYearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Instant as KInstant

/**
 * The request-clock arithmetic on kotlinx-datetime against the `java.time` code it replaced
 * ([RequestClockOracle]), on seeded random moments (`Random(20261103)`) — MON-24, Task 7 slice 3.
 *
 * Every function that reads a zone is asked in zones chosen for what they do to a clock: a spring
 * gap and an autumn repeat (Prague, New York), a change at midnight (São Paulo before 2019, Havana,
 * Beirut), a skipped calendar day (Apia, 30 December 2011), half-hour shifts (Lord Howe, St John's,
 * India), a negative summer offset (Dublin, Casablanca) and the date line (Kiritimati, Pago Pago).
 * Half the moments are drawn within three hours of one of the zone's own transitions, the rest from
 * 1990 to 2040, and the windows lean on the thresholds the code branches at: two hours, two days, a
 * day before a cap, and the quiet hours at 21:00 and 08:00.
 */
class RequestClockParityTest {

    private val random = Random(20261103)

    @Test
    fun offersMatchJavaTime() {
        repeat(ITERATIONS) {
            val zone = randomZone()
            val now = randomMoment(zone)
            val cap = randomCap(now)
            val expected = RequestClockOracle.offer(now, cap, zone)
            val actual = AnswerBy.offer(now, cap, zone.toKotlinTimeZone())
            assertEquals(expected, actual.toOld(), "offer($now, $cap, $zone)")
        }
    }

    @Test
    fun resolutionsMatchJavaTime() {
        repeat(ITERATIONS) {
            val zone = randomZone()
            val now = randomMoment(zone)
            val cap = randomCap(now)
            val (old, new) = randomPick(now, zone)
            assertEquals(
                RequestClockOracle.resolve(old, now, cap, zone),
                AnswerBy.resolve(new, now, cap, zone.toKotlinTimeZone()),
                "resolve($old, $now, $cap, $zone)"
            )
        }
    }

    @Test
    fun picksTravelAsTheSameText() {
        repeat(ITERATIONS) {
            val date = LocalDate.ofEpochDay(random.nextLong(MIN_EPOCH_DAY, MAX_EPOCH_DAY))
            val text = AnswerByPick.encode(AnswerByPick.OnDate(date.toKotlinLocalDate()))
            assertEquals("DATE:$date", text)
            val mutated = mutate(text)
            val old = runCatching { LocalDate.parse(mutated.removePrefix("DATE:")) }.getOrNull()
                ?.takeIf { mutated.startsWith("DATE:") }
            val decoded = AnswerByPick.decode(mutated)
            val expected = when {
                AnswerByPreset.entries.any { it.name == mutated } -> decoded
                old != null -> AnswerByPick.OnDate(old.toKotlinLocalDate())
                else -> AnswerByPick.DEFAULT
            }
            assertEquals(expected, decoded, "decode($mutated)")
        }
    }

    @Test
    fun remindersMatchJavaTime() {
        repeat(ITERATIONS) {
            val zone = randomZone()
            val sent = randomMoment(zone)
            val answerBy = sent + randomWindow()
            val notBefore = sent + random.nextLong(-HOUR, maxOf(answerBy - sent, 0L) + HOUR + 1)
            assertEquals(
                RequestClockOracle.plan(sent, answerBy, notBefore, zone),
                ReminderPlan.plan(sent, answerBy, notBefore, zone.toKotlinTimeZone()),
                "plan($sent, $answerBy, $notBefore, $zone)"
            )
            val probe = randomMoment(zone)
            assertEquals(
                RequestClockOracle.isQuietAt(probe, zone),
                ReminderPlan.isQuietAt(probe, zone.toKotlinTimeZone()),
                "isQuietAt($probe, $zone)"
            )
        }
    }

    @Test
    fun capsMatchJavaTime() {
        repeat(ITERATIONS) {
            val zone = randomZone()
            val kZone = zone.toKotlinTimeZone()
            val now = randomMoment(zone)
            val days = List(random.nextInt(0, 4)) { randomDay(now, zone) }
            assertEquals(
                RequestClockOracle.forSwap(days, zone),
                RequestCap.forSwap(days.map { it.toKotlinLocalDate() }, kZone),
                "forSwap($days, $zone)"
            )
            val current = randomLocal(now, zone)
            val proposed = randomLocal(now, zone)
            assertEquals(
                RequestClockOracle.forChange(current, proposed, now, zone),
                RequestCap.forChange(current.toKotlinLocalDateTime(), proposed.toKotlinLocalDateTime(), now, kZone),
                "forChange($current, $proposed, $now, $zone)"
            )
            assertEquals(
                RequestClockOracle.forEvent(current, now, zone),
                RequestCap.forEvent(current.toKotlinLocalDateTime(), now, kZone),
                "forEvent($current, $now, $zone)"
            )
        }
    }

    @Test
    fun aDayStartsWhereItDidInEveryZone() {
        // Every day of the years that hold the zones' midnight changes, and the day Apia skipped.
        ZONES.map(ZoneId::of).forEach { zone ->
            var day = LocalDate.of(2011, 1, 1)
            while (day.year < 2019) {
                // A swap's cap is its first day's start, which is how the cap reads a start of day.
                assertEquals(
                    RequestClockOracle.startOfDay(day, zone),
                    RequestCap.forSwap(listOf(day.toKotlinLocalDate()), zone.toKotlinTimeZone()),
                    "start of $day in $zone"
                )
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun theHistoryReadsAnInstantOnTheSameWallClock() {
        repeat(ITERATIONS) {
            val zone = randomZone()
            val millis = randomMoment(zone)
            assertEquals(
                RequestClockOracle.localOf(millis, zone),
                KInstant.fromEpochMilliseconds(millis).toLocalDateTime(zone.toKotlinTimeZone()).toJavaLocalDateTime(),
                "localOf($millis, $zone)"
            )
        }
    }

    @Test
    fun aMonthHasTheSameDays() {
        repeat(ITERATIONS) {
            val month = YearMonth.of(random.nextInt(1600, 2400), random.nextInt(1, 13))
            val kMonth = month.toKotlinYearMonth()
            assertEquals(
                RequestClockOracle.daysOf(month).map { it.toKotlinLocalDate() },
                (1..kMonth.numberOfDays).map { kMonth.onDay(it) },
                "$month"
            )
            val date = month.atDay(1).plusDays(random.nextLong(-40, 70))
            assertEquals(
                RequestClockOracle.inMonth(date, month),
                date.toKotlinLocalDate().yearMonth == kMonth,
                "$date in $month"
            )
        }
    }

    // ---- inputs -----------------------------------------------------------------------------------

    private fun randomZone(): ZoneId = ZoneId.of(ZONES[random.nextInt(ZONES.size)])

    /** A moment within three hours of one of [zone]'s transitions half the time, else 1990–2040. */
    private fun randomMoment(zone: ZoneId): Long {
        val anywhere = random.nextLong(FROM_MILLIS, UNTIL_MILLIS)
        if (random.nextBoolean()) return anywhere
        val transition = zone.rules.nextTransition(Instant.ofEpochMilli(anywhere)) ?: return anywhere
        return transition.instant.toEpochMilli() + random.nextLong(-3 * HOUR, 3 * HOUR + 1)
    }

    /** A cap: none, or one near a threshold the offer branches at, or anywhere up to 40 days. */
    private fun randomCap(now: Long): Long? = when (random.nextInt(5)) {
        0 -> null
        1 -> now + 2 * HOUR + random.nextLong(-MINUTE, MINUTE + 1)
        2 -> now + 48 * HOUR + random.nextLong(-MINUTE, MINUTE + 1)
        3 -> now + random.nextLong(-HOUR, 3 * DAY)
        else -> now + random.nextLong(-HOUR, 40 * DAY)
    }

    /** A sending-to-deadline window around the three counts' edges, from empty to a month. */
    private fun randomWindow(): Long = when (random.nextInt(4)) {
        0 -> 6 * HOUR + random.nextLong(-MINUTE, MINUTE + 1)
        1 -> DAY + random.nextLong(-MINUTE, MINUTE + 1)
        2 -> random.nextLong(-HOUR, 6 * HOUR)
        else -> random.nextLong(0, 31 * DAY)
    }

    private fun randomPick(now: Long, zone: ZoneId): Pair<OldPick, AnswerByPick> =
        if (random.nextBoolean()) {
            val preset = AnswerByPreset.entries[random.nextInt(AnswerByPreset.entries.size)]
            OldPick.Preset(preset) to AnswerByPick.Preset(preset)
        } else {
            val date = randomDay(now, zone)
            OldPick.OnDate(date) to AnswerByPick.OnDate(date.toKotlinLocalDate())
        }

    /** A day from a week before [now] to 40 days after it, on [zone]'s calendar. */
    private fun randomDay(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(random.nextLong(-7, 41))

    /** A wall-clock time near [now] on [zone]'s calendar, at a minute that may sit in a gap or a repeat. */
    private fun randomLocal(now: Long, zone: ZoneId) =
        Instant.ofEpochMilli(now + random.nextLong(-2 * DAY, 10 * DAY)).atZone(zone).toLocalDateTime()
            .withMinute(random.nextInt(60))
            .withSecond(if (random.nextInt(4) == 0) random.nextInt(60) else 0)

    /** [text] unchanged, or with one character dropped, doubled or replaced. */
    private fun mutate(text: String): String {
        if (random.nextInt(3) == 0) return text
        val at = random.nextInt(text.length)
        return when (random.nextInt(3)) {
            0 -> text.removeRange(at, at + 1)
            1 -> text.substring(0, at + 1) + text.substring(at)
            else -> text.substring(0, at) + "0-+:9TXa"[random.nextInt(8)] + text.substring(at + 1)
        }
    }

    private fun AnswerByOffer.toOld() = RequestClockOracle.OldOffer(
        band = band,
        nowMillis = nowMillis,
        chips = chips.map { chip ->
            RequestClockOracle.OldChip((chip.pick as AnswerByPick.Preset).preset, chip.answerByMillis, chip.compressed)
        },
        fixedMillis = fixedMillis,
        latestMillis = latestMillis
    )

    private companion object {
        const val ITERATIONS = 5_000
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        /** 1990-01-01 and 2040-01-01, in epoch millis. */
        const val FROM_MILLIS = 631_152_000_000L
        const val UNTIL_MILLIS = 2_208_988_800_000L

        /** -9999-01-01 and +19999-12-31, epoch days: dates every encoder writes, signs included. */
        val MIN_EPOCH_DAY = LocalDate.of(-9999, 1, 1).toEpochDay()
        val MAX_EPOCH_DAY = LocalDate.of(19999, 12, 31).toEpochDay()

        val ZONES = listOf(
            "Europe/Prague", "America/New_York", "America/Sao_Paulo", "America/Havana", "Asia/Beirut",
            "Pacific/Apia", "Australia/Lord_Howe", "America/St_Johns", "Asia/Kolkata", "Europe/Dublin",
            "Africa/Casablanca", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC"
        )
    }
}
