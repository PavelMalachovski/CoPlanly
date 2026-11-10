package com.coparently.app.data.local

import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [Converters] against the `java.time` converter it replaced (MON-24 phase 2), in both directions.
 *
 * The entities moved to common code, so their date-time columns are converted with
 * `kotlinx.datetime` types now. The text in the columns may not change by one byte: phones hold
 * these rows, the sync compares a row whole, an older build reads what this one writes, and the
 * calendar's range queries (`EventDao`, `ExpenseDao`, `JournalDao`) compare the columns as text, so
 * a dropped `:00` or a fraction printed as `.500` instead of `.5` would sort an event out of its
 * day. The old converter is copied below verbatim, as the oracle.
 *
 * Writing: every value gives the same text. Reading: every text gives the same value, and a text
 * the old converter refused is refused (the old one threw `DateTimeParseException`, the new one
 * `IllegalArgumentException`; nothing catches either around a DAO read, so a refusal is a refusal).
 */
class ConvertersParityTest {

    private val converters = Converters()

    @Test
    fun `a date-time is written as ISO_LOCAL_DATE_TIME wrote it`() {
        fixedDateTimes().forEach(::assertWritesTheSame)
        val random = Random(SEED)
        repeat(CASES) { assertWritesTheSame(randomDateTime(random)) }
    }

    @Test
    fun `a date is written as ISO_LOCAL_DATE wrote it`() {
        fixedDates().forEach(::assertWritesTheSame)
        val random = Random(SEED)
        repeat(CASES) { assertWritesTheSame(randomDate(random)) }
    }

    @Test
    fun `every date-time text the old converter wrote is read back to the same value`() {
        val random = Random(SEED)
        (fixedDateTimes() + List(CASES) { randomDateTime(random) }).forEach { value ->
            val text = OldConverters.fromLocalDateTime(value)!!
            assertEquals(value, converters.toLocalDateTime(text)?.toJavaLocalDateTime(), text)
        }
    }

    @Test
    fun `every date text the old converter wrote is read back to the same value`() {
        val random = Random(SEED)
        (fixedDates() + List(CASES) { randomDate(random) }).forEach { value ->
            val text = OldConverters.fromLocalDate(value)!!
            assertEquals(value, converters.toLocalDate(text)?.toJavaLocalDate(), text)
        }
    }

    @Test
    fun `a date-time text is accepted and refused as before`() {
        dateTimeTexts().forEach { text ->
            assertReadsTheSame(text, { OldConverters.toLocalDateTime(it) }) {
                converters.toLocalDateTime(it)?.toJavaLocalDateTime()
            }
        }
        val random = Random(SEED)
        repeat(CASES) {
            val text = mutate(OldConverters.fromLocalDateTime(randomDateTime(random))!!, random)
            assertReadsTheSame(text, { OldConverters.toLocalDateTime(it) }) {
                converters.toLocalDateTime(it)?.toJavaLocalDateTime()
            }
        }
    }

    @Test
    fun `a date text is accepted and refused as before`() {
        dateTexts().forEach { text ->
            assertReadsTheSame(text, { OldConverters.toLocalDate(it) }) {
                converters.toLocalDate(it)?.toJavaLocalDate()
            }
        }
        val random = Random(SEED)
        repeat(CASES) {
            val text = mutate(OldConverters.fromLocalDate(randomDate(random))!!, random)
            assertReadsTheSame(text, { OldConverters.toLocalDate(it) }) {
                converters.toLocalDate(it)?.toJavaLocalDate()
            }
        }
    }

    @Test
    fun `null stays null in both directions`() {
        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun `text order is time order within the years the app stores`() {
        // The range queries compare the columns as text. That was true of the old text for every
        // year from 0 to 9999, and it is the same text, but say it where it is relied on.
        val random = Random(SEED)
        val values = List(CASES) { randomDateTime(random, years = 0..9999) }.sorted()
        val texts = values.map { converters.fromLocalDateTime(it.toKotlinLocalDateTime())!! }
        assertEquals(texts.sorted(), texts)
    }

    private fun assertWritesTheSame(value: LocalDateTime) {
        assertEquals(
            OldConverters.fromLocalDateTime(value),
            converters.fromLocalDateTime(value.toKotlinLocalDateTime()),
            "write $value"
        )
    }

    private fun assertWritesTheSame(value: LocalDate) {
        assertEquals(
            OldConverters.fromLocalDate(value),
            converters.fromLocalDate(value.toKotlinLocalDate()),
            "write $value"
        )
    }

    private fun <T> assertReadsTheSame(text: String, old: (String) -> T, new: (String) -> T) {
        val expected = runCatching { old(text) }
        val actual = runCatching { new(text) }
        when {
            expected.isSuccess && actual.isSuccess ->
                assertEquals(expected.getOrNull(), actual.getOrNull(), "read \"$text\"")
            expected.isFailure && actual.isFailure -> {
                if (actual.exceptionOrNull() !is IllegalArgumentException) {
                    fail("read \"$text\" failed with ${actual.exceptionOrNull()}, not IllegalArgumentException")
                }
            }
            expected.isSuccess -> fail(
                "read \"$text\": the old converter read ${expected.getOrNull()}, " +
                    "the new one threw ${actual.exceptionOrNull()}"
            )
            else -> fail(
                "read \"$text\": the old converter threw ${expected.exceptionOrNull()}, " +
                    "the new one read ${actual.getOrNull()}"
            )
        }
    }

    private fun fixedDateTimes(): List<LocalDateTime> {
        val times = listOf(
            0 to 0, // midnight
            0 to 1, // a nanosecond past midnight
            12 * HOUR + 30 * MINUTE to 0, // seconds of zero, minutes not
            9 * HOUR + 15 * MINUTE + 7 to 0,
            9 * HOUR + 15 * MINUTE + 7 to 500_000_000, // half a second: ".5", not ".500"
            9 * HOUR + 15 * MINUTE + 7 to 123_000_000, // a millisecond value
            9 * HOUR + 15 * MINUTE + 7 to 120_000_000,
            9 * HOUR + 15 * MINUTE + 7 to 123_456_000, // a microsecond value
            9 * HOUR + 15 * MINUTE + 7 to 123_400_000,
            9 * HOUR + 15 * MINUTE + 7 to 123_456_789, // a nanosecond value
            9 * HOUR + 15 * MINUTE + 7 to 1_000,
            9 * HOUR + 15 * MINUTE + 7 to 1,
            23 * HOUR + 59 * MINUTE + 59 to 999_999_999, // the last instant of a day
            23 * HOUR + 59 * MINUTE to 0 // `AllDayEvent.LAST_MINUTE`
        )
        return fixedDates().flatMap { date ->
            times.map { (second, nano) -> date.atStartOfDay().plusSeconds(second.toLong()).withNano(nano) }
        }
    }

    private fun fixedDates(): List<LocalDate> = listOf(
        LocalDate.of(2026, 11, 2),
        LocalDate.of(2024, 2, 29), // a leap day
        LocalDate.of(2000, 1, 1),
        LocalDate.of(1970, 1, 1),
        LocalDate.of(1900, 12, 31),
        LocalDate.of(999, 6, 15), // a three-digit year: padded to four
        LocalDate.of(1, 1, 1),
        LocalDate.of(0, 1, 1),
        LocalDate.of(-1, 12, 31), // a sign below year 0
        LocalDate.of(-12345, 3, 4),
        LocalDate.of(9999, 12, 31), // the last four-digit year
        LocalDate.of(10_000, 1, 1), // a sign above it
        LocalDate.of(123_456, 7, 8),
        LocalDate.MIN,
        LocalDate.MAX
    )

    private fun dateTimeTexts(): List<String> = listOf(
        "2026-11-02T09:15:07",
        "2026-11-02T09:15", // `LocalDateTime.toString()` with seconds of zero
        "2026-11-02t09:15:07", // a lower-case separator
        "2026-11-02T09:15:07.",
        "2026-11-02T09:15:07.5",
        "2026-11-02T09:15:07.500",
        "2026-11-02T09:15:07.123456789",
        "2026-11-02T09:15:07.1234567891",
        "2026-11-02T24:00:00",
        "2026-11-02T23:60:00",
        "2026-11-02T23:59:60",
        "2023-02-29T10:00:00",
        "2024-02-29T10:00:00",
        "2026-11-02 09:15:07",
        "2026-11-02T09:15:07Z",
        "2026-11-02T09:15:07+01:00",
        "2026-11-02T9:15:07",
        "2026-11-02T09:15:7",
        "2026-11-2T09:15:07",
        "+2026-11-02T09:15:07",
        "+12026-11-02T09:15:07",
        "-0001-12-31T00:00:00",
        "0000-01-01T00:00:00",
        "-0000-01-01T00:00:00",
        "999-01-01T00:00:00",
        "10000-01-01T00:00:00",
        "+999999999-12-31T23:59:59.999999999",
        "+1000000000-01-01T00:00:00",
        "2026-11-02T09:15:07 ",
        " 2026-11-02T09:15:07",
        "２０２６-11-02T09:15:07",
        "",
        "T",
        "2026-11-02"
    )

    private fun dateTexts(): List<String> = listOf(
        "2026-11-02",
        "2024-02-29",
        "2023-02-29",
        "2026-04-31",
        "2026-13-01",
        "2026-00-01",
        "2026-11-00",
        "0000-01-01",
        "-0001-01-01",
        "-0000-01-01",
        "+10000-01-01",
        "+9999-01-01",
        "10000-01-01",
        "999-01-01",
        "+999999999-12-31",
        "-999999999-01-01",
        "+1000000000-01-01",
        "2026-11-02T00:00",
        "2026-11-2",
        "2026/11/02",
        " 2026-11-02",
        "2026-11-02 ",
        ""
    )

    private fun randomDateTime(random: Random, years: IntRange = -10_000..20_000): LocalDateTime {
        val date = randomDate(random, years)
        val second = random.nextInt(SECONDS_PER_DAY)
        val nano = when (random.nextInt(FRACTION_SHAPES)) {
            0 -> 0
            1 -> random.nextInt(MILLIS_PER_SECOND) * NANOS_PER_MILLI
            2 -> random.nextInt(MICROS_PER_SECOND) * NANOS_PER_MICRO
            else -> random.nextInt(NANOS_PER_SECOND)
        }
        val time = if (random.nextInt(SECOND_SHAPES) == 0) second - second % MINUTE else second
        return date.atStartOfDay().plusSeconds(time.toLong()).withNano(nano)
    }

    private fun randomDate(random: Random, years: IntRange = -10_000..20_000): LocalDate {
        val year = when (random.nextInt(YEAR_SHAPES)) {
            0 -> random.nextInt(years.first, years.last + 1)
            else -> random.nextInt(COMMON_FIRST_YEAR, COMMON_LAST_YEAR + 1)
        }.coerceIn(years)
        return LocalDate.ofYearDay(year, 1).plusDays(random.nextLong(DAYS_PER_YEAR.toLong()))
    }

    /** Valid text with one character replaced, removed or added, to reach the refusals. */
    private fun mutate(text: String, random: Random): String {
        val at = random.nextInt(text.length + 1)
        val char = MUTATION_CHARS[random.nextInt(MUTATION_CHARS.length)]
        return when (random.nextInt(MUTATIONS)) {
            0 -> text
            1 -> if (at < text.length) text.removeRange(at, at + 1) else text
            2 -> text.substring(0, at) + char + text.substring(at)
            else -> if (at < text.length) text.substring(0, at) + char + text.substring(at + 1) else text
        }
    }

    /** The converter as it was before MON-24 phase 2, copied verbatim: the oracle. */
    private object OldConverters {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun fromLocalDateTime(value: LocalDateTime?): String? {
            return value?.format(formatter)
        }

        fun toLocalDateTime(value: String?): LocalDateTime? {
            return value?.let { LocalDateTime.parse(it, formatter) }
        }

        fun fromLocalDate(value: LocalDate?): String? {
            return value?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }

        fun toLocalDate(value: String?): LocalDate? {
            return value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
        }
    }

    private companion object {
        const val SEED = 20_261_102
        const val CASES = 5_000
        const val MINUTE = 60
        const val HOUR = 3_600
        const val SECONDS_PER_DAY = 86_400
        const val DAYS_PER_YEAR = 365
        const val MILLIS_PER_SECOND = 1_000
        const val MICROS_PER_SECOND = 1_000_000
        const val NANOS_PER_SECOND = 1_000_000_000
        const val NANOS_PER_MILLI = 1_000_000
        const val NANOS_PER_MICRO = 1_000
        const val FRACTION_SHAPES = 4
        const val SECOND_SHAPES = 3
        const val YEAR_SHAPES = 4
        const val MUTATIONS = 4
        const val COMMON_FIRST_YEAR = 1900
        const val COMMON_LAST_YEAR = 2100
        const val MUTATION_CHARS = "0123456789-:T.+tZ "
    }
}
