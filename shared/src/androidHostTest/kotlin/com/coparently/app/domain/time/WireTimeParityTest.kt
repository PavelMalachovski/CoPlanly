package com.coparently.app.domain.time

import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.export.RecordTimeText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime
import org.junit.Test
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Every wire and record time writer and reader against the `java.time` code it replaced
 * ([WireTimeOracle]), on 5 000 seeded random inputs each (`Random(20261101)`).
 *
 * A writer must produce the same text, and throw where the old one threw. A reader must return the
 * same value for every text the old one read, and fail for every text it refused — with
 * `IllegalArgumentException` where `java.time` threw a `DateTimeException`, and
 * `ArithmeticException` where it threw that, because each caller maps a failure without looking
 * at its type beyond that. The random text is built from the grammar's pieces with wrong lengths,
 * signs and separators mixed in, and from valid text mutated a character at a time, so the
 * refusals are exercised as hard as the acceptances.
 */
class WireTimeParityTest {

    private val zones: List<String> = (ZoneId.getAvailableZoneIds().sorted() + FIXED_OFFSETS)

    @Test
    fun writersMatchOnRandomInstants() {
        val random = Random(SEED)
        repeat(CASES) {
            val millis = randomMillis(random)
            val zone = zones[random.nextInt(zones.size)]
            val wire = WireTimeOracle.toWire(millis)
            assertEquals(wire, EventTimestamp.toWire(millis), "event toWire($millis)")
            assertEquals(wire, CustodyTimestamp.toWire(millis), "custody toWire($millis)")
            assertSameOutcome("event fromWire($wire)", { WireTimeOracle.eventFromWire(wire) }) {
                EventTimestamp.fromWire(wire)
            }
            assertEquals(
                WireTimeOracle.custodyFromWire(wire),
                CustodyTimestamp.fromWire(wire),
                "custody fromWire($wire)"
            )
            assertEquals(
                WireTimeOracle.toWallClock(millis, ZoneId.of(zone)),
                EventTimestamp.toWallClock(millis, TimeZone.of(zone)).toJavaLocalDateTime(),
                "toWallClock($millis, $zone)"
            )
            assertEquals(
                WireTimeOracle.recordInstant(millis, ZoneId.of(zone)),
                RecordTimeText.instant(millis, TimeZone.of(zone)),
                "record instant($millis, $zone)"
            )
        }
    }

    @Test
    fun recordInstantMatchesInEveryZoneAndOffset() {
        // Every zone at least once, local mean time before 1900 included, so an offset of a few
        // seconds (printed `+00:00`) and one with seconds past the minute are both reached.
        val random = Random(SEED)
        zones.forEach { zone ->
            repeat(ZONE_SAMPLES) {
                val millis = random.nextLong(YEAR_1800_MILLIS, YEAR_2200_MILLIS)
                assertEquals(
                    WireTimeOracle.recordInstant(millis, ZoneId.of(zone)),
                    RecordTimeText.instant(millis, TimeZone.of(zone)),
                    "record instant($millis, $zone)"
                )
            }
        }
    }

    @Test
    fun writersMatchOnRandomWallClocks() {
        val random = Random(SEED)
        repeat(CASES) {
            val local = randomLocalDateTime(random)
            val zone = zones[random.nextInt(zones.size)]
            val kotlin = local.toKotlinLocalDateTime()
            assertSameOutcome("ofWallClock($local, $zone)", { WireTimeOracle.ofWallClock(local, ZoneId.of(zone)) }) {
                EventTimestamp.ofWallClock(kotlin, TimeZone.of(zone))
            }
            assertEquals(WireTimeOracle.dateTime(local), IsoText.dateTime(kotlin), "dateTime($local)")
            assertEquals(WireTimeOracle.recordWallClock(local), RecordTimeText.wallClock(kotlin), "wallClock($local)")
            assertEquals(WireTimeOracle.date(local.toLocalDate()), IsoText.date(kotlin.date), "date($local)")
            assertEquals(
                WireTimeOracle.recordDate(local.toLocalDate()),
                RecordTimeText.date(kotlin.date),
                "record date($local)"
            )
            assertSameOutcome("basicDate($local)", { WireTimeOracle.basicDate(local.toLocalDate()) }) {
                IsoText.basicDate(kotlin.date)
            }
            assertEquals(
                WireTimeOracle.hourMinute(local.toLocalTime()),
                IsoText.hourMinute(kotlin.time),
                "hourMinute($local)"
            )
        }
    }

    @Test
    fun wallClocksMatchAroundEveryTransition() {
        // Every gap and overlap from 1990 to 2040, in every zone the JVM knows: the minutes on
        // both sides of each transition, and inside it.
        zones.forEach { id ->
            val javaZone = ZoneId.of(id)
            val zone = TimeZone.of(id)
            var transition = javaZone.rules.nextTransition(Instant.parse(TRANSITIONS_FROM))
            while (transition != null && transition.instant.isBefore(Instant.parse(TRANSITIONS_TO))) {
                val edges = listOf(transition.dateTimeBefore, transition.dateTimeAfter)
                edges.flatMap { edge -> TRANSITION_OFFSETS_MINUTES.map { edge.plusMinutes(it) } }.forEach { local ->
                    assertEquals(
                        WireTimeOracle.ofWallClock(local, javaZone),
                        EventTimestamp.ofWallClock(local.toKotlinLocalDateTime(), zone),
                        "ofWallClock($local, $id)"
                    )
                }
                transition = javaZone.rules.nextTransition(transition.instant)
            }
        }
    }

    @Test
    fun dateTimeReadersMatchOnGrammarText() {
        val random = Random(SEED)
        repeat(CASES) { assertDateTimeReadersAgree(grammarDateTime(random)) }
    }

    @Test
    fun dateTimeReadersMatchOnMutatedText() {
        val random = Random(SEED)
        repeat(CASES) {
            val valid = WireTimeOracle.toWire(randomMillis(random)) + validOffset(random)
            assertDateTimeReadersAgree(mutate(valid, random, DATE_TIME_ALPHABET))
        }
    }

    @Test
    fun hourMinuteReaderMatchesOnRandomText() {
        val random = Random(SEED)
        repeat(CASES) {
            val text = if (random.nextBoolean()) {
                digits(random, random.nextInt(0, 4)) + pick(random, listOf(":", ":", ".", "", "-")) +
                    digits(random, random.nextInt(0, 4))
            } else {
                mutate(
                    WireTimeOracle.hourMinute(LocalTime.of(random.nextInt(24), random.nextInt(60))),
                    random,
                    TIME_ALPHABET
                )
            }
            assertSameOutcome("parseHourMinute($text)", { WireTimeOracle.parseHourMinute(text) }) {
                IsoText.parseHourMinute(text).toJavaLocalTime()
            }
        }
        // Every two-digit pair, both sides: the whole of the pattern's numeric range.
        (0..99).forEach { hour ->
            (0..99).forEach { minute ->
                val text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                assertSameOutcome("parseHourMinute($text)", { WireTimeOracle.parseHourMinute(text) }) {
                    IsoText.parseHourMinute(text).toJavaLocalTime()
                }
            }
        }
    }

    @Test
    fun dateReaderMatchesOnRandomText() {
        val random = Random(SEED)
        repeat(CASES) {
            val text = if (random.nextBoolean()) {
                grammarDate(random)
            } else {
                mutate(WireTimeOracle.date(randomLocalDateTime(random).toLocalDate()), random, DATE_TIME_ALPHABET)
            }
            assertSameOutcome("parseDate($text)", { WireTimeOracle.parseDate(text) }) {
                IsoText.parseDate(text).toJavaLocalDate()
            }
        }
    }

    @Test
    fun convertersAreLossless() {
        // The app hands java.time values to these functions through kotlinx-datetime's converters
        // until Task 7; a conversion that lost anything would be a difference this file misses.
        val random = Random(SEED)
        repeat(CASES) {
            val local = randomLocalDateTime(random)
            assertEquals(local, local.toKotlinLocalDateTime().toJavaLocalDateTime())
            assertEquals(local.toLocalDate(), local.toLocalDate().toKotlinLocalDate().toJavaLocalDate())
            assertEquals(local.toLocalTime(), local.toLocalTime().toKotlinLocalTime().toJavaLocalTime())
        }
    }

    private fun assertDateTimeReadersAgree(text: String) {
        assertSameOutcome("event fromWire($text)", { WireTimeOracle.eventFromWire(text) }) {
            EventTimestamp.fromWire(text)
        }
        assertEquals(WireTimeOracle.custodyFromWire(text), CustodyTimestamp.fromWire(text), "custody fromWire($text)")
        assertSameOutcome("parseDateTime($text)", { WireTimeOracle.parseDateTime(text) }) {
            IsoText.parseDateTime(text).toJavaLocalDateTime()
        }
        assertSameOutcome("parseOffsetWallClock($text)", { OffsetDateTime.parse(text).toLocalDateTime() }) {
            IsoText.parseOffsetWallClock(text).toJavaLocalDateTime()
        }
        // The time half of the same text, which `LocalTime.parse` reads alone.
        val time = text.substringAfter('T', text)
        assertSameOutcome("parseTime($time)", { LocalTime.parse(time) }) {
            IsoText.parseTime(time).toJavaLocalTime()
        }
    }

    /**
     * [old] and [new] both return the same value, or both fail — `new` with the exception kind
     * the callers rely on for the one `old` threw.
     */
    private fun <T> assertSameOutcome(label: String, old: () -> T, new: () -> T) {
        val expected = runCatching(old)
        val actual = runCatching(new)
        val oldFailure = expected.exceptionOrNull()
        val newFailure = actual.exceptionOrNull()
        when {
            oldFailure == null && newFailure == null -> assertEquals(expected.getOrNull(), actual.getOrNull(), label)
            oldFailure == null -> fail("$label: java.time gave ${expected.getOrNull()}, new code threw $newFailure")
            newFailure == null -> fail("$label: java.time threw $oldFailure, new code gave ${actual.getOrNull()}")
            oldFailure is ArithmeticException -> assertKind<ArithmeticException>(label, oldFailure, newFailure)
            oldFailure is DateTimeException -> assertKind<IllegalArgumentException>(label, oldFailure, newFailure)
            else -> fail("$label: java.time threw an unexpected $oldFailure")
        }
    }

    private inline fun <reified E : Throwable> assertKind(label: String, old: Throwable, new: Throwable) {
        if (new !is E) fail("$label: java.time threw $old, new code $new")
    }

    /**
     * [IsoText.dateTimeShortest] against `java.time.LocalDateTime.toString()` itself, the call the
     * writers it replaces made: zero seconds, and fractions in whole milliseconds, microseconds and
     * nanoseconds, are each drawn often.
     */
    @Test
    fun shortestDateTimeMatchesToString() {
        val random = Random(SEED)
        repeat(CASES) {
            val base = randomLocalDateTime(random)
            val second = if (random.nextBoolean()) 0 else base.second
            val nano = when (random.nextInt(5)) {
                0 -> 0
                1 -> random.nextInt(1000) * NANOS_PER_MILLI
                2 -> random.nextInt(1_000_000) * NANOS_PER_MICRO
                else -> base.nano
            }
            val local = base.withSecond(second).withNano(nano)
            assertEquals(local.toString(), IsoText.dateTimeShortest(local.toKotlinLocalDateTime()), "$local")
        }
    }

    private fun randomMillis(random: Random): Long = when (random.nextInt(5)) {
        0 -> random.nextLong()
        1 -> random.nextLong(YEAR_0_MILLIS, YEAR_10000_MILLIS)
        2 -> random.nextLong(YEAR_2020_MILLIS, YEAR_2030_MILLIS) / MILLIS_PER_SECOND * MILLIS_PER_SECOND
        else -> random.nextLong(YEAR_1900_MILLIS, YEAR_2200_MILLIS)
    }

    private fun randomLocalDateTime(random: Random): LocalDateTime {
        val year = when (random.nextInt(6)) {
            0 -> random.nextInt(-999_999_999, 1_000_000_000)
            1 -> random.nextInt(-10_000, 20_000)
            2 -> random.nextInt(0, 10_000)
            else -> random.nextInt(1900, 2200)
        }
        val month = random.nextInt(1, 13)
        val day = random.nextInt(1, LocalDate.of(year, month, 1).lengthOfMonth() + 1)
        val nano = when (random.nextInt(4)) {
            0 -> 0
            1 -> random.nextInt(1000) * NANOS_PER_MILLI
            else -> random.nextInt(NANOS_PER_SECOND)
        }
        return LocalDateTime.of(year, month, day, random.nextInt(24), random.nextInt(60), random.nextInt(60), nano)
    }

    /**
     * A date-time built from the grammar's parts. Each part is right most of the time and wrong now
     * and then — a missing or extra digit, a sign, another separator, a value out of range, a digit
     * from another script — so about a third of these parse and the rest fail one rule at a time.
     */
    private fun grammarDateTime(random: Random): String = buildString {
        append(grammarDate(random))
        append(part(random, "T", listOf("t", " ", "", "TT", "x")))
        append(field(random, 0..23))
        append(part(random, ":", listOf("", ".", "::")))
        append(field(random, 0..59))
        if (random.nextInt(4) > 0) {
            append(part(random, ":", listOf("", ".", "::")))
            append(field(random, 0..59))
            if (random.nextBoolean()) {
                append(part(random, ".", listOf(",", "", "..")))
                append(digits(random, random.nextInt(0, 12)))
            }
        }
        if (random.nextBoolean()) append(grammarOffset(random))
    }

    private fun grammarDate(random: Random): String = buildString {
        val sign = part(random, "", listOf("+", "-", "+-", "--"))
        append(sign)
        val yearDigits = when {
            !right(random) -> random.nextInt(0, 12)
            sign == "+" -> random.nextInt(5, 11)
            sign == "-" -> random.nextInt(4, 11)
            else -> 4
        }
        append(digits(random, yearDigits))
        append(part(random, "-", listOf("", "/", ".", "--")))
        append(field(random, 1..12))
        append(part(random, "-", listOf("", "/", "--")))
        append(field(random, 1..28))
    }

    private fun grammarOffset(random: Random): String =
        if (random.nextInt(4) == 0) {
            part(random, "Z", listOf("z", "ZZ", " Z", "UTC", "GMT", "Z ", "+"))
        } else {
            buildString {
                append(part(random, if (random.nextBoolean()) "+" else "-", listOf("", "--", "+-", "*")))
                append(field(random, 0..18))
                if (random.nextInt(3) > 0) {
                    append(part(random, ":", listOf("", "::", ".")))
                    append(field(random, 0..59))
                    if (random.nextBoolean()) {
                        append(part(random, ":", listOf("", "::", ".")))
                        append(field(random, 0..59))
                    }
                }
            }
        }

    /** Whether a generated part is the right one: nine times in ten. */
    private fun right(random: Random): Boolean = random.nextInt(10) > 0

    /** [usual] most of the time, otherwise one of [wrong]. */
    private fun part(random: Random, usual: String, wrong: List<String>): String =
        if (right(random)) usual else pick(random, wrong)

    /** Two digits in [range] most of the time; otherwise any count of digits, in range or not. */
    private fun field(random: Random, range: IntRange): String =
        if (right(random)) {
            random.nextInt(range.first, range.last + 1).toString().padStart(2, '0')
        } else {
            digits(random, random.nextInt(0, 4))
        }

    private fun validOffset(random: Random): String = when (random.nextInt(4)) {
        0 -> ""
        1 -> pick(random, listOf("Z", "z"))
        else -> {
            val seconds = random.nextInt(-MAX_OFFSET_SECONDS, MAX_OFFSET_SECONDS + 1)
            java.time.ZoneOffset.ofTotalSeconds(seconds).id.let { if (it == "Z") "+00:00" else it }
        }
    }

    /** [text] with one to three characters replaced, inserted, deleted or duplicated. */
    private fun mutate(text: String, random: Random, alphabet: String): String {
        val chars = StringBuilder(text)
        repeat(random.nextInt(1, 4)) {
            val at = if (chars.isEmpty()) 0 else random.nextInt(chars.length)
            val c = alphabet[random.nextInt(alphabet.length)]
            when (random.nextInt(4)) {
                0 -> if (chars.isNotEmpty()) chars.setCharAt(at, c)
                1 -> chars.insert(at, c)
                2 -> if (chars.isNotEmpty()) chars.deleteCharAt(at)
                else -> if (chars.isNotEmpty()) chars.insert(at, chars[at])
            }
        }
        return chars.toString()
    }

    private fun digits(random: Random, count: Int): String = buildString {
        repeat(count) {
            // Now and then a digit from another script, which `java.time` does not read.
            append(
                when (random.nextInt(50)) {
                    0 -> '\u0661'
                    1 -> '\uFF11'
                    else -> '0' + random.nextInt(10)
                }
            )
        }
    }

    private fun <T> pick(random: Random, options: List<T>): T = options[random.nextInt(options.size)]

    private companion object {
        const val SEED = 20261101
        const val CASES = 5_000
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000
        const val NANOS_PER_MICRO = 1_000
        const val NANOS_PER_SECOND = 1_000_000_000
        const val MAX_OFFSET_SECONDS = 18 * 3600
        const val YEAR_0_MILLIS = -62_167_219_200_000L
        const val YEAR_10000_MILLIS = 253_402_300_800_000L
        const val YEAR_1800_MILLIS = -5_364_662_400_000L
        const val YEAR_1900_MILLIS = -2_208_988_800_000L
        const val ZONE_SAMPLES = 20
        const val YEAR_2200_MILLIS = 7_258_118_400_000L
        const val YEAR_2020_MILLIS = 1_577_836_800_000L
        const val YEAR_2030_MILLIS = 1_893_456_000_000L
        const val TRANSITIONS_FROM = "1990-01-01T00:00:00Z"
        const val TRANSITIONS_TO = "2040-01-01T00:00:00Z"
        val TRANSITION_OFFSETS_MINUTES = listOf(-61L, -1L, 0L, 1L, 29L, 30L, 59L, 60L, 61L)
        val FIXED_OFFSETS = listOf("Z", "+05:30", "-03:30", "+14:00", "-12:00", "-00:00:30", "+00:00:30", "-01:00:30")
        const val DATE_TIME_ALPHABET = "0123456789-+:.TtZz ,/"
        const val TIME_ALPHABET = "0123456789:+- ."
    }
}
