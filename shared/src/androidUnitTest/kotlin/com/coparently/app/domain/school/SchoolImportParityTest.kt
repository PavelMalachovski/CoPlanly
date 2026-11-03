package com.coparently.app.domain.school

import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * The school import's derived ids and fingerprints against the `java.time` code that made them
 * before the import's domain moved to kotlinx-datetime (MON-24).
 *
 * Both are stored: an id is the imported event's id on both phones, and a fingerprint is the
 * connection's record of what the school last sent. A byte of difference would make every
 * imported event a new one, or rewrite every event once. The old code hashed
 * `String.toByteArray(UTF_8)` of `java.time`'s own `toString()` texts; this draws titles with
 * accents, emoji and unpaired surrogates, and times with seconds, fractions and five-digit years.
 */
class SchoolImportParityTest {

    private val random = Random(SEED)

    @Test
    fun `a fingerprint is the hash java time and the JVM's UTF-8 made`() {
        repeat(SAMPLES) {
            val title = text()
            val description = if (random.nextBoolean()) null else text()
            val start = dateTime()
            val end = if (random.nextInt(4) == 0) null else dateTime()
            val old = sha256Hex(listOf(title, description.orEmpty(), start, end ?: "").joinToString("\u0000")).take(16)
            val fields = ImportFields(title, description, start.toKotlinLocalDateTime(), end?.toKotlinLocalDateTime())
            assertEquals(old, fields.fingerprint, "fingerprint of $title, $start, $end")
        }
    }

    @Test
    fun `an id is the hash java time and the JVM's UTF-8 made`() {
        repeat(SAMPLES) {
            val target = target(baseUrl = "https://${text()}.example/bakalari", username = text())
            val key = text()
            val kind = SchoolImportKind.entries[random.nextInt(SchoolImportKind.entries.size)]
            val old = oldId(target, kind.stored, key)
            assertEquals(old, SchoolImportPlanner.idOf(target, kind, key), "id of $kind under $key")
            val day = dateTime().toLocalDate()
            val date = day.toKotlinLocalDate()
            assertEquals(oldId(target, "hours", day.toString()), SchoolImportIds.hours(target, date))
            assertEquals(oldId(target, "dayoff", day.toString()), SchoolImportIds.dayOff(target, date))
        }
    }

    @Test
    fun `the Monday of a week is the one java time named`() {
        var day = LocalDate.of(2025, 12, 1)
        while (day.isBefore(LocalDate.of(2027, 1, 1))) {
            val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
            assertEquals(monday.toKotlinLocalDate(), SchoolWeek.mondayOf(day.toKotlinLocalDate()), "week of $day")
            day = day.plusDays(1)
        }
    }

    private fun oldId(target: SchoolImportTarget, kind: String, key: String): String =
        "school-" + sha256Hex("bakalari|${target.baseUrl}|${target.username}|$kind|$key").take(32)

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Text from a small alphabet that holds what a hash could get wrong. */
    private fun text(): String = buildString {
        repeat(random.nextInt(0, 12)) { append(ALPHABET[random.nextInt(ALPHABET.size)]) }
    }

    private fun dateTime(): LocalDateTime {
        val year = if (random.nextInt(20) == 0) random.nextInt(10_000, 20_000) else random.nextInt(2000, 2100)
        val base = LocalDateTime.of(year, 1, 1, 0, 0).plusDays(random.nextLong(0, 365))
            .plusMinutes(random.nextLong(0, 24 * 60))
        return when (random.nextInt(4)) {
            0 -> base.plusSeconds(random.nextLong(1, 60))
            1 -> base.plusNanos(random.nextLong(1, 1_000_000_000))
            else -> base
        }
    }

    private fun target(baseUrl: String, username: String) = SchoolImportTarget(
        baseUrl = baseUrl,
        username = username,
        childId = "child",
        familyId = null,
        ownerUid = "owner",
        ownerSlot = "mom"
    )

    private companion object {
        const val SEED = 20261103L
        const val SAMPLES = 5_000
        val ALPHABET = listOf("a", "Z", "č", "ř", "ü", "ß", "Ж", "|", " ", "\u0000", "😀", "\uD800", "\uDC00", "1")
    }
}
