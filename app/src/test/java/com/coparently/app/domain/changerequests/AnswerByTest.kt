package com.coparently.app.domain.changerequests

import com.coparently.app.domain.changerequests.AnswerByPick.OnDate
import com.coparently.app.domain.changerequests.AnswerByPick.Preset
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")

/** [text], a local date-time in Prague, as epoch millis. */
private fun at(text: String): Long = LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli()

/** Saturday 10 October 2026, 10:07, still on summer time: the clocks go back on 25 October. */
private val NOW = at("2026-10-10T10:07")

private const val HOUR = 3_600_000L

/**
 * The deadline a proposer can choose (phase 3a, spec §4.2): the presets and a picked date, rounded to
 * the quarter hour, compressed by a near cap, replaced by one fixed time when the cap is two to
 * forty-eight hours away, and absent under two hours.
 */
class AnswerByTest {

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    @Test
    fun `with no cap the presets are tomorrow, three days and a week, rounded up to the quarter hour`() {
        val offer = AnswerBy.offer(NOW, null, ZONE)

        assertEquals(AnswerByBand.CHOICES, offer.band)
        assertEquals(NOW, offer.nowMillis)
        assertEquals(
            listOf(
                AnswerByChip(Preset(AnswerByPreset.TOMORROW), at("2026-10-11T10:15"), compressed = false),
                AnswerByChip(Preset(AnswerByPreset.THREE_DAYS), at("2026-10-13T10:15"), compressed = false),
                AnswerByChip(Preset(AnswerByPreset.WEEK), at("2026-10-17T10:15"), compressed = false)
            ),
            offer.chips
        )
        assertEquals(Preset(AnswerByPreset.THREE_DAYS), AnswerByPick.DEFAULT)
        assertEquals(at("2026-10-13T10:15"), AnswerBy.resolve(AnswerByPick.DEFAULT, NOW, null, ZONE))

        // A time already on a quarter is unchanged; one millisecond past it goes to the next.
        val onTheQuarter = at("2026-10-10T10:15")
        assertEquals(
            at("2026-10-11T10:15"),
            AnswerBy.resolve(Preset(AnswerByPreset.TOMORROW), onTheQuarter, null, ZONE)
        )
        assertEquals(
            at("2026-10-11T10:30"),
            AnswerBy.resolve(Preset(AnswerByPreset.TOMORROW), onTheQuarter + 1, null, ZONE)
        )
    }

    @Test
    fun `a picked date reads eight in the evening`() {
        assertEquals(
            at("2026-10-14T20:00"),
            AnswerBy.resolve(OnDate(date(10, 14)), NOW, null, ZONE)
        )
    }

    @Test
    fun `an option later than a day before the cap reads the latest time`() {
        val cap = at("2026-10-15T00:00")
        val offer = AnswerBy.offer(NOW, cap, ZONE)

        assertEquals(AnswerByBand.CHOICES, offer.band)
        assertEquals(at("2026-10-14T00:00"), offer.latestMillis)
        assertEquals(
            listOf(
                AnswerByChip(Preset(AnswerByPreset.TOMORROW), at("2026-10-11T10:15"), compressed = false),
                AnswerByChip(Preset(AnswerByPreset.THREE_DAYS), at("2026-10-13T10:15"), compressed = false),
                AnswerByChip(Preset(AnswerByPreset.WEEK), at("2026-10-14T00:00"), compressed = true)
            ),
            offer.chips
        )
        assertEquals(at("2026-10-14T00:00"), AnswerBy.resolve(OnDate(date(10, 20)), NOW, cap, ZONE))
        // A date that still leaves the full day keeps its evening.
        assertEquals(at("2026-10-13T20:00"), AnswerBy.resolve(OnDate(date(10, 13)), NOW, cap, ZONE))

        // The latest time is a quarter hour as well: a cap at 09:07 leaves 09:00 the day before.
        val oddCap = at("2026-10-15T09:07")
        assertEquals(at("2026-10-14T09:00"), AnswerBy.offer(NOW, oddCap, ZONE).latestMillis)
        assertEquals(at("2026-10-14T09:00"), AnswerBy.resolve(Preset(AnswerByPreset.WEEK), NOW, oddCap, ZONE))

        // A latest time in the hour the clocks repeat stays in its pass: 24 hours before 01:40 UTC on the 26th
        // is 01:40 UTC on the 25th, which reads 02:40 on winter time and rounds down to 02:30 (01:30 UTC).
        val capInTheRepeat = Instant.parse("2026-10-26T01:40:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(),
            AnswerBy.offer(NOW, capInTheRepeat, ZONE).latestMillis
        )
    }

    @Test
    fun `options that land on the same time merge into one chip`() {
        val cap = at("2026-10-13T08:00")
        val offer = AnswerBy.offer(NOW, cap, ZONE)

        assertEquals(AnswerByBand.CHOICES, offer.band)
        assertEquals(
            listOf(
                AnswerByChip(Preset(AnswerByPreset.TOMORROW), at("2026-10-11T10:15"), compressed = false),
                AnswerByChip(Preset(AnswerByPreset.THREE_DAYS), at("2026-10-12T08:00"), compressed = true)
            ),
            offer.chips
        )
        // The week was merged into the three days' chip, and still resolves to the same time.
        assertEquals(at("2026-10-12T08:00"), AnswerBy.resolve(Preset(AnswerByPreset.WEEK), NOW, cap, ZONE))
        assertEquals(at("2026-10-12T08:00"), AnswerBy.resolve(AnswerByPick.DEFAULT, NOW, cap, ZONE))

        // A latest time that is exactly Tomorrow's leaves Tomorrow as it was asked for: not compressed,
        // and the two longer presets are merged into it.
        val exactFit = AnswerBy.offer(NOW, at("2026-10-12T10:15"), ZONE)
        assertEquals(
            listOf(AnswerByChip(Preset(AnswerByPreset.TOMORROW), at("2026-10-11T10:15"), compressed = false)),
            exactFit.chips
        )
    }

    @Test
    fun `between two and forty-eight hours there is one deadline halfway, rounded down`() {
        val cap = at("2026-10-11T08:00")
        val offer = AnswerBy.offer(NOW, cap, ZONE)

        assertEquals(AnswerByBand.FIXED, offer.band)
        // 21 h 53 min to the cap: halfway is 21:03:30, which reads 21:00.
        assertEquals(at("2026-10-10T21:00"), offer.fixedMillis)
        assertTrue(offer.chips.isEmpty())
        listOf(
            AnswerByPick.DEFAULT,
            Preset(AnswerByPreset.TOMORROW),
            Preset(AnswerByPreset.WEEK),
            OnDate(date(10, 14)),
            OnDate(date(10, 9))
        ).forEach { pick ->
            assertEquals(at("2026-10-10T21:00"), AnswerBy.resolve(pick, NOW, cap, ZONE), "$pick")
        }

        // Two hours is the first lead that gets a deadline; forty-eight is the first that gets choices.
        val twoHours = AnswerBy.offer(NOW, NOW + 2 * HOUR, ZONE)
        assertEquals(AnswerByBand.FIXED, twoHours.band)
        assertEquals(at("2026-10-10T11:00"), twoHours.fixedMillis)
        assertEquals(AnswerByBand.FIXED, AnswerBy.offer(NOW, NOW + 48 * HOUR - 1, ZONE).band)
        assertEquals(AnswerByBand.CHOICES, AnswerBy.offer(NOW, NOW + 48 * HOUR, ZONE).band)

        // The hour from 02:00 to 03:00 on 25 October is lived twice, and halfway can land in the second.
        // 01:50 UTC reads 02:50 on winter time; rounded down it is 02:45 winter time, which is 01:45 UTC.
        // Rounding the local reading and converting it back would pick summer time and land an hour early.
        val beforeTheRepeat = Instant.parse("2026-10-25T00:10:00Z").toEpochMilli()
        val capAfterIt = Instant.parse("2026-10-25T03:30:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-25T01:45:00Z").toEpochMilli(),
            AnswerBy.offer(beforeTheRepeat, capAfterIt, ZONE).fixedMillis
        )
    }

    @Test
    fun `under two hours there is no deadline`() {
        val offer = AnswerBy.offer(NOW, at("2026-10-10T11:30"), ZONE)

        assertEquals(AnswerByBand.TOO_SOON, offer.band)
        assertTrue(offer.chips.isEmpty())
        assertNull(offer.fixedMillis)
        assertNull(offer.latestMillis)
        assertNull(AnswerBy.resolve(AnswerByPick.DEFAULT, NOW, at("2026-10-10T11:30"), ZONE))
        assertNull(AnswerBy.resolve(OnDate(date(10, 14)), NOW, at("2026-10-10T11:30"), ZONE))

        // The edge, and a cap that is already behind (a swap for today starts at 00:00 today).
        assertEquals(AnswerByBand.TOO_SOON, AnswerBy.offer(NOW, NOW + 2 * HOUR - 1, ZONE).band)
        assertEquals(AnswerByBand.TOO_SOON, AnswerBy.offer(NOW, at("2026-10-10T00:00"), ZONE).band)
        assertNull(AnswerBy.resolve(AnswerByPick.DEFAULT, NOW, at("2026-10-10T00:00"), ZONE))
    }

    @Test
    fun `a picked date too near falls back to tomorrow, and nothing is further than 31 days`() {
        // Too near: today's evening is an hour away, so the pick reads the Tomorrow time instead.
        val evening = at("2026-10-10T19:00")
        assertEquals(at("2026-10-11T19:00"), AnswerBy.resolve(OnDate(date(10, 10)), evening, null, ZONE))
        assertEquals(at("2026-10-11T19:00"), AnswerBy.resolve(OnDate(date(9, 1)), evening, null, ZONE))

        // Too far: 31 days after 10:07 is 09:07 by the clock, because the clocks go back on 25 October;
        // the bound is rounded down to 09:00.
        val farthest = at("2026-11-10T09:00")
        assertEquals(farthest, AnswerBy.offer(NOW, null, ZONE).latestMillis)
        assertEquals(farthest, AnswerBy.resolve(OnDate(date(12, 31)), NOW, null, ZONE))
        assertTrue(farthest <= NOW + AnswerBy.MAX_AHEAD_MILLIS)

        // A cap further away than that does not lift the bound: the rules refuse a longer deadline.
        val farCap = at("2027-01-31T00:00")
        assertEquals(farthest, AnswerBy.offer(NOW, farCap, ZONE).latestMillis)
        assertEquals(farthest, AnswerBy.resolve(OnDate(LocalDate.of(2027, 1, 30)), NOW, farCap, ZONE))
    }

    @Test
    fun `a week across the clock change keeps its time of day`() {
        // The clocks go back on Sunday 25 October: seven days of 24 hours would read 09:15.
        val beforeTheChange = at("2026-10-20T10:07")

        assertEquals(
            at("2026-10-27T10:15"),
            AnswerBy.resolve(Preset(AnswerByPreset.WEEK), beforeTheChange, null, ZONE)
        )
    }

    @Test
    fun `the pick survives its saved form`() {
        val picks = listOf(
            Preset(AnswerByPreset.TOMORROW),
            Preset(AnswerByPreset.THREE_DAYS),
            Preset(AnswerByPreset.WEEK),
            OnDate(date(10, 20))
        )
        picks.forEach { pick ->
            assertEquals(pick, AnswerByPick.decode(AnswerByPick.encode(pick)), "$pick")
        }
        assertEquals("TOMORROW", AnswerByPick.encode(Preset(AnswerByPreset.TOMORROW)))
        assertEquals("THREE_DAYS", AnswerByPick.encode(Preset(AnswerByPreset.THREE_DAYS)))
        assertEquals("WEEK", AnswerByPick.encode(Preset(AnswerByPreset.WEEK)))
        assertEquals("DATE:2026-10-20", AnswerByPick.encode(OnDate(date(10, 20))))

        listOf("garbage", "", "DATE:", "DATE:not-a-date", "DATE:2026-02-30", "tomorrow").forEach { text ->
            assertEquals(AnswerByPick.DEFAULT, AnswerByPick.decode(text), text)
        }
        assertEquals(AnswerByPick.DEFAULT, AnswerByPick.decode(null))
    }
}
