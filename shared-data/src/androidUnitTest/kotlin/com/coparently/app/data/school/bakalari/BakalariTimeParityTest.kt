package com.coparently.app.data.school.bakalari

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bakaláři parsers' two time readers against the `java.time` code they replaced (MON-24): a
 * lesson's time ([BakalariTime]) and the school's wall clock (`BakalariJson.wallClock`), on text
 * built from each grammar's pieces with wrong lengths, signs, separators and non-ASCII digits mixed
 * in, so the refusals are exercised as hard as the acceptances.
 */
class BakalariTimeParityTest {

    private val random = Random(SEED)

    @Test
    fun `a lesson time reads as H colon mm and then ISO read it`() {
        val texts = List(SAMPLES) { lessonText() } +
            (0..99).flatMap { hour -> (0..99).map { minute -> "$hour:${minute.toString().padStart(2, '0')}" } } +
            listOf("24:00", "24:01", "0000000000000000008:00", "00000000000000000008:00", "9999999999999999999:00")
        var read = 0
        for (text in texts) {
            val expected = oldLessonTime(text)
            assertEquals(expected, BakalariTime.parse(text)?.toJavaLocalTime(), "lesson time '$text'")
            if (expected != null) read++
        }
        // Both readers' acceptances are reached, not only their refusals.
        assertTrue(read > texts.size / 10, "only $read of ${texts.size} read")
    }

    @Test
    fun `a wall clock reads as OffsetDateTime and then LocalDateTime read it`() {
        var read = 0
        repeat(SAMPLES) {
            val text = wallClockText()
            val expected = oldWallClock(text)
            assertEquals(expected, BakalariJson.wallClock(text)?.toJavaLocalDateTime(), "wall clock '$text'")
            if (expected != null) read++
        }
        assertTrue(read > SAMPLES / 10, "only $read of $SAMPLES read")
    }

    /** The parser's reading before MON-24. */
    private fun oldLessonTime(value: String): LocalTime? = try {
        LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm"))
    } catch (ignored: DateTimeParseException) {
        runCatching { LocalTime.parse(value) }.getOrNull()
    }

    /** `BakalariJson.wallClock` before MON-24, trimming included. */
    private fun oldWallClock(text: String): LocalDateTime? {
        val value = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return parseOrNull { OffsetDateTime.parse(value).toLocalDateTime() }
            ?: parseOrNull { LocalDateTime.parse(value) }
    }

    private fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (ignored: DateTimeParseException) {
        null
    }

    /** Right in each part most of the time, wrong now and then, so about half read. */
    private fun lessonText(): String = buildString {
        append(orElse("", "+", "-", " "))
        append(digits(if (right()) random.nextInt(1, 3) else random.nextInt(0, 22)))
        append(orElse(":", ".", "", "::"))
        append(digits(if (right()) 2 else random.nextInt(0, 4)))
        if (random.nextInt(4) == 0) {
            append(orElse(":", "."))
            append(digits(if (right()) 2 else random.nextInt(0, 3)))
            if (random.nextBoolean()) append(orElse(".", ",") + digits(random.nextInt(0, 11)))
        }
        if (!right()) append(pick(" ", "Z", "+01:00", "a"))
    }

    private fun wallClockText(): String = buildString {
        if (!right()) append(" ")
        append(orElse("", "+", "-") + digits(if (right()) 4 else random.nextInt(3, 11)))
        append(orElse("-", "/", "") + field(1..12) + orElse("-", "") + field(1..28))
        append(orElse("T", "t", " ", ""))
        append(field(0..23) + orElse(":", ".", "") + field(0..59))
        if (random.nextInt(4) > 0) {
            append(":" + field(0..59))
            if (random.nextBoolean()) append(orElse(".", ",") + digits(random.nextInt(0, 11)))
        }
        append(pick("", "Z", "z", "+01:00", "+02:00", "-05:30", "+0100", "+18:00", "+19:00", "+01:00:30", "+1"))
        if (!right()) append(pick(" ", "x", "[Europe/Prague]"))
    }

    private fun field(range: IntRange): String =
        if (right()) {
            random.nextInt(range.first, range.last + 1).toString().padStart(2, '0')
        } else {
            digits(random.nextInt(0, 4))
        }

    /** True nine times in ten. */
    private fun right(): Boolean = random.nextInt(10) > 0

    /** [expected] nine times in ten, otherwise one of [wrong]. */
    private fun orElse(expected: String, vararg wrong: String): String = if (right()) expected else pick(*wrong)

    private fun digits(count: Int): String = buildString {
        repeat(count) { append(if (random.nextInt(30) == 0) '٣' else '0' + random.nextInt(10)) }
    }

    private fun pick(vararg choices: String): String = choices[random.nextInt(choices.size)]

    private companion object {
        const val SEED = 20261103L
        const val SAMPLES = 20_000
    }
}
