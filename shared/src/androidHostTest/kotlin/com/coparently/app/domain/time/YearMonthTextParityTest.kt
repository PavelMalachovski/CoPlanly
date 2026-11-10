package com.coparently.app.domain.time

import kotlinx.datetime.toKotlinYearMonth
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * [IsoText.yearMonth] and [IsoText.parseYearMonth] against `java.time.YearMonth`, which wrote and
 * read a payment's month, child support's first month and a settle-up card's month until MON-24
 * phase 2 (Task 8e): random months across java.time's whole year range, and random and mutated
 * text.
 */
class YearMonthTextParityTest {

    @Test
    fun writerMatchesToString() {
        val random = Random(SEED)
        val corpus = listOf(
            -999_999_999, -10_000, -9999, -1000, -999, -1, 0, 1, 999, 1000, 2026, 9999, 10_000, 999_999_999
        )
        val years = corpus + List(RANDOM_CASES) { randomYear(random) }
        years.forEach { year ->
            val month = random.nextInt(1, MONTHS + 1)
            val java = java.time.YearMonth.of(year, month)
            assertEquals(java.toString(), IsoText.yearMonth(java.toKotlinYearMonth()), "yearMonth($year, $month)")
        }
    }

    @Test
    fun readerMatchesParseOnRandomText() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val year = randomYear(random)
            val valid = java.time.YearMonth.of(year, random.nextInt(1, MONTHS + 1)).toString()
            assertSameOutcome(valid)
            assertSameOutcome(mutate(valid, random))
            assertSameOutcome(buildString { repeat(random.nextInt(0, MAX_TEXT)) { append(ALPHABET.random(random)) } })
        }
    }

    private fun assertSameOutcome(text: String) {
        val old = runCatching { java.time.YearMonth.parse(text).toKotlinYearMonth() }
        val new = runCatching { IsoText.parseYearMonth(text) }
        assertEquals(old.getOrNull(), new.getOrNull(), "parseYearMonth(\"$text\")")
        if (old.isFailure) {
            assertEquals(true, new.exceptionOrNull() is IllegalArgumentException, "parseYearMonth(\"$text\") fails")
        }
    }

    private fun randomYear(random: Random): Int = when (random.nextInt(4)) {
        0 -> random.nextInt(-999_999_999, 1_000_000_000)
        1 -> random.nextInt(-20_000, 20_001)
        else -> random.nextInt(1900, 2200)
    }

    private fun mutate(text: String, random: Random): String {
        val at = random.nextInt(text.length + 1)
        return when (random.nextInt(3)) {
            0 -> text.substring(0, at) + ALPHABET.random(random) + text.substring(at)
            1 -> if (at < text.length) text.removeRange(at, at + 1) else text
            else -> if (at < text.length) text.replaceRange(at, at + 1, ALPHABET.random(random).toString()) else text
        }
    }

    private companion object {
        const val SEED = 20261103
        const val RANDOM_CASES = 10_000
        const val MONTHS = 12
        const val MAX_TEXT = 10
        const val ALPHABET = "0123456789012345678901234567890123456789--+ T/"
    }
}
